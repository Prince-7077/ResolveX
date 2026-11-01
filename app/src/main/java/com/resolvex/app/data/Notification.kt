package com.resolvex.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class NotificationItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val targetUserEmail: String,
    val complaintId: String? = null,
    val title: String,
    val message: String,
    val type: String, // "STATUS_CHANGE", "ASSIGNMENT", "EVIDENCE_REQUEST", "ESCALATION", "RESOLVED", "REOPENED", "FEEDBACK"
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class NotificationRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "resolvex_notifications_store"
        private const val KEY_NOTIFICATIONS = "notifications_json"

        @Volatile
        private var instance: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    @Synchronized
    fun getNotificationsForUser(email: String): List<NotificationItem> {
        val jsonStr = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
        val list = mutableListOf<NotificationItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            val cleanEmail = email.trim().lowercase()
            for (i in 0 until jsonArray.length()) {
                val item = fromJson(jsonArray.getJSONObject(i))
                if (item.targetUserEmail.equals(cleanEmail, ignoreCase = true) || item.targetUserEmail.equals("all", ignoreCase = true)) {
                    list.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    @Synchronized
    fun getUnreadCount(email: String): Int {
        return getNotificationsForUser(email).count { !it.isRead }
    }

    @Synchronized
    fun addNotification(notification: NotificationItem) {
        val all = getAllInternal().toMutableList()
        all.add(0, notification)
        saveList(all)
    }

    @Synchronized
    fun markAsRead(notificationId: String) {
        val all = getAllInternal().toMutableList()
        val index = all.indexOfFirst { it.id == notificationId }
        if (index >= 0) {
            all[index] = all[index].copy(isRead = true)
            saveList(all)
        }
    }

    @Synchronized
    fun markAllAsRead(email: String) {
        val all = getAllInternal().toMutableList()
        val cleanEmail = email.trim().lowercase()
        for (i in all.indices) {
            if (all[i].targetUserEmail.equals(cleanEmail, ignoreCase = true)) {
                all[i] = all[i].copy(isRead = true)
            }
        }
        saveList(all)
    }

    @Synchronized
    fun clearNotificationsForUser(email: String) {
        val all = getAllInternal().toMutableList()
        val cleanEmail = email.trim().lowercase()
        all.removeAll { it.targetUserEmail.equals(cleanEmail, ignoreCase = true) }
        saveList(all)
    }

    private fun getAllInternal(): List<NotificationItem> {
        val jsonStr = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
        val list = mutableListOf<NotificationItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveList(list: List<NotificationItem>) {
        val jsonArray = JSONArray()
        list.take(200).forEach { item ->
            jsonArray.put(toJson(item))
        }
        prefs.edit().putString(KEY_NOTIFICATIONS, jsonArray.toString()).apply()
    }

    private fun toJson(item: NotificationItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("targetUserEmail", item.targetUserEmail)
            put("complaintId", item.complaintId ?: JSONObject.NULL)
            put("title", item.title)
            put("message", item.message)
            put("type", item.type)
            put("isRead", item.isRead)
            put("timestamp", item.timestamp)
        }
    }

    private fun fromJson(obj: JSONObject): NotificationItem {
        return NotificationItem(
            id = obj.getString("id"),
            targetUserEmail = obj.getString("targetUserEmail"),
            complaintId = if (obj.isNull("complaintId")) null else obj.getString("complaintId"),
            title = obj.getString("title"),
            message = obj.getString("message"),
            type = obj.optString("type", "GENERAL"),
            isRead = obj.optBoolean("isRead", false),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }
}
