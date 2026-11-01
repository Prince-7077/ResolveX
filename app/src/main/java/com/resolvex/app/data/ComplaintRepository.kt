package com.resolvex.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class ComplaintRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val notificationRepo by lazy { NotificationRepository.getInstance(context) }

    companion object {
        private const val PREF_NAME = "resolvex_complaints_store"
        private const val KEY_COMPLAINTS_JSON = "complaints_json_data"

        @Volatile
        private var instance: ComplaintRepository? = null

        fun getInstance(context: Context): ComplaintRepository {
            return instance ?: synchronized(this) {
                instance ?: ComplaintRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Generates a unique complaint ID formatted as RX-YYYY-XXXXXX
     * Guaranteed unique against current stored complaints.
     */
    @Synchronized
    fun generateUniqueComplaintId(): String {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        var uniqueId: String
        do {
            val randomNum = (100000..999999).random()
            uniqueId = "RX-$year-$randomNum"
        } while (getComplaintById(uniqueId) != null)
        return uniqueId
    }

    @Synchronized
    fun getAllComplaints(): List<Complaint> {
        val jsonStr = prefs.getString(KEY_COMPLAINTS_JSON, null) ?: return emptyList()
        val list = mutableListOf<Complaint>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(fromJson(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun getPublicComplaints(): List<Complaint> {
        return getAllComplaints().filter { it.isPublic }
    }

    @Synchronized
    fun getComplaintsForCitizen(email: String): List<Complaint> {
        if (email.isBlank()) return emptyList()
        return getAllComplaints().filter { it.citizenEmail.equals(email, ignoreCase = true) }
    }

    @Synchronized
    fun getComplaintsForDepartment(department: String): List<Complaint> {
        if (department.isBlank()) return emptyList()
        return getAllComplaints().filter { it.assignedDepartment.contains(department, ignoreCase = true) }
    }

    @Synchronized
    fun getComplaintsForOfficer(officerIdOrEmail: String, department: String?): List<Complaint> {
        val all = getAllComplaints()
        val cleanKey = officerIdOrEmail.trim().lowercase()
        return all.filter { c ->
            val matchesOfficer = c.assignedOfficer?.lowercase()?.contains(cleanKey) == true
            val matchesDept = if (!department.isNullOrBlank()) {
                c.assignedDepartment.contains(department, ignoreCase = true)
            } else false
            matchesOfficer || matchesDept
        }
    }

    @Synchronized
    fun getComplaintById(id: String): Complaint? {
        if (id.isBlank()) return null
        return getAllComplaints().firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }

    @Synchronized
    fun getComplaintsWithCoordinates(): List<Complaint> {
        return getAllComplaints().filter { it.hasValidCoordinates() }
    }

    @Synchronized
    fun getPublicComplaintsWithCoordinates(): List<Complaint> {
        return getAllComplaints().filter { it.isPublic && it.hasValidCoordinates() }
    }

    @Synchronized
    fun saveComplaint(complaint: Complaint) {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == complaint.id }
        if (index >= 0) {
            currentList[index] = complaint
        } else {
            currentList.add(0, complaint)

            // Send confirmation notification to citizen
            if (complaint.citizenEmail.isNotBlank()) {
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = complaint.citizenEmail,
                        complaintId = complaint.id,
                        title = "Complaint Registered",
                        message = "Your complaint #${complaint.id} ('${complaint.title}') has been successfully registered in the civic registry.",
                        type = "STATUS_CHANGE"
                    )
                )
            }
        }
        saveList(currentList)
    }

    @Synchronized
    fun toggleSupportVote(complaintId: String, citizenEmail: String): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == complaintId }
        if (index >= 0) {
            val existing = currentList[index]
            val emailKey = citizenEmail.trim().lowercase()
            if (emailKey.isBlank()) return false

            val alreadySupported = existing.supportedByEmails.any { it.trim().lowercase() == emailKey }
            val updatedEmails = if (alreadySupported) {
                existing.supportedByEmails.filter { it.trim().lowercase() != emailKey }
            } else {
                existing.supportedByEmails + emailKey
            }
            val newCount = updatedEmails.size

            val updated = existing.copy(
                supportCount = newCount,
                supportedByEmails = updatedEmails
            )
            currentList[index] = updated
            saveList(currentList)
            return !alreadySupported
        }
        return false
    }

    @Synchronized
    fun updateComplaintStatus(
        id: String,
        newStatus: ComplaintStatus,
        department: String? = null,
        priority: String? = null,
        adminNotes: String? = null,
        updatedBy: String? = null
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val newHistory = existing.getEffectiveHistory().toMutableList()

            val targetDept = department ?: existing.assignedDepartment
            val targetPriority = priority ?: existing.priority
            val targetNotes = adminNotes ?: existing.adminNotes

            if (existing.status != newStatus || department != null || priority != null) {
                val eventTitle = when (newStatus) {
                    ComplaintStatus.SUBMITTED -> "Complaint Submitted"
                    ComplaintStatus.UNDER_REVIEW -> "Under Review by Municipal Authority"
                    ComplaintStatus.EVIDENCE_REQUESTED -> "Additional Information Requested"
                    ComplaintStatus.VERIFIED -> "Complaint Verified & Department Routing"
                    ComplaintStatus.ASSIGNED -> "Assigned to $targetDept"
                    ComplaintStatus.IN_PROGRESS -> "Work in Progress / Crew Dispatched"
                    ComplaintStatus.ACTION_TAKEN -> "Corrective Action Recorded"
                    ComplaintStatus.RESOLVED -> "Complaint Formally Resolved"
                    ComplaintStatus.REJECTED -> "Complaint Rejected"
                }

                val remarksText = buildString {
                    if (!adminNotes.isNullOrBlank()) append(adminNotes)
                    if (department != null && department != existing.assignedDepartment) {
                        if (isNotEmpty()) append(" • ")
                        append("Department allocated: $department")
                    }
                    if (priority != null && priority != existing.priority) {
                        if (isNotEmpty()) append(" • ")
                        append("Priority updated: $priority")
                    }
                }

                newHistory.add(
                    TrackingEvent(
                        status = newStatus,
                        title = eventTitle,
                        remarks = remarksText,
                        timestamp = System.currentTimeMillis(),
                        updatedBy = updatedBy ?: "Municipal Authority"
                    )
                )
            }

            val updated = existing.copy(
                status = newStatus,
                assignedDepartment = targetDept,
                priority = targetPriority,
                adminNotes = targetNotes,
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)

            // Notify citizen
            if (existing.citizenEmail.isNotBlank()) {
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = existing.citizenEmail,
                        complaintId = existing.id,
                        title = "Status Update: ${newStatus.displayName}",
                        message = "Your complaint #${existing.id} has been moved to ${newStatus.displayName} by ${updatedBy ?: "the municipal authority"}.",
                        type = "STATUS_CHANGE"
                    )
                )
            }

            return true
        }
        return false
    }

    /**
     * Upload and attach administrative evidence (site photos, inspection video, work order documents)
     * with remarks to a query without resolving it.
     */
    @Synchronized
    fun addAdminEvidence(
        id: String,
        evidence: AdminEvidence,
        adminRemarks: String? = null,
        updatedBy: String
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val updatedEvidenceList = existing.adminEvidenceList + evidence
            val newHistory = existing.getEffectiveHistory().toMutableList()

            val remarkDetail = buildString {
                append("Attached ${evidence.mediaType.lowercase()}: ${evidence.fileName}")
                if (!evidence.remarks.isNullOrBlank()) {
                    append(" • ${evidence.remarks}")
                }
                if (!adminRemarks.isNullOrBlank() && adminRemarks != evidence.remarks) {
                    append(" • $adminRemarks")
                }
            }

            newHistory.add(
                TrackingEvent(
                    status = existing.status,
                    title = "Officer Evidence Uploaded (${evidence.mediaType})",
                    remarks = remarkDetail,
                    timestamp = System.currentTimeMillis(),
                    updatedBy = updatedBy
                )
            )

            val updated = existing.copy(
                adminEvidenceList = updatedEvidenceList,
                adminNotes = adminRemarks ?: existing.adminNotes,
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)
            return true
        }
        return false
    }

    /**
     * Assigns the query to a department and specific officer with internal remarks and workflow status.
     */
    @Synchronized
    fun assignAndReviewQuery(
        id: String,
        newStatus: ComplaintStatus,
        department: String,
        officer: String?,
        internalRemarks: String?,
        updatedBy: String
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val newHistory = existing.getEffectiveHistory().toMutableList()

            val summaryRemarks = buildString {
                append("Assigned to: $department")
                if (!officer.isNullOrBlank()) {
                    append(" (Officer: $officer)")
                }
                if (!internalRemarks.isNullOrBlank()) {
                    append(" • Note: $internalRemarks")
                }
            }

            newHistory.add(
                TrackingEvent(
                    status = newStatus,
                    title = "Assigned to $department",
                    remarks = summaryRemarks,
                    timestamp = System.currentTimeMillis(),
                    updatedBy = updatedBy
                )
            )

            val updated = existing.copy(
                status = newStatus,
                assignedDepartment = department,
                assignedOfficer = officer?.ifBlank { null } ?: existing.assignedOfficer,
                adminNotes = internalRemarks ?: existing.adminNotes,
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)

            // Notify citizen
            if (existing.citizenEmail.isNotBlank()) {
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = existing.citizenEmail,
                        complaintId = existing.id,
                        title = "Complaint Assigned",
                        message = "Your complaint #${existing.id} has been assigned to $department.",
                        type = "ASSIGNMENT"
                    )
                )
            }

            // Notify officer if assigned
            if (!officer.isNullOrBlank()) {
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = officer,
                        complaintId = existing.id,
                        title = "New Complaint Allocated",
                        message = "Complaint #${existing.id} ('${existing.title}') has been assigned to you for inspection and action.",
                        type = "ASSIGNMENT"
                    )
                )
            }

            return true
        }
        return false
    }

    /**
     * Request additional information/evidence from citizen.
     */
    @Synchronized
    fun requestAdditionalInfo(
        id: String,
        requestDetails: String,
        requestedBy: String
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val newHistory = existing.getEffectiveHistory().toMutableList()

            newHistory.add(
                TrackingEvent(
                    status = ComplaintStatus.EVIDENCE_REQUESTED,
                    title = "Additional Information Requested",
                    remarks = requestDetails,
                    timestamp = System.currentTimeMillis(),
                    updatedBy = requestedBy
                )
            )

            val updated = existing.copy(
                status = ComplaintStatus.EVIDENCE_REQUESTED,
                adminNotes = "Citizen Info Requested: $requestDetails",
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)

            // Notify citizen
            if (existing.citizenEmail.isNotBlank()) {
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = existing.citizenEmail,
                        complaintId = existing.id,
                        title = "Information Requested",
                        message = "The municipal officer requested more information for #${existing.id}: $requestDetails",
                        type = "EVIDENCE_REQUEST"
                    )
                )
            }

            return true
        }
        return false
    }

    /**
     * Explicit resolution with required resolution note and optional after-action evidence.
     * Query is never automatically resolved.
     */
    @Synchronized
    fun resolveComplaint(
        id: String,
        resolutionNote: String,
        resolutionEvidenceUri: String? = null,
        resolvedBy: String
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val newHistory = existing.getEffectiveHistory().toMutableList()
            val now = System.currentTimeMillis()

            newHistory.add(
                TrackingEvent(
                    status = ComplaintStatus.RESOLVED,
                    title = "Complaint Formally Resolved",
                    remarks = resolutionNote,
                    timestamp = now,
                    updatedBy = resolvedBy
                )
            )

            val updated = existing.copy(
                status = ComplaintStatus.RESOLVED,
                resolutionNote = resolutionNote,
                resolutionEvidenceUri = resolutionEvidenceUri ?: existing.resolutionEvidenceUri,
                resolvedAt = now,
                resolvedBy = resolvedBy,
                adminNotes = "Resolution Note: $resolutionNote",
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)

            // Notify citizen to provide feedback
            if (existing.citizenEmail.isNotBlank()) {
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = existing.citizenEmail,
                        complaintId = existing.id,
                        title = "Issue Marked Resolved",
                        message = "Complaint #${existing.id} has been resolved by $resolvedBy. Please confirm if the issue is fixed.",
                        type = "RESOLVED"
                    )
                )
            }

            return true
        }
        return false
    }

    /**
     * Explicit rejection with mandatory rejection reason.
     */
    @Synchronized
    fun rejectComplaint(
        id: String,
        rejectionReason: String,
        rejectedBy: String
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val newHistory = existing.getEffectiveHistory().toMutableList()
            val now = System.currentTimeMillis()

            newHistory.add(
                TrackingEvent(
                    status = ComplaintStatus.REJECTED,
                    title = "Complaint Rejected",
                    remarks = rejectionReason,
                    timestamp = now,
                    updatedBy = rejectedBy
                )
            )

            val updated = existing.copy(
                status = ComplaintStatus.REJECTED,
                rejectionReason = rejectionReason,
                adminNotes = "Rejection Reason: $rejectionReason",
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)

            // Notify citizen
            if (existing.citizenEmail.isNotBlank()) {
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = existing.citizenEmail,
                        complaintId = existing.id,
                        title = "Complaint Rejected",
                        message = "Complaint #${existing.id} was rejected. Reason: $rejectionReason",
                        type = "STATUS_CHANGE"
                    )
                )
            }

            return true
        }
        return false
    }

    /**
     * Citizen Feedback & Rating (Phase 5)
     */
    @Synchronized
    fun recordCitizenFeedback(
        id: String,
        rating: Int,
        comment: String?,
        evidenceUri: String?,
        citizenEmail: String
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val newHistory = existing.getEffectiveHistory().toMutableList()
            val now = System.currentTimeMillis()

            newHistory.add(
                TrackingEvent(
                    status = existing.status,
                    title = "Citizen Feedback Submitted ($rating★)",
                    remarks = comment ?: "Citizen rated resolution $rating/5 stars.",
                    timestamp = now,
                    updatedBy = existing.citizenName.ifBlank { citizenEmail }
                )
            )

            val updated = existing.copy(
                feedbackRating = rating,
                feedbackComment = comment,
                feedbackEvidenceUri = evidenceUri,
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)
            return true
        }
        return false
    }

    /**
     * Citizen Reopen Request (Phase 5)
     */
    @Synchronized
    fun reopenComplaint(
        id: String,
        reason: String,
        citizenEmail: String
    ): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) {
            val existing = currentList[index]
            val newHistory = existing.getEffectiveHistory().toMutableList()
            val now = System.currentTimeMillis()

            newHistory.add(
                TrackingEvent(
                    status = ComplaintStatus.UNDER_REVIEW,
                    title = "Complaint Reopened by Citizen",
                    remarks = "Reopen Request: $reason",
                    timestamp = now,
                    updatedBy = existing.citizenName.ifBlank { citizenEmail }
                )
            )

            val updated = existing.copy(
                status = ComplaintStatus.UNDER_REVIEW,
                isReopened = true,
                reopenCount = existing.reopenCount + 1,
                reopenReason = reason,
                adminNotes = "Reopened by citizen: $reason",
                history = newHistory
            )
            currentList[index] = updated
            saveList(currentList)

            // Notify admin and assigned department
            notificationRepo.addNotification(
                NotificationItem(
                    targetUserEmail = "admin@resolvex.gov.in",
                    complaintId = existing.id,
                    title = "Complaint Reopened: #${existing.id}",
                    message = "Citizen ${existing.citizenName} reported the issue is not fixed: $reason",
                    type = "REOPENED"
                )
            )

            return true
        }
        return false
    }

    /**
     * Smart Escalation check based on SLA (Phase 4)
     */
    @Synchronized
    fun checkAndTriggerSlaEscalations(): List<Complaint> {
        val currentList = getAllComplaints().toMutableList()
        val escalatedComplaints = mutableListOf<Complaint>()
        var changesMade = false

        for (i in currentList.indices) {
            val complaint = currentList[i]
            if (complaint.isOverdue() && !complaint.isEscalated) {
                val newLevel = 1
                val reason = "Unresolved beyond ${complaint.getSlaDurationHours()}h SLA threshold."
                val newHistory = complaint.getEffectiveHistory().toMutableList()

                newHistory.add(
                    TrackingEvent(
                        status = complaint.status,
                        title = "Automated SLA Escalation (Level $newLevel)",
                        remarks = reason,
                        timestamp = System.currentTimeMillis(),
                        updatedBy = "ResolveX SLA Engine"
                    )
                )

                val updated = complaint.copy(
                    isEscalated = true,
                    escalationLevel = newLevel,
                    escalationReason = reason,
                    history = newHistory
                )
                currentList[i] = updated
                escalatedComplaints.add(updated)
                changesMade = true

                // Notify admin
                notificationRepo.addNotification(
                    NotificationItem(
                        targetUserEmail = "admin@resolvex.gov.in",
                        complaintId = complaint.id,
                        title = "SLA Escalation Alert: #${complaint.id}",
                        message = "Complaint #${complaint.id} ('${complaint.title}') exceeded SLA and has been escalated.",
                        type = "ESCALATION"
                    )
                )
            }
        }

        if (changesMade) {
            saveList(currentList)
        }
        return escalatedComplaints
    }

    @Synchronized
    fun deleteComplaint(id: String): Boolean {
        val currentList = getAllComplaints().toMutableList()
        val removed = currentList.removeAll { it.id == id }
        if (removed) {
            saveList(currentList)
        }
        return removed
    }

    private fun saveList(list: List<Complaint>) {
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(toJson(item))
        }
        prefs.edit().putString(KEY_COMPLAINTS_JSON, jsonArray.toString()).apply()
    }

    private fun toJson(complaint: Complaint): JSONObject {
        val historyArray = JSONArray()
        complaint.getEffectiveHistory().forEach { event ->
            val eventObj = JSONObject().apply {
                put("status", event.status.name)
                put("title", event.title)
                put("remarks", event.remarks ?: JSONObject.NULL)
                put("timestamp", event.timestamp)
                put("updatedBy", event.updatedBy ?: JSONObject.NULL)
            }
            historyArray.put(eventObj)
        }

        val supportedEmailsArray = JSONArray()
        complaint.supportedByEmails.forEach { supportedEmailsArray.put(it) }

        val evidenceArray = JSONArray()
        complaint.adminEvidenceList.forEach { ev ->
            val evObj = JSONObject().apply {
                put("id", ev.id)
                put("mediaUri", ev.mediaUri)
                put("mediaType", ev.mediaType)
                put("fileName", ev.fileName)
                put("remarks", ev.remarks ?: JSONObject.NULL)
                put("uploadedAt", ev.uploadedAt)
                put("uploadedBy", ev.uploadedBy)
            }
            evidenceArray.put(evObj)
        }

        val keywordsArray = JSONArray()
        complaint.aiExtractedKeywords.forEach { keywordsArray.put(it) }

        return JSONObject().apply {
            put("id", complaint.id)
            put("title", complaint.title)
            put("description", complaint.description)
            put("category", complaint.category)
            put("status", complaint.status.name)
            put("priority", complaint.priority)
            put("assignedDepartment", complaint.assignedDepartment)
            put("assignedOfficer", complaint.assignedOfficer ?: JSONObject.NULL)
            put("citizenName", complaint.citizenName)
            put("citizenPhone", complaint.citizenPhone)
            put("citizenEmail", complaint.citizenEmail)
            put("locationAddress", complaint.locationAddress)
            put("latitude", complaint.latitude ?: JSONObject.NULL)
            put("longitude", complaint.longitude ?: JSONObject.NULL)
            put("imageUri", complaint.imageUri ?: JSONObject.NULL)
            put("videoUri", complaint.videoUri ?: JSONObject.NULL)
            put("isPublic", complaint.isPublic)
            put("createdAt", complaint.createdAt)
            put("adminNotes", complaint.adminNotes ?: JSONObject.NULL)
            put("adminEvidenceList", evidenceArray)
            put("resolutionNote", complaint.resolutionNote ?: JSONObject.NULL)
            put("resolvedAt", complaint.resolvedAt ?: JSONObject.NULL)
            put("resolvedBy", complaint.resolvedBy ?: JSONObject.NULL)
            put("resolutionEvidenceUri", complaint.resolutionEvidenceUri ?: JSONObject.NULL)
            put("rejectionReason", complaint.rejectionReason ?: JSONObject.NULL)
            put("history", historyArray)

            // Smart Escalation & SLA
            put("isEscalated", complaint.isEscalated)
            put("escalationLevel", complaint.escalationLevel)
            put("escalationReason", complaint.escalationReason ?: JSONObject.NULL)

            // Citizen Feedback & Reopen
            put("feedbackRating", complaint.feedbackRating ?: JSONObject.NULL)
            put("feedbackComment", complaint.feedbackComment ?: JSONObject.NULL)
            put("feedbackEvidenceUri", complaint.feedbackEvidenceUri ?: JSONObject.NULL)
            put("isReopened", complaint.isReopened)
            put("reopenCount", complaint.reopenCount)
            put("reopenReason", complaint.reopenReason ?: JSONObject.NULL)

            // AI Intelligence fields
            put("aiSuggestedCategory", complaint.aiSuggestedCategory ?: JSONObject.NULL)
            put("aiSuggestedDepartment", complaint.aiSuggestedDepartment ?: JSONObject.NULL)
            put("aiSuggestedUrgency", complaint.aiSuggestedUrgency ?: JSONObject.NULL)
            put("aiSummary", complaint.aiSummary ?: JSONObject.NULL)
            put("aiUrgencyReason", complaint.aiUrgencyReason ?: JSONObject.NULL)
            put("aiExtractedKeywords", keywordsArray)
            put("aiSuggestedNextAction", complaint.aiSuggestedNextAction ?: JSONObject.NULL)
            put("possibleDuplicateOfId", complaint.possibleDuplicateOfId ?: JSONObject.NULL)
            put("supportCount", complaint.supportCount)
            put("supportedByEmails", supportedEmailsArray)
        }
    }

    private fun fromJson(obj: JSONObject): Complaint {
        val statusStr = obj.optString("status", ComplaintStatus.SUBMITTED.name)
        val status = try {
            ComplaintStatus.valueOf(statusStr)
        } catch (e: Exception) {
            ComplaintStatus.SUBMITTED
        }

        val lat = if (obj.isNull("latitude")) null else obj.optDouble("latitude")
        val lon = if (obj.isNull("longitude")) null else obj.optDouble("longitude")
        val img = if (obj.isNull("imageUri")) null else obj.optString("imageUri")
        val vid = if (obj.isNull("videoUri")) null else obj.optString("videoUri")
        val notes = if (obj.isNull("adminNotes")) null else obj.optString("adminNotes")
        val officer = if (obj.isNull("assignedOfficer")) null else obj.optString("assignedOfficer")
        val resNote = if (obj.isNull("resolutionNote")) null else obj.optString("resolutionNote")
        val resAt = if (obj.isNull("resolvedAt")) null else obj.optLong("resolvedAt")
        val resBy = if (obj.isNull("resolvedBy")) null else obj.optString("resolvedBy")
        val resEvidence = if (obj.isNull("resolutionEvidenceUri")) null else obj.optString("resolutionEvidenceUri")
        val rejReason = if (obj.isNull("rejectionReason")) null else obj.optString("rejectionReason")
        val nextAction = if (obj.isNull("aiSuggestedNextAction")) null else obj.optString("aiSuggestedNextAction")

        val evidenceList = mutableListOf<AdminEvidence>()
        val evidenceArray = obj.optJSONArray("adminEvidenceList")
        if (evidenceArray != null) {
            for (i in 0 until evidenceArray.length()) {
                val evObj = evidenceArray.getJSONObject(i)
                val evRemarks = if (evObj.isNull("remarks")) null else evObj.optString("remarks")
                evidenceList.add(
                    AdminEvidence(
                        id = evObj.optString("id", java.util.UUID.randomUUID().toString()),
                        mediaUri = evObj.optString("mediaUri", ""),
                        mediaType = evObj.optString("mediaType", "PHOTO"),
                        fileName = evObj.optString("fileName", "evidence"),
                        remarks = if (evRemarks.isNullOrBlank()) null else evRemarks,
                        uploadedAt = evObj.optLong("uploadedAt", System.currentTimeMillis()),
                        uploadedBy = evObj.optString("uploadedBy", "Admin")
                    )
                )
            }
        }

        val historyList = mutableListOf<TrackingEvent>()
        val historyArray = obj.optJSONArray("history")
        if (historyArray != null) {
            for (i in 0 until historyArray.length()) {
                val hObj = historyArray.getJSONObject(i)
                val evStatusStr = hObj.optString("status", ComplaintStatus.SUBMITTED.name)
                val evStatus = try {
                    ComplaintStatus.valueOf(evStatusStr)
                } catch (e: Exception) {
                    ComplaintStatus.SUBMITTED
                }
                val evRemarks = if (hObj.isNull("remarks")) null else hObj.optString("remarks")
                val evUpdatedBy = if (hObj.isNull("updatedBy")) null else hObj.optString("updatedBy")
                historyList.add(
                    TrackingEvent(
                        status = evStatus,
                        title = hObj.optString("title", evStatus.displayName),
                        remarks = if (evRemarks.isNullOrBlank()) null else evRemarks,
                        timestamp = hObj.optLong("timestamp", System.currentTimeMillis()),
                        updatedBy = if (evUpdatedBy.isNullOrBlank()) null else evUpdatedBy
                    )
                )
            }
        }

        val supportedEmails = mutableListOf<String>()
        val emailsArray = obj.optJSONArray("supportedByEmails")
        if (emailsArray != null) {
            for (i in 0 until emailsArray.length()) {
                supportedEmails.add(emailsArray.optString(i))
            }
        }

        val keywordsList = mutableListOf<String>()
        val keywordsArray = obj.optJSONArray("aiExtractedKeywords")
        if (keywordsArray != null) {
            for (i in 0 until keywordsArray.length()) {
                keywordsList.add(keywordsArray.optString(i))
            }
        }

        val aiCategory = if (obj.isNull("aiSuggestedCategory")) null else obj.optString("aiSuggestedCategory")
        val aiDept = if (obj.isNull("aiSuggestedDepartment")) null else obj.optString("aiSuggestedDepartment")
        val aiUrgency = if (obj.isNull("aiSuggestedUrgency")) null else obj.optString("aiSuggestedUrgency")
        val aiSummary = if (obj.isNull("aiSummary")) null else obj.optString("aiSummary")
        val aiReason = if (obj.isNull("aiUrgencyReason")) null else obj.optString("aiUrgencyReason")
        val duplicateId = if (obj.isNull("possibleDuplicateOfId")) null else obj.optString("possibleDuplicateOfId")

        return Complaint(
            id = obj.getString("id"),
            title = obj.getString("title"),
            description = obj.getString("description"),
            category = obj.getString("category"),
            status = status,
            priority = obj.optString("priority", "Normal"),
            assignedDepartment = obj.optString("assignedDepartment", "Pending Assignment"),
            assignedOfficer = if (officer.isNullOrBlank()) null else officer,
            citizenName = obj.optString("citizenName", "Citizen"),
            citizenPhone = obj.optString("citizenPhone", ""),
            citizenEmail = obj.optString("citizenEmail", ""),
            locationAddress = obj.optString("locationAddress", "Location Not Provided"),
            latitude = lat,
            longitude = lon,
            imageUri = if (img.isNullOrBlank()) null else img,
            videoUri = if (vid.isNullOrBlank()) null else vid,
            isPublic = obj.optBoolean("isPublic", true),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            adminNotes = if (notes.isNullOrBlank()) null else notes,
            adminEvidenceList = evidenceList,
            resolutionNote = if (resNote.isNullOrBlank()) null else resNote,
            resolvedAt = resAt,
            resolvedBy = if (resBy.isNullOrBlank()) null else resBy,
            resolutionEvidenceUri = if (resEvidence.isNullOrBlank()) null else resEvidence,
            rejectionReason = if (rejReason.isNullOrBlank()) null else rejReason,
            history = historyList,

            isEscalated = obj.optBoolean("isEscalated", false),
            escalationLevel = obj.optInt("escalationLevel", 0),
            escalationReason = if (obj.isNull("escalationReason")) null else obj.optString("escalationReason"),

            feedbackRating = if (obj.isNull("feedbackRating")) null else obj.optInt("feedbackRating"),
            feedbackComment = if (obj.isNull("feedbackComment")) null else obj.optString("feedbackComment"),
            feedbackEvidenceUri = if (obj.isNull("feedbackEvidenceUri")) null else obj.optString("feedbackEvidenceUri"),
            isReopened = obj.optBoolean("isReopened", false),
            reopenCount = obj.optInt("reopenCount", 0),
            reopenReason = if (obj.isNull("reopenReason")) null else obj.optString("reopenReason"),

            aiSuggestedCategory = if (aiCategory.isNullOrBlank()) null else aiCategory,
            aiSuggestedDepartment = if (aiDept.isNullOrBlank()) null else aiDept,
            aiSuggestedUrgency = if (aiUrgency.isNullOrBlank()) null else aiUrgency,
            aiSummary = if (aiSummary.isNullOrBlank()) null else aiSummary,
            aiUrgencyReason = if (aiReason.isNullOrBlank()) null else aiReason,
            aiExtractedKeywords = keywordsList,
            aiSuggestedNextAction = if (nextAction.isNullOrBlank()) null else nextAction,
            possibleDuplicateOfId = if (duplicateId.isNullOrBlank()) null else duplicateId,
            supportCount = obj.optInt("supportCount", 0),
            supportedByEmails = supportedEmails
        )
    }
}
