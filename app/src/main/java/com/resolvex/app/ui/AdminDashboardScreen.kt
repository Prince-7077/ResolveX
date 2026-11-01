package com.resolvex.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.resolvex.app.R
import com.resolvex.app.ai.AiConfigManager
import com.resolvex.app.data.AdminEvidence
import com.resolvex.app.data.Complaint
import com.resolvex.app.data.ComplaintRepository
import com.resolvex.app.data.ComplaintStatus
import com.resolvex.app.data.SessionManager
import com.resolvex.app.data.UserRepository
import com.resolvex.app.data.UserRole
import com.resolvex.app.data.UserAccount
import com.resolvex.app.data.UserStatus
import com.resolvex.app.data.AuditLogEntry
import com.resolvex.app.location.GoogleMapsLauncher
import com.resolvex.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val complaintRepo = remember { ComplaintRepository.getInstance(context) }
    val aiConfig = remember { AiConfigManager.getInstance(context) }
    val userRepo = remember { UserRepository.getInstance(context) }
    val currentSession = remember { sessionManager.getSession() }

    var allComplaints by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf<ComplaintStatus?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Admin portal sub-tabs
    var adminPortalTab by remember { mutableStateOf("QUEUE") } // "QUEUE", "ROOT_CAUSE", "ACCOUNTS", "AUDIT"
    var allAccounts by remember { mutableStateOf<List<UserAccount>>(emptyList()) }
    var allAuditLogs by remember { mutableStateOf<List<AuditLogEntry>>(emptyList()) }
    var showCreateOfficerDialog by remember { mutableStateOf(false) }

    // Active workflow query dialog
    var activeWorkflowComplaint by remember { mutableStateOf<Complaint?>(null) }

    fun refreshList() {
        val all = complaintRepo.getAllComplaints()
        allComplaints = if (currentSession?.role == UserRole.DEPARTMENT_OFFICER && !currentSession.department.isNullOrBlank()) {
            all.filter {
                it.assignedDepartment.contains(currentSession.department!!, ignoreCase = true) ||
                it.category.contains(currentSession.department!!, ignoreCase = true)
            }
        } else {
            all
        }
        activeWorkflowComplaint?.let { active ->
            activeWorkflowComplaint = allComplaints.firstOrNull { it.id == active.id }
        }
        if (currentSession?.role == UserRole.SUPER_ADMIN) {
            allAccounts = userRepo.getAllUsers()
            allAuditLogs = userRepo.getAuditLogs()
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    val filteredList = if (selectedFilter == null) {
        allComplaints
    } else {
        allComplaints.filter { it.status == selectedFilter }
    }

    val submittedCount = allComplaints.count { it.status == ComplaintStatus.SUBMITTED }
    val underReviewCount = allComplaints.count { it.status == ComplaintStatus.UNDER_REVIEW || it.status == ComplaintStatus.EVIDENCE_REQUESTED || it.status == ComplaintStatus.VERIFIED }
    val inProgressCount = allComplaints.count { it.status == ComplaintStatus.ASSIGNED || it.status == ComplaintStatus.IN_PROGRESS || it.status == ComplaintStatus.ACTION_TAKEN }
    val resolvedCount = allComplaints.count { it.status == ComplaintStatus.RESOLVED }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            NavyDeep,
            NavyDark,
            Color(0xFF040817)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // ── Top Navigation Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate("citizen_dashboard")
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("← Back", color = SoftCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Image(
                        painter = painterResource(id = R.drawable.resolvex_logo),
                        contentDescription = "ResolveX Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Column {
                        Text(
                            text = "ResolveX",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Admin Portal",
                            color = NeonPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Map shortcut
                    TextButton(
                        onClick = { navController.navigate("civic_map") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("🗺️ Map", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // AI Key settings
                    TextButton(
                        onClick = { showApiKeyDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("⚙️ AI Key", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Sign Out
                    TextButton(
                        onClick = { showLogoutDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Sign Out",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Admin Profile Badge Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF1B1936),
                                Color(0xFF11172E)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(NeonPurple.copy(alpha = 0.7f), ElectricBlue.copy(alpha = 0.4f))),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MUNICIPAL GOVERNANCE WORKFLOW",
                            color = NeonPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = currentSession?.fullName?.ifBlank { "Administrator" } ?: "Administrator",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentSession?.email ?: "admin@resolvex.gov.in",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(NeonPurple.copy(alpha = 0.2f))
                            .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = currentSession?.department?.ifBlank { "Admin" } ?: "Admin",
                            color = NeonPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // System Metrics Cards (Responsive 4-item row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AdminStatCard(title = "Submitted", count = "$submittedCount", color = ElectricCyan, modifier = Modifier.weight(1f))
                AdminStatCard(title = "In Review", count = "$underReviewCount", color = SoftCyan, modifier = Modifier.weight(1f))
                AdminStatCard(title = "In Progress", count = "$inProgressCount", color = Color(0xFFFBBF24), modifier = Modifier.weight(1f))
                AdminStatCard(title = "Resolved", count = "$resolvedCount", color = Color(0xFF4ADE80), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-tabs Row (Queue, AI Root Cause, Accounts, Audit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AdminTabButton("Queue", adminPortalTab == "QUEUE", Modifier.weight(1f)) { adminPortalTab = "QUEUE" }
                AdminTabButton("AI Root Cause", adminPortalTab == "ROOT_CAUSE", Modifier.weight(1.2f)) { adminPortalTab = "ROOT_CAUSE" }
                if (currentSession?.role == UserRole.SUPER_ADMIN) {
                    AdminTabButton("Accounts", adminPortalTab == "ACCOUNTS", Modifier.weight(1f)) { adminPortalTab = "ACCOUNTS" }
                    AdminTabButton("Audit Logs", adminPortalTab == "AUDIT", Modifier.weight(1f)) { adminPortalTab = "AUDIT" }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (adminPortalTab == "QUEUE") {
                // Filter Chips Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Civic Queries Queue (${filteredList.size})",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "↻ Refresh Queue",
                        color = SoftCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { refreshList() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Pills Row (Horizontally scrollable to eliminate all overflow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterPill(title = "All (${allComplaints.size})", isSelected = selectedFilter == null, onClick = { selectedFilter = null })
                    ComplaintStatus.values().forEach { st ->
                        val count = allComplaints.count { it.status == st }
                        FilterPill(title = "${st.displayName} ($count)", isSelected = selectedFilter == st, onClick = { selectedFilter = st })
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Empty State OR Complaint List
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NavyCard)
                            .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(16.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🛡️", fontSize = 34.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Complaints in Selected View",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Complaints submitted by citizens will appear here for query inspection, evidence attachment, department assignment, and formal closure.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    filteredList.forEach { complaint ->
                        AdminComplaintManagementCard(
                            complaint = complaint,
                            onOpenWorkflow = {
                                activeWorkflowComplaint = complaint
                            },
                            onTrackView = {
                                navController.navigate("track_complaint/${complaint.id}")
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            } else if (adminPortalTab == "ROOT_CAUSE") {
                AiRootCauseDetectiveSection(allComplaints = allComplaints)
            } else if (adminPortalTab == "ACCOUNTS") {
                SuperAdminAccountsSection(
                    accounts = allAccounts,
                    onCreateClick = { showCreateOfficerDialog = true },
                    onToggleStatus = { account ->
                        val newStatus = if (account.status == UserStatus.ACTIVE) UserStatus.SUSPENDED else UserStatus.ACTIVE
                        userRepo.updateUserStatus(account.id, newStatus, performedBy = currentSession?.email ?: "superadmin@resolvex.gov.in")
                        allAccounts = userRepo.getAllUsers()
                        allAuditLogs = userRepo.getAuditLogs()
                        Toast.makeText(context, "${account.fullName} status updated to ${newStatus.displayName}", Toast.LENGTH_SHORT).show()
                    }
                )
            } else if (adminPortalTab == "AUDIT") {
                SuperAdminAuditSection(auditLogs = allAuditLogs)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Active 4-Step Query Management Workflow Dialog
        activeWorkflowComplaint?.let { complaint ->
            AdminQueryWorkflowDialog(
                complaint = complaint,
                currentAdminName = currentSession?.fullName?.ifBlank { "Municipal Administrator" } ?: "Municipal Administrator",
                onDismiss = {
                    activeWorkflowComplaint = null
                    refreshList()
                },
                complaintRepo = complaintRepo,
                onComplaintUpdated = {
                    refreshList()
                }
            )
        }

        // API Key Settings Dialog
        if (showApiKeyDialog) {
            var inputKey by remember { mutableStateOf(aiConfig.getGeminiApiKey() ?: "") }
            AlertDialog(
                onDismissRequest = { showApiKeyDialog = false },
                title = { Text("Google Gemini API Configuration", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Enter your Google Gemini API key to enable cloud multimodal complaint intelligence and multilingual welfare assistant.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = inputKey,
                            onValueChange = { inputKey = it },
                            placeholder = { Text("AIzaSy...", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = Color(0xFF233554),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (aiConfig.isCloudBackendConnected()) {
                            Text("✓ Gemini Cloud Engine Connected", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("ℹ️ Running in On-Device Semantic Engine mode (No key set)", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            aiConfig.setGeminiApiKey(inputKey.trim())
                            showApiKeyDialog = false
                            Toast.makeText(context, "API Key updated successfully", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text("Save Key", color = NavyDeep, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("Close", color = SoftCyan)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Exit Administrator Session", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to sign out from the administrative console?", color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            sessionManager.clearSession()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel", color = SoftCyan)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // Create Department Officer / Admin Dialog (Super Admin only)
        if (showCreateOfficerDialog) {
            var newName by remember { mutableStateOf("") }
            var newEmail by remember { mutableStateOf("") }
            var newPassword by remember { mutableStateOf("") }
            var newDept by remember { mutableStateOf("Roads & Infrastructure") }
            var newRole by remember { mutableStateOf(UserRole.DEPARTMENT_OFFICER) }
            var createError by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { showCreateOfficerDialog = false },
                title = { Text("Create Official Account", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Provision an authorized officer or municipal administrator account.", color = TextSecondary, fontSize = 11.sp)
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Official Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { newEmail = it },
                            label = { Text("Government Email (@resolvex.gov.in)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Temporary Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newDept,
                            onValueChange = { newDept = it },
                            label = { Text("Department / Municipal Ward") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (newRole == UserRole.DEPARTMENT_OFFICER) NeonPurple else NavyCard)
                                    .clickable { newRole = UserRole.DEPARTMENT_OFFICER }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Department Officer", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (newRole == UserRole.ADMIN) NeonPurple else NavyCard)
                                    .clickable { newRole = UserRole.ADMIN }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Municipal Admin", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (createError != null) {
                            Text(createError ?: "", color = Color(0xFFFF5252), fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isBlank() || newEmail.isBlank() || newPassword.length < 6) {
                                createError = "Please enter valid name, email and password (min 6 chars)"
                                return@Button
                            }
                            val performedByEmail = currentSession?.email ?: "superadmin@resolvex.gov.in"
                            val res = if (newRole == UserRole.ADMIN) {
                                userRepo.createAdmin(
                                    fullName = newName.trim(),
                                    email = newEmail.trim(),
                                    mobile = "9876500000",
                                    department = newDept.trim(),
                                    employeeId = "ADM-${(100..999).random()}",
                                    plainPassword = newPassword,
                                    performedBy = performedByEmail
                                )
                            } else {
                                userRepo.createDepartmentOfficer(
                                    fullName = newName.trim(),
                                    email = newEmail.trim(),
                                    mobile = "9876500000",
                                    department = newDept.trim(),
                                    employeeId = "OFF-${(100..999).random()}",
                                    plainPassword = newPassword,
                                    performedBy = performedByEmail
                                )
                            }
                            res.onSuccess {
                                showCreateOfficerDialog = false
                                allAccounts = userRepo.getAllUsers()
                                allAuditLogs = userRepo.getAuditLogs()
                                Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                createError = it.message ?: "Failed to create account"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                    ) {
                        Text("Create Account", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateOfficerDialog = false }) {
                        Text("Cancel", color = SoftCyan)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun FilterPill(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NeonPurple else NavyCard)
            .border(1.dp, if (isSelected) NeonPurple else Color(0xFF233554), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun AdminStatCard(title: String, count: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NavyCard)
            .border(0.5.dp, Color(0xFF233554), RoundedCornerShape(12.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = count, color = color, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = title, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun AdminComplaintManagementCard(
    complaint: Complaint,
    onOpenWorkflow: () -> Unit,
    onTrackView: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(complaint.createdAt))
    val statusColor = when (complaint.status) {
        ComplaintStatus.SUBMITTED          -> ElectricCyan
        ComplaintStatus.UNDER_REVIEW       -> SoftCyan
        ComplaintStatus.EVIDENCE_REQUESTED -> Color(0xFFF97316)
        ComplaintStatus.VERIFIED           -> ElectricBlue
        ComplaintStatus.ASSIGNED           -> NeonPurple
        ComplaintStatus.IN_PROGRESS        -> Color(0xFFFBBF24)
        ComplaintStatus.ACTION_TAKEN       -> Color(0xFF22D3EE)
        ComplaintStatus.RESOLVED           -> Color(0xFF4ADE80)
        ComplaintStatus.REJECTED           -> Color(0xFFFF5252)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF233554), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            // Header Row: ID, Category & Live Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = complaint.id,
                        color = ElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = complaint.category,
                        color = SoftCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = complaint.status.displayName,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Citizen Info Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0C162E))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Citizen: ${complaint.citizenName} • ${complaint.citizenPhone.ifBlank { complaint.citizenEmail }}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title & Description
            Text(
                text = complaint.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = complaint.description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata: Department, Evidence and Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dept: ${complaint.assignedDepartment}",
                    color = if (complaint.assignedDepartment.contains("Pending")) TextMuted else ElectricCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                if (complaint.adminEvidenceList.isNotEmpty()) {
                    Text(
                        text = "📁 ${complaint.adminEvidenceList.size} Admin Evidence",
                        color = SoftCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(text = "Submitted: $dateStr", color = TextMuted, fontSize = 9.sp)

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(color = Color(0xFF1E2E4E), thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(10.dp))

            // Two Action Buttons (Proper wrapping, never cut off)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenWorkflow,
                    modifier = Modifier.weight(1.3f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚡", fontSize = 12.sp)
                        Text(
                            text = "Query Workflow",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onTrackView,
                    modifier = Modifier.weight(0.9f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132247))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔍", fontSize = 11.sp)
                        Text(
                            text = "Track View",
                            color = SoftCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Complete 4-Step Query Management Workflow Dialog
 * Step 1: View Query
 * Step 2: Upload Evidence (photos, videos, documents with live preview & remarks)
 * Step 3: Review and Assign (department, officer, remarks, request info)
 * Step 4: Resolution (explicit resolution note required, never auto-resolved)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQueryWorkflowDialog(
    complaint: Complaint,
    currentAdminName: String,
    onDismiss: () -> Unit,
    complaintRepo: ComplaintRepository,
    onComplaintUpdated: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) } // 0=View, 1=Evidence, 2=Assign, 3=Resolution

    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(complaint.createdAt))

    // Step 2 Evidence inputs
    var evidenceMediaType by remember { mutableStateOf("PHOTO") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var evidenceRemarks by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedMediaUri = uri
            selectedFileName = uri.lastPathSegment?.takeLast(20) ?: "evidence_file"
        }
    }

    // Step 3 Review & Assign inputs
    var assignedDept by remember(complaint.id) { mutableStateOf(complaint.assignedDepartment) }
    var assignedOfficer by remember(complaint.id) { mutableStateOf(complaint.assignedOfficer ?: "") }
    var internalRemarks by remember(complaint.id) { mutableStateOf(complaint.adminNotes ?: "") }
    var workflowStatus by remember(complaint.id) { mutableStateOf(complaint.status) }
    var isDeptDropdownOpen by remember { mutableStateOf(false) }
    var isStatusDropdownOpen by remember { mutableStateOf(false) }

    // Step 4 Resolution inputs
    var resolutionNote by remember { mutableStateOf(complaint.resolutionNote ?: "") }
    var rejectionReason by remember { mutableStateOf("") }

    val departments = listOf(
        "Roads & Infrastructure Division",
        "Water Supply & Sewerage Board",
        "Electricity & Power Distribution",
        "Solid Waste & Sanitation Wing",
        "Public Health & Disaster Response",
        "Parks & Urban Horticulture",
        "Traffic & Public Transport",
        "Municipal Governance Division"
    )

    val validWorkflowStatuses = listOf(
        ComplaintStatus.UNDER_REVIEW,
        ComplaintStatus.ASSIGNED,
        ComplaintStatus.IN_PROGRESS,
        ComplaintStatus.ACTION_TAKEN
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NavyDark)
                    .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                // Header: Reference ID & Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Query: ${complaint.id}",
                            color = ElectricCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Status: ${complaint.status.displayName}",
                            color = SoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("✕ Close", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Workflow Step Pills Navigation (Horizontal scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val stepLabels = listOf(
                        "1. View Details",
                        "2. Upload Evidence",
                        "3. Review & Assign",
                        "4. Resolution"
                    )
                    stepLabels.forEachIndexed { index, label ->
                        val isSelected = currentStep == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) NeonPurple else NavyCard)
                                .border(1.dp, if (isSelected) NeonPurple else Color(0xFF233554), RoundedCornerShape(8.dp))
                                .clickable { currentStep = index }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E2E4E), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                // ── Step Content (Scrollable) ──────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (currentStep) {
                        // ── STEP 1: View Query ──────────────────────────────
                        0 -> {
                            Text("STEP 1: QUERY DETAILS & CITIZEN REPORT", color = NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NavyCard)
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(complaint.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(complaint.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)

                                    HorizontalDivider(color = Color(0xFF1E2E4E), thickness = 0.5.dp)

                                    Text("Citizen: ${complaint.citizenName} (${complaint.citizenPhone.ifBlank { complaint.citizenEmail }})", color = SoftCyan, fontSize = 11.sp)
                                    Text("Category: ${complaint.category} • Priority: ${complaint.priority}", color = TextMuted, fontSize = 11.sp)
                                    Text("Location: ${complaint.locationAddress}", color = TextMuted, fontSize = 11.sp)

                                    if (complaint.latitude != null && complaint.longitude != null) {
                                        val latStr = String.format(Locale.US, "%.5f", complaint.latitude)
                                        val lonStr = String.format(Locale.US, "%.5f", complaint.longitude)
                                        Text("GPS Fix: $latStr° N, $lonStr° E", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    GoogleMapsLauncher.openLocation(
                                                        context = context,
                                                        latitude = complaint.latitude,
                                                        longitude = complaint.longitude,
                                                        label = complaint.title
                                                    )
                                                },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                                            ) {
                                                Text("🗺️ Open in Google Maps", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    GoogleMapsLauncher.openDirections(
                                                        context = context,
                                                        destinationLat = complaint.latitude,
                                                        destinationLon = complaint.longitude,
                                                        destinationName = complaint.title
                                                    )
                                                },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46))
                                            ) {
                                                Text("🧭 Get Directions", color = Color(0xFF6EE7B7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Text("Submitted: $dateStr", color = TextMuted, fontSize = 10.sp)

                                    if (complaint.imageUri != null || complaint.videoUri != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (complaint.imageUri != null) Text("📷 Citizen Photo Attached", color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            if (complaint.videoUri != null) Text("🎥 Citizen Video Attached", color = NeonPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // AI Analysis Insights Card (if available)
                            if (!complaint.aiSummary.isNullOrBlank() || !complaint.aiSuggestedCategory.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF120A28))
                                        .border(0.5.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("🤖 AI Complaint Intelligence", color = NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        complaint.aiSummary?.let { Text("Summary: $it", color = TextSecondary, fontSize = 11.sp) }
                                        complaint.aiSuggestedDepartment?.let { Text("Recommended Dept: $it", color = SoftCyan, fontSize = 11.sp) }
                                        complaint.aiSuggestedUrgency?.let { Text("Urgency Level: $it (${complaint.aiUrgencyReason ?: ""})", color = Color(0xFFFBBF24), fontSize = 11.sp) }
                                    }
                                }
                            }

                            // Activity History Log
                            Text("TRACKING HISTORY LOG", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            complaint.getEffectiveHistory().forEach { event ->
                                val evDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(event.timestamp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0C162E))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(event.title, color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(evDate, color = TextMuted, fontSize = 9.sp)
                                        }
                                        event.remarks?.let { Text(it, color = TextSecondary, fontSize = 10.sp) }
                                        event.updatedBy?.let { Text("By: $it", color = TextMuted, fontSize = 9.sp) }
                                    }
                                }
                            }
                        }

                        // ── STEP 2: Upload Evidence ─────────────────────────
                        1 -> {
                            Text("STEP 2: UPLOAD DEPARTMENT EVIDENCE", color = NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Attach site photos, inspection videos, or work order documents. Note: Uploading evidence will NOT automatically mark this query as resolved.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )

                            // Media Type Selector
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("PHOTO" to "📷 Photo", "VIDEO" to "🎥 Video", "DOCUMENT" to "📄 Document").forEach { (type, label) ->
                                    val isSel = evidenceMediaType == type
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) ElectricCyan.copy(alpha = 0.2f) else NavyCard)
                                            .border(1.dp, if (isSel) ElectricCyan else Color(0xFF233554), RoundedCornerShape(8.dp))
                                            .clickable { evidenceMediaType = type }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, color = if (isSel) ElectricCyan else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // File picker trigger
                            Button(
                                onClick = {
                                    val mimeType = when (evidenceMediaType) {
                                        "VIDEO" -> "video/*"
                                        "DOCUMENT" -> "application/pdf"
                                        else -> "image/*"
                                    }
                                    filePicker.launch(mimeType)
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132247))
                            ) {
                                Text(
                                    text = if (selectedMediaUri != null) "✓ Selected: $selectedFileName" else "📁 Select $evidenceMediaType File",
                                    color = ElectricCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Remarks input
                            OutlinedTextField(
                                value = evidenceRemarks,
                                onValueChange = { evidenceRemarks = it },
                                placeholder = { Text("Site inspection remarks or document details...", color = TextMuted, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonPurple,
                                    unfocusedBorderColor = Color(0xFF233554),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            // Attach Evidence button
                            Button(
                                onClick = {
                                    if (selectedMediaUri == null && evidenceRemarks.isBlank()) {
                                        Toast.makeText(context, "Please select a file or enter remarks", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val evidence = AdminEvidence(
                                        mediaUri = selectedMediaUri?.toString() ?: "attached://note",
                                        mediaType = evidenceMediaType,
                                        fileName = selectedFileName.ifBlank { "admin_remark_${System.currentTimeMillis()}" },
                                        remarks = evidenceRemarks.ifBlank { null },
                                        uploadedBy = currentAdminName
                                    )
                                    complaintRepo.addAdminEvidence(
                                        id = complaint.id,
                                        evidence = evidence,
                                        adminRemarks = evidenceRemarks.ifBlank { null },
                                        updatedBy = currentAdminName
                                    )
                                    Toast.makeText(context, "Evidence attached successfully. Query remains in progress.", Toast.LENGTH_SHORT).show()
                                    selectedMediaUri = null
                                    selectedFileName = ""
                                    evidenceRemarks = ""
                                    onComplaintUpdated()
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Attach Evidence to Query", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Existing attached evidence list
                            if (complaint.adminEvidenceList.isNotEmpty()) {
                                Text("ATTACHED EVIDENCE FILES (${complaint.adminEvidenceList.size})", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                complaint.adminEvidenceList.forEach { ev ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NavyCard)
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Text("${ev.mediaType}: ${ev.fileName}", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            ev.remarks?.let { Text(it, color = TextSecondary, fontSize = 10.sp) }
                                            Text("Uploaded by: ${ev.uploadedBy}", color = TextMuted, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // ── STEP 3: Review and Assign ────────────────────────
                        2 -> {
                            Text("STEP 3: REVIEW & ASSIGN", color = NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            // Department dropdown
                            Text("Assigned Department:", color = TextSecondary, fontSize = 11.sp)
                            ExposedDropdownMenuBox(
                                expanded = isDeptDropdownOpen,
                                onExpandedChange = { isDeptDropdownOpen = !isDeptDropdownOpen },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = assignedDept,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDeptDropdownOpen) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonPurple,
                                        unfocusedBorderColor = Color(0xFF233554),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = isDeptDropdownOpen,
                                    onDismissRequest = { isDeptDropdownOpen = false },
                                    modifier = Modifier.background(NavyCard)
                                ) {
                                    departments.forEach { dept ->
                                        DropdownMenuItem(
                                            text = { Text(dept, color = TextPrimary) },
                                            onClick = {
                                                assignedDept = dept
                                                isDeptDropdownOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Officer name field
                            Text("Assign Field Officer / Supervisor:", color = TextSecondary, fontSize = 11.sp)
                            OutlinedTextField(
                                value = assignedOfficer,
                                onValueChange = { assignedOfficer = it },
                                placeholder = { Text("e.g. Officer Rajesh Kumar", color = TextMuted, fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonPurple,
                                    unfocusedBorderColor = Color(0xFF233554),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            // Status dropdown
                            Text("Workflow Status:", color = TextSecondary, fontSize = 11.sp)
                            ExposedDropdownMenuBox(
                                expanded = isStatusDropdownOpen,
                                onExpandedChange = { isStatusDropdownOpen = !isStatusDropdownOpen },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = workflowStatus.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStatusDropdownOpen) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonPurple,
                                        unfocusedBorderColor = Color(0xFF233554),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = isStatusDropdownOpen,
                                    onDismissRequest = { isStatusDropdownOpen = false },
                                    modifier = Modifier.background(NavyCard)
                                ) {
                                    validWorkflowStatuses.forEach { st ->
                                        DropdownMenuItem(
                                            text = { Text(st.displayName, color = TextPrimary) },
                                            onClick = {
                                                workflowStatus = st
                                                isStatusDropdownOpen = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Internal remarks field
                            Text("Internal Administrative Instructions:", color = TextSecondary, fontSize = 11.sp)
                            OutlinedTextField(
                                value = internalRemarks,
                                onValueChange = { internalRemarks = it },
                                placeholder = { Text("Instructions for field crew or inspection notes...", color = TextMuted, fontSize = 12.sp) },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonPurple,
                                    unfocusedBorderColor = Color(0xFF233554),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            // Update Assignment button
                            Button(
                                onClick = {
                                    complaintRepo.assignAndReviewQuery(
                                        id = complaint.id,
                                        newStatus = workflowStatus,
                                        department = assignedDept,
                                        officer = assignedOfficer.ifBlank { null },
                                        internalRemarks = internalRemarks.ifBlank { null },
                                        updatedBy = currentAdminName
                                    )
                                    Toast.makeText(context, "Assignment and status updated to ${workflowStatus.displayName}", Toast.LENGTH_SHORT).show()
                                    onComplaintUpdated()
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                            ) {
                                Text("Update Assignment & Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // ── STEP 4: Resolution & Closure ────────────────────
                        3 -> {
                            Text("STEP 4: FORMAL RESOLUTION & CLOSURE", color = NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F1E38))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "⚠️ Resolution Rule: A query must NEVER be automatically marked as resolved after being raised or after uploading evidence. An authorized administrator must explicitly provide resolution notes before marking closure.",
                                    color = SoftCyan,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            // Resolution Note input
                            Text("Official Resolution Explanation / Note *", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = resolutionNote,
                                onValueChange = { resolutionNote = it },
                                placeholder = { Text("Detail the corrective action taken, inspection results, and contractor sign-off...", color = TextMuted, fontSize = 12.sp) },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4ADE80),
                                    unfocusedBorderColor = Color(0xFF233554),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            // Explicit Resolution button
                            Button(
                                onClick = {
                                    if (resolutionNote.trim().length < 5) {
                                        Toast.makeText(context, "Please provide a valid resolution note (min 5 chars)", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    complaintRepo.resolveComplaint(
                                        id = complaint.id,
                                        resolutionNote = resolutionNote.trim(),
                                        resolvedBy = currentAdminName
                                    )
                                    Toast.makeText(context, "Complaint ${complaint.id} marked as RESOLVED", Toast.LENGTH_SHORT).show()
                                    onComplaintUpdated()
                                    onDismiss()
                                },
                                enabled = resolutionNote.trim().length >= 5,
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Text("✓ Mark Officially Resolved", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFF1E2E4E), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(4.dp))

                            // Rejection Section
                            Text("Reject Query (If False / Duplicate / Out of Jurisdiction)", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = rejectionReason,
                                onValueChange = { rejectionReason = it },
                                placeholder = { Text("Mandatory reason for rejection...", color = TextMuted, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFF5252),
                                    unfocusedBorderColor = Color(0xFF233554),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Button(
                                onClick = {
                                    if (rejectionReason.trim().isBlank()) {
                                        Toast.makeText(context, "Rejection reason is required", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    complaintRepo.rejectComplaint(
                                        id = complaint.id,
                                        rejectionReason = rejectionReason.trim(),
                                        rejectedBy = currentAdminName
                                    )
                                    Toast.makeText(context, "Complaint ${complaint.id} REJECTED", Toast.LENGTH_SHORT).show()
                                    onComplaintUpdated()
                                    onDismiss()
                                },
                                enabled = rejectionReason.trim().isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B))
                            ) {
                                Text("Reject Complaint with Reason", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminTabButton(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NeonPurple else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun AiRootCauseDetectiveSection(allComplaints: List<Complaint>) {
    val categoryGroups = allComplaints.groupBy { it.category }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF150F2C))
                .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🕵️", fontSize = 18.sp)
                    Text("AI Civic Root Cause Detective", color = NeonPurple, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Analyzes civic complaint clusters and recurring infrastructure failures across municipal sectors. Distinguishes preliminary hypotheses from verified ground findings.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        if (allComplaints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NavyCard)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No complaints available for cluster pattern analysis", color = TextMuted, fontSize = 12.sp)
            }
        } else {
            categoryGroups.forEach { (category, list) ->
                val count = list.size
                val (hypothesis, recommendation) = when {
                    category.contains("Road", ignoreCase = true) ->
                        "Recurring asphalt erosion is strongly correlated with subsurface drainage blockages or unsealed trench utilities causing moisture entrapment under bitumen layers." to
                        "Mandate municipal trenching permits and joint inspection between Public Works and Water & Sewerage before laying topcoat asphalt."
                    category.contains("Water", ignoreCase = true) ->
                        "Recurrent pressure drop and pipe ruptures indicate aging distribution pipes and water hammer effects from manual sluice valve operation." to
                        "Deploy automated surge relief valves and replace cast iron mains with high-density polyethylene (HDPE) lines."
                    category.contains("Electric", ignoreCase = true) ->
                        "Frequent streetlight failures point to transformer phase imbalances and moisture penetration inside junction pedestals." to
                        "Install IP67-rated sealed junction boxes and scheduled thermal imaging audits of local feeder pillars."
                    category.contains("Sanitation", ignoreCase = true) || category.contains("Waste", ignoreCase = true) ->
                        "Overflow patterns align with peak weekend commercial waste generation exceeding secondary transfer bin capacity." to
                        "Increase collection frequency to twice daily in commercial markets and introduce QR-code verified waste pickup tracking."
                    else ->
                        "Repeated grievances suggest localized civic maintenance backlogs and delayed preventative service cycles in this sector." to
                        "Conduct targeted multi-departmental field inspection and reallocate municipal ward maintenance budget."
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavyCard)
                        .border(1.dp, Color(0xFF233554), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonPurple.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("$count Reports Clustered", color = NeonPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // AI Hypothesis
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0D24))
                                .border(0.5.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("💡 AI Root-Cause Hypothesis", color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(hypothesis, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                        }

                        // Preventive Action
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0C192E))
                                .border(0.5.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text("🛡️ Suggested Preventive Action", color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(recommendation, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                        }

                        // Disclaimer
                        Text(
                            text = "⚠️ Preliminary AI hypothesis based on complaint frequency. Requires physical ground audit prior to capital expenditure.",
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminAccountsSection(
    accounts: List<UserAccount>,
    onCreateClick: () -> Unit,
    onToggleStatus: (UserAccount) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Municipal User Accounts (${accounts.size})", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Role-based access management & personnel provisioning", color = TextMuted, fontSize = 11.sp)
            }

            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("+ New Officer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        accounts.forEach { acc ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyCard)
                    .border(1.dp, Color(0xFF233554), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc.fullName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(acc.email, color = TextMuted, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Role: ${acc.role.displayName}", color = SoftCyan, fontSize = 10.sp)
                            if (!acc.department.isNullOrBlank()) {
                                Text("• ${acc.department}", color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    }

                    Button(
                        onClick = { onToggleStatus(acc) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (acc.status == UserStatus.ACTIVE) Color(0xFF1E3A2F) else Color(0xFF3A1E1E)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (acc.status == UserStatus.ACTIVE) "Active" else "Suspended",
                            color = if (acc.status == UserStatus.ACTIVE) Color(0xFF4ADE80) else Color(0xFFFF5252),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminAuditSection(auditLogs: List<AuditLogEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("System Audit Logs (${auditLogs.size})", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Immutable Security Ledger", color = SoftCyan, fontSize = 10.sp)
        }

        if (auditLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyCard)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No security audit events recorded yet", color = TextMuted, fontSize = 11.sp)
            }
        } else {
            auditLogs.reversed().take(50).forEach { log ->
                val timeStr = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()).format(Date(log.timestamp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NavyDark)
                        .border(0.5.dp, Color(0xFF1E2E4E), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(log.action, color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(timeStr, color = TextMuted, fontSize = 9.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Actor: ${log.performedBy} → Target: ${log.target}", color = SoftCyan, fontSize = 10.sp)
                        if (!log.details.isNullOrBlank()) {
                            Text(log.details, color = TextSecondary, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
