package com.resolvex.app.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "resolvex_user_session"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_MOBILE = "mobile"
        private const val KEY_OCCUPATION = "occupation"
        private const val KEY_ROLE = "role"
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_EMPLOYEE_ID = "employee_id"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"

        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun saveSession(session: UserSession) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_ID, session.userId)
            putString(KEY_FULL_NAME, session.fullName)
            putString(KEY_EMAIL, session.email)
            putString(KEY_MOBILE, session.mobile)
            putString(KEY_OCCUPATION, session.occupation)
            putString(KEY_ROLE, session.role.name)
            putString(KEY_DEPARTMENT, session.department ?: "")
            putString(KEY_EMPLOYEE_ID, session.employeeId ?: "")
            apply()
        }
    }

    fun getSession(): UserSession? {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (!isLoggedIn) return null

        val userId = prefs.getString(KEY_USER_ID, "") ?: ""
        val fullName = prefs.getString(KEY_FULL_NAME, "") ?: ""
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val mobile = prefs.getString(KEY_MOBILE, "") ?: ""
        val occupation = prefs.getString(KEY_OCCUPATION, "") ?: ""
        val roleStr = prefs.getString(KEY_ROLE, UserRole.CITIZEN.name) ?: UserRole.CITIZEN.name
        val department = prefs.getString(KEY_DEPARTMENT, null)
        val employeeId = prefs.getString(KEY_EMPLOYEE_ID, null)

        val role = try {
            UserRole.valueOf(roleStr)
        } catch (e: Exception) {
            UserRole.CITIZEN
        }

        return UserSession(
            userId = userId,
            fullName = fullName,
            email = email,
            mobile = mobile,
            occupation = occupation,
            role = role,
            department = if (department.isNullOrBlank()) null else department,
            employeeId = if (employeeId.isNullOrBlank()) null else employeeId
        )
    }

    fun clearSession() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            remove(KEY_USER_ID)
            remove(KEY_FULL_NAME)
            remove(KEY_EMAIL)
            remove(KEY_MOBILE)
            remove(KEY_OCCUPATION)
            remove(KEY_ROLE)
            remove(KEY_DEPARTMENT)
            remove(KEY_EMPLOYEE_ID)
            apply()
        }
    }

    fun hasSeenOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
    }

    fun setSeenOnboarding(seen: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, seen).apply()
    }
}
