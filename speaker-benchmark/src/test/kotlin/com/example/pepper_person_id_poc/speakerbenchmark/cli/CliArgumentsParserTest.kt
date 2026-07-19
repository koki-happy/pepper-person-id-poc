package com.example.pepper_person_id_poc.speakerbenchmark.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliArgumentsParserTest {
    @Test
    fun `parses config run and validate-only regardless of option order`() {
        val command = CliArgumentsParser.parse(
            arrayOf("--validate-only", "--config", "config/benchmark.json"),
        )

        assertTrue(command is CliCommand.Run)
        assertEquals(Path.of("config/benchmark.json"), command.configPath)
        assertTrue(command.validateOnly)
    }

    @Test
    fun `parses inline config value`() {
        val command = CliArgumentsParser.parse(arrayOf("--config=config/benchmark.json"))

        assertTrue(command is CliCommand.Run)
        assertFalse(command.validateOnly)
    }

    @Test
    fun `parses inspect model as a separate command`() {
        val command = CliArgumentsParser.parse(arrayOf("--inspect-model", "models/campplus.onnx"))

        assertEquals(CliCommand.InspectModel(Path.of("models/campplus.onnx")), command)
    }

    @Test
    fun `rejects missing config and unknown options`() {
        assertFailsWith<CliArgumentException> {
            CliArgumentsParser.parse(emptyArray())
        }
        assertFailsWith<CliArgumentException> {
            CliArgumentsParser.parse(arrayOf("--unknown"))
        }
    }

    @Test
    fun `rejects conflicting inspect options`() {
        assertFailsWith<CliArgumentException> {
            CliArgumentsParser.parse(
                arrayOf("--config", "config.json", "--inspect-model", "model.onnx"),
            )
        }
    }
}
