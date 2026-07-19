package com.example.pepper_person_id_poc.speakerbenchmark.util

import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sha256Test {
    @Test
    fun `computes lowercase SHA-256 for bytes and files`() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val bytes = "abc".toByteArray()
        val path = createTempFile("sha256-test")
        Files.write(path, bytes)

        assertEquals(expected, Sha256.digest(bytes))
        assertEquals(expected, Sha256.digest(path))
        assertTrue(Sha256.matches(path, expected.uppercase()))
        assertFalse(Sha256.matches(path, "0".repeat(64)))
    }
}
