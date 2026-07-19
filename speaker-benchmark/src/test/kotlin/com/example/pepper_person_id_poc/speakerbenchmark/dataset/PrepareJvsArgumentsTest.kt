package com.example.pepper_person_id_poc.speakerbenchmark.dataset

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrepareJvsArgumentsTest {
    @Test
    fun `uses local ignored paths by default`() {
        val command = PrepareJvsArguments.parse(emptyArray())

        assertEquals("data/downloads/jvs_ver1.zip", command.archivePath)
        assertEquals("data/audio/jvs-evaluation", command.outputDirectory)
        assertFalse(command.force)
    }

    @Test
    fun `parses overrides force and help`() {
        val command = PrepareJvsArguments.parse(
            arrayOf("--archive", "archive.zip", "--output", "prepared", "--force", "--help"),
        )

        assertEquals("archive.zip", command.archivePath)
        assertEquals("prepared", command.outputDirectory)
        assertTrue(command.force)
        assertTrue(command.showHelp)
    }

    @Test
    fun `rejects unknown options`() {
        assertFailsWith<IllegalArgumentException> {
            PrepareJvsArguments.parse(arrayOf("--download"))
        }
    }
}
