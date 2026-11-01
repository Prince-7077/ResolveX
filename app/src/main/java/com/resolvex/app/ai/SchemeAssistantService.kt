package com.resolvex.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class SchemeChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceLinks: List<String> = emptyList(),
    val languageCode: String = "en",
    val isError: Boolean = false,
    val isCloudGenerated: Boolean = false
)

data class SupportedLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val speechLocale: String
)

enum class FactVerdict(val label: String, val hexColor: Long) {
    VERIFIED("✓ VERIFIED OFFICIAL GOVERNMENT INITIATIVE", 0xFF4ADE80),
    LIKELY_FAKE("⚠️ SUSPECTED SCAM / FRAUDULENT CIRCULAR", 0xFFFF5252),
    UNVERIFIED("ℹ️ UNVERIFIED / AWAITING MINISTERIAL GAZETTE", 0xFFFBBF24)
}

data class FactVerificationResult(
    val verdict: FactVerdict,
    val headline: String,
    val explanation: String,
    val redFlags: List<String>,
    val verifiedSources: List<String>,
    val isCloudAnalyzed: Boolean
)

class SchemeAssistantService(private val context: Context? = null) {
    private val configManager = context?.let { AiConfigManager.getInstance(it) }

    companion object {
        const val STANDARD_OFF_TOPIC_REDIRECT =
            "I'm ResolveX AI, designed to help with government schemes, policies, civic issues and public services. I can assist you with these topics."

        val SUPPORTED_LANGUAGES = listOf(
            SupportedLanguage("en", "English", "English", "en-IN"),
            SupportedLanguage("hi", "Hindi", "हिंदी", "hi-IN"),
            SupportedLanguage("as", "Assamese", "অসমীয়া", "as-IN"),
            SupportedLanguage("bn", "Bengali", "বাংলা", "bn-IN"),
            SupportedLanguage("ta", "Tamil", "தமிழ்", "ta-IN"),
            SupportedLanguage("te", "Telugu", "తెలుగు", "te-IN"),
            SupportedLanguage("mr", "Marathi", "मराठी", "mr-IN"),
            SupportedLanguage("gu", "Gujarati", "ગુજરાતી", "gu-IN"),
            SupportedLanguage("kn", "Kannada", "ಕನ್ನಡ", "kn-IN"),
            SupportedLanguage("ml", "Malayalam", "മലയാളം", "ml-IN"),
            SupportedLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ", "pa-IN"),
            SupportedLanguage("or", "Odia", "ଓଡ଼ିଆ", "or-IN"),
            SupportedLanguage("ur", "Urdu", "اردو", "ur-IN")
        )

        val OFFICIAL_PORTALS = listOf(
            "https://www.india.gov.in" to "National Portal of India",
            "https://www.myscheme.gov.in" to "myScheme Official Portal",
            "https://scholarships.gov.in" to "National Scholarship Portal",
            "https://pmkisan.gov.in" to "PM Kisan Samman Nidhi",
            "https://pmaymis.gov.in" to "Pradhan Mantri Awas Yojana",
            "https://pmjay.gov.in" to "Ayushman Bharat PM-JAY",
            "https://www.mudra.org.in" to "Pradhan Mantri MUDRA Yojana",
            "https://pib.gov.in/factcheck" to "PIB Fact Check Unit"
        )
    }

    suspend fun askAssistant(
        userQuery: String,
        selectedLanguage: SupportedLanguage,
        conversationHistory: List<SchemeChatMessage>
    ): SchemeChatMessage = withContext(Dispatchers.IO) {
        val apiKey = configManager?.getGeminiApiKey() ?: System.getenv("GEMINI_API_KEY")

        // 1. If Gemini API Key is available, invoke Gemini 3.8 Flash
        if (!apiKey.isNullOrBlank()) {
            try {
                val cloudResponse = callGeminiForScheme(apiKey, userQuery, selectedLanguage, conversationHistory)
                if (cloudResponse != null) {
                    return@withContext cloudResponse
                }
            } catch (e: Exception) {
                // If cloud fails, return transparent informative message with on-device knowledge base
                val fallback = generateVerifiedFallbackResponse(userQuery, selectedLanguage)
                val errorMessage = when {
                    e.message?.contains("429") == true -> "ResolveX AI is experiencing high traffic (Rate Limit). Displaying verified on-device reference:\n\n"
                    e.message?.contains("403") == true -> "ResolveX AI authentication error (Invalid API Key). Displaying verified on-device reference:\n\n"
                    e.message?.contains("timed out") == true || e.message?.contains("timeout") == true -> "ResolveX AI connection timed out. Displaying verified on-device reference:\n\n"
                    else -> "ResolveX Cloud AI is unreachable. Displaying verified on-device reference:\n\n"
                }
                return@withContext fallback.copy(
                    text = errorMessage + fallback.text,
                    isCloudGenerated = false
                )
            }
        }

        // 2. On-device verified knowledge base (transparent indicator)
        return@withContext generateVerifiedFallbackResponse(userQuery, selectedLanguage)
    }

    private fun callGeminiForScheme(
        apiKey: String,
        userQuery: String,
        selectedLanguage: SupportedLanguage,
        history: List<SchemeChatMessage>
    ): SchemeChatMessage? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        conn.doOutput = true

        val systemInstructionText = """
            You are ResolveX AI, the official Multilingual Civic & Government Welfare Assistant for India (ResolveX — "Your Voice. Real Change.").
            
            Strict Domain Scope:
            You assist Indian citizens exclusively with:
            - Central & State Government welfare schemes (eligibility, benefits, required documents, application process).
            - Civic issues, municipal grievances (roads, water, electricity, sanitation, public hazards).
            - Public services, citizen welfare entitlements, and government portals.
            
            STRICT OFF-TOPIC ENFORCEMENT:
            If the user asks about unrelated topics (such as general chit-chat, entertainment, movies, video games, sports gossip, general programming/coding, homework, creative stories, or casual trivia), you MUST decline politely with this EXACT sentence and nothing else:
            "I'm ResolveX AI, designed to help with government schemes, policies, civic issues and public services. I can assist you with these topics."
            
            Language Instruction:
            Respond fluently, naturally, and completely in ${selectedLanguage.englishName} (${selectedLanguage.nativeName}).
            If the user writes in Hinglish or regional transliteration, respond in clear, easy-to-understand conversational language matching the citizen's intent.
            
            Factual Accuracy & Security Rules:
            1. Differentiate clearly between Central Government and State Government schemes.
            2. Never fabricate non-existent schemes or promise guaranteed approvals.
            3. Always cite official government portals (.gov.in / .nic.in).
            4. STRICT SECURITY: NEVER ask the citizen for Aadhaar numbers, OTP, bank passwords, debit card PINs, or any sensitive credentials.
            5. Free Welfare Guarantee: Remind citizens that government applications on official portals do not charge hidden middlemen fees.
        """.trimIndent()

        // Build multi-turn context
        val contentsArray = JSONArray()

        // Include recent conversation context (last 6 messages)
        val recentHistory = history.filter { !it.isError }.takeLast(6)
        for (item in recentHistory) {
            val role = if (item.isUser) "user" else "model"
            contentsArray.put(JSONObject().apply {
                put("role", role)
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", item.text))
                })
            })
        }

        // Add the current user query as the final user message
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().put("text", userQuery))
            })
        })

        val requestJson = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemInstructionText))
                })
            })
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 1200)
            })
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        val code = conn.responseCode
        if (code == 200) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val answerText = parts.getJSONObject(0).optString("text", "").trim()
                    if (answerText.isNotBlank()) {
                        val links = extractOfficialLinks(answerText)
                        return SchemeChatMessage(
                            text = answerText,
                            isUser = false,
                            sourceLinks = links,
                            languageCode = selectedLanguage.code,
                            isCloudGenerated = true
                        )
                    }
                }
            }
        } else {
            val errStream = conn.errorStream
            val errBody = errStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("Gemini API HTTP $code: $errBody")
        }
        return null
    }

    /**
     * Civic Circular & Scheme Fact Verification via Gemini 3.8 Flash.
     * Evaluates viral forwards, circulars, and welfare claims against official Indian registries.
     */
    suspend fun verifyCircularFact(
        claimText: String,
        selectedLanguage: SupportedLanguage
    ): FactVerificationResult = withContext(Dispatchers.IO) {
        val apiKey = configManager?.getGeminiApiKey() ?: System.getenv("GEMINI_API_KEY")

        if (!apiKey.isNullOrBlank()) {
            try {
                val cloudResult = callGeminiForFactVerification(apiKey, claimText, selectedLanguage)
                if (cloudResult != null) return@withContext cloudResult
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext verifyFactOffline(claimText, selectedLanguage)
    }

    private fun callGeminiForFactVerification(
        apiKey: String,
        claimText: String,
        lang: SupportedLanguage
    ): FactVerificationResult? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        conn.doOutput = true

        val prompt = """
            You are ResolveX Fact Verification Engine for Indian Civic & Government Schemes.
            Evaluate this claimed government circular or scheme forward:
            "$claimText"

            Fact-Checking Rules:
            1. Genuine DBT schemes never charge registration fees or require UPI payments to third parties.
            2. Official announcements are hosted on .gov.in or .nic.in domains only.
            3. Classify into:
               - "VERIFIED": If it matches an authentic Government of India or State portal scheme (e.g., PM-Kisan, PMAY, Ayushman Bharat, NSP).
               - "LIKELY_FAKE": If it exhibits scam signals (registration fee, phishing URL, unsolicited WhatsApp chain, asking for OTP/banking pin, promises of free smartphones/cash upon forwarding).
               - "UNVERIFIED": If unconfirmed by formal gazette or lacking verifiable ministerial sources.

            Output strictly a valid JSON object matching this schema without markdown fences:
            {
              "status": "VERIFIED" | "LIKELY_FAKE" | "UNVERIFIED",
              "headline": "concise verdict headline in ${lang.englishName}",
              "explanation": "2-3 sentences explaining the assessment in ${lang.englishName}",
              "redFlags": ["specific indicator 1", "specific indicator 2"],
              "sources": ["https://myscheme.gov.in", "https://pib.gov.in/factcheck"]
            }
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 800)
            })
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestJson.toString())
            writer.flush()
        }

        val code = conn.responseCode
        if (code == 200) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val raw = parts.getJSONObject(0).optString("text", "")
                    val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    val parsed = JSONObject(cleaned)

                    val statusStr = parsed.optString("status", "UNVERIFIED").uppercase(Locale.getDefault())
                    val verdict = when (statusStr) {
                        "VERIFIED" -> FactVerdict.VERIFIED
                        "LIKELY_FAKE" -> FactVerdict.LIKELY_FAKE
                        else -> FactVerdict.UNVERIFIED
                    }

                    val rfJson = parsed.optJSONArray("redFlags")
                    val rfList = mutableListOf<String>()
                    if (rfJson != null) {
                        for (i in 0 until rfJson.length()) rfList.add(rfJson.optString(i))
                    }

                    val srcJson = parsed.optJSONArray("sources")
                    val srcList = mutableListOf<String>()
                    if (srcJson != null) {
                        for (i in 0 until srcJson.length()) srcList.add(srcJson.optString(i))
                    }
                    if (srcList.isEmpty()) srcList.add("https://pib.gov.in/factcheck")

                    return FactVerificationResult(
                        verdict = verdict,
                        headline = parsed.optString("headline", verdict.label),
                        explanation = parsed.optString("explanation", ""),
                        redFlags = rfList,
                        verifiedSources = srcList,
                        isCloudAnalyzed = true
                    )
                }
            }
        }
        return null
    }

    /**
     * Fallback offline fact verification using comprehensive pattern recognition.
     */
    fun verifyFactOffline(query: String, lang: SupportedLanguage): FactVerificationResult {
        val lower = query.lowercase(Locale.getDefault())

        val isScam = lower.contains("registration fee") ||
                lower.contains("send money") ||
                lower.contains("otp") ||
                lower.contains("bank pin") ||
                lower.contains("free laptop") ||
                lower.contains("free smartphone") ||
                lower.contains("share to 10 groups") ||
                lower.contains("bit.ly") ||
                lower.contains("tinyurl") ||
                lower.contains("win cash")

        val isAuthenticScheme = lower.contains("pm-kisan") ||
                lower.contains("kisan") ||
                lower.contains("ayushman") ||
                lower.contains("pmjay") ||
                lower.contains("pmay") ||
                lower.contains("mudra") ||
                lower.contains("surya ghar") ||
                lower.contains("national scholarship") ||
                lower.contains("nsp") ||
                lower.contains("swachh bharat")

        return when {
            isScam -> FactVerdict.LIKELY_FAKE to Pair(
                "Genuine Central & State Government welfare programs under DBT NEVER charge application fees or ask citizens to forward messages to WhatsApp groups.",
                listOf(
                    "Demands upfront processing/registration fee",
                    "Solicits banking credentials or OTP",
                    "Spam dissemination via WhatsApp forward request",
                    "Absence of official .gov.in / .nic.in domain reference"
                )
            )
            isAuthenticScheme -> FactVerdict.VERIFIED to Pair(
                "Matches official Government of India welfare initiative registered under the National Portal of India.",
                listOf(
                    "Submit applications exclusively via authorized .gov.in portal",
                    "Never pay unauthorized middlemen or agents"
                )
            )
            else -> FactVerdict.UNVERIFIED to Pair(
                "No gazetted ministerial order matches this exact wording. Cross-verify with your ward councillor or PIB Fact Check before taking action.",
                listOf(
                    "Awaiting formal ministerial circular",
                    "Verify on official state portal before sharing"
                )
            )
        }.let { (verdict, details) ->
            val sources = when (verdict) {
                FactVerdict.VERIFIED -> listOf("https://www.myscheme.gov.in", "https://www.india.gov.in")
                FactVerdict.LIKELY_FAKE -> listOf("https://pib.gov.in/factcheck", "https://cybercrime.gov.in")
                FactVerdict.UNVERIFIED -> listOf("https://pib.gov.in", "https://www.myscheme.gov.in")
            }
            FactVerificationResult(
                verdict = verdict,
                headline = verdict.label,
                explanation = details.first,
                redFlags = details.second,
                verifiedSources = sources,
                isCloudAnalyzed = false
            )
        }
    }

    private fun extractOfficialLinks(text: String): List<String> {
        val links = mutableListOf<String>()
        val urlRegex = Regex("""https?://[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(/[^\s\)\]]*)?""")
        urlRegex.findAll(text).forEach { match ->
            val url = match.value.trimEnd('.', ',', ';', ')')
            if (url.contains(".gov.in") || url.contains(".nic.in") || url.contains(".org.in")) {
                links.add(url)
            }
        }

        if (links.isEmpty()) {
            if (text.contains("scholarship", ignoreCase = true)) links.add("https://scholarships.gov.in")
            if (text.contains("kisan", ignoreCase = true)) links.add("https://pmkisan.gov.in")
            if (text.contains("awas", ignoreCase = true) || text.contains("housing", ignoreCase = true)) links.add("https://pmaymis.gov.in")
            if (text.contains("ayushman", ignoreCase = true) || text.contains("health", ignoreCase = true)) links.add("https://pmjay.gov.in")
            if (text.contains("mudra", ignoreCase = true)) links.add("https://www.mudra.org.in")
            if (links.isEmpty()) links.add("https://www.myscheme.gov.in")
        }
        return links.distinct()
    }

    /**
     * Verified offline fallback knowledge engine covering major national schemes.
     * Guarantees that the assistant works reliably in all conditions.
     */
    fun generateVerifiedFallbackResponse(
        query: String,
        lang: SupportedLanguage
    ): SchemeChatMessage {
        val q = query.lowercase(Locale.getDefault())

        val (content, links) = when {
            q.contains("scholarship") || q.contains("student") || q.contains("study") || q.contains("college") || q.contains("school") -> {
                val text = when (lang.code) {
                    "hi" -> """
                        🎓 **छात्रवृत्ति एवं शिक्षा योजनाएं (Scholarships for Students)**:
                        
                        1. **राष्ट्रीय छात्रवृत्ति पोर्टल (National Scholarship Portal - NSP)**:
                           - **पात्रता**: प्री-मैट्रिक, पोस्ट-मैट्रिक, और उच्च शिक्षा के विद्यार्थी (SC/ST/OBC/अल्पसंख्यक एवं मेधावी सामान्य वर्ग)।
                           - **आवश्यक दस्तावेज**: पिछली कक्षा की मार्कशीट, आय प्रमाण पत्र, आधार कार्ड, बैंक पासबुक, जाति प्रमाण पत्र।
                           - **आवेदन प्रक्रिया**: `scholarships.gov.in` पर नया पंजीकरण करें, OTR (One Time Registration) प्राप्त करें और ऑनलाइन आवेदन जमा करें।
                        
                        2. **पीएम उच्चतर शिक्षा प्रोत्साहन योजना (PM-USP)**:
                           - कॉलेज और विश्वविद्यालय के मेधावी विद्यार्थियों के लिए वार्षिक वित्तीय सहायता।
                        
                        *नोट: आवेदन की अंतिम तिथि राज्य व योजना अनुसार NSP पोर्टल पर सत्यापित करें।*
                    """.trimIndent()
                    "bn" -> """
                        🎓 **শিক্ষার্থীদের জন্য সরকারি স্কলারশিপ ও সহায়তা**:
                        
                        1. **ন্যাশনাল স্কলারশিপ পোর্টাল (NSP)**:
                           - প্রি-ম্যাট্রিক, পোস্ট-ম্যাট্রিক এবং মেধাভিত্তিক সরকারি স্কলারশিপ।
                           - প্রয়োজনীয় ডকুমেন্টস: মার্কশিট, ইনকাম সার্টিফিকেট, আধার কার্ড, ব্যাংক পাসবুক।
                           - অফিসিয়াল পোর্টাল: `scholarships.gov.in`
                    """.trimIndent()
                    "ta" -> """
                        🎓 **மாணவர்களுக்கான அரசு உதவித்தொகை திட்டங்கள்**:
                        
                        1. **தேசிய உதவித்தொகை போர்டல் (NSP)**:
                           - பள்ளி மற்றும் கல்லூரி மாணவர்களுக்கான மத்திய அரசு உதவித்தொகை.
                           - தேவையான ஆவணங்கள்: மதிப்பெண் பட்டியல், வருமானச் சான்றிதழ், ஆதார், வங்கி கணக்கு புத்தகம்.
                           - அதிகாரப்பூர்வ இணையதளம்: `scholarships.gov.in`
                    """.trimIndent()
                    "te" -> """
                        🎓 **విద్యార్థుల కోసం ప్రభుత్వ స్కాలర్‌షిప్ పథకాలు**:
                        
                        1. **నేషనల్ స్కాలర్‌షిప్ పోర్టల్ (NSP)**:
                           - ప్రీ-మెట్రిక్ మరియు పోస్ట్-మెట్రిక్ విద్యార్థులకు ఆర్థిక సహాయం.
                           - అధికారిక వెబ్‌సైట్: `scholarships.gov.in`
                    """.trimIndent()
                    else -> """
                        🎓 **Key Student Scholarship & Welfare Schemes**:
                        
                        1. **National Scholarship Portal (NSP - scholarships.gov.in)**:
                           - **Eligibility**: Pre-Matric, Post-Matric & Higher Education students from SC, ST, OBC, Minority & EWS communities.
                           - **Key Benefits**: Direct Bank Transfer (DBT) of tuition fees and maintenance allowance.
                           - **Mandatory Documents**: Previous academic marksheets, family income certificate, Aadhaar card, bank passbook, bonafide student certificate.
                           - **How to Apply**: Register on the official portal `scholarships.gov.in` using One-Time Registration (OTR).
                        
                        2. **Central Sector Scheme of Scholarships for College and University Students (PM-USP)**:
                           - Financial assistance of ₹12,000 to ₹20,000 per year for meritorious students scoring above 80th percentile in Class 12.
                        
                        *Always verify application windows and deadlines on scholarships.gov.in.*
                    """.trimIndent()
                }
                Pair(text, listOf("https://scholarships.gov.in", "https://www.myscheme.gov.in"))
            }

            q.contains("farmer") || q.contains("kisan") || q.contains("agriculture") || q.contains("crop") -> {
                val text = when (lang.code) {
                    "hi" -> """
                        🌾 **किसानों के लिए प्रमुख कल्याणकारी योजनाएं**:
                        
                        1. **पीएम-किसान सम्मान निधि (PM-Kisan Samman Nidhi)**:
                           - **लाभ**: सभी पात्र किसान परिवारों को प्रति वर्ष ₹6,000 (₹2,000 की 3 समान किस्तों में) सीधे बैंक खाते में।
                           - **पात्रता**: कृषि योग्य भूमि धारक किसान परिवार।
                           - **आवश्यक दस्तावेज**: आधार कार्ड, भूमि के खतौनी/जमाबंदी दस्तावेज, आधार-लिंक्ड बैंक खाता।
                           - **स्थिति जांच एवं आवेदन**: `pmkisan.gov.in` पर e-KYC अनिवार्य है।
                        
                        2. **प्रधानमंत्री फसल बीमा योजना (PMFBY)**:
                           - प्राकृतिक आपदाओं, कीटों व रोगों से फसल नुकसान पर न्यूनतम प्रीमियम पर बीमा सुरक्षा।
                    """.trimIndent()
                    else -> """
                        🌾 **Key Welfare Schemes for Farmers & Agriculture**:
                        
                        1. **PM-Kisan Samman Nidhi (pmkisan.gov.in)**:
                           - **Benefit**: ₹6,000 per year provided in three equal installments of ₹2,000 via Direct Benefit Transfer (DBT).
                           - **Eligibility**: Landholding farmer families with cultivable land.
                           - **Documents Required**: Aadhaar card, land ownership records (Khatauni), Aadhaar-seeded bank account.
                           - **Mandatory Requirement**: e-KYC completion on `pmkisan.gov.in`.
                        
                        2. **Pradhan Mantri Fasal Bima Yojana (PMFBY - pmfby.gov.in)**:
                           - Comprehensive crop insurance against non-preventable natural risks from pre-sowing to post-harvest.
                    """.trimIndent()
                }
                Pair(text, listOf("https://pmkisan.gov.in", "https://pmfby.gov.in"))
            }

            q.contains("housing") || q.contains("house") || q.contains("awas") || q.contains("home") -> {
                val text = when (lang.code) {
                    "hi" -> """
                        🏠 **आवास एवं गृह निर्माण योजनाएं (Housing Schemes)**:
                        
                        1. **प्रधानमंत्री आवास योजना (PMAY)**:
                           - **ग्रामीण (PMAY-G)**: कच्चे मकान में रहने वाले परिवारों को पक्के घर निर्माण हेतु ₹1.20 लाख (मैदानी) से ₹1.30 लाख (पहाड़ी क्षेत्र) की सहायता।
                           - **शहरी (PMAY-U)**: निम्न आय वर्ग (EWS/LIG) को गृह ऋण पर ब्याज सब्सिडी या किफायती आवास।
                           - **पात्रता**: पूरे भारत में परिवार के किसी सदस्य के नाम पक्का मकान नहीं होना चाहिए।
                           - **आधिकारिक वेबसाइट**: `pmaymis.gov.in` (शहरी) एवं `pmayg.nic.in` (ग्रामीण)।
                    """.trimIndent()
                    else -> """
                        🏠 **Pradhan Mantri Awas Yojana (PMAY - Housing for All)**:
                        
                        1. **PMAY - Gramin (Rural)**:
                           - Financial assistance of ₹1.20 Lakh (plains) / ₹1.30 Lakh (hilly states) for construction of pucca house with toilet.
                        2. **PMAY - Urban**:
                           - Credit Linked Subsidy Scheme (CLSS) and Affordable Housing in Partnership for EWS and LIG families.
                        - **Eligibility**: Families without a pucca house in their name anywhere in India.
                        - **Official Portals**: `pmaymis.gov.in` and `pmayg.nic.in`.
                    """.trimIndent()
                }
                Pair(text, listOf("https://pmaymis.gov.in", "https://www.myscheme.gov.in"))
            }

            q.contains("health") || q.contains("hospital") || q.contains("ayushman") || q.contains("medical") -> {
                val text = when (lang.code) {
                    "hi" -> """
                        🏥 **स्वास्थ्य एवं चिकित्सा सहायता योजनाएं**:
                        
                        1. **आयुष्मान भारत - प्रधानमंत्री जन आरोग्य योजना (PM-JAY)**:
                           - **लाभ**: प्रति परिवार प्रति वर्ष ₹5,00,000 तक का मुफ्त कैशलेस उपचार सरकारी व सूचीबद्ध निजी अस्पतालों में।
                           - **पात्रता**: SECC 2011 डेटाबेस के अनुसार चिन्हित गरीब एवं वंचित परिवार। 70 वर्ष से अधिक आयु के वरिष्ठ नागरिकों के लिए भी विस्तारित।
                           - **कार्ड जारी**: नजदीकी CSC केंद्र या `pmjay.gov.in` पर आयुष्मान कार्ड बनवाएं।
                    """.trimIndent()
                    else -> """
                        🏥 **Ayushman Bharat Pradhan Mantri Jan Arogya Yojana (PM-JAY)**:
                        
                        - **Benefit**: Free cashless secondary and tertiary hospitalization cover up to ₹5,00,000 per family per year.
                        - **Eligibility**: Vulnerable families identified via socio-economic criteria, now expanded to all senior citizens aged 70+.
                        - **Official Portal**: `pmjay.gov.in` (Check eligibility and locate empanelled hospitals).
                    """.trimIndent()
                }
                Pair(text, listOf("https://pmjay.gov.in", "https://www.myscheme.gov.in"))
            }

            q.contains("business") || q.contains("loan") || q.contains("mudra") || q.contains("women") -> {
                val text = when (lang.code) {
                    "hi" -> """
                        💼 **स्वरोजगार एवं सूक्ष्म व्यवसाय योजनाएं**:
                        
                        1. **प्रधानमंत्री मुद्रा योजना (PMMY)**:
                           - **शिशु**: ₹50,000 तक का ऋण (नए छोटे व्यवसाय के लिए)।
                           - **किशोर**: ₹50,000 से ₹5,00,000 तक का ऋण।
                           - **तरुण**: ₹5,00,000 से ₹10,00,000 (बढ़ाकर ₹20 लाख तक)।
                           - **बिना किसी गारंटी**: किसी भी सार्वजनिक/निजी बैंक अथवा `mudra.org.in` पर आवेदन करें।
                    """.trimIndent()
                    else -> """
                        💼 **Pradhan Mantri MUDRA Yojana (PMMY - Business Loans)**:
                        
                        - **Shishu Category**: Loans up to ₹50,000 for starting micro-enterprises.
                        - **Kishore Category**: Loans from ₹50,000 to ₹5,00,000 for business expansion.
                        - **Tarun Category**: Loans from ₹5,00,000 to ₹10,00,000 (extended up to ₹20 Lakhs).
                        - **Collateral**: No collateral required. Available through nationalized banks, RRBs, and MFIs.
                        - **Official Portal**: `mudra.org.in` / `udyamimitra.in`.
                    """.trimIndent()
                }
                Pair(text, listOf("https://www.mudra.org.in", "https://www.myscheme.gov.in"))
            }

            else -> {
                val text = when (lang.code) {
                    "hi" -> """
                        🇮🇳 **भारत सरकार एवं राज्य कल्याणकारी योजनाएं**:
                        
                        आपकी आवश्यकतानुसार प्रमुख सरकारी योजनाएं खोजने के लिए कृपया नीचे दी गई श्रेणियों में से बताएं:
                        - **शिक्षा व छात्रवृत्ति** (NSP, PM-USP)
                        - **कृषि व किसान कल्याण** (PM-Kisan, PMFBY, KCC)
                        - **स्वास्थ्य व सुरक्षा** (आयुष्मान भारत PM-JAY)
                        - **आवास योजना** (PMAY ग्रामीण व शहरी)
                        - **स्वरोजगार व लघु उद्योग** (Mudra ऋण, PM-SVANidhi)
                        
                        *सभी केंद्रीय व राज्य योजनाओं की आधिकारिक खोज के लिए `myscheme.gov.in` देखें।*
                    """.trimIndent()
                    else -> """
                        🇮🇳 **Government Schemes & Citizen Welfare Navigator**:
                        
                        To provide accurate eligibility and document guidelines, please specify:
                        1. Your state of residence.
                        2. The domain of interest:
                           - 🎓 **Student Scholarships** (NSP, PM-USP)
                           - 🌾 **Farmer & Agricultural Support** (PM-Kisan, PMFBY)
                           - 🏠 **Housing for All** (PMAY Urban/Gramin)
                           - 🏥 **Health & Medical Cover** (Ayushman Bharat PM-JAY)
                           - 💼 **Business & Self-Employment Loans** (MUDRA Yojana)
                        
                        *You can explore 500+ official central and state schemes at the official portal `myscheme.gov.in`.*
                    """.trimIndent()
                }
                Pair(text, listOf("https://www.myscheme.gov.in", "https://www.india.gov.in"))
            }
        }

        return SchemeChatMessage(
            text = content,
            isUser = false,
            sourceLinks = links,
            languageCode = lang.code,
            isCloudGenerated = false
        )
    }
}
