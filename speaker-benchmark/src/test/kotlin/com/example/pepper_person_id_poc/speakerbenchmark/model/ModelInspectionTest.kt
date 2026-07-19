package com.example.pepper_person_id_poc.speakerbenchmark.model

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModelInspectionTest {
    @Test
    fun `inspects stable model file metadata without claiming graph inspection`() {
        val directory = createTempDirectory("model-inspection-test")
        val path = directory.resolve("model.onnx")
        Files.write(path, byteArrayOf(0x01, 0x7f, 0xff.toByte()))

        val result = FileModelInspector().inspect(path)

        assertEquals("ONNX", result.formatHint)
        assertEquals(3, result.fileSizeBytes)
        assertEquals("017fff", result.leadingBytesHex)
        assertFalse(result.graphMetadataInspected)
    }
}
