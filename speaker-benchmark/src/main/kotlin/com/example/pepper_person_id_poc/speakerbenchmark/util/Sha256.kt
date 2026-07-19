package com.example.pepper_person_id_poc.speakerbenchmark.util

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object Sha256 {
    private val hexPattern = Regex("^[0-9a-fA-F]{64}$")

    fun digest(path: Path): String {
        val messageDigest = MessageDigest.getInstance(ALGORITHM)
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) messageDigest.update(buffer, 0, count)
            }
        }
        return messageDigest.digest().toHex()
    }

    fun digest(bytes: ByteArray): String = MessageDigest.getInstance(ALGORITHM).digest(bytes).toHex()

    fun matches(path: Path, expected: String): Boolean {
        require(hexPattern.matches(expected)) { "Expected SHA-256 must be exactly 64 hexadecimal characters" }
        return digest(path).equals(expected, ignoreCase = true)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private const val ALGORITHM = "SHA-256"
}
