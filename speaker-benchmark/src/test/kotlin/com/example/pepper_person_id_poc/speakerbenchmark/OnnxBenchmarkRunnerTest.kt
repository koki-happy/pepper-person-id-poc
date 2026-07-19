package com.example.pepper_person_id_poc.speakerbenchmark

import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetEntry
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetSplit
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.RecordingType
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnnxBenchmarkRunnerTest {
    @Test
    fun `headline evaluation excludes configured-duration misses and non-final splits`() {
        val entries = listOf(
            entry("known", "test-2", DatasetSplit.TEST, 2.04),
            entry("known", "test-4", DatasetSplit.TEST, 4.0),
            entry("unknown", "unknown-5", DatasetSplit.UNKNOWN, 5.0),
            entry("known", "development-3", DatasetSplit.DEVELOPMENT, 3.0),
        )

        val selected = selectHeadlineEvaluationEntries(entries, listOf(2, 3, 5))

        assertEquals(listOf("test-2", "unknown-5"), selected.map { it.utteranceId })
    }

    @Test
    fun `duration grouping uses configured values and never rounds a distant duration`() {
        assertEquals(2, matchConfiguredDuration(2.049, listOf(2, 3, 5)))
        assertEquals(2, matchConfiguredDuration(2.05, listOf(2, 3, 5)))
        assertNull(matchConfiguredDuration(2.050001, listOf(2, 3, 5)))
        assertNull(matchConfiguredDuration(2.051, listOf(2, 3, 5)))
        assertNull(matchConfiguredDuration(2.49, listOf(2, 3, 5)))
        assertEquals(7, matchConfiguredDuration(7.0, listOf(2, 7)))
    }

    @Test
    fun `duration limitations consider only test and final unknown splits`() {
        val entries = listOf(
            entry("known", "enroll-2s", DatasetSplit.ENROLLMENT, 2.0),
            entry("known", "test-not-2s", DatasetSplit.TEST, 2.49),
            entry("unknown", "unknown-3s", DatasetSplit.UNKNOWN, 3.0),
            entry("unknown", "unknown-5s", DatasetSplit.UNKNOWN, 5.0),
        )

        val limitations = OnnxBenchmarkRunner().benchmarkLimitations(entries, listOf(2, 3, 5))

        assertTrue(limitations.any { "2s" in it && "configured duration groups" in it })
    }

    @Test
    fun `acceptance limitations require per-speaker recording counts and both duration splits`() {
        val entries = buildList {
            (1..4).forEach { speakerNumber ->
                val speaker = "known-$speakerNumber"
                repeat(5) { add(entry(speaker, "$speaker-enroll-$it", DatasetSplit.ENROLLMENT, 3.0)) }
                repeat(5) { add(entry(speaker, "$speaker-dev-$it", DatasetSplit.DEVELOPMENT, 3.0)) }
                repeat(10) { index ->
                    add(entry(speaker, "$speaker-test-$index", DatasetSplit.TEST, listOf(2.0, 3.0, 5.0)[index % 3]))
                }
            }
            (1..4).forEach { speakerNumber ->
                val speaker = "dev-unknown-$speakerNumber"
                repeat(5) { add(entry(speaker, "$speaker-$it", DatasetSplit.DEVELOPMENT, 3.0)) }
            }
            (1..4).forEach { speakerNumber ->
                val speaker = "unknown-$speakerNumber"
                repeat(10) { index ->
                    add(entry(speaker, "$speaker-$index", DatasetSplit.UNKNOWN, listOf(2.0, 3.0, 5.0)[index % 3]))
                }
            }
        }

        assertTrue(OnnxBenchmarkRunner().benchmarkLimitations(entries, listOf(2, 3, 5)).isEmpty())

        val insufficient = entries.filterNot { it.utteranceId == "known-1-test-9" }
        val limitations = OnnxBenchmarkRunner().benchmarkLimitations(insufficient, listOf(2, 3, 5))
        assertTrue(limitations.any { "known-1" in it && "test=9" in it })
    }

    private fun entry(
        speakerId: String,
        utteranceId: String,
        split: DatasetSplit,
        durationSeconds: Double,
    ) = DatasetEntry(
        speakerId = speakerId,
        utteranceId = utteranceId,
        sourceGroupId = "group-$utteranceId",
        split = split,
        path = Path.of("$utteranceId.wav"),
        configuredPath = "$utteranceId.wav",
        language = "ja",
        sampleRate = 16_000,
        durationSeconds = durationSeconds,
        recordingDevice = "test",
        recordingType = RecordingType.PUBLIC_DATASET,
        rowNumber = 2,
    )
}
