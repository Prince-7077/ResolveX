package com.resolvex.app.data

enum class UserRole(val displayName: String) {
    CITIZEN("Citizen"),
    DEPARTMENT_OFFICER("Department Officer"),
    ADMIN("Administrator"),
    SUPER_ADMIN("Super Administrator")
}

enum class UserStatus(val displayName: String) {
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    PENDING_VERIFICATION("Pending")
}

data class UserAccount(
    val id: String,
    val fullName: String,
    val email: String,
    val mobile: String,
    val occupation: String = "Citizen",
    val role: UserRole,
    val department: String? = null,
    val employeeId: String? = null,
    val passwordHash: String,
    val passwordSalt: String,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null,
    val isEmailVerified: Boolean = true
)

data class AuditLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: String,
    val performedBy: String,
    val target: String,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null
)

data class UserSession(
    val userId: String,
    val fullName: String,
    val email: String,
    val mobile: String,
    val occupation: String,
    val role: UserRole,
    val department: String? = null,
    val employeeId: String? = null
)
