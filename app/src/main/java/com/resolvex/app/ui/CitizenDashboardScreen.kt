package com.resolvex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.navigation.NavController
import com.resolvex.app.R
import com.resolvex.app.data.Complaint
import com.resolvex.app.data.ComplaintRepository
import com.resolvex.app.data.ComplaintStatus
import com.resolvex.app.data.NotificationItem
import com.resolvex.app.data.NotificationRepository
import com.resolvex.app.data.SessionManager
import com.resolvex.app.ui.theme.ElectricBlue
import com.resolvex.app.ui.theme.ElectricCyan
import com.resolvex.app.ui.theme.NavyCard
import com.resolvex.app.ui.theme.NavyDark
import com.resolvex.app.ui.theme.NavyDeep
import com.resolvex.app.ui.theme.NeonPurple
import com.resolvex.app.ui.theme.SoftCyan
import com.resolvex.app.ui.theme.TextMuted
import com.resolvex.app.ui.theme.TextPrimary
import com.resolvex.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CitizenDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val complaintRepo = remember { ComplaintRepository.getInstance(context) }
    val notifRepo = remember { NotificationRepository.getInstance(context) }
    val currentSession = remember { sessionManager.getSession() }

    var myComplaints by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var publicComplaints by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var unreadNotifCount by remember { mutableStateOf(0) }

    // Dialog & tab states
    var activeTab by remember { mutableStateOf("MY_COMPLAINTS") } // "MY_COMPLAINTS", "COMMUNITY", "TRANSPARENCY"
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showCharterDialog by remember { mutableStateOf(false) }

    fun refreshData() {
        val userEmail = currentSession?.email ?: ""
        myComplaints = complaintRepo.getComplaintsForCitizen(userEmail)
        publicComplaints = complaintRepo.getPublicComplaints()
        unreadNotifCount = notifRepo.getUnreadCount(userEmail)
        complaintRepo.checkAndTriggerSlaEscalations()
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            NavyDeep,
            NavyDark,
            Color(0xFF040817)
        )
    )

    val activeCount = myComplaints.count { it.status != ComplaintStatus.RESOLVED && it.status != ComplaintStatus.REJECTED }
    val resolvedCount = myComplaints.count { it.status == ComplaintStatus.RESOLVED }

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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ── Top Header Bar ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.resolvex_logo),
                        contentDescription = "ResolveX Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Column {
                        Text(
                            text = "ResolveX",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Citizen Portal",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Action icons: Notifications & Profile
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Notification Bell
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NavyCard)
                            .border(1.dp, Color(0xFF233554), CircleShape)
                            .clickable {
                                showNotificationCenter = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔔", fontSize = 16.sp)
                        if (unreadNotifCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF5252)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadNotifCount > 9) "9+" else "$unreadNotifCount",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Profile Avatar
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricCyan, ElectricBlue)))
                            .clickable {
                                showProfileDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (currentSession?.fullName?.take(1) ?: "C").uppercase(),
                            color = NavyDeep,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Citizen Welcome Card ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF132349),
                                Color(0xFF0D1730)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(ElectricCyan.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.3f))),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Namaste,",
                                color = SoftCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = currentSession?.fullName?.ifBlank { "Citizen" } ?: "Citizen",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Occupation badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ElectricCyan.copy(alpha = 0.15f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentSession?.occupation ?: "General Citizen",
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Your civic participation helps municipal authorities build cleaner, safer, and better-governed communities across India.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── System Stats Summary ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CitizenStatCard(title = "My Grievances", value = "${myComplaints.size}", modifier = Modifier.weight(1f))
                CitizenStatCard(title = "In Progress", value = "$activeCount", modifier = Modifier.weight(1f))
                CitizenStatCard(title = "Resolved", value = "$resolvedCount", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Primary Action CTAs ───────────────────────────────────────
            Button(
                onClick = { navController.navigate("raise_complaint") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(ElectricCyan, ElectricBlue)),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Raise a Civic Complaint",
                        color = NavyDeep,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Tool Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { navController.navigate("civic_map") },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D2040)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🗺️", fontSize = 13.sp)
                        Text(text = "Live Map", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Button(
                    onClick = { navController.navigate("track_complaint") },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132247)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🔍", fontSize = 13.sp)
                        Text(text = "Track", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Button(
                    onClick = { navController.navigate("scheme_assistant") },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B143A)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🤖", fontSize = 13.sp)
                        Text(text = "Schemes", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Dedicated Track Complaint Lookup Card
            TrackComplaintLookupCard(
                onTrack = { searchId ->
                    if (searchId.isNotBlank()) {
                        navController.navigate("track_complaint/$searchId")
                    } else {
                        navController.navigate("track_complaint")
                    }
                }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // ── Section Tabs Switcher: My Complaints vs Community Feed vs Transparency ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabPill(
                    label = "My Complaints (${myComplaints.size})",
                    isSelected = activeTab == "MY_COMPLAINTS",
                    modifier = Modifier.weight(1f)
                ) { activeTab = "MY_COMPLAINTS" }

                TabPill(
                    label = "Community Issues (${publicComplaints.size})",
                    isSelected = activeTab == "COMMUNITY",
                    modifier = Modifier.weight(1f)
                ) { activeTab = "COMMUNITY" }

                TabPill(
                    label = "Transparency",
                    isSelected = activeTab == "TRANSPARENCY",
                    modifier = Modifier.weight(1f)
                ) { activeTab = "TRANSPARENCY" }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Tab 1: My Complaints ──────────────────────────────────────
            if (activeTab == "MY_COMPLAINTS") {
                if (myComplaints.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(NavyCard)
                            .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(18.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "📋", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Complaints Raised Yet",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "When you report civic issues like potholes, streetlights or water leaks, they will appear here with live tracking.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    myComplaints.forEach { complaint ->
                        CitizenComplaintCard(
                            complaint = complaint,
                            onTrackClick = {
                                navController.navigate("track_complaint/${complaint.id}")
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // ── Tab 2: Community Issues (Public Grievances & Upvoting) ──
            if (activeTab == "COMMUNITY") {
                if (publicComplaints.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(NavyCard)
                            .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(18.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🌐", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "No Public Issues Found", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Issues marked public by citizens will appear here for community solidarity and upvoting.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    publicComplaints.forEach { publicItem ->
                        CommunityIssueCard(
                            complaint = publicItem,
                            currentUserEmail = currentSession?.email ?: "",
                            onVoteClick = {
                                val userEmail = currentSession?.email ?: ""
                                if (userEmail.isNotBlank()) {
                                    complaintRepo.toggleSupportVote(publicItem.id, userEmail)
                                    refreshData()
                                }
                            },
                            onTrackClick = {
                                navController.navigate("track_complaint/${publicItem.id}")
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // ── Tab 3: Civic Transparency Dashboard ───────────────────────
            if (activeTab == "TRANSPARENCY") {
                val totalGovComplaints = complaintRepo.getAllComplaints().size
                val resolvedGov = complaintRepo.getAllComplaints().count { it.status == ComplaintStatus.RESOLVED }
                val rate = if (totalGovComplaints > 0) (resolvedGov * 100) / totalGovComplaints else 0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(NavyCard)
                        .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏛️", fontSize = 18.sp)
                        Column {
                            Text("Official Civic Transparency Portal", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Direct accountability and public service turnaround metrics", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E2E4E), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransparencyMetricBox("Total Grievances", "$totalGovComplaints", ElectricCyan, Modifier.weight(1f))
                        TransparencyMetricBox("Resolved Grievances", "$resolvedGov", Color(0xFF4ADE80), Modifier.weight(1f))
                        TransparencyMetricBox("Resolution Rate", "$rate%", NeonPurple, Modifier.weight(1f))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.5.dp, Color(0xFF233554), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📜 Citizen Charter Guarantee", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Every citizen complaint in ResolveX is governed by municipal SLA timelines: Critical/Hazard issues within 24h, High urgency within 48h, and General civic repairs within 72h. Citizens retain the sovereign right to inspect and reopen unsatisfactory closures.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Notification Center Dialog ────────────────────────────────────
        if (showNotificationCenter) {
            val userEmail = currentSession?.email ?: ""
            val notifs = notifRepo.getNotificationsForUser(userEmail)

            AlertDialog(
                onDismissRequest = { showNotificationCenter = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notifications (${notifs.size})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (notifs.any { !it.isRead }) {
                            Text(
                                text = "Mark All Read",
                                color = SoftCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    notifRepo.markAllAsRead(userEmail)
                                    refreshData()
                                }
                            )
                        }
                    }
                },
                text = {
                    if (notifs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No notifications yet", color = TextMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notifs) { notif ->
                                val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notif.timestamp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (notif.isRead) NavyDark else Color(0xFF162544))
                                        .border(0.5.dp, if (notif.isRead) Color(0xFF1E2E4E) else ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            notifRepo.markAsRead(notif.id)
                                            refreshData()
                                            if (notif.complaintId != null) {
                                                showNotificationCenter = false
                                                navController.navigate("track_complaint/${notif.complaintId}")
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(notif.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(timeStr, color = TextMuted, fontSize = 10.sp)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(notif.message, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showNotificationCenter = false },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text("Close", color = NavyDeep, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Citizen Profile & Account Settings Dialog ─────────────────────
        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                title = { Text("Citizen Account Profile", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProfileInfoRow("Full Name", currentSession?.fullName ?: "Citizen")
                        ProfileInfoRow("Email Address", currentSession?.email ?: "")
                        ProfileInfoRow("Mobile", currentSession?.mobile ?: "")
                        ProfileInfoRow("Occupation", currentSession?.occupation ?: "General Citizen")
                        ProfileInfoRow("Platform Role", currentSession?.role?.displayName ?: "Citizen")

                        HorizontalDivider(color = Color(0xFF1E2E4E), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F1A30))
                                .clickable {
                                    showProfileDialog = false
                                    showCharterDialog = true
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📜 Citizen Charter & Rights", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("View →", color = SoftCyan, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showProfileDialog = false
                            showLogoutDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("Close", color = SoftCyan)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // ── Citizen Charter & Legal Rights Dialog ─────────────────────────
        if (showCharterDialog) {
            AlertDialog(
                onDismissRequest = { showCharterDialog = false },
                title = { Text("ResolveX Citizen Charter", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ResolveX is committed to zero-tolerance corruption, public transparency, and digital accountability.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        CharterPoint("1. Service Level Agreements", "All civic complaints must be officially reviewed within 24 hours of submission.")
                        CharterPoint("2. Evidence Integrity", "Officers cannot mark an issue resolved without verifiable inspection notes and action proof.")
                        CharterPoint("3. Sovereign Reopen Right", "If a citizen is unsatisfied with resolution quality, they may reopen the query with feedback.")
                        CharterPoint("4. Privacy & Protection", "Private complaints remain strictly isolated to the authorized municipal officers.")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showCharterDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text("Understood", color = NavyDeep, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(18.dp)
            )
        }

        // ── Logout Confirmation Dialog ────────────────────────────────────
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Sign Out", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to sign out from your citizen session?", color = TextSecondary) },
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
    }
}

@Composable
private fun TabPill(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .then(
                if (isSelected) Modifier.background(Brush.horizontalGradient(listOf(ElectricCyan, ElectricBlue)))
                else Modifier.background(Color.Transparent)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) NavyDeep else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun TransparencyMetricBox(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NavyDark)
            .border(0.5.dp, Color(0xFF233554), RoundedCornerShape(10.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CharterPoint(title: String, desc: String) {
    Column {
        Text(title, color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
fun CitizenStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NavyCard)
            .border(0.5.dp, Color(0xFF233554), RoundedCornerShape(14.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = ElectricCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CommunityIssueCard(
    complaint: Complaint,
    currentUserEmail: String,
    onVoteClick: () -> Unit,
    onTrackClick: () -> Unit
) {
    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(complaint.createdAt))
    val isVoted = complaint.supportedByEmails.contains(currentUserEmail.lowercase().trim())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF233554), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(complaint.id, color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(complaint.category, color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF0F2537))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(complaint.status.displayName, color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(complaint.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(complaint.description, color = TextSecondary, fontSize = 11.sp, maxLines = 2, lineHeight = 15.sp)

            if (complaint.locationAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📍", fontSize = 10.sp)
                    Text(complaint.locationAddress, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer with Upvote Button and Track Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Support / Upvote Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isVoted) ElectricCyan else Color(0xFF142442))
                        .border(1.dp, ElectricCyan, RoundedCornerShape(8.dp))
                        .clickable { onVoteClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (isVoted) "👍" else "🤝", fontSize = 12.sp)
                        Text(
                            text = "${complaint.supportCount} Support",
                            color = if (isVoted) NavyDeep else ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(onClick = onTrackClick) {
                    Text("Track Details →", color = SoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CitizenComplaintCard(
    complaint: Complaint,
    onTrackClick: () -> Unit = {}
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
            .clickable { onTrackClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Complaint ID & Category
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

                // Status Badge
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

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = complaint.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = complaint.description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Assigned Department
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "🏛️", fontSize = 11.sp)
                Text(
                    text = "Dept: ${complaint.assignedDepartment}",
                    color = if (complaint.assignedDepartment.contains("Pending")) TextMuted else ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (complaint.locationAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "📍", fontSize = 11.sp)
                    Text(
                        text = complaint.locationAddress,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Latest admin remarks preview (if any)
            if (!complaint.adminNotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF191736))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(text = "💬", fontSize = 11.sp)
                    Text(
                        text = "Admin Note: ${complaint.adminNotes}",
                        color = NeonPurple,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Media attachment indicator
            if (complaint.imageUri != null || complaint.videoUri != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (complaint.imageUri != null) {
                        Text(text = "📷 Photo Attached", color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (complaint.videoUri != null) {
                        Text(text = "🎥 Video Attached", color = NeonPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Date & Track CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Submitted: $dateStr",
                    color = TextMuted,
                    fontSize = 10.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Track Timeline",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "→",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TrackComplaintLookupCard(onTrack: (String) -> Unit) {
    var trackInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF233554), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "🛰️", fontSize = 14.sp)
                    Text(
                        text = "Track Complaint Status",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Live Timeline",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter any Complaint Reference ID to track current stage, department handling, and official remarks.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = trackInput,
                    onValueChange = { trackInput = it },
                    placeholder = { Text("Complaint ID (e.g. RX-2026-...)", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF233554),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = NavyDark,
                        unfocusedContainerColor = NavyDark
                    )
                )

                Button(
                    onClick = { onTrack(trackInput.trim()) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text(text = "Track", color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
