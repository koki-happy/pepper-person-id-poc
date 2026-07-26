package com.example.pepper_person_id_poc

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.infrastructure.audio.AndroidPcmAudioRecorder
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrictSpeakerAudioPipelineTest {
    @Test
    fun liveRecorder_initializesOnly16KhzMonoPcm16WithSileroVad() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}",
            ).use { descriptor ->
                descriptor.fileDescriptor
            }
            instrumentation.waitForIdleSync()
        }
        val recorder = AndroidPcmAudioRecorder(context)
        try {
            recorder.start()
            val state = withTimeout(TimeUnit.SECONDS.toMillis(15)) {
                while (
                    recorder.state.value.status == AudioRecordingStatus.IDLE ||
                    recorder.state.value.status == AudioRecordingStatus.STARTING
                ) {
                    delay(50L)
                }
                recorder.state.value
            }

            assertWithMessage(
                "recording status; error=${state.error}; diagnostics=${state.captureInitialization}",
            ).that(state.status)
                .isEqualTo(AudioRecordingStatus.RECORDING)
            assertThat(state.sampleRate).isEqualTo(16_000)
            assertThat(state.vadModelName).contains("Silero")
            assertThat(state.error).isNull()
        } finally {
            recorder.close()
        }
    }
}
