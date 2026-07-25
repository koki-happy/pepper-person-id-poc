package com.example.pepper_person_id_poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.infrastructure.repository.FileAnonymousFaceClusterRepository
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileAnonymousClusterRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val file get() = File(context.filesDir, "biometric/anonymous-face-clusters.bin")

    @Before
    fun clearBefore() {
        FileAnonymousFaceClusterRepository(context).deleteAll()
    }

    @After
    fun clearAfter() {
        FileAnonymousFaceClusterRepository(context).deleteAll()
    }

    @Test
    fun identify_persistsRestoresSeparatesModelsAndResetsCounter() {
        val repository = FileAnonymousFaceClusterRepository(context)
        val first = repository.identify("model-a", floatArrayOf(1f, 0f), 0.9f, 20)
        val same = repository.identify("model-a", floatArrayOf(0.99f, 0.01f), 0.9f, 20)
        val otherModel = repository.identify("model-b", floatArrayOf(1f, 0f), 0.9f, 20)

        assertThat(first.anonymousId).isEqualTo("anonymous-face-001")
        assertThat(same.anonymousId).isEqualTo(first.anonymousId)
        assertThat(otherModel.anonymousId).isEqualTo("anonymous-face-002")
        assertThat(otherModel.currentModelClusterCount).isEqualTo(1)
        assertThat(otherModel.totalClusterCount).isEqualTo(2)
        assertThat(FileAnonymousFaceClusterRepository(context).getAll()).hasSize(2)

        repository.deleteAll()
        val afterDelete = repository.identify("model-a", floatArrayOf(1f, 0f), 0.9f, 20)
        assertThat(afterDelete.anonymousId).isEqualTo("anonymous-face-001")
    }

    @Test
    fun newCluster_reportsExistingScoresWithoutSelfScore() {
        val repository = FileAnonymousFaceClusterRepository(context)
        val noComparison = repository.identify("model", floatArrayOf(1f, 0f), 0.9f, 20)
        val belowThreshold = repository.identify("model", floatArrayOf(0f, 1f), 0.9f, 20)

        assertThat(noComparison.bestExistingScore).isNull()
        assertThat(noComparison.candidateScores).isEmpty()
        assertThat(belowThreshold.bestExistingScore).isWithin(0.0001f).of(0f)
        assertThat(belowThreshold.candidateScores.map { it.anonymousId })
            .containsExactly("anonymous-face-001")
        assertThat(belowThreshold.candidateScores.none { it.selected }).isTrue()
    }

    @Test
    fun updateLimit_reusesIdWithoutWritingFile() {
        val repository = FileAnonymousFaceClusterRepository(context)
        val first = repository.identify("model", floatArrayOf(1f, 0f), 0f, 1)
        val before = file.readBytes()
        val modifiedBefore = file.lastModified()

        val frozen = repository.identify("model", floatArrayOf(0f, 1f), 0f, 1)

        assertThat(frozen.anonymousId).isEqualTo(first.anonymousId)
        assertThat(file.readBytes().asList()).containsExactlyElementsIn(before.asList()).inOrder()
        assertThat(file.lastModified()).isEqualTo(modifiedBefore)
        assertThat(repository.getAll().single().updatedAtMillis)
            .isEqualTo(repository.getAll().single().createdAtMillis)
    }

    @Test
    fun corruptFile_isolatedlyFallsBackToEmptyState() {
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        val repository = FileAnonymousFaceClusterRepository(context)

        assertThat(repository.getAll()).isEmpty()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun getAllAndCount_useInProcessSnapshotWithoutReopeningFile() {
        val repository = FileAnonymousFaceClusterRepository(context)
        repository.identify("model", floatArrayOf(1f, 0f), 0.9f, 20)
        assertThat(file.delete()).isTrue()

        assertThat(repository.count()).isEqualTo(1)
        assertThat(repository.getAll().single().anonymousId).isEqualTo("anonymous-face-001")
    }
}
