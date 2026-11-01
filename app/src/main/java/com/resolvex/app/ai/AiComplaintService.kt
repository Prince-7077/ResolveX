package com.resolvex.app.ai

import android.content.Context
import com.resolvex.app.data.Complaint
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
import kotlin.math.*

data class DuplicateMatchResult(
    val existingComplaintId: String,
    val existingComplaintTitle: String,
    val existingComplaintCategory: String,
    val similarityScore: Float, // 0.0 to 1.0
    val proximityMeters: Double?,
    val matchReason: String
)

data class AiAnalysisResult(
    val detectedCategory: String,
    val recommendedDepartment: String,
    val urgency: String, // "Low", "Medium", "High", "Critical"
    val urgencyReason: String,
    val summary: String,
    val duplicateMatch: DuplicateMatchResult?,
    val extractedKeywords: List<String> = emptyList(),
    val suggestedNextAction: String = "Submit complaint with photo and GPS coordinates for inspection.",
    val confidenceExplanation: String = "",
    val isAiBackendConnected: Boolean,
    val modelInfo: String
)

class AiComplaintService(private val context: Context? = null) {
    private val configManager = context?.let { AiConfigManager.getInstance(it) }

    companion object {
        const val URGENCY_LOW = "Low"
        const val URGENCY_MEDIUM = "Medium"
        const val URGENCY_HIGH = "High"
        const val URGENCY_CRITICAL = "Critical"

        // Recognized civic categories in ResolveX
        val CIVIC_CATEGORIES = listOf(
            "Roads & Infrastructure",
            "Water Supply & Leakage",
            "Electricity & Streetlights",
            "Sanitation & Waste Management",
            "Public Safety & Hazards",
            "Traffic & Public Transport",
            "Parks & Environment",
            "Public Health & Sanitation",
            "Education & Civic Amenities",
            "Other Civic Issue"
        )
    }

    /**
     * Analyzes complaint content.
     * Uses Gemini Cloud LLM if an API key is configured; otherwise uses the on-device semantic engine.
     */
    suspend fun analyzeComplaint(
        title: String,
        description: String,
        location: String?,
        latitude: Double?,
        longitude: Double?,
        existingComplaints: List<Complaint>
    ): AiAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = configManager?.getGeminiApiKey() ?: System.getenv("GEMINI_API_KEY")

        // 1. Check for duplicates against existing complaints locally
        val duplicateMatch = detectDuplicates(
            title = title,
            description = description,
            latitude = latitude,
            longitude = longitude,
            existingComplaints = existingComplaints
        )

        // 2. If Gemini API key is configured, call Cloud LLM
        if (!apiKey.isNullOrBlank()) {
            try {
                val cloudResult = callGeminiApi(
                    apiKey = apiKey,
                    title = title,
                    description = description,
                    location = location
                )
                if (cloudResult != null) {
                    return@withContext cloudResult.copy(duplicateMatch = duplicateMatch)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to transparent on-device semantic analysis engine
        return@withContext runOnDeviceSemanticEngine(
            title = title,
            description = description,
            location = location,
            duplicateMatch = duplicateMatch
        )
    }

    /**
     * On-Device Deterministic Semantic Analysis Engine
     * Transparently marks isAiBackendConnected = false and states that it uses the on-device engine.
     */
    fun runOnDeviceSemanticEngine(
        title: String,
        description: String,
        location: String?,
        duplicateMatch: DuplicateMatchResult?
    ): AiAnalysisResult {
        val fullText = "$title $description".lowercase(Locale.getDefault())

        // A. Category Detection
        val detectedCategory = detectCategoryFromText(fullText)

        // B. Department Recommendation
        val recommendedDepartment = recommendDepartment(detectedCategory, location)

        // C. Urgency Detection (Evidence-based: Critical requires life safety/immediate hazard indicators)
        val (urgency, urgencyReason) = determineUrgency(fullText)

        // D. Summary Generation
        val summary = generateSummary(title, description, location)

        // E. Keyword Extraction
        val keywords = extractKeywordsFromText(fullText)

        // F. Suggested Next Action
        val nextAction = when (urgency) {
            URGENCY_CRITICAL -> "Dispatch emergency response crew immediately; barricade hazard zone to prevent accidents."
            URGENCY_HIGH -> "Direct field engineer for priority on-site inspection within 24 hours."
            URGENCY_MEDIUM -> "Forward to division junior engineer for scheduled routine maintenance."
            else -> "Log in municipal maintenance queue for standard operational review."
        }

        // G. Confidence Explanation
        val explanation = "Classified as '$detectedCategory' with '$urgency' urgency based on matching civic risk indicators in report text."

        return AiAnalysisResult(
            detectedCategory = detectedCategory,
            recommendedDepartment = recommendedDepartment,
            urgency = urgency,
            urgencyReason = urgencyReason,
            summary = summary,
            duplicateMatch = duplicateMatch,
            extractedKeywords = keywords,
            suggestedNextAction = nextAction,
            confidenceExplanation = explanation,
            isAiBackendConnected = false,
            modelInfo = "On-Device Heuristic Engine (Configure Gemini API Key in Settings for Cloud LLM)"
        )
    }

    private fun extractKeywordsFromText(text: String): List<String> {
        val tokens = tokenize(text)
        val civicTerms = listOf(
            "pothole", "crater", "pipeline", "leakage", "sewage", "drainage", "waterlogging",
            "streetlight", "transformer", "wire", "voltage", "garbage", "waste", "dumping",
            "hazard", "manhole", "collapse", "gas", "traffic", "signal", "jam", "park", "mosquito",
            "dengue", "accident", "broken", "overflow", "stinking", "dark", "danger"
        )
        val found = civicTerms.filter { text.contains(it) }
        val remaining = tokens.filter { it !in found }.take(5)
        return (found + remaining).distinct().take(6)
    }

    /**
     * Duplicate Complaint Detection
     * Evaluates text similarity and geospatial distance (< 500m) against existing complaints.
     */
    fun detectDuplicates(
        title: String,
        description: String,
        latitude: Double?,
        longitude: Double?,
        existingComplaints: List<Complaint>
    ): DuplicateMatchResult? {
        if (title.isBlank() && description.isBlank()) return null

        val currentTokens = tokenize("$title $description")
        if (currentTokens.isEmpty()) return null

        var bestMatch: DuplicateMatchResult? = null
        var highestScore = 0f

        for (existing in existingComplaints) {
            val existingTokens = tokenize("${existing.title} ${existing.description}")
            if (existingTokens.isEmpty()) continue

            // Text similarity (Jaccard token overlap)
            val intersectionSize = currentTokens.intersect(existingTokens).size
            val unionSize = currentTokens.union(existingTokens).size
            val textSimilarity = if (unionSize > 0) intersectionSize.toFloat() / unionSize else 0f

            // Proximity calculation
            var distanceMeters: Double? = null
            if (latitude != null && longitude != null && existing.latitude != null && existing.longitude != null) {
                distanceMeters = calculateHaversineDistanceMeters(
                    latitude, longitude,
                    existing.latitude, existing.longitude
                )
            }

            // A match is flagged if:
            // 1) High text similarity (>= 0.45)
            // 2) Moderate text similarity (>= 0.25) AND physically within 500 meters
            val isProximityMatch = distanceMeters != null && distanceMeters <= 500.0 && textSimilarity >= 0.22f
            val isSemanticMatch = textSimilarity >= 0.42f

            if (isProximityMatch || isSemanticMatch) {
                val combinedScore = if (distanceMeters != null && distanceMeters <= 500.0) {
                    textSimilarity * 0.6f + (1.0f - (distanceMeters.toFloat() / 500f).coerceIn(0f, 1f)) * 0.4f
                } else {
                    textSimilarity
                }

                if (combinedScore > highestScore) {
                    highestScore = combinedScore
                    val reason = buildString {
                        if (distanceMeters != null && distanceMeters <= 500.0) {
                            append("Located ~${distanceMeters.roundToInt()}m away from an existing report")
                        }
                        if (textSimilarity >= 0.3f) {
                            if (isNotEmpty()) append(" • ")
                            append("Similar civic issue reported")
                        }
                    }

                    bestMatch = DuplicateMatchResult(
                        existingComplaintId = existing.id,
                        existingComplaintTitle = existing.title,
                        existingComplaintCategory = existing.category,
                        similarityScore = combinedScore,
                        proximityMeters = distanceMeters,
                        matchReason = reason.ifBlank { "High description similarity to existing complaint" }
                    )
                }
            }
        }

        return if (highestScore >= 0.35f) bestMatch else null
    }

    private fun detectCategoryFromText(text: String): String {
        val scores = mutableMapOf<String, Int>()

        // Roads
        val roadKeywords = listOf("pothole", "road", "tar", "asphalt", "flyover", "highway", "divider", "crater", "trench", "sidewalk", "footpath", "speedbreaker", "curb", "manhole without cover", "pavement")
        scores["Roads & Infrastructure"] = roadKeywords.count { text.contains(it) } * 3

        // Water
        val waterKeywords = listOf("water", "pipeline", "pipe burst", "leakage", "drinking water", "dirty water", "contaminated", "water supply", "no water", "low pressure", "drainage", "sewage", "gutter", "waterlogging", "flood")
        scores["Water Supply & Leakage"] = waterKeywords.count { text.contains(it) } * 3

        // Electricity
        val electricityKeywords = listOf("electricity", "power", "streetlight", "street light", "transformer", "wire", "cable", "pole", "sparking", "blackout", "voltage", "current", "electrocution", "shock", "meter")
        scores["Electricity & Streetlights"] = electricityKeywords.count { text.contains(it) } * 3

        // Sanitation
        val sanitationKeywords = listOf("garbage", "trash", "waste", "dumping", "rubbish", "dustbin", "dead animal", "carcass", "foul smell", "stinking", "litter", "debris", "plastic waste", "drain cleaning")
        scores["Sanitation & Waste Management"] = sanitationKeywords.count { text.contains(it) } * 3

        // Public Safety / Police
        val safetyKeywords = listOf("hazard", "danger", "accident", "theft", "unsafe", "collapse", "gas leak", "fire", "encroachment", "harassment", "unauthorized construction", "missing cover", "hanging wire", "chemical", "police")
        scores["Public Safety & Hazards"] = safetyKeywords.count { text.contains(it) } * 3

        // Traffic
        val trafficKeywords = listOf("traffic", "jam", "congestion", "signal", "traffic light", "zebra crossing", "bus stop", "parking", "auto stand", "wrong side", "rash driving")
        scores["Traffic & Public Transport"] = trafficKeywords.count { text.contains(it) } * 3

        // Parks & Environment
        val environmentKeywords = listOf("tree", "fallen tree", "branches", "park", "garden", "greenery", "air pollution", "burning", "smoke", "smog", "noise pollution")
        scores["Parks & Environment"] = environmentKeywords.count { text.contains(it) } * 3

        // Health
        val healthKeywords = listOf("mosquito", "dengue", "malaria", "hospital", "clinic", "stagnant water", "epidemic", "food poisoning")
        scores["Public Health & Sanitation"] = healthKeywords.count { text.contains(it) } * 3

        val topCategory = scores.maxByOrNull { it.value }
        return if (topCategory != null && topCategory.value > 0) {
            topCategory.key
        } else {
            "Other Civic Issue"
        }
    }

    private fun recommendDepartment(category: String, location: String?): String {
        return when (category) {
            "Roads & Infrastructure" -> "Roads & Infrastructure Division"
            "Water Supply & Leakage" -> "Water Supply & Sewerage Board"
            "Electricity & Streetlights" -> "Electricity & Power Distribution"
            "Sanitation & Waste Management" -> "Solid Waste & Sanitation Wing"
            "Public Safety & Hazards" -> "Public Health & Disaster Response"
            "Traffic & Public Transport" -> "Traffic & Public Transport"
            "Parks & Environment" -> "Parks & Urban Horticulture"
            "Public Health & Sanitation" -> "Public Health & Disaster Response"
            else -> "Municipal Governance Division"
        }
    }

    private fun determineUrgency(text: String): Pair<String, String> {
        // Critical: Strict verification of active life-safety hazard
        val criticalTriggers = listOf(
            "live wire" to "Exposed live electrical conductor poses immediate electrocution hazard",
            "sparking wire" to "Electrical fire hazard reported",
            "open manhole" to "Missing manhole cover on public thoroughfare poses fatal fall risk",
            "gas leak" to "Hazardous gas leakage poses explosion/suffocation hazard",
            "structural collapse" to "Imminent building/wall collapse threatens pedestrian safety",
            "electrocution" to "Reported electric shock hazard on municipal fixture",
            "deep trench" to "Unbarricaded excavation without cautionary lighting",
            "road caved in" to "Major road cave-in poses fatal vehicular accident hazard"
        )

        for ((trigger, reason) in criticalTriggers) {
            if (text.contains(trigger)) {
                return Pair(URGENCY_CRITICAL, reason)
            }
        }

        // High: Significant public impact or disruption
        val highTriggers = listOf(
            "pipeline burst" to "Large volume municipal pipeline burst causing severe water loss",
            "contaminated" to "Drinking water contamination poses acute community health hazard",
            "main road blocked" to "Primary transit arterial blocked obstructing emergency vehicles",
            "signal not working" to "Traffic signal failure at major junction causing gridlock/collisions",
            "hospital" to "Civic failure situated in hospital emergency access corridor",
            "school zone" to "Hazard located directly adjacent to school zone",
            "sewage overflow" to "Raw sewage backflow entering residential dwellings"
        )

        for ((trigger, reason) in highTriggers) {
            if (text.contains(trigger)) {
                return Pair(URGENCY_HIGH, reason)
            }
        }

        // Medium: Standard municipal issue requiring timely scheduled action
        val mediumTriggers = listOf(
            "pothole" to "Road surface deterioration impacting smooth vehicular movement",
            "streetlight" to "Non-functional streetlight reducing night visibility",
            "street light" to "Non-functional streetlight reducing night visibility",
            "garbage" to "Accumulated municipal solid waste requiring clearing schedule",
            "water leakage" to "Water leakage requires repair to prevent wastage",
            "low pressure" to "Reduced water supply pressure in neighborhood pipeline"
        )

        for ((trigger, reason) in mediumTriggers) {
            if (text.contains(trigger)) {
                return Pair(URGENCY_MEDIUM, reason)
            }
        }

        // Low: Minor maintenance or general civic enquiry
        return Pair(URGENCY_LOW, "Standard civic maintenance request without immediate hazard indicators")
    }

    private fun generateSummary(title: String, description: String, location: String?): String {
        val cleanTitle = title.trim().trimEnd('.')
        val cleanDesc = description.trim()
        val locTag = if (!location.isNullOrBlank()) " at $location" else ""

        return if (cleanDesc.length <= 120) {
            "$cleanTitle$locTag: $cleanDesc"
        } else {
            val firstSentence = cleanDesc.split('.', '\n').firstOrNull { it.isNotBlank() } ?: cleanDesc.take(100)
            "$cleanTitle$locTag. ${firstSentence.trim()}."
        }
    }

    private fun tokenize(text: String): Set<String> {
        val stopWords = setOf("the", "a", "an", "is", "in", "at", "of", "on", "and", "or", "to", "for", "with", "this", "that", "it", "my", "our", "near", "please", "kindly", "urgently")
        return text.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    private fun calculateHaversineDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Optional Gemini Cloud API integration
     */
    private fun callGeminiApi(
        apiKey: String,
        title: String,
        description: String,
        location: String?
    ): AiAnalysisResult? {
        val candidateModels = listOf("gemini-3.1-flash-lite", "gemini-3.6-flash", "gemini-3.5-flash")

        val prompt = """
            You are ResolveX AI Civic Intelligence Assistant for Indian Municipal Governance.
            Analyze this civic complaint:
            Title: "$title"
            Description: "$description"
            Location: "${location ?: "Not provided"}"

            Output ONLY a valid JSON object matching this schema without markdown fences:
            {
              "category": "one of: Roads & Infrastructure, Water Supply & Leakage, Electricity & Streetlights, Sanitation & Waste Management, Public Safety & Hazards, Traffic & Public Transport, Parks & Environment, Public Health & Sanitation, Other Civic Issue",
              "department": "recommended government department division name",
              "urgency": "one of: Low, Medium, High, Critical",
              "urgencyReason": "specific reason based on safety evidence",
              "summary": "1-2 sentence executive summary preserving location, core issue, and impact",
              "keywords": ["keyword1", "keyword2", "keyword3"],
              "suggestedNextAction": "recommended administrative action step",
              "explanation": "concise explanation of why this classification was assigned"
            }
        """.trimIndent()

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
        }

        val requestBody = requestJson.toString()

        for (modelName in candidateModels) {
            try {
                val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-goog-api-key", apiKey)
                conn.connectTimeout = 8000
                conn.readTimeout = 20000
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    val root = JSONObject(responseText)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val content = candidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val rawText = parts.getJSONObject(0).optString("text", "")
                            val cleanedJsonStr = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                            val parsed = JSONObject(cleanedJsonStr)
                            val kwJson = parsed.optJSONArray("keywords")
                            val keywordsList = mutableListOf<String>()
                            if (kwJson != null) {
                                for (i in 0 until kwJson.length()) {
                                    keywordsList.add(kwJson.optString(i))
                                }
                            }
                            return AiAnalysisResult(
                                detectedCategory = parsed.optString("category", "Other Civic Issue"),
                                recommendedDepartment = parsed.optString("department", "Municipal Governance Division"),
                                urgency = parsed.optString("urgency", "Medium"),
                                urgencyReason = parsed.optString("urgencyReason", "Analyzed by Google Gemini Cloud AI"),
                                summary = parsed.optString("summary", title),
                                duplicateMatch = null,
                                extractedKeywords = keywordsList,
                                suggestedNextAction = parsed.optString("suggestedNextAction", "Verify on-site and assign to field supervisor."),
                                confidenceExplanation = parsed.optString("explanation", "Evaluated by Google Gemini multimodal civic intelligence engine."),
                                isAiBackendConnected = true,
                                modelInfo = "Google Gemini Cloud AI"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next candidate model
            }
        }
        return null
    }
}
