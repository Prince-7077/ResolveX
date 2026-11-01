package com.resolvex.app.data

enum class ComplaintStatus(val displayName: String) {
    SUBMITTED("Submitted"),
    UNDER_REVIEW("Under Review"),
    EVIDENCE_REQUESTED("Evidence Requested"),
    VERIFIED("Verified"),
    ASSIGNED("Assigned"),
    IN_PROGRESS("In Progress"),
    ACTION_TAKEN("Action Taken"),
    RESOLVED("Resolved"),
    REJECTED("Rejected")
}

data class AdminEvidence(
    val id: String = java.util.UUID.randomUUID().toString(),
    val mediaUri: String,
    val mediaType: String, // "PHOTO", "VIDEO", "DOCUMENT"
    val fileName: String,
    val remarks: String? = null,
    val uploadedAt: Long = System.currentTimeMillis(),
    val uploadedBy: String
)

data class TrackingEvent(
    val status: ComplaintStatus,
    val title: String,
    val remarks: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val updatedBy: String? = null
)

data class Complaint(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val status: ComplaintStatus = ComplaintStatus.SUBMITTED,
    val priority: String = "Normal",
    val assignedDepartment: String = "Pending Assignment",
    val assignedOfficer: String? = null,
    val citizenName: String,
    val citizenPhone: String,
    val citizenEmail: String,
    val locationAddress: String = "Location Not Provided",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageUri: String? = null,
    val videoUri: String? = null,
    val isPublic: Boolean = true, // Public or Private visibility (Phase 3)
    val createdAt: Long = System.currentTimeMillis(),
    val adminNotes: String? = null,
    val adminEvidenceList: List<AdminEvidence> = emptyList(),
    val resolutionNote: String? = null,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null,
    val resolutionEvidenceUri: String? = null, // After-action evidence photo (Phase 6)
    val rejectionReason: String? = null,
    val history: List<TrackingEvent> = emptyList(),

    // Smart Escalation & SLA (Phase 4)
    val isEscalated: Boolean = false,
    val escalationLevel: Int = 0, // 0 = Normal, 1 = Escalated to Officer, 2 = Escalated to Admin
    val escalationReason: String? = null,

    // Citizen Feedback & Reopen (Phase 5)
    val feedbackRating: Int? = null, // 1 to 5 stars
    val feedbackComment: String? = null,
    val feedbackEvidenceUri: String? = null,
    val isReopened: Boolean = false,
    val reopenCount: Int = 0,
    val reopenReason: String? = null,

    // AI Complaint Intelligence fields (Phase 7)
    val aiSuggestedCategory: String? = null,
    val aiSuggestedDepartment: String? = null,
    val aiSuggestedUrgency: String? = null,
    val aiSummary: String? = null,
    val aiUrgencyReason: String? = null,
    val aiExtractedKeywords: List<String> = emptyList(),
    val aiSuggestedNextAction: String? = null,
    val possibleDuplicateOfId: String? = null,
    val supportCount: Int = 0,
    val supportedByEmails: List<String> = emptyList()
) {
    /**
     * Returns the full tracking history list or synthesizes the initial submission event
     * if history has not been explicitly initialized for legacy complaints.
     */
    fun getEffectiveHistory(): List<TrackingEvent> {
        if (history.isNotEmpty()) return history
        return listOf(
            TrackingEvent(
                status = ComplaintStatus.SUBMITTED,
                title = "Complaint Submitted",
                remarks = "Registered in ResolveX registry",
                timestamp = createdAt,
                updatedBy = citizenName.ifBlank { "Citizen" }
            )
        )
    }

    /**
     * Checks if this complaint has valid geographic coordinates for map display.
     */
    fun hasValidCoordinates(): Boolean {
        return latitude != null && longitude != null &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /**
     * Configured SLA in milliseconds based on priority and category.
     */
    fun getSlaDurationHours(): Int {
        return when (priority.lowercase()) {
            "critical" -> 24
            "high" -> 48
            else -> when {
                category.contains("Safety", ignoreCase = true) || category.contains("Hazard", ignoreCase = true) -> 24
                category.contains("Water", ignoreCase = true) || category.contains("Electric", ignoreCase = true) -> 48
                else -> 120 // 5 days for normal
            }
        }
    }

    fun isOverdue(): Boolean {
        if (status == ComplaintStatus.RESOLVED || status == ComplaintStatus.REJECTED) return false
        val elapsedHours = (System.currentTimeMillis() - createdAt) / (1000 * 60 * 60)
        return elapsedHours > getSlaDurationHours()
    }
}
