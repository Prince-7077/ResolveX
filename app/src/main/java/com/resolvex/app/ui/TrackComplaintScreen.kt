package com.resolvex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.resolvex.app.location.GoogleMapsLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.resolvex.app.R
import com.resolvex.app.data.Complaint
import com.resolvex.app.data.ComplaintRepository
import com.resolvex.app.data.ComplaintStatus
import com.resolvex.app.data.TrackingEvent
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

private enum class TimelineStage(
    val status: ComplaintStatus,
    val stageName: String,
    val defaultDescription: String,
    val iconEmoji: String
) {
    SUBMITTED(
        ComplaintStatus.SUBMITTED,
        "Submitted",
        "Complaint registered in ResolveX system",
        "📝"
    ),
    UNDER_REVIEW(
        ComplaintStatus.UNDER_REVIEW,
        "Under Review",
        "Reviewed by municipal authority & evidence verified",
        "🛡️"
    ),
    ASSIGNED(
        ComplaintStatus.ASSIGNED,
        "Assigned",
        "Allocated to competent municipal department & officer",
        "🏛️"
    ),
    IN_PROGRESS(
        ComplaintStatus.IN_PROGRESS,
        "In Progress / Action",
        "Field crew dispatched / corrective action underway",
        "⚙️"
    ),
    RESOLVED(
        ComplaintStatus.RESOLVED,
        "Resolved",
        "Inspected and officially resolved with notes",
        "✅"
    )
}

private enum class StepState {
    COMPLETED,
    CURRENT,
    PENDING,
    REJECTED
}

@Composable
fun TrackComplaintScreen(
    navController: NavController,
    initialComplaintId: String = ""
) {
    val context = LocalContext.current
    val complaintRepo = remember { ComplaintRepository.getInstance(context) }

    var searchInput by remember { mutableStateOf(initialComplaintId) }
    var activeComplaintId by remember { mutableStateOf(initialComplaintId) }
    var currentComplaint by remember { mutableStateOf<Complaint?>(null) }
    var hasSearched by remember { mutableStateOf(initialComplaintId.isNotBlank()) }

    var showReopenDialog by remember { mutableStateOf(false) }
    var reopenReason by remember { mutableStateOf("") }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Complaint ID", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Complaint ID copied", Toast.LENGTH_SHORT).show()
    }

    fun loadComplaint(id: String) {
        val trimmed = id.trim()
        activeComplaintId = trimmed
        currentComplaint = if (trimmed.isNotBlank()) complaintRepo.getComplaintById(trimmed) else null
        hasSearched = true
    }

    LaunchedEffect(initialComplaintId) {
        if (initialComplaintId.isNotBlank()) {
            loadComplaint(initialComplaintId)
        }
    }

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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("citizen_dashboard")
                        }
                    }) {
                        Text("← Back", color = SoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Track Complaint",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (currentComplaint != null) {
                    Text(
                        text = "Refresh",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NavyCard)
                            .clickable {
                                loadComplaint(activeComplaintId)
                                Toast.makeText(context, "Tracking updated", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar for Complaint ID
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NavyCard)
                    .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "Look up by Unique Complaint ID",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = { searchInput = it },
                            placeholder = { Text("e.g. RX-2026-102948", color = TextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
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
                            onClick = {
                                if (searchInput.trim().isBlank()) {
                                    Toast.makeText(context, "Please enter a Complaint ID", Toast.LENGTH_SHORT).show()
                                } else {
                                    loadComplaint(searchInput)
                                }
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                        ) {
                            Text(text = "Track", color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val complaint = currentComplaint

            if (complaint == null) {
                // Empty Search State / Not Found State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(NavyCard)
                        .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(18.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (hasSearched && searchInput.isNotBlank()) "🔍" else "📍",
                            fontSize = 40.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (hasSearched && searchInput.isNotBlank()) "Complaint Not Found" else "No Complaint Selected",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hasSearched && searchInput.isNotBlank())
                                "No complaint matches ID '$searchInput'. Verify the reference code or open tracking directly from your complaints list."
                            else
                                "Enter a valid Complaint ID above or tap any of your registered complaints in 'My Complaints' to view live status.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                // Active Complaint Details & Visual Tracking Timeline
                ComplaintDetailsSection(
                    complaint = complaint,
                    onCopyId = {
                        copyToClipboard(complaint.id)
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Official Admin Remarks Card (or authentic empty state)
                AdminRemarksCard(adminNotes = complaint.adminNotes)

                Spacer(modifier = Modifier.height(20.dp))

                // Visual Vertical Lifecycle Timeline
                TimelineSection(complaint = complaint)

                Spacer(modifier = Modifier.height(20.dp))

                // Activity / Tracking History Log
                TrackingHistorySection(complaint = complaint)

                Spacer(modifier = Modifier.height(20.dp))

                // SLA & Escalation Status Card
                SlaStatusCard(complaint = complaint)

                // Before and After Evidence Card (if images or admin evidence exist)
                BeforeAfterEvidenceCard(complaint = complaint)

                // Citizen Feedback Section (when Resolved)
                if (complaint.status == ComplaintStatus.RESOLVED) {
                    Spacer(modifier = Modifier.height(20.dp))
                    CitizenFeedbackSection(
                        complaint = complaint,
                        onSubmitFeedback = { rating, comment ->
                            complaintRepo.recordCitizenFeedback(complaint.id, rating, comment, null, complaint.citizenEmail)
                            loadComplaint(complaint.id)
                            Toast.makeText(context, "Thank you! Feedback recorded.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Reopen Grievance Section (when Resolved or Rejected)
                if (complaint.status == ComplaintStatus.RESOLVED || complaint.status == ComplaintStatus.REJECTED) {
                    Spacer(modifier = Modifier.height(20.dp))
                    ReopenComplaintSection(
                        complaint = complaint,
                        onOpenReopenDialog = {
                            reopenReason = ""
                            showReopenDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Reopen Grievance Dialog
        if (showReopenDialog && currentComplaint != null) {
            AlertDialog(
                onDismissRequest = { showReopenDialog = false },
                title = { Text("Reopen Grievance #${currentComplaint?.id}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "If the reported civic issue was not fixed adequately or has resurfaced, state the problem clearly. The query will be returned to municipal officers for re-investigation.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedTextField(
                            value = reopenReason,
                            onValueChange = { reopenReason = it },
                            label = { Text("Reason for Reopening *") },
                            placeholder = { Text("e.g. Pothole filled with loose gravel and washed out again...", color = TextMuted) },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = Color(0xFF233554),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (reopenReason.trim().length >= 5) {
                                complaintRepo.reopenComplaint(currentComplaint!!.id, reopenReason.trim(), currentComplaint!!.citizenEmail)
                                showReopenDialog = false
                                loadComplaint(currentComplaint!!.id)
                                Toast.makeText(context, "Complaint reopened and submitted for re-investigation.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Please enter a reason with at least 5 characters.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Confirm Reopen", color = NavyDeep, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReopenDialog = false }) {
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
private fun ComplaintDetailsSection(
    complaint: Complaint,
    onCopyId: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date(complaint.createdAt))

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
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF132247),
                        Color(0xFF0C1630)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(statusColor.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.3f))),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            // Row 1: Complaint ID & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = complaint.id,
                        color = ElectricCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "📋 Copy",
                        color = SoftCyan,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A2E56))
                            .clickable { onCopyId() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Live Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(statusColor.copy(alpha = 0.2f))
                        .border(1.dp, statusColor.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = complaint.status.displayName,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = complaint.title,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Category & Priority
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = complaint.category,
                    color = SoftCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = "•", color = TextMuted, fontSize = 12.sp)
                Text(
                    text = "Priority: ${complaint.priority}",
                    color = if (complaint.priority.equals("High", true) || complaint.priority.equals("Emergency", true)) Color(0xFFFF6B6B) else TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description
            Text(
                text = complaint.description,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata Chips: Department & Submission Date
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyDark.copy(alpha = 0.7f))
                    .border(0.5.dp, Color(0xFF233554), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Department
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "🏛️", fontSize = 12.sp)
                    Text(text = "Assigned Department:", color = TextMuted, fontSize = 11.sp)
                    Text(
                        text = complaint.assignedDepartment,
                        color = if (complaint.assignedDepartment.contains("Pending", ignoreCase = true)) Color(0xFFFBBF24) else ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Submission Date
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "📅", fontSize = 12.sp)
                    Text(text = "Submitted On:", color = TextMuted, fontSize = 11.sp)
                    Text(text = dateStr, color = TextPrimary, fontSize = 11.sp)
                }

                // Location if available
                if (complaint.locationAddress.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "📍", fontSize = 12.sp)
                        Text(text = "Location:", color = TextMuted, fontSize = 11.sp)
                        Text(text = complaint.locationAddress, color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // Real GPS Coordinates & Google Maps Actions
                if (complaint.latitude != null && complaint.longitude != null) {
                    val latStr = String.format(java.util.Locale.US, "%.5f", complaint.latitude)
                    val lonStr = String.format(java.util.Locale.US, "%.5f", complaint.longitude)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "🛰️", fontSize = 12.sp)
                        Text(text = "GPS Fix:", color = TextMuted, fontSize = 11.sp)
                        Text(text = "$latStr° N, $lonStr° E", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A))
                        ) {
                            Text("🗺️ Google Maps", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46))
                        ) {
                            Text("🧭 Directions", color = Color(0xFF6EE7B7), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                // Attached Evidence indicators & preview
                if (complaint.imageUri != null || complaint.videoUri != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (complaint.imageUri != null) {
                                Text(text = "📷 Photo Evidence Attached", color = SoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (complaint.videoUri != null) {
                                Text(text = "🎥 Video Evidence", color = NeonPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (complaint.imageUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black)
                                    .border(0.5.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            ) {
                                AsyncImage(
                                    model = complaint.imageUri,
                                    contentDescription = "Grievance photo evidence",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // Department Uploaded Evidence (if any)
            if (complaint.adminEvidenceList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F1E38))
                        .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📁", fontSize = 13.sp)
                        Text("Department Evidence & Inspection Files", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    complaint.adminEvidenceList.forEach { ev ->
                        val icon = when (ev.mediaType) {
                            "VIDEO" -> "🎥"
                            "DOCUMENT" -> "📄"
                            else -> "📷"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(NavyDark)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(icon, fontSize = 12.sp)
                                Column {
                                    Text(ev.fileName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    if (!ev.remarks.isNullOrBlank()) {
                                        Text(ev.remarks, color = TextMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                            Text(
                                text = "By ${ev.uploadedBy}",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // AI Intelligence Section (read-only, shown only when AI data exists)
            val hasAiData = !complaint.aiSuggestedCategory.isNullOrBlank() ||
                    !complaint.aiSuggestedUrgency.isNullOrBlank() ||
                    !complaint.aiSummary.isNullOrBlank() ||
                    !complaint.possibleDuplicateOfId.isNullOrBlank()

            if (hasAiData) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF120A28))
                        .border(0.5.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🤖", fontSize = 13.sp)
                        Text("AI Complaint Intelligence", color = NeonPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!complaint.aiSummary.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F0C1E))
                                .padding(8.dp)
                        ) {
                            Text("📝 AI Summary", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(complaint.aiSummary!!, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }

                    if (!complaint.aiSuggestedCategory.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🏷️", fontSize = 11.sp)
                            Text("AI Category:", color = TextMuted, fontSize = 10.sp)
                            Text(complaint.aiSuggestedCategory!!, color = Color(0xFFA78BFA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!complaint.aiSuggestedDepartment.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🏛️", fontSize = 11.sp)
                            Text("AI Dept Suggestion:", color = TextMuted, fontSize = 10.sp)
                            Text(complaint.aiSuggestedDepartment!!, color = SoftCyan, fontSize = 11.sp)
                        }
                    }

                    if (!complaint.aiSuggestedUrgency.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⚠️", fontSize = 11.sp)
                            Text("AI Urgency:", color = TextMuted, fontSize = 10.sp)
                            val urgencyColor = when (complaint.aiSuggestedUrgency) {
                                "Critical" -> Color(0xFFFF5252)
                                "High"     -> Color(0xFFFF9800)
                                "Medium"   -> Color(0xFFFBBF24)
                                else       -> Color(0xFF4ADE80)
                            }
                            Text(
                                complaint.aiSuggestedUrgency!!,
                                color = urgencyColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!complaint.aiUrgencyReason.isNullOrBlank()) {
                                Text("• ${complaint.aiUrgencyReason}", color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    }

                    if (!complaint.possibleDuplicateOfId.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🔗", fontSize = 11.sp)
                            Text("Similar to:", color = TextMuted, fontSize = 10.sp)
                            Text(complaint.possibleDuplicateOfId!!, color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminRemarksCard(adminNotes: String?) {
    if (!adminNotes.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1B1938),
                            Color(0xFF0F1E38)
                        )
                    )
                )
                .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "🏛️", fontSize = 14.sp)
                        Text(
                            text = "LATEST OFFICIAL ADMIN REMARKS",
                            color = NeonPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(NeonPurple.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(text = "Official Record", color = NeonPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = adminNotes,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        // Requirement 9: Proper empty state if no admin notes yet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NavyCard)
                .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "💬", fontSize = 18.sp)
                Column {
                    Text(
                        text = "No Administrator Remarks Yet",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Official updates and instructions from municipal officers will appear here when reviewed.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineSection(complaint: Complaint) {
    val stages = TimelineStage.values()

    val currentStatus = complaint.status
    val isRejected = currentStatus == ComplaintStatus.REJECTED

    fun getStageOrder(status: ComplaintStatus): Int = when (status) {
        ComplaintStatus.SUBMITTED          -> 0
        ComplaintStatus.UNDER_REVIEW       -> 1
        ComplaintStatus.EVIDENCE_REQUESTED -> 1
        ComplaintStatus.VERIFIED           -> 1
        ComplaintStatus.ASSIGNED           -> 2
        ComplaintStatus.IN_PROGRESS        -> 3
        ComplaintStatus.ACTION_TAKEN       -> 3
        ComplaintStatus.RESOLVED           -> 4
        ComplaintStatus.REJECTED           -> 1
    }

    val currentOrder = getStageOrder(currentStatus)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resolution Stage Timeline",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Stage ${currentOrder + 1} of ${stages.size}",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            stages.forEachIndexed { index, stage ->
                val stageOrder = index
                val stepState = when {
                    isRejected && stageOrder == 1 -> StepState.REJECTED
                    isRejected && stageOrder > 1 -> StepState.PENDING
                    stageOrder < currentOrder -> StepState.COMPLETED
                    stageOrder == currentOrder -> StepState.CURRENT
                    else -> StepState.PENDING
                }

                val isLast = index == stages.size - 1

                // Check for recorded timestamp in history
                val matchingEvent = complaint.getEffectiveHistory().lastOrNull { it.status == stage.status }
                val timestampStr = when {
                    matchingEvent != null -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(matchingEvent.timestamp))
                    stage == TimelineStage.SUBMITTED -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(complaint.createdAt))
                    stepState == StepState.PENDING -> "Pending"
                    else -> null
                }

                TimelineNodeItem(
                    stage = stage,
                    state = stepState,
                    timestamp = timestampStr,
                    remarks = matchingEvent?.remarks,
                    isLast = isLast
                )
            }
        }
    }
}

@Composable
private fun TimelineNodeItem(
    stage: TimelineStage,
    state: StepState,
    timestamp: String?,
    remarks: String?,
    isLast: Boolean
) {
    val nodeColor = when (state) {
        StepState.COMPLETED -> ElectricCyan
        StepState.CURRENT -> ElectricBlue
        StepState.PENDING -> Color(0xFF233554)
        StepState.REJECTED -> Color(0xFFFF5252)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        // Node Icon & Connecting Line Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            // Circle Node
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            StepState.COMPLETED -> Brush.linearGradient(listOf(ElectricCyan, ElectricBlue))
                            StepState.CURRENT -> Brush.linearGradient(listOf(ElectricBlue, NeonPurple))
                            StepState.REJECTED -> Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFB71C1C)))
                            StepState.PENDING -> Brush.linearGradient(listOf(Color(0xFF131D33), Color(0xFF0F172A)))
                        }
                    )
                    .border(
                        width = if (state == StepState.CURRENT) 2.dp else 1.dp,
                        color = if (state == StepState.CURRENT) ElectricCyan.copy(alpha = pulseAlpha) else nodeColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (state) {
                        StepState.COMPLETED -> "✓"
                        StepState.REJECTED -> "✕"
                        StepState.CURRENT -> stage.iconEmoji
                        StepState.PENDING -> "${stage.ordinal + 1}"
                    },
                    color = if (state == StepState.PENDING) TextMuted else Color.White,
                    fontSize = if (state == StepState.COMPLETED || state == StepState.REJECTED) 12.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Connecting line to next node
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(
                            if (state == StepState.COMPLETED) ElectricCyan.copy(alpha = 0.7f)
                            else Color(0xFF1E2E4E)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stage.stageName,
                        color = when (state) {
                            StepState.COMPLETED -> ElectricCyan
                            StepState.CURRENT -> ElectricBlue
                            StepState.REJECTED -> Color(0xFFFF5252)
                            StepState.PENDING -> TextMuted
                        },
                        fontSize = 14.sp,
                        fontWeight = if (state == StepState.CURRENT || state == StepState.COMPLETED) FontWeight.Bold else FontWeight.Medium
                    )

                    if (state == StepState.CURRENT) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElectricCyan.copy(alpha = 0.2f))
                                .border(0.5.dp, ElectricCyan, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(text = "CURRENT", color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        color = if (state == StepState.PENDING) TextMuted else SoftCyan,
                        fontSize = 10.sp,
                        fontWeight = if (state == StepState.PENDING) FontWeight.Normal else FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = stage.defaultDescription,
                color = if (state == StepState.PENDING) TextMuted.copy(alpha = 0.7f) else TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            // Remarks specific to this stage (if available)
            if (!remarks.isNullOrBlank() && state != StepState.PENDING) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0C162E))
                        .border(0.5.dp, Color(0xFF233554), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Remark: $remarks",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingHistorySection(complaint: Complaint) {
    val history = complaint.getEffectiveHistory()
    val hasMultipleUpdates = history.size > 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity & Audit History",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${history.size} ${if (history.size == 1) "Event" else "Events"}",
                    color = SoftCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Display in reverse chronological order (newest on top)
            history.reversed().forEachIndexed { index, event ->
                HistoryEventItem(event = event)
                if (index < history.size - 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Proper empty note if only initial submission exists
            if (!hasMultipleUpdates) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C162E))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Awaiting initial municipal review. New activity, departmental re-routing, and status modifications will be logged above in real-time.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEventItem(event: TrackingEvent) {
    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(event.timestamp))

    val eventColor = when (event.status) {
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
            .clip(RoundedCornerShape(12.dp))
            .background(NavyDark)
            .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(eventColor)
                    )
                    Text(
                        text = event.title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = dateStr,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            if (!event.remarks.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.remarks,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            if (!event.updatedBy.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "👤", fontSize = 10.sp)
                    Text(
                        text = "Updated by: ${event.updatedBy}",
                        color = SoftCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SlaStatusCard(complaint: Complaint) {
    val isOverdue = complaint.isOverdue()
    val slaHours = complaint.getSlaDurationHours()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isOverdue) Color(0xFF261010) else Color(0xFF0F1A30))
            .border(1.dp, if (isOverdue) Color(0xFFFF5252).copy(alpha = 0.7f) else ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isOverdue) "🚨" else "⏱️", fontSize = 14.sp)
                    Text(
                        text = if (isOverdue) "SLA BREACH ALERT" else "SLA Resolution Commitment",
                        color = if (isOverdue) Color(0xFFFF5252) else ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isOverdue) Color(0xFFFF5252) else ElectricCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${slaHours}h SLA",
                        color = if (isOverdue) Color.White else ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isOverdue) {
                Text(
                    text = "This complaint has exceeded its mandated resolution window. It has been automatically flagged to the ${complaint.escalationLevel ?: "Municipal Supervisor"} for expedited action.",
                    color = Color(0xFFFF8A80),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            } else {
                Text(
                    text = "Standard turnaround commitment: ${slaHours} hours for ${complaint.priority} priority ${complaint.category} issues.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun BeforeAfterEvidenceCard(complaint: Complaint) {
    val hasCitizenPhoto = !complaint.imageUri.isNullOrBlank()
    val adminPhotoUri = complaint.resolutionEvidenceUri
        ?: complaint.adminEvidenceList.firstOrNull { it.mediaType == "PHOTO" }?.mediaUri
    val hasAdminEvidence = !adminPhotoUri.isNullOrBlank() || complaint.adminEvidenceList.isNotEmpty()

    if (!hasCitizenPhoto && !hasAdminEvidence) return

    Spacer(modifier = Modifier.height(20.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📸", fontSize = 14.sp)
                Text("Verification & Evidence Comparison", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Citizen Before Evidence
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D182E))
                        .border(0.5.dp, Color(0xFF233554), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BEFORE", color = Color(0xFFFF9800), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (hasCitizenPhoto) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = complaint.imageUri,
                                    contentDescription = "Original citizen grievance photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Original grievance proof", color = SoftCyan, fontSize = 9.sp, textAlign = TextAlign.Center)
                        } else {
                            Text("No citizen photo", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }

                // Official After Evidence
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D221D))
                        .border(0.5.dp, Color(0xFF1B4D3E), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AFTER / ACTION", color = Color(0xFF4ADE80), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        if (!adminPhotoUri.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = adminPhotoUri,
                                    contentDescription = "Official resolution photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Resolution proof", color = Color(0xFF4ADE80), fontSize = 9.sp, textAlign = TextAlign.Center)
                        } else if (complaint.adminEvidenceList.isNotEmpty()) {
                            Text("✅ Rectification Proof", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${complaint.adminEvidenceList.size} inspection items filed", color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
                        } else {
                            Text("Action pending", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CitizenFeedbackSection(
    complaint: Complaint,
    onSubmitFeedback: (Int, String) -> Unit
) {
    var rating by remember { mutableStateOf(complaint.feedbackRating ?: 5) }
    var comment by remember { mutableStateOf(complaint.feedbackComment ?: "") }
    val isAlreadySubmitted = complaint.feedbackRating != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF233554), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⭐", fontSize = 14.sp)
                Text(
                    text = if (isAlreadySubmitted) "Your Resolution Rating" else "Rate Resolution Quality",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Star Rating row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { star ->
                    Text(
                        text = if (star <= rating) "★" else "☆",
                        color = if (star <= rating) Color(0xFFFBBF24) else TextMuted,
                        fontSize = 26.sp,
                        modifier = Modifier.clickable(enabled = !isAlreadySubmitted) {
                            rating = star
                        }
                    )
                }
            }

            if (isAlreadySubmitted) {
                if (comment.isNotBlank()) {
                    Text(
                        text = "\"$comment\"",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                Text("Thank you for rating municipal service delivery.", color = Color(0xFF4ADE80), fontSize = 10.sp)
            } else {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Leave remarks on resolution timeliness and quality...", color = TextMuted, fontSize = 11.sp) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
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
                    onClick = { onSubmitFeedback(rating, comment.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Submit Feedback & Star Rating", color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ReopenComplaintSection(
    complaint: Complaint,
    onOpenReopenDialog: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F1212))
            .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unsatisfied with Resolution?", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (complaint.isReopened) {
                    Text("Reopened ${complaint.reopenCount}x", color = Color(0xFFFF9800), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Citizens have the right to reopen grievances if the ground reality does not match the official closure remarks.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Button(
                onClick = onOpenReopenDialog,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reopen Grievance for Review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
