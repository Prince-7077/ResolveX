package com.resolvex.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

sealed class AuthResult {
    data class Success(val user: UserAccount) : AuthResult()
    data class Failure(val message: String) : AuthResult()
    data class RateLimited(val remainingSeconds: Long) : AuthResult()
    data class AccountSuspended(val reason: String) : AuthResult()
}

class UserRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "resolvex_user_accounts"
        private const val KEY_USERS_JSON = "users_json_data"
        private const val KEY_AUDIT_LOGS_JSON = "audit_logs_json_data"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts_json"

        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 60_000L // 60 seconds

        @Volatile
        private var instance: UserRepository? = null

        fun getInstance(context: Context): UserRepository {
            return instance ?: synchronized(this) {
                instance ?: UserRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        ensureDefaultAccountsSeeded()
    }

    @Synchronized
    private fun ensureDefaultAccountsSeeded() {
        if (!prefs.contains(KEY_USERS_JSON)) {
            val defaultAccounts = mutableListOf<UserAccount>()

            // 1. Super Administrator
            val saSalt = PasswordHasher.generateSalt()
            defaultAccounts.add(
                UserAccount(
                    id = "SA-0001",
                    fullName = "Director General (Super Admin)",
                    email = "superadmin@resolvex.gov.in",
                    mobile = "9876500001",
                    occupation = "Civic Platform Authority",
                    role = UserRole.SUPER_ADMIN,
                    department = "Apex Civic Operations",
                    employeeId = "SA-GOV-001",
                    passwordHash = PasswordHasher.hashPassword("SuperAdmin@2026", saSalt),
                    passwordSalt = saSalt,
                    status = UserStatus.ACTIVE
                )
            )

            // 2. Administrator
            val adminSalt = PasswordHasher.generateSalt()
            defaultAccounts.add(
                UserAccount(
                    id = "ADM-0001",
                    fullName = "Municipal Administrator",
                    email = "admin@resolvex.gov.in",
                    mobile = "9876500002",
                    occupation = "Municipal Officer",
                    role = UserRole.ADMIN,
                    department = "Central Operations",
                    employeeId = "ADM-GOV-002",
                    passwordHash = PasswordHasher.hashPassword("Admin@2026", adminSalt),
                    passwordSalt = adminSalt,
                    status = UserStatus.ACTIVE
                )
            )

            // 3. Department Officer (Roads)
            val roadsOfficerSalt = PasswordHasher.generateSalt()
            defaultAccounts.add(
                UserAccount(
                    id = "OFF-0001",
                    fullName = "Er. Suresh Sharma",
                    email = "officer.roads@resolvex.gov.in",
                    mobile = "9876500003",
                    occupation = "Executive Engineer",
                    role = UserRole.DEPARTMENT_OFFICER,
                    department = "Roads & Infrastructure",
                    employeeId = "OFF-RDS-01",
                    passwordHash = PasswordHasher.hashPassword("Officer@2026", roadsOfficerSalt),
                    passwordSalt = roadsOfficerSalt,
                    status = UserStatus.ACTIVE
                )
            )

            // 4. Department Officer (Water)
            val waterOfficerSalt = PasswordHasher.generateSalt()
            defaultAccounts.add(
                UserAccount(
                    id = "OFF-0002",
                    fullName = "Er. Priya Patel",
                    email = "officer.water@resolvex.gov.in",
                    mobile = "9876500004",
                    occupation = "Assistant Water Engineer",
                    role = UserRole.DEPARTMENT_OFFICER,
                    department = "Water Supply & Leakage",
                    employeeId = "OFF-WTR-02",
                    passwordHash = PasswordHasher.hashPassword("Officer@2026", waterOfficerSalt),
                    passwordSalt = waterOfficerSalt,
                    status = UserStatus.ACTIVE
                )
            )

            // 5. Demo Citizen
            val citizenSalt = PasswordHasher.generateSalt()
            defaultAccounts.add(
                UserAccount(
                    id = "CIT-0001",
                    fullName = "Aarav Sharma",
                    email = "citizen@resolvex.org",
                    mobile = "9876543210",
                    occupation = "General Citizen",
                    role = UserRole.CITIZEN,
                    department = null,
                    employeeId = null,
                    passwordHash = PasswordHasher.hashPassword("Citizen@2026", citizenSalt),
                    passwordSalt = citizenSalt,
                    status = UserStatus.ACTIVE
                )
            )

            saveUsers(defaultAccounts)

            addAuditLog(
                action = "SYSTEM_INIT",
                performedBy = "System",
                target = "SeedAccounts",
                details = "Initialized default secure system accounts (SuperAdmin, Admin, Department Officers, Citizen)"
            )
        }
    }

    @Synchronized
    fun getAllUsers(): List<UserAccount> {
        val jsonStr = prefs.getString(KEY_USERS_JSON, null) ?: return emptyList()
        val list = mutableListOf<UserAccount>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(userFromJson(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun getUserById(id: String): UserAccount? {
        return getAllUsers().firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }

    @Synchronized
    fun getUserByEmail(email: String): UserAccount? {
        return getAllUsers().firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }
    }

    @Synchronized
    fun getUsersByRole(role: UserRole): List<UserAccount> {
        return getAllUsers().filter { it.role == role }
    }

    /**
     * Public Registration strictly creates CITIZEN accounts only.
     * Prevents privilege escalation.
     */
    @Synchronized
    fun registerCitizen(
        fullName: String,
        email: String,
        mobile: String,
        occupation: String,
        plainPassword: String
    ): Result<UserAccount> {
        val cleanEmail = email.trim().lowercase()
        if (getUserByEmail(cleanEmail) != null) {
            return Result.failure(IllegalArgumentException("An account with this email address already exists."))
        }

        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(plainPassword, salt)
        val newCitizen = UserAccount(
            id = "CIT-${(1000..9999).random()}",
            fullName = fullName.trim(),
            email = cleanEmail,
            mobile = mobile.trim(),
            occupation = occupation.ifBlank { "Citizen" },
            role = UserRole.CITIZEN, // Hardcoded: never trust frontend for privileged roles
            department = null,
            employeeId = null,
            passwordHash = hash,
            passwordSalt = salt,
            status = UserStatus.ACTIVE
        )

        val users = getAllUsers().toMutableList()
        users.add(0, newCitizen)
        saveUsers(users)

        addAuditLog(
            action = "CITIZEN_REGISTRATION",
            performedBy = cleanEmail,
            target = newCitizen.id,
            details = "Citizen registered: ${newCitizen.fullName}"
        )

        return Result.success(newCitizen)
    }

    /**
     * Creates a Department Officer account.
     * Callable only by ADMIN or SUPER_ADMIN.
     */
    @Synchronized
    fun createDepartmentOfficer(
        fullName: String,
        email: String,
        mobile: String,
        department: String,
        employeeId: String,
        plainPassword: String,
        performedBy: String
    ): Result<UserAccount> {
        val cleanEmail = email.trim().lowercase()
        if (getUserByEmail(cleanEmail) != null) {
            return Result.failure(IllegalArgumentException("An account with this email address already exists."))
        }

        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(plainPassword, salt)
        val newOfficer = UserAccount(
            id = "OFF-${(1000..9999).random()}",
            fullName = fullName.trim(),
            email = cleanEmail,
            mobile = mobile.trim(),
            occupation = "Department Officer",
            role = UserRole.DEPARTMENT_OFFICER,
            department = department.trim(),
            employeeId = employeeId.trim(),
            passwordHash = hash,
            passwordSalt = salt,
            status = UserStatus.ACTIVE
        )

        val users = getAllUsers().toMutableList()
        users.add(0, newOfficer)
        saveUsers(users)

        addAuditLog(
            action = "CREATE_OFFICER",
            performedBy = performedBy,
            target = newOfficer.id,
            details = "Created officer ${newOfficer.fullName} for department: $department (Employee ID: $employeeId)"
        )

        return Result.success(newOfficer)
    }

    /**
     * Creates an Authorized Administrator account.
     * Callable ONLY by SUPER_ADMIN.
     */
    @Synchronized
    fun createAdmin(
        fullName: String,
        email: String,
        mobile: String,
        department: String = "Central Operations",
        employeeId: String,
        plainPassword: String,
        performedBy: String
    ): Result<UserAccount> {
        val cleanEmail = email.trim().lowercase()
        if (getUserByEmail(cleanEmail) != null) {
            return Result.failure(IllegalArgumentException("An account with this email address already exists."))
        }

        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(plainPassword, salt)
        val newAdmin = UserAccount(
            id = "ADM-${(1000..9999).random()}",
            fullName = fullName.trim(),
            email = cleanEmail,
            mobile = mobile.trim(),
            occupation = "Administrator",
            role = UserRole.ADMIN,
            department = department.trim(),
            employeeId = employeeId.trim(),
            passwordHash = hash,
            passwordSalt = salt,
            status = UserStatus.ACTIVE
        )

        val users = getAllUsers().toMutableList()
        users.add(0, newAdmin)
        saveUsers(users)

        addAuditLog(
            action = "CREATE_ADMIN",
            performedBy = performedBy,
            target = newAdmin.id,
            details = "Super Admin created authorized Admin: ${newAdmin.fullName} (Emp ID: $employeeId)"
        )

        return Result.success(newAdmin)
    }

    /**
     * Authenticates user with rate limiting and password verification.
     */
    @Synchronized
    fun authenticate(email: String, plainPassword: String): AuthResult {
        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()

        // 1. Check Rate Limiter
        val rateLimitInfo = getRateLimitInfo(cleanEmail)
        if (rateLimitInfo != null && rateLimitInfo.isLocked(now)) {
            val remainingSec = (rateLimitInfo.lockoutUntil - now) / 1000
            return AuthResult.RateLimited(remainingSec)
        }

        // 2. Lookup User
        val user = getUserByEmail(cleanEmail)
        if (user == null) {
            recordFailedAttempt(cleanEmail, now)
            return AuthResult.Failure("Invalid email or password.")
        }

        // 3. Check Account Status
        if (user.status == UserStatus.SUSPENDED) {
            return AuthResult.AccountSuspended("This account has been suspended. Please contact platform administrator.")
        }

        // 4. Verify Password Cryptographically
        val matches = PasswordHasher.verifyPassword(plainPassword, user.passwordSalt, user.passwordHash)
        if (!matches) {
            recordFailedAttempt(cleanEmail, now)
            return AuthResult.Failure("Invalid email or password.")
        }

        // Reset rate limit on success
        clearRateLimit(cleanEmail)

        // Update Last Login
        val updatedUser = user.copy(lastLoginAt = now)
        val users = getAllUsers().toMutableList()
        val idx = users.indexOfFirst { it.id == user.id }
        if (idx >= 0) {
            users[idx] = updatedUser
            saveUsers(users)
        }

        addAuditLog(
            action = "USER_LOGIN",
            performedBy = cleanEmail,
            target = user.id,
            details = "Successful login as ${user.role.displayName}"
        )

        return AuthResult.Success(updatedUser)
    }

    @Synchronized
    fun updateUserStatus(userId: String, newStatus: UserStatus, performedBy: String): Boolean {
        val users = getAllUsers().toMutableList()
        val idx = users.indexOfFirst { it.id == userId }
        if (idx >= 0) {
            val user = users[idx]
            // Cannot suspend Super Admin
            if (user.role == UserRole.SUPER_ADMIN && newStatus == UserStatus.SUSPENDED) {
                return false
            }
            val updated = user.copy(status = newStatus)
            users[idx] = updated
            saveUsers(users)

            addAuditLog(
                action = "UPDATE_USER_STATUS",
                performedBy = performedBy,
                target = userId,
                details = "Changed status of ${user.fullName} (${user.email}) to ${newStatus.displayName}"
            )
            return true
        }
        return false
    }

    @Synchronized
    fun resetPassword(email: String, newPlainPassword: String, performedBy: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val users = getAllUsers().toMutableList()
        val idx = users.indexOfFirst { it.email.equals(cleanEmail, ignoreCase = true) }
        if (idx >= 0) {
            val user = users[idx]
            val newSalt = PasswordHasher.generateSalt()
            val newHash = PasswordHasher.hashPassword(newPlainPassword, newSalt)
            users[idx] = user.copy(passwordHash = newHash, passwordSalt = newSalt)
            saveUsers(users)

            addAuditLog(
                action = "PASSWORD_RESET",
                performedBy = performedBy,
                target = user.id,
                details = "Password reset completed for ${user.email}"
            )
            return true
        }
        return false
    }

    // ── Audit Logs ───────────────────────────────────────────────────────────

    @Synchronized
    fun getAuditLogs(): List<AuditLogEntry> {
        val jsonStr = prefs.getString(KEY_AUDIT_LOGS_JSON, null) ?: return emptyList()
        val list = mutableListOf<AuditLogEntry>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AuditLogEntry(
                        id = obj.getString("id"),
                        action = obj.getString("action"),
                        performedBy = obj.getString("performedBy"),
                        target = obj.getString("target"),
                        timestamp = obj.getLong("timestamp"),
                        details = if (obj.has("details") && !obj.isNull("details")) obj.getString("details") else null
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    @Synchronized
    fun addAuditLog(action: String, performedBy: String, target: String, details: String? = null) {
        val currentLogs = getAuditLogs().toMutableList()
        currentLogs.add(
            0,
            AuditLogEntry(
                action = action,
                performedBy = performedBy,
                target = target,
                details = details
            )
        )
        // Keep last 250 audit log entries
        val trimmed = if (currentLogs.size > 250) currentLogs.take(250) else currentLogs
        val arr = JSONArray()
        for (log in trimmed) {
            val obj = JSONObject().apply {
                put("id", log.id)
                put("action", log.action)
                put("performedBy", log.performedBy)
                put("target", log.target)
                put("timestamp", log.timestamp)
                put("details", log.details ?: JSONObject.NULL)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_AUDIT_LOGS_JSON, arr.toString()).apply()
    }

    // ── Private Serialization & Rate Limiting Helpers ────────────────────────

    private fun saveUsers(users: List<UserAccount>) {
        val arr = JSONArray()
        for (u in users) {
            val obj = JSONObject().apply {
                put("id", u.id)
                put("fullName", u.fullName)
                put("email", u.email)
                put("mobile", u.mobile)
                put("occupation", u.occupation)
                put("role", u.role.name)
                put("department", u.department ?: JSONObject.NULL)
                put("employeeId", u.employeeId ?: JSONObject.NULL)
                put("passwordHash", u.passwordHash)
                put("passwordSalt", u.passwordSalt)
                put("status", u.status.name)
                put("createdAt", u.createdAt)
                put("lastLoginAt", u.lastLoginAt ?: JSONObject.NULL)
                put("isEmailVerified", u.isEmailVerified)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
    }

    private fun userFromJson(obj: JSONObject): UserAccount {
        return UserAccount(
            id = obj.getString("id"),
            fullName = obj.getString("fullName"),
            email = obj.getString("email"),
            mobile = obj.getString("mobile"),
            occupation = obj.optString("occupation", "Citizen"),
            role = try { UserRole.valueOf(obj.getString("role")) } catch (e: Exception) { UserRole.CITIZEN },
            department = if (obj.has("department") && !obj.isNull("department")) obj.getString("department") else null,
            employeeId = if (obj.has("employeeId") && !obj.isNull("employeeId")) obj.getString("employeeId") else null,
            passwordHash = obj.getString("passwordHash"),
            passwordSalt = obj.getString("passwordSalt"),
            status = try { UserStatus.valueOf(obj.getString("status")) } catch (e: Exception) { UserStatus.ACTIVE },
            createdAt = obj.getLong("createdAt"),
            lastLoginAt = if (obj.has("lastLoginAt") && !obj.isNull("lastLoginAt")) obj.getLong("lastLoginAt") else null,
            isEmailVerified = obj.optBoolean("isEmailVerified", true)
        )
    }

    private data class RateLimitRecord(
        val count: Int,
        val lockoutUntil: Long
    ) {
        fun isLocked(now: Long): Boolean = now < lockoutUntil
    }

    private fun getRateLimitInfo(email: String): RateLimitRecord? {
        val raw = prefs.getString("${KEY_FAILED_ATTEMPTS}_$email", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            RateLimitRecord(
                count = obj.getInt("count"),
                lockoutUntil = obj.getLong("lockoutUntil")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun recordFailedAttempt(email: String, now: Long) {
        val current = getRateLimitInfo(email)
        val newCount = (current?.count ?: 0) + 1
        val lockoutUntil = if (newCount >= MAX_FAILED_ATTEMPTS) {
            now + LOCKOUT_DURATION_MS
        } else {
            0L
        }
        val obj = JSONObject().apply {
            put("count", newCount)
            put("lockoutUntil", lockoutUntil)
        }
        prefs.edit().putString("${KEY_FAILED_ATTEMPTS}_$email", obj.toString()).apply()
    }

    private fun clearRateLimit(email: String) {
        prefs.edit().remove("${KEY_FAILED_ATTEMPTS}_$email").apply()
    }
}
