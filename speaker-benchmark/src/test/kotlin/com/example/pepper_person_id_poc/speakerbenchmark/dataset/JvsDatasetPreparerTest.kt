package com.example.pepper_person_id_poc.speakerbenchmark.dataset

import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavEncoding
import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavReader
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetManifestParser
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetSplit
import com.example.pepper_person_id_poc.speakerbenchmark.pcm16Wav
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvsDatasetPreparerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `production plan satisfies required speakers counts durations and disjoint sources`() {
        val plan = JvsPreparationPlan.production()
        val counts = buildMap<Pair<String, DatasetSplit>, Int> {
            plan.profiles.forEach { profile ->
                profile.speakerIds.forEach { speaker ->
                    put(speaker to profile.split, profile.clipsPerDuration * plan.durationsSeconds.size)
                }
            }
        }

        (1..4).forEach { number ->
            val speaker = "jvs%03d".format(number)
            assertEquals(6, counts.getValue(speaker to DatasetSplit.ENROLLMENT))
            assertEquals(6, counts.getValue(speaker to DatasetSplit.DEVELOPMENT))
            assertEquals(12, counts.getValue(speaker to DatasetSplit.TEST))
        }
        (5..8).forEach { number ->
            assertEquals(6, counts.getValue("jvs%03d".format(number) to DatasetSplit.DEVELOPMENT))
        }
        (9..12).forEach { number ->
            assertEquals(12, counts.getValue("jvs%03d".format(number) to DatasetSplit.UNKNOWN))
        }
        assertEquals(listOf(2, 3, 5), plan.durationsSeconds)

        val registeredRanges = plan.profiles.take(3).map { it.candidateUtteranceNumbers.toSet() }
        assertTrue(registeredRanges[0].intersect(registeredRanges[1]).isEmpty())
        assertTrue(registeredRanges[0].intersect(registeredRanges[2]).isEmpty())
        assertTrue(registeredRanges[1].intersect(registeredRanges[2]).isEmpty())
    }

    @Test
    fun `plan rejects assigning one original recording more than once`() {
        assertFailsWith<IllegalArgumentException> {
            JvsPreparationPlan(
                durationsSeconds = listOf(2, 3, 5),
                profiles = listOf(
                    JvsSplitProfile(listOf("jvs001"), DatasetSplit.ENROLLMENT, 1..3, 1),
                    JvsSplitProfile(listOf("jvs001"), DatasetSplit.TEST, 3..5, 1),
                ),
            )
        }
    }

    @Test
    fun `prepares only planned ZIP entries with exact crops and leakage-safe manifest`() {
        val archive = temporaryDirectory.resolve("synthetic-jvs.zip")
        val output = temporaryDirectory.resolve("prepared")
        val plan = smallPlan()
        createSyntheticArchive(archive, plan)

        val result = JvsDatasetPreparer.prepare(
            JvsPreparationOptions(archivePath = archive, outputDirectory = output),
            plan,
        )

        assertEquals(15, result.entryCount)
        assertEquals(Sha256.digest(archive), result.archiveSha256)
        val manifest = DatasetManifestParser.parse(result.manifestPath)
        assertEquals(15, manifest.entries.size)
        assertEquals(15, manifest.entries.map { it.sourceGroupId }.distinct().size)
        assertEquals(setOf("jvs001", "jvs002", "jvs003"), manifest.entries.map { it.speakerId }.toSet())

        val expectedGroups = setOf(
            "jvs001" to DatasetSplit.ENROLLMENT,
            "jvs001" to DatasetSplit.DEVELOPMENT,
            "jvs001" to DatasetSplit.TEST,
            "jvs002" to DatasetSplit.DEVELOPMENT,
            "jvs003" to DatasetSplit.UNKNOWN,
        )
        assertEquals(expectedGroups, manifest.entries.map { it.speakerId to it.split }.toSet())
        manifest.entries.groupBy { it.speakerId to it.split }.forEach { (_, entries) ->
            assertEquals(setOf(2.0, 3.0, 5.0), entries.map { it.durationSeconds }.toSet())
        }
        manifest.entries.forEach { entry ->
            val wav = WavReader.read(entry.path)
            assertEquals(WavEncoding.PCM_SIGNED_16, wav.encoding)
            assertEquals(16_000, wav.sampleRate)
            assertEquals(entry.durationSeconds, wav.durationSeconds)
            assertTrue(wav.samples.all { abs(it - 0.25f) < 1.0f / 32_768 })
        }

        val metadata = Files.readString(output.resolve("preparation-metadata.json"))
        assertTrue(metadata.contains(result.archiveSha256))
        assertTrue(metadata.contains("not an official publisher checksum"))
        assertTrue(Files.isRegularFile(output.resolve(".jvs-prepared-dataset")))
    }

    @Test
    fun `force never replaces an existing unmarked directory`() {
        val archive = temporaryDirectory.resolve("synthetic-jvs.zip")
        Files.write(archive, byteArrayOf(1))
        val output = temporaryDirectory.resolve("unowned")
        Files.createDirectories(output)
        Files.writeString(output.resolve("keep.txt"), "user data")

        val exception = assertFailsWith<IllegalArgumentException> {
            JvsDatasetPreparer.prepare(
                JvsPreparationOptions(archivePath = archive, outputDirectory = output, force = true),
                smallPlan(),
            )
        }

        assertTrue(exception.message.orEmpty().contains("unmarked"))
        assertTrue(Files.isRegularFile(output.resolve("keep.txt")))
    }

    @Test
    fun `production CLI pin matches the observed 2026-07-13 archive`() {
        assertEquals(3_536_595_425L, JvsCorpusPin.FILE_SIZE_BYTES)
        assertEquals("37180e2f87bd1a3e668d7c020378f77cebf61dd57d4d74c71eb0114f386a3999", JvsCorpusPin.SHA256)
        assertTrue(JvsCorpusPin.PIN_NOTE.contains("not an official publisher checksum"))
    }

    private fun smallPlan(): JvsPreparationPlan = JvsPreparationPlan(
        durationsSeconds = listOf(2, 3, 5),
        profiles = listOf(
            JvsSplitProfile(listOf("jvs001"), DatasetSplit.ENROLLMENT, 1..3, 1),
            JvsSplitProfile(listOf("jvs001"), DatasetSplit.DEVELOPMENT, 4..6, 1),
            JvsSplitProfile(listOf("jvs001"), DatasetSplit.TEST, 7..9, 1),
            JvsSplitProfile(listOf("jvs002"), DatasetSplit.DEVELOPMENT, 1..3, 1),
            JvsSplitProfile(listOf("jvs003"), DatasetSplit.UNKNOWN, 1..3, 1),
        ),
    )

    private fun createSyntheticArchive(path: Path, plan: JvsPreparationPlan) {
        val sourceKeys = plan.profiles.flatMap { profile ->
            profile.speakerIds.flatMap { speaker ->
                profile.candidateUtteranceNumbers.map { number -> speaker to number }
            }
        }
        val sourceSamples = ShortArray(24_000 * 6) { 8_192 }
        val wav = pcm16Wav(sourceSamples, sampleRate = 24_000)
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            sourceKeys.forEach { (speaker, number) ->
                val entryName =
                    "jvs_ver1/$speaker/parallel100/wav24kHz16bit/VOICEACTRESS100_%03d.wav".format(number)
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(wav)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("jvs_ver1/jvs001/nonpara30/wav24kHz16bit/ignored.wav"))
            zip.write(wav)
            zip.closeEntry()
        }
    }
}
