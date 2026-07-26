package com.example.pepper_person_id_poc

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.application.contract.AnonymousClusterRepository
import com.example.pepper_person_id_poc.application.session.AnonymousSessionStartupState
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.infrastructure.AppContainer
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionAnonymousClusterLifecycleTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val application get() = context.applicationContext as PepperPersonIdApplication
    private val legacyDirectory get() = File(context.filesDir, "biometric")
    private val legacyFiles get() = listOf(
        File(legacyDirectory, "anonymous-face-clusters.bin"),
        File(legacyDirectory, "anonymous-speaker-clusters.bin"),
    )

    @Before
    fun clearBefore() = runBlocking {
        awaitApplicationReady()
        application.container.anonymousFaceClusterRepository.deleteAll()
        application.container.anonymousSpeakerClusterRepository.deleteAll()
        deleteLegacyFiles()
    }

    @After
    fun clearAfter() {
        application.container.anonymousFaceClusterRepository.deleteAll()
        application.container.anonymousSpeakerClusterRepository.deleteAll()
        deleteLegacyFiles()
    }

    @Test
    fun appContainerCreation_deletesLegacyBiometricFiles() {
        legacyDirectory.mkdirs()
        legacyFiles.forEach { it.writeBytes(byteArrayOf(1, 2, 3, 4)) }
        assertThat(legacyFiles.all(File::isFile)).isTrue()

        AppContainer(context)

        assertThat(legacyFiles.any(File::exists)).isFalse()
        assertThat(legacyDirectory.exists()).isFalse()
    }

    @Test
    fun activityRecreation_retainsApplicationScopedFaceAndSpeakerClusters() {
        val faceRepository = application.container.anonymousFaceClusterRepository
        val speakerRepository = application.container.anonymousSpeakerClusterRepository
        createCluster(faceRepository, ModelSpaceId("face-space"), 100L)
        createCluster(speakerRepository, ModelSpaceId("speaker-space"), 100L)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.onActivity { recreatedActivity ->
                val recreatedContainer =
                    (recreatedActivity.application as PepperPersonIdApplication).container
                assertThat(recreatedContainer.anonymousFaceClusterRepository)
                    .isSameInstanceAs(faceRepository)
                assertThat(recreatedContainer.anonymousSpeakerClusterRepository)
                    .isSameInstanceAs(speakerRepository)
                assertThat(recreatedContainer.anonymousFaceClusterRepository.getAll()
                    .map { it.anonymousId })
                    .containsExactly("anonymous-face-001")
                assertThat(recreatedContainer.anonymousSpeakerClusterRepository.getAll()
                    .map { it.anonymousId })
                    .containsExactly("anonymous-speaker-001")
            }
        }
    }

    @Test
    fun explicitDeleteAll_clearsBothModalitiesAndResetsIdCounters() {
        val faceRepository = application.container.anonymousFaceClusterRepository
        val speakerRepository = application.container.anonymousSpeakerClusterRepository
        createCluster(faceRepository, ModelSpaceId("face-space"), 100L)
        createCluster(speakerRepository, ModelSpaceId("speaker-space"), 100L)

        faceRepository.deleteAll()
        speakerRepository.deleteAll()

        assertThat(faceRepository.getAll()).isEmpty()
        assertThat(speakerRepository.getAll()).isEmpty()
        assertThat(createCluster(faceRepository, ModelSpaceId("face-space"), 200L).anonymousId)
            .isEqualTo("anonymous-face-001")
        assertThat(
            createCluster(speakerRepository, ModelSpaceId("speaker-space"), 200L).anonymousId,
        ).isEqualTo("anonymous-speaker-001")
        assertThat(legacyFiles.any(File::exists)).isFalse()
    }

    private suspend fun awaitApplicationReady() {
        val state = withTimeout(5_000L) {
            application.startupState.first {
                it !is AnonymousSessionStartupState.Initializing
            }
        }
        assertThat(state).isEqualTo(AnonymousSessionStartupState.Ready)
    }

    private fun createCluster(
        repository: AnonymousClusterRepository,
        modelSpaceId: ModelSpaceId,
        nowElapsedRealtime: Long,
    ): AnonymousCluster = checkNotNull(
        repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = nowElapsedRealtime,
        ),
    )

    private fun deleteLegacyFiles() {
        legacyFiles.forEach(File::delete)
        legacyDirectory.delete()
    }
}
