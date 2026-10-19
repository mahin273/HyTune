package com.mahin.hytune.domain.backup.engine

import java.security.MessageDigest

object BackupChecksumCalculator {

    fun computeSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(content: String, expectedChecksum: String): Boolean {
        if (expectedChecksum.isBlank()) return false
        val computed = computeSha256(content)
        return computed.equals(expectedChecksum.trim(), ignoreCase = true)
    }
}
