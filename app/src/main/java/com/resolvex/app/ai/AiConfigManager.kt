package com.resolvex.app.ai

import android.content.Context
import android.content.SharedPreferences

class AiConfigManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "resolvex_ai_config"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"

        @Volatile
        private var instance: AiConfigManager? = null

        fun getInstance(context: Context): AiConfigManager {
            return instance ?: synchronized(this) {
                instance ?: AiConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getGeminiApiKey(): String? {
        val key = prefs.getString(KEY_GEMINI_API_KEY, null)?.trim()
        if (!key.isNullOrBlank()) return key
        val envKey = try { System.getenv("GEMINI_API_KEY")?.trim() } catch (e: Exception) { null }
        if (!envKey.isNullOrBlank()) return envKey
        return null
    }

    fun setGeminiApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
        }
    }

    fun isCloudBackendConnected(): Boolean {
        return !getGeminiApiKey().isNullOrBlank()
    }
}
