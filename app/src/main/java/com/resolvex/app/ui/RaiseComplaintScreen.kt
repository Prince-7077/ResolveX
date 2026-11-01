package com.resolvex.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.resolvex.app.R
import com.resolvex.app.ai.SchemeAssistantService
import com.resolvex.app.ai.SupportedLanguage
import com.resolvex.app.location.GoogleMapsLauncher
import com.resolvex.app.data.Complaint
import com.resolvex.app.data.ComplaintRepository
import com.resolvex.app.data.ComplaintStatus
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
import com.resolvex.app.data.NotificationRepository
import com.resolvex.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.util.Locale
import com.resolvex.app.ai.AiComplaintService
import com.resolvex.app.ai.AiAnalysisResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaiseComplaintScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager.getInstance(context) }
    val complaintRepo = remember { ComplaintRepository.getInstance(context) }
    val currentSession = sessionManager.getSession()

    // Form inputs
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf("Normal") }
    var isPublicComplaint by remember { mutableStateOf(true) }
    var draftRestoredBanner by remember { mutableStateOf(false) }

    // Draft SharedPreferences
    val draftPrefs = remember { context.getSharedPreferences("resolvex_complaint_draft", Context.MODE_PRIVATE) }

    // Restore draft on initial render if fields are blank
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val savedTitle = draftPrefs.getString("draft_title", "") ?: ""
        val savedDesc = draftPrefs.getString("draft_desc", "") ?: ""
        val savedCat = draftPrefs.getString("draft_cat", "") ?: ""
        if (title.isBlank() && (savedTitle.isNotBlank() || savedDesc.isNotBlank())) {
            title = savedTitle
            description = savedDesc
            if (savedCat.isNotBlank()) category = savedCat
            draftRestoredBanner = true
        }
    }

    // Location inputs
    var locationAddress by remember { mutableStateOf("") }
    var detectedLatitude by remember { mutableStateOf<Double?>(null) }
    var detectedLongitude by remember { mutableStateOf<Double?>(null) }
    var isDetectingLocation by remember { mutableStateOf(false) }
    var showLocationPickerModal by remember { mutableStateOf(false) }

    // Media attachments
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var attachedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraVideoUri by remember { mutableStateOf<Uri?>(null) }

    // Speech-to-text language mode (supporting all 13 Indian languages)
    var selectedSpeechLang by remember { mutableStateOf(SchemeAssistantService.SUPPORTED_LANGUAGES[0]) }
    var isSpeechLangMenuOpen by remember { mutableStateOf(false) }

    // Validation & dialog
    var titleError by remember { mutableStateOf<String?>(null) }
    var descError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submittedComplaintId by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // AI Intelligence state
    var aiAnalysisResult by remember { mutableStateOf<AiAnalysisResult?>(null) }
    var isAnalyzingWithAi by remember { mutableStateOf(false) }
    var showDuplicateWarning by remember { mutableStateOf(false) }
    var aiCategoryOverridden by remember { mutableStateOf(false) }

    val categories = listOf(
        "Roads & Infrastructure",
        "Water Supply & Leakage",
        "Electricity & Streetlights",
        "Sanitation & Waste Management",
        "Public Safety & Hazards",
        "Parks & Environment",
        "Other Civic Issue"
    )

    // Helper: Create safe temporary file Uri for Camera
    fun createTempMediaUri(isVideo: Boolean): Uri {
        val extension = if (isVideo) ".mp4" else ".jpg"
        val prefix = if (isVideo) "VID_" else "IMG_"
        val tempFile = File.createTempFile(prefix + System.currentTimeMillis(), extension, context.cacheDir).apply {
            createNewFile()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    }

    // Camera Launchers
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraImageUri != null) {
            attachedImageUri = tempCameraImageUri
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success && tempCameraVideoUri != null) {
            attachedVideoUri = tempCameraVideoUri
        }
    }

    // Gallery Launchers
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) attachedImageUri = uri
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) attachedVideoUri = uri
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val uri = createTempMediaUri(isVideo = false)
            tempCameraImageUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission required to capture photos", Toast.LENGTH_SHORT).show()
        }
    }

    val videoCameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val uri = createTempMediaUri(isVideo = true)
            tempCameraVideoUri = uri
            captureVideoLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission required to record video", Toast.LENGTH_SHORT).show()
        }
    }

    // Speech Recognizer Launcher
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                description = if (description.isBlank()) recognizedText else "$description $recognizedText"
                descError = null
            }
        }
    }

    // Audio Permission Launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedSpeechLang.speechLocale)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${selectedSpeechLang.nativeName} (${selectedSpeechLang.englishName})...")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    // Location Permission Launcher: opens in-app interactive map picker
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            showLocationPickerModal = true
        } else {
            Toast.makeText(context, "Location permission denied. Please grant location access to pick your location on the map.", Toast.LENGTH_SHORT).show()
        }
    }

    fun submitComplaint() {
        var isValid = true

        if (title.trim().isEmpty()) {
            titleError = "Please enter complaint title"
            isValid = false
        } else if (title.trim().length < 5) {
            titleError = "Title must be at least 5 characters"
            isValid = false
        } else {
            titleError = null
        }

        if (description.trim().isEmpty()) {
            descError = "Please describe the problem"
            isValid = false
        } else if (description.trim().length < 10) {
            descError = "Description must be at least 10 characters"
            isValid = false
        } else {
            descError = null
        }

        if (category.trim().isEmpty()) {
            categoryError = "Please select category"
            isValid = false
        } else {
            categoryError = null
        }

        if (!isValid) return

        isSubmitting = true
        coroutineScope.launch {
            delay(500)
            val generatedId = complaintRepo.generateUniqueComplaintId()
            val submissionTime = System.currentTimeMillis()
            val newComplaint = Complaint(
                id = generatedId,
                title = title.trim(),
                description = description.trim(),
                category = category.trim(),
                status = ComplaintStatus.SUBMITTED,
                priority = aiAnalysisResult?.urgency ?: priority,
                assignedDepartment = "Pending Department Assignment",
                citizenName = currentSession?.fullName ?: "Citizen",
                citizenPhone = currentSession?.mobile ?: "",
                citizenEmail = currentSession?.email ?: "",
                locationAddress = if (locationAddress.isNotBlank()) locationAddress.trim() else "Sector Civic Location Tagged",
                latitude = detectedLatitude,
                longitude = detectedLongitude,
                imageUri = attachedImageUri?.toString(),
                videoUri = attachedVideoUri?.toString(),
                createdAt = submissionTime,
                isPublic = isPublicComplaint,
                aiSuggestedCategory = aiAnalysisResult?.detectedCategory,
                aiSuggestedDepartment = aiAnalysisResult?.recommendedDepartment,
                aiSuggestedUrgency = aiAnalysisResult?.urgency,
                aiSummary = aiAnalysisResult?.summary,
                aiUrgencyReason = aiAnalysisResult?.urgencyReason,
                aiExtractedKeywords = aiAnalysisResult?.extractedKeywords ?: emptyList(),
                aiSuggestedNextAction = aiAnalysisResult?.suggestedNextAction,
                possibleDuplicateOfId = aiAnalysisResult?.duplicateMatch?.existingComplaintId,
                history = listOf(
                    com.resolvex.app.data.TrackingEvent(
                        status = ComplaintStatus.SUBMITTED,
                        title = "Complaint Registered",
                        remarks = "Civic complaint registered in ResolveX system.",
                        timestamp = submissionTime,
                        updatedBy = currentSession?.fullName ?: "Citizen"
                    )
                )
            )

            complaintRepo.saveComplaint(newComplaint)
            draftPrefs.edit().clear().apply()
            if (newComplaint.citizenEmail.isNotBlank()) {
                NotificationRepository.getInstance(context).addNotification(
                    com.resolvex.app.data.NotificationItem(
                        targetUserEmail = newComplaint.citizenEmail,
                        complaintId = newComplaint.id,
                        title = "Complaint Registered",
                        message = "Your grievance #${newComplaint.id} has been registered in the ResolveX system.",
                        type = "STATUS_CHANGE"
                    )
                )
            }
            complaintRepo.checkAndTriggerSlaEscalations()
            isSubmitting = false
            submittedComplaintId = generatedId
            showSuccessDialog = true
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
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header bar
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
                            navController.navigate("citizen_dashboard") {
                                popUpTo("raise_complaint") { inclusive = true }
                            }
                        }
                    }) {
                        Text("← Back", color = SoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Raise a Complaint",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Notice
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F1E38))
                    .border(1.dp, ElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Provide accurate details, photos or location for prompt municipal action.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Complaint Category Dropdown
            Text(text = "Category *", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = isCategoryDropdownExpanded,
                onExpandedChange = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Select civic issue category", color = TextMuted) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    isError = categoryError != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = customFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = isCategoryDropdownExpanded,
                    onDismissRequest = { isCategoryDropdownExpanded = false },
                    modifier = Modifier
                        .background(NavyCard)
                        .border(1.dp, Color(0xFF233554), RoundedCornerShape(8.dp))
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = cat,
                                    color = if (category == cat) ElectricCyan else TextPrimary,
                                    fontWeight = if (category == cat) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                category = cat
                                isCategoryDropdownExpanded = false
                                categoryError = null
                            }
                        )
                    }
                }
            }
            if (categoryError != null) {
                Text(text = categoryError ?: "", color = Color(0xFFFF5252), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Complaint Title
            Text(text = "Complaint Title *", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (titleError != null) titleError = null
                },
                placeholder = { Text("e.g. Hazardous deep pothole on Main Road", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = titleError != null,
                shape = RoundedCornerShape(14.dp),
                colors = customFieldColors()
            )
            if (titleError != null) {
                Text(text = titleError ?: "", color = Color(0xFFFF5252), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Complaint Description with Voice-to-Text Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Detailed Description *", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                // Voice input language toggle and Mic button
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        Text(
                            text = "${selectedSpeechLang.nativeName} ▾",
                            color = SoftCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { isSpeechLangMenuOpen = true }
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E2E4E))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        DropdownMenu(
                            expanded = isSpeechLangMenuOpen,
                            onDismissRequest = { isSpeechLangMenuOpen = false },
                            modifier = Modifier.background(NavyCard)
                        ) {
                            SchemeAssistantService.SUPPORTED_LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${lang.nativeName} (${lang.englishName})",
                                            color = if (lang.code == selectedSpeechLang.code) ElectricCyan else TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (lang.code == selectedSpeechLang.code) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedSpeechLang = lang
                                        isSpeechLangMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.horizontalGradient(listOf(ElectricCyan, ElectricBlue)))
                            .clickable {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedSpeechLang.speechLocale)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${selectedSpeechLang.nativeName} (${selectedSpeechLang.englishName})...")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Voice input not supported", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "🎙️", fontSize = 12.sp)
                            Text(text = "Speak", color = NavyDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    if (descError != null) descError = null
                },
                placeholder = { Text("Type or tap 'Speak' to describe the problem, exact location landmarks, and urgency...", color = TextMuted) },
                minLines = 4,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
                isError = descError != null,
                shape = RoundedCornerShape(14.dp),
                colors = customFieldColors()
            )
            if (descError != null) {
                Text(text = descError ?: "", color = Color(0xFFFF5252), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            }

            // ---------------------------------------------------------------
            // AI Complaint Intelligence Panel
            // ---------------------------------------------------------------
            Spacer(modifier = Modifier.height(12.dp))

            // "Analyze with AI" trigger button
            if (title.trim().length >= 5 && description.trim().length >= 10) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isAnalyzingWithAi = true
                            aiCategoryOverridden = false
                            val service = AiComplaintService(context)
                            val existing = complaintRepo.getAllComplaints()
                            val result = service.analyzeComplaint(
                                title = title.trim(),
                                description = description.trim(),
                                location = locationAddress.ifBlank { null },
                                latitude = detectedLatitude,
                                longitude = detectedLongitude,
                                existingComplaints = existing
                            )
                            aiAnalysisResult = result
                            // Auto-apply suggested category if citizen hasn't manually selected one
                            if (category.isBlank() && result.detectedCategory.isNotBlank()) {
                                category = result.detectedCategory
                            }
                            showDuplicateWarning = result.duplicateMatch != null
                            isAnalyzingWithAi = false
                        }
                    },
                    enabled = !isAnalyzingWithAi,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.18f))
                ) {
                    if (isAnalyzingWithAi) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonPurple, strokeWidth = 2.dp)
                            Text("Analyzing Complaint...", color = NeonPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🤖", fontSize = 14.sp)
                            Text(
                                text = if (aiAnalysisResult != null) "Re-Analyze with AI" else "Analyze with AI",
                                color = NeonPurple,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // AI Result Card
            AnimatedVisibility(visible = aiAnalysisResult != null) {
                val result = aiAnalysisResult
                if (result != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF120A28))
                            .border(1.dp, NeonPurple.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🤖", fontSize = 14.sp)
                                Text(
                                    "AI Complaint Intelligence",
                                    color = NeonPurple,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (result.isAiBackendConnected) Color(0xFF065F46) else Color(0xFF1E1B4B))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (result.isAiBackendConnected) "☁️ Gemini" else "📱 On-Device",
                                    color = if (result.isAiBackendConnected) Color(0xFF34D399) else Color(0xFFA78BFA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // AI Summary (only if meaningful)
                        if (result.summary.isNotBlank() && description.trim().length > 80) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F0C1E))
                                    .padding(10.dp)
                            ) {
                                Text("📝 AI Summary", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(result.summary, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }

                        // Detected Category + accept/change chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("🏷️ Detected Category", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(result.detectedCategory, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Accept chip
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ElectricCyan.copy(alpha = 0.15f))
                                        .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            category = result.detectedCategory
                                            aiCategoryOverridden = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text("✓ Accept", color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                // Change chip (opens dropdown again)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E2E4E))
                                        .border(1.dp, Color(0xFF334466), RoundedCornerShape(8.dp))
                                        .clickable {
                                            aiCategoryOverridden = true
                                            isCategoryDropdownExpanded = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text("✎ Change", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Department Recommendation
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🏛️", fontSize = 12.sp)
                            Column {
                                Text("Recommended Department", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(result.recommendedDepartment, color = SoftCyan, fontSize = 12.sp)
                            }
                        }

                        // Urgency Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("⚠️ Urgency Level", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                val urgencyColor = when (result.urgency) {
                                    "Critical" -> Color(0xFFFF5252)
                                    "High"     -> Color(0xFFFF9800)
                                    "Medium"   -> Color(0xFFFBBF24)
                                    else       -> Color(0xFF4ADE80)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(urgencyColor.copy(alpha = 0.18f))
                                        .border(1.dp, urgencyColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Text(result.urgency, color = urgencyColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(result.urgencyReason, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                            }
                        }

                        // Extracted Keywords
                        if (result.extractedKeywords.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🔑 Key Issue Indicators", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    result.extractedKeywords.forEach { kw ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF1E1738))
                                                .border(0.5.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(kw, color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }

                        // Suggested Next Action
                        if (result.suggestedNextAction.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1A2E))
                                    .padding(8.dp)
                            ) {
                                Text("💡 Suggested Action", color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(result.suggestedNextAction, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }

                        // Model info line
                        Text(
                            text = "ℹ️ ${result.modelInfo}",
                            color = TextMuted,
                            fontSize = 9.sp,
                            lineHeight = 13.sp
                        )
                    }

                    // Duplicate Warning Section
                    if (showDuplicateWarning && result.duplicateMatch != null) {
                        val dup = result.duplicateMatch
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A0A00))
                                .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚠️", fontSize = 14.sp)
                                Text(
                                    "Possible Duplicate Complaint",
                                    color = Color(0xFFFF9800),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "A similar complaint already exists in the system.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F0A00))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Existing ID", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(dup.existingComplaintId, color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(dup.existingComplaintTitle, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(dup.matchReason, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Support existing complaint instead
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val userEmail = currentSession?.email ?: ""
                                            if (userEmail.isNotBlank()) {
                                                complaintRepo.toggleSupportVote(dup.existingComplaintId, userEmail)
                                            }
                                            navController.navigate("track_complaint/${dup.existingComplaintId}") {
                                                popUpTo("citizen_dashboard")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                ) {
                                    Text("👍 Support Existing", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                // Submit anyway
                                Button(
                                    onClick = { showDuplicateWarning = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2E4E))
                                ) {
                                    Text("Submit Anyway", color = SoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. GPS Location Tagging
            Text(text = "Location & GPS Tagging", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = locationAddress,
                    onValueChange = { locationAddress = it },
                    placeholder = { Text("Landmark, street name or ward", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = customFieldColors()
                )

                Button(
                    onClick = {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            showLocationPickerModal = true
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF132349))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "🗺️", fontSize = 13.sp)
                        Text(text = "Live Map", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // In-App Confirmed Location Preview Card
            if (detectedLatitude != null && detectedLongitude != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0C162E))
                        .border(1.dp, Color(0xFF1E3A8A), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("📍", fontSize = 13.sp)
                                Text(
                                    text = "Selected Incident Location",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF064E3B))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("✓ In-App Map Tag", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        val latStr = String.format(java.util.Locale.US, "%.5f", detectedLatitude)
                        val lonStr = String.format(java.util.Locale.US, "%.5f", detectedLongitude)
                        Text(
                            text = "Coordinates: $latStr° N, $lonStr° E",
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Action Buttons: Change Location on Map & Remove Location
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showLocationPickerModal = true },
                                modifier = Modifier.weight(1.1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2E56))
                            ) {
                                Text("✏️ Change on Map", color = SoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    detectedLatitude = null
                                    detectedLongitude = null
                                    locationAddress = ""
                                    Toast.makeText(context, "Location tag removed", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(0.9f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B151E))
                            ) {
                                Text("✕ Remove", color = Color(0xFFFCA5A5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Photos & Videos Evidence
            Text(text = "Evidence (Photo & Video)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Take Photo
                MediaActionButton(
                    icon = "📷",
                    label = "Take Photo",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )

                // Record Video
                MediaActionButton(
                    icon = "🎥",
                    label = "Record Video",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        videoCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Upload Image
                MediaActionButton(
                    icon = "🖼️",
                    label = "Gallery Image",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pickImageLauncher.launch("image/*")
                    }
                )

                // Upload Video
                MediaActionButton(
                    icon = "📹",
                    label = "Gallery Video",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        pickVideoLauncher.launch("video/*")
                    }
                )
            }

            // Attached Media Previews
            if (attachedImageUri != null || attachedVideoUri != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavyCard)
                        .border(1.dp, Color(0xFF233554), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Attached Files:", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    // Image preview
                    if (attachedImageUri != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "📷", fontSize = 16.sp)
                                Text(
                                    text = "Photo Attached (${attachedImageUri?.lastPathSegment?.takeLast(16) ?: "image.jpg"})",
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Remove",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { attachedImageUri = null }
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Video preview
                    if (attachedVideoUri != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "🎥", fontSize = 16.sp)
                                Text(
                                    text = "Video Attached (${attachedVideoUri?.lastPathSegment?.takeLast(16) ?: "video.mp4"})",
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Remove",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { attachedVideoUri = null }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

            // Visibility Setting: Public vs Private
            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D1B33))
                    .border(1.dp, Color(0xFF1E3258), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Grievance Privacy & Visibility",
                    color = SoftCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPublicComplaint) ElectricCyan.copy(alpha = 0.2f) else Color(0xFF0A1224))
                            .border(1.dp, if (isPublicComplaint) ElectricCyan else Color(0xFF1E2E4E), RoundedCornerShape(10.dp))
                            .clickable { isPublicComplaint = true }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🌐 Public", color = if (isPublicComplaint) ElectricCyan else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Visible on Map & community voting", color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isPublicComplaint) NeonPurple.copy(alpha = 0.2f) else Color(0xFF0A1224))
                            .border(1.dp, if (!isPublicComplaint) NeonPurple else Color(0xFF1E2E4E), RoundedCornerShape(10.dp))
                            .clickable { isPublicComplaint = false }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔒 Private", color = if (!isPublicComplaint) NeonPurple else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "Confidential, official view only", color = TextMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // Auto-save draft on typing
            androidx.compose.runtime.DisposableEffect(title, description, category) {
                if (title.isNotBlank() || description.isNotBlank()) {
                    draftPrefs.edit()
                        .putString("draft_title", title)
                        .putString("draft_desc", description)
                        .putString("draft_cat", category)
                        .apply()
                }
                onDispose { }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Prominent Submit Button
            Button(
                onClick = { submitComplaint() },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isSubmitting) Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF233554)))
                            else Brush.horizontalGradient(listOf(ElectricCyan, ElectricBlue)),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSubmitting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan, strokeWidth = 2.dp)
                            Text(text = "Registering Complaint...", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Submit Complaint",
                            color = NavyDeep,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Success Confirmation Dialog
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSuccessDialog = false
                    navController.popBackStack()
                },
                title = {
                    Text(text = "Complaint Submitted Successfully!", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Your civic complaint has been registered in the ResolveX registry.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(NavyCard)
                                .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "COMPLAINT REFERENCE ID", color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = submittedComplaintId ?: "",
                                    color = ElectricCyan,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        Text(
                            text = "Municipal administrators will verify and assign this issue to the responsible department.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = submittedComplaintId
                            showSuccessDialog = false
                            if (!id.isNullOrBlank()) {
                                navController.navigate("track_complaint/$id") {
                                    popUpTo("citizen_dashboard")
                                }
                            } else {
                                navController.popBackStack()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text(text = "Track Complaint", color = NavyDeep, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSuccessDialog = false
                            navController.popBackStack()
                        }
                    ) {
                        Text(text = "Back to Dashboard", color = SoftCyan)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // In-App Interactive Map Location Selection Modal
        if (showLocationPickerModal) {
            LocationPickerModal(
                initialLatitude = detectedLatitude,
                initialLongitude = detectedLongitude,
                onConfirmLocation = { lat, lon ->
                    detectedLatitude = lat
                    detectedLongitude = lon
                    val latStr = String.format(java.util.Locale.US, "%.5f", lat)
                    val lonStr = String.format(java.util.Locale.US, "%.5f", lon)
                    if (locationAddress.isBlank() || locationAddress.startsWith("GPS Coordinates:")) {
                        locationAddress = "GPS Coordinates: $latStr° N, $lonStr° E"
                    }
                    Toast.makeText(context, "Location confirmed: $latStr° N, $lonStr° E", Toast.LENGTH_SHORT).show()
                    showLocationPickerModal = false
                },
                onDismiss = {
                    showLocationPickerModal = false
                }
            )
        }
    }
}

@Composable
fun MediaActionButton(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NavyCard)
            .border(1.dp, Color(0xFF233554), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = icon, fontSize = 16.sp)
            Text(text = label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun customFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = Color(0xFF233554),
    errorBorderColor = Color(0xFFFF5252),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    errorTextColor = TextPrimary,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextMuted,
    errorLabelColor = Color(0xFFFF5252),
    focusedContainerColor = NavyDark,
    unfocusedContainerColor = NavyDark,
    errorContainerColor = NavyDark
)
