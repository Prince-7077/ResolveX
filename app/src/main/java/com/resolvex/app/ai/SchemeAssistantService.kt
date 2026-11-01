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
    val languageCode: String = "en"
)

data class SupportedLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val speechLocale: String
)

class SchemeAssistantService(private val context: Context? = null) {
    private val configManager = context?.let { AiConfigManager.getInstance(it) }

    companion object {
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
            "https://www.mudra.org.in" to "Pradhan Mantri MUDRA Yojana"
        )
    }

    suspend fun askAssistant(
        userQuery: String,
        selectedLanguage: SupportedLanguage,
        conversationHistory: List<SchemeChatMessage>
    ): SchemeChatMessage = withContext(Dispatchers.IO) {
        val apiKey = configManager?.getGeminiApiKey() ?: System.getenv("GEMINI_API_KEY")

        // If Gemini API is configured, use Google Gemini with strict prompt
        if (!apiKey.isNullOrBlank()) {
            try {
                val cloudResponse = callGeminiForScheme(apiKey, userQuery, selectedLanguage, conversationHistory)
                if (cloudResponse != null) {
                    return@withContext cloudResponse
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // On-device verified knowledge base fallback (always reliable, no external dependencies required)
        return@withContext generateVerifiedFallbackResponse(userQuery, selectedLanguage)
    }

    private fun callGeminiForScheme(
        apiKey: String,
        userQuery: String,
        selectedLanguage: SupportedLanguage,
        history: List<SchemeChatMessage>
    ): SchemeChatMessage? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10000
        conn.readTimeout = 12000
        conn.doOutput = true

        val systemInstruction = """
            You are the ResolveX Multilingual Government Scheme & Citizen Welfare Assistant for India.
            Your purpose is to help Indian citizens understand and apply for verified Central and State Government welfare schemes.
            Target Language: Respond exclusively in ${selectedLanguage.englishName} (${selectedLanguage.nativeName}).

            Core Guidelines:
            1. Explain eligibility criteria, benefits, required documents, and step-by-step application procedures clearly.
            2. When information is incomplete, ask polite follow-up questions (e.g. state of residence, age, income slab, category).
            3. Differentiate clearly between Central Government and State Government schemes.
            4. Provide official verified portal URLs such as myscheme.gov.in, scholarships.gov.in, pmkisan.gov.in, india.gov.in.
            5. STRICT SECURITY: NEVER ask the user for Aadhaar numbers, OTP, passwords, bank account numbers, or UPI PINs.
            6. ACCURACY: Never fabricate non-existent schemes or guarantee 100% selection. Clearly cite official portals.
        """.trimIndent()

        val fullPrompt = "$systemInstruction\n\nCitizen's Question: \"$userQuery\""

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", fullPrompt))
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
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
                    val answerText = parts.getJSONObject(0).optString("text", "")
                    if (answerText.isNotBlank()) {
                        val links = mutableListOf<String>()
                        if (answerText.contains("scholarship", ignoreCase = true)) links.add("https://scholarships.gov.in")
                        if (answerText.contains("kisan", ignoreCase = true)) links.add("https://pmkisan.gov.in")
                        if (answerText.contains("awas", ignoreCase = true) || answerText.contains("housing", ignoreCase = true)) links.add("https://pmaymis.gov.in")
                        if (answerText.contains("ayushman", ignoreCase = true) || answerText.contains("health", ignoreCase = true)) links.add("https://pmjay.gov.in")
                        if (links.isEmpty()) links.add("https://www.myscheme.gov.in")

                        return SchemeChatMessage(
                            text = answerText.trim(),
                            isUser = false,
                            sourceLinks = links.distinct(),
                            languageCode = selectedLanguage.code
                        )
                    }
                }
            }
        }
        return null
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
            languageCode = lang.code
        )
    }
}
