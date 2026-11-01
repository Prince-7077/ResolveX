package com.resolvex.app

import com.resolvex.app.ai.AiComplaintService
import com.resolvex.app.ai.SchemeAssistantService
import com.resolvex.app.ai.SupportedLanguage
import com.resolvex.app.data.AdminEvidence
import com.resolvex.app.data.Complaint
import com.resolvex.app.data.ComplaintStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResolveXFeatureTests {

    @Test
    fun testComplaintStatuses() {
        val statuses = ComplaintStatus.values().map { it.name }
        assertTrue(statuses.contains("SUBMITTED"))
        assertTrue(statuses.contains("UNDER_REVIEW"))
        assertTrue(statuses.contains("EVIDENCE_REQUESTED"))
        assertTrue(statuses.contains("ASSIGNED"))
        assertTrue(statuses.contains("IN_PROGRESS"))
        assertTrue(statuses.contains("ACTION_TAKEN"))
        assertTrue(statuses.contains("RESOLVED"))
        assertTrue(statuses.contains("REJECTED"))
    }

    @Test
    fun testAdminEvidenceModel() {
        val evidence = AdminEvidence(
            id = "ev_101",
            mediaUri = "content://media/external/images/media/42",
            mediaType = "IMAGE",
            fileName = "site_inspection_01.jpg",
            remarks = "Field inspection verified the ruptured pipe.",
            uploadedAt = 1730454600000L,
            uploadedBy = "admin@resolvex.gov.in"
        )
        assertEquals("IMAGE", evidence.mediaType)
        assertEquals("site_inspection_01.jpg", evidence.fileName)
        assertEquals("admin@resolvex.gov.in", evidence.uploadedBy)
        assertEquals(1730454600000L, evidence.uploadedAt)
    }

    @Test
    fun testAiSemanticEngineRoadCategory() = runBlocking {
        val aiService = AiComplaintService()
        val title = "Massive pothole on main road"
        val desc = "Deep pothole and damaged asphalt pavement along the municipal road. Footpath curb is broken."
        
        val analysis = aiService.runOnDeviceSemanticEngine(
            title = title,
            description = desc,
            location = "Delhi",
            duplicateMatch = null
        )

        assertNotNull(analysis)
        assertEquals("Roads & Infrastructure", analysis.detectedCategory)
        assertTrue(analysis.recommendedDepartment.isNotEmpty())
        assertEquals("Medium", analysis.urgency)
        assertNotNull(analysis.summary)
        assertTrue(analysis.extractedKeywords.isNotEmpty())
        assertTrue(analysis.confidenceExplanation.isNotBlank())
        assertTrue(analysis.suggestedNextAction.isNotBlank())
    }

    @Test
    fun testAiSemanticEngineWaterCategory() = runBlocking {
        val aiService = AiComplaintService()
        val title = "Severe water pipeline leakage"
        val desc = "Clean drinking water leaking continuously from burst municipal line since 2 days."
        
        val analysis = aiService.runOnDeviceSemanticEngine(
            title = title,
            description = desc,
            location = "Mumbai, Maharashtra",
            duplicateMatch = null
        )

        assertNotNull(analysis)
        assertEquals("Water Supply & Leakage", analysis.detectedCategory)
        assertTrue(analysis.recommendedDepartment.contains("Water") || analysis.recommendedDepartment.contains("Jal"))
        assertTrue(analysis.extractedKeywords.contains("leakage") || analysis.extractedKeywords.contains("water") || analysis.extractedKeywords.contains("pipeline"))
    }

    @Test
    fun testAiDuplicateDetection() = runBlocking {
        val aiService = AiComplaintService()
        val existing = listOf(
            Complaint(
                id = "RX-20261101-1001",
                title = "Broken water pipeline on MG road",
                description = "Severe water leak flooding the main road near bus stop.",
                category = "Water Supply & Leakage",
                status = ComplaintStatus.SUBMITTED,
                citizenName = "Ramesh Kumar",
                citizenPhone = "9876543210",
                citizenEmail = "ramesh@example.com",
                latitude = 28.6139,
                longitude = 77.2090
            )
        )

        val newTitle = "Water pipe broken at MG road bus stop"
        val newDesc = "Water flooding the street from pipe rupture."
        val duplicateMatch = aiService.detectDuplicates(
            title = newTitle,
            description = newDesc,
            latitude = 28.6140,
            longitude = 77.2091,
            existingComplaints = existing
        )

        assertNotNull("Should detect similarity with existing MG road water pipe report", duplicateMatch)
        assertEquals("RX-20261101-1001", duplicateMatch?.existingComplaintId)
    }

    @Test
    fun testSchemeAssistantOfflineKnowledge() {
        val service = SchemeAssistantService()
        val english = SupportedLanguage("en", "English", "English", "en-IN")
        val hindi = SupportedLanguage("hi", "Hindi", "हिंदी", "hi-IN")

        val responseKisanEn = service.generateVerifiedFallbackResponse(
            query = "Tell me about PM-Kisan financial support for small farmers",
            lang = english
        )
        assertTrue(responseKisanEn.text.contains("PM-Kisan") || responseKisanEn.text.contains("Kisan"))
        assertTrue(responseKisanEn.sourceLinks.any { it.contains("pmkisan.gov.in") })

        val responseScholarshipHi = service.generateVerifiedFallbackResponse(
            query = "National scholarship portal eligibility",
            lang = hindi
        )
        assertTrue(responseScholarshipHi.text.contains("छात्रवृत्ति") || responseScholarshipHi.text.contains("NSP"))
        assertTrue(responseScholarshipHi.sourceLinks.any { it.contains("scholarships.gov.in") })
    }

    @Test
    fun testSchemeAssistantMultilingualLanguageSupport() {
        val supportedLangs = SchemeAssistantService.SUPPORTED_LANGUAGES.map { it.englishName }
        val requiredLangs = listOf(
            "English", "Hindi", "Assamese", "Bengali", "Tamil", "Telugu",
            "Marathi", "Gujarati", "Kannada", "Malayalam", "Punjabi", "Odia", "Urdu"
        )
        for (lang in requiredLangs) {
            assertTrue("Language $lang must be in supported list", supportedLangs.contains(lang))
        }
    }
}
