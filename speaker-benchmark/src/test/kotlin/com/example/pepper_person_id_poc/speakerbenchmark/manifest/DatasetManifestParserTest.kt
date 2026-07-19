package com.example.pepper_person_id_poc.speakerbenchmark.manifest

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatasetManifestParserTest {
    @Test
    fun `parses exact manifest including RFC4180 escaped fields`() {
        val text = header + "\n" +
            "spk1,utt1,group1,enrollment,a.wav,ja,16000,2.5,\"Pepper, lobby\",pepper_live\n"

        val manifest = DatasetManifestParser.parseText(text, Path.of("dataset/manifest.csv"))

        val entry = manifest.entries.single()
        assertEquals("Pepper, lobby", entry.recordingDevice)
        assertEquals(DatasetSplit.ENROLLMENT, entry.split)
        assertEquals(RecordingType.PEPPER_LIVE, entry.recordingType)
        assertTrue(entry.path.endsWith(Path.of("dataset/a.wav")))
    }

    @Test
    fun `rejects reordered or additional header columns`() {
        val wrongHeader = header.replace("speaker_id,utterance_id", "utterance_id,speaker_id")

        assertFailsWith<ManifestException> {
            DatasetManifestParser.parseText(
                "$wrongHeader\nspk1,utt1,group1,enrollment,a.wav,ja,16000,2.5,Pepper,pepper_live",
                Path.of("manifest.csv"),
            )
        }
    }

    @Test
    fun `rejects duplicate utterance IDs`() {
        val text = header + "\n" +
            "spk1,utt1,group1,enrollment,a.wav,ja,16000,2,Pepper,pepper_live\n" +
            "spk1,utt1,group2,enrollment,b.wav,ja,16000,2,Pepper,pepper_live"

        assertFailsWith<ManifestException> {
            DatasetManifestParser.parseText(text, Path.of("manifest.csv"))
        }
    }

    @Test
    fun `detects source group leakage across splits`() {
        val text = header + "\n" +
            "spk1,enroll1,same-source,enrollment,a.wav,ja,16000,2,Pepper,pepper_live\n" +
            "spk1,test1,same-source,test,b.wav,ja,16000,2,Pepper,pepper_live"

        val exception = assertFailsWith<ManifestException> {
            DatasetManifestParser.parseText(text, Path.of("manifest.csv"))
        }

        assertTrue(exception.message.orEmpty().contains("same-source"))
        assertTrue(exception.message.orEmpty().contains("enrollment/test"))
    }

    @Test
    fun `rejects whitespace and unsupported sample rates`() {
        val text = header + "\n" +
            "spk1, utt1,group1,enrollment,a.wav,ja,8000,2,Pepper,pepper_live"

        assertFailsWith<ManifestException> {
            DatasetManifestParser.parseText(text, Path.of("manifest.csv"))
        }
    }

    private companion object {
        val header: String = DatasetManifestParser.requiredHeader.joinToString(",")
    }
}
