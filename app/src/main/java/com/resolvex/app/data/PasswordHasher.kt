package com.resolvex.app.data

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Enterprise-grade cryptographic password hashing using PBKDF2WithHmacSHA256.
 * Generates unique salts per user and runs 12,000 iterations.
 */
object PasswordHasher {
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(SALT_LENGTH)
        random.nextBytes(saltBytes)
        return bytesToHex(saltBytes)
    }

    fun hashPassword(password: String, saltHex: String): String {
        val saltBytes = hexToBytes(saltSaltSanitized(saltHex))
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = skf.generateSecret(spec).encoded
        return bytesToHex(hashBytes)
    }

    fun verifyPassword(password: String, saltHex: String, storedHash: String): Boolean {
        if (password.isEmpty() || saltHex.isEmpty() || storedHash.isEmpty()) return false
        val computedHash = hashPassword(password, saltHex)
        return slowEquals(computedHash, storedHash)
    }

    private fun saltSaltSanitized(saltHex: String): String {
        return if (saltHex.length < 32) saltHex.padEnd(32, '0') else saltHex.take(32)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /** Constant-time comparison to prevent timing attacks */
    private fun slowEquals(a: String, b: String): Boolean {
        var diff = a.length xor b.length
        for (i in 0 until minOf(a.length, b.length)) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
