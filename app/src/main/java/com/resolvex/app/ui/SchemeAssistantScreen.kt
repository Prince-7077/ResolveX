package com.resolvex.app.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.resolvex.app.ai.SchemeAssistantService
import com.resolvex.app.ai.SchemeChatMessage
import com.resolvex.app.ai.SupportedLanguage
import com.resolvex.app.ai.FactVerdict
import com.resolvex.app.ai.FactVerificationResult
import com.resolvex.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchemeAssistantScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val assistantService = remember { SchemeAssistantService(context) }

    var assistantMode by remember { mutableStateOf("SCHEMES") } // "SCHEMES" vs "CIVIC_FACT"
    var selectedLanguage by remember { mutableStateOf(SchemeAssistantService.SUPPORTED_LANGUAGES[0]) } // default English
    var isLangMenuExpanded by remember { mutableStateOf(false) }

    // TTS Setup
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var speakingMessageText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                coroutineScope.launch(Dispatchers.Main) {
                    speakingMessageText = null
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                coroutineScope.launch(Dispatchers.Main) {
                    speakingMessageText = null
                }
            }
        })
        tts = textToSpeech
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    fun getLocaleForLanguage(code: String): Locale {
        return when (code.lowercase()) {
            "hi" -> Locale.forLanguageTag("hi-IN")
            "as" -> Locale.forLanguageTag("as-IN")
            "bn" -> Locale.forLanguageTag("bn-IN")
            "ta" -> Locale.forLanguageTag("ta-IN")
            "te" -> Locale.forLanguageTag("te-IN")
            "mr" -> Locale.forLanguageTag("mr-IN")
            "gu" -> Locale.forLanguageTag("gu-IN")
            "kn" -> Locale.forLanguageTag("kn-IN")
            "ml" -> Locale.forLanguageTag("ml-IN")
            "pa" -> Locale.forLanguageTag("pa-IN")
            "or" -> Locale.forLanguageTag("or-IN")
            "ur" -> Locale.forLanguageTag("ur-IN")
            else -> Locale.forLanguageTag("en-IN")
        }
    }

    fun toggleTts(msg: SchemeChatMessage) {
        if (speakingMessageText == msg.text) {
            tts?.stop()
            speakingMessageText = null
            return
        }

        if (tts == null || !isTtsReady) {
            Toast.makeText(context, "Voice synthesizer is initializing...", Toast.LENGTH_SHORT).show()
            return
        }

        tts?.stop()
        val targetLocale = getLocaleForLanguage(msg.languageCode)
        val avail = tts?.isLanguageAvailable(targetLocale)

        if (avail == TextToSpeech.LANG_AVAILABLE ||
            avail == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            avail == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
            tts?.language = targetLocale
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.95f)

            val cleanSpeechText = msg.text
                .replace(Regex("[*#`_~>•\\[\\]]"), " ")
                .replace(Regex("https?://\\S+"), " official portal ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "scheme_utterance_${msg.id}")
            }
            tts?.speak(cleanSpeechText, TextToSpeech.QUEUE_FLUSH, params, "scheme_utterance_${msg.id}")
            speakingMessageText = msg.text
        } else {
            val langName = SchemeAssistantService.SUPPORTED_LANGUAGES.find { it.code == msg.languageCode }?.englishName ?: msg.languageCode
            Toast.makeText(
                context,
                "Notice: '$langName' TTS voice is not installed on this Android device. Please read the displayed text or download the voice pack in Android Settings > Text-to-Speech.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    var messages by remember {
        mutableStateOf(
            listOf(
                SchemeChatMessage(
                    text = "Namaste! I am your Multilingual Government Scheme & Welfare Assistant. Ask me about central & state schemes, student scholarships, PM-Kisan farmer benefits, PMAY housing, Ayushman Bharat, or self-employment loans.",
                    isUser = false,
                    sourceLinks = listOf("https://www.myscheme.gov.in", "https://www.india.gov.in"),
                    languageCode = "en"
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Civic Fact verification state
    var circularText by remember { mutableStateOf("") }
    var isVerifyingFact by remember { mutableStateOf(false) }
    var factVerificationResult by remember { mutableStateOf<FactVerificationResult?>(null) }

    // Voice recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                inputText = matches[0]
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.speechLocale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.speechLocale)
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(selectedLanguage.speechLocale, "en-IN", "hi-IN"))
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask your government scheme question in ${selectedLanguage.nativeName}...")
                }
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice questions", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendMessage(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || isThinking) return

        val userMsg = SchemeChatMessage(
            text = trimmed,
            isUser = true,
            languageCode = selectedLanguage.code
        )
        messages = messages + userMsg
        inputText = ""
        isThinking = true

        coroutineScope.launch {
            val reply = assistantService.askAssistant(trimmed, selectedLanguage, messages)
            isThinking = false
            messages = messages + reply
            try {
                listState.animateScrollToItem(messages.size - 1)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    val quickPrompts = listOf(
        "🌾 PM-Kisan Samman Nidhi",
        "🏥 Ayushman Bharat PM-JAY",
        "🏠 PMAY Housing Subsidy",
        "🎓 National Scholarship Portal",
        "💼 Mudra Business Loan",
        "⚡ PM Surya Ghar Solar"
    )

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
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { navController.popBackStack() },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("← Back", color = SoftCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Column {
                        Text(
                            text = if (assistantMode == "SCHEMES") "Scheme AI Assistant" else "Civic Fact Verification",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "13 Indian Languages • Official Portals",
                            color = Color(0xFFA78BFA),
                            fontSize = 10.sp
                        )
                    }
                }

                // Language Selector Dropdown
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF162544))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { isLangMenuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🌐", fontSize = 11.sp)
                            Text(selectedLanguage.nativeName, color = ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DropdownMenu(
                        expanded = isLangMenuExpanded,
                        onDismissRequest = { isLangMenuExpanded = false },
                        modifier = Modifier.background(NavyDark)
                    ) {
                        SchemeAssistantService.SUPPORTED_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(lang.nativeName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(lang.englishName, color = TextMuted, fontSize = 10.sp)
                                    }
                                },
                                onClick = {
                                    selectedLanguage = lang
                                    isLangMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // ── Mode Switcher Tabs (Schemes vs Civic Fact) ──────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (assistantMode == "SCHEMES") ElectricCyan else Color.Transparent)
                        .clickable { assistantMode = "SCHEMES" }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🤖 Scheme Assistant",
                        color = if (assistantMode == "SCHEMES") NavyDeep else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (assistantMode == "CIVIC_FACT") NeonPurple else Color.Transparent)
                        .clickable { assistantMode = "CIVIC_FACT" }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📜 Civic Fact Verifier",
                        color = if (assistantMode == "CIVIC_FACT") Color.White else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (assistantMode == "SCHEMES") {
                // Quick Topic Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF131D33))
                                .border(0.5.dp, ElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable { sendMessage(prompt.substringAfter(" ")) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(prompt, color = SoftCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E2E4E), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))

                // ── Messages List ───────────────────────────────────────────────
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(
                            message = msg,
                            isSpeaking = speakingMessageText == msg.text,
                            onToggleTts = { toggleTts(msg) }
                        )
                    }

                    if (isThinking) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ElectricCyan, strokeWidth = 2.dp)
                                Text("Consulting verified government schemes...", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // ── Bottom Input Row ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B18))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mic button for speech question
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NavyCard)
                            .border(1.dp, Color(0xFF233554), CircleShape)
                            .clickable {
                                // Stop any active TTS to prevent assistant from listening to itself
                                if (speakingMessageText != null || tts?.isSpeaking == true) {
                                    tts?.stop()
                                    speakingMessageText = null
                                }

                                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.speechLocale)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage.speechLocale)
                                        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(selectedLanguage.speechLocale, "en-IN", "hi-IN"))
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask in ${selectedLanguage.nativeName} (${selectedLanguage.englishName})...")
                                    }
                                    try {
                                        speechLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Voice input not supported", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎙️", fontSize = 16.sp)
                    }

                    // Text Input
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Ask in ${selectedLanguage.nativeName}...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Color(0xFF233554),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = NavyCard,
                            unfocusedContainerColor = NavyCard
                        )
                    )

                    // Send button
                    Button(
                        onClick = { sendMessage(inputText) },
                        enabled = inputText.isNotBlank() && !isThinking,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        modifier = Modifier.height(46.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text("Send", color = NavyDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            } else {
                // ── Mode 2: Civic Fact & Circular Verification ─────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF131A33))
                            .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📜 Civic Fact & Circular Intelligence", color = SoftCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Paste official notices, viral welfare messages, or government order claims. ResolveX evaluates claims against verified central & state gazettes and official .gov.in registries.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = circularText,
                        onValueChange = { circularText = it },
                        placeholder = { Text("Paste government circular text, WhatsApp scheme forward, or welfare subsidy claim here...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Color(0xFF233554),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = NavyCard,
                            unfocusedContainerColor = NavyCard
                        )
                    )

                    Button(
                        onClick = {
                            if (circularText.trim().length >= 10) {
                                isVerifyingFact = true
                                coroutineScope.launch {
                                    factVerificationResult = assistantService.verifyCircularFact(circularText.trim(), selectedLanguage)
                                    isVerifyingFact = false
                                }
                            } else {
                                Toast.makeText(context, "Please enter at least 10 characters to verify", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isVerifyingFact && circularText.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isVerifyingFact) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing against Government Databases...", color = Color.White, fontSize = 12.sp)
                        } else {
                            Text("🔍 Verify Authenticity", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    factVerificationResult?.let { res ->
                        FactResultCard(result = res)
                    }
                }
            }
        }
    }
}

@Composable
fun FactResultCard(result: FactVerificationResult) {
    val resultColor = Color(result.verdict.hexColor)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NavyCard)
            .border(1.dp, resultColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(result.headline, color = resultColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (result.isCloudAnalyzed) NeonPurple.copy(alpha = 0.2f) else Color(0xFF1E2E4E))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (result.isCloudAnalyzed) "🤖 Gemini 3.8 Flash" else "🛡️ Gazette Heuristic",
                        color = if (result.isCloudAnalyzed) Color(0xFFA78BFA) else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(result.explanation, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)

            if (result.redFlags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Audit Points & Indicators:", color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    result.redFlags.forEach { rf ->
                        Text("• $rf", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            if (result.verifiedSources.isNotEmpty()) {
                val context = LocalContext.current
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Verified Verification Authorities:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    result.verifiedSources.forEach { src ->
                        Text(
                            text = "🔗 $src",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(src))
                                    context.startActivity(browserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open browser: $src", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: SchemeChatMessage,
    isSpeaking: Boolean,
    onToggleTts: () -> Unit
) {
    val context = LocalContext.current
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Brush.linearGradient(listOf(ElectricBlue, Color(0xFF0056B3)))
                    else Brush.linearGradient(listOf(Color(0xFF131D33), NavyCard))
                )
                .border(
                    width = 0.5.dp,
                    color = if (isUser) ElectricCyan.copy(alpha = 0.5f) else Color(0xFF233554),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (message.isCloudGenerated) "🤖" else "🏛️", fontSize = 11.sp)
                            Text(
                                text = if (message.isCloudGenerated) "ResolveX AI (Gemini)" else "Official Welfare Navigator",
                                color = if (message.isCloudGenerated) Color(0xFFA78BFA) else ElectricCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Listen / Stop TTS button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF162544))
                                .clickable { onToggleTts() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(if (isSpeaking) "⏹️ Stop" else "🔊 Listen", color = SoftCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = message.text,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Source links
                if (!isUser && message.sourceLinks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0B1428))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Official Verification Portals:", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        message.sourceLinks.forEach { link ->
                            Text(
                                text = "🔗 $link",
                                color = SoftCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                        context.startActivity(browserIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open browser: $link", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
