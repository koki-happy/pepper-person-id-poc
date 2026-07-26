package com.example.pepper_person_id_poc

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationInput
import com.example.pepper_person_id_poc.infrastructure.speaker.PyannoteSegmentationOnnxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PyannoteSegmentationOnnxEngineTest {
    @Test
    fun exactArm64OnnxRuntimePairLoadsRunsAndCloses() {
        val engine = PyannoteSegmentationOnnxEngine(
            context = ApplicationProvider.getApplicationContext(),
        )
        try {
            engine.prepare()
            val result = engine.segment(
                SpeakerSegmentationInput(
                    windowId = "silence-10s",
                    startSample = 0L,
                    pcm16 = ShortArray(PyannoteSegmentationOnnxEngine.WINDOW_SAMPLES),
                ),
            )

            assertEquals(589, result.frames.size)
            assertEquals(160_000L, result.endSample)
            assertTrue(result.overlapRatio in 0f..1f)
            assertEquals(PyannoteSegmentationOnnxEngine.RUNTIME_ID, result.runtimeId)
            assertTrue(result.inferenceTimeMillis >= 0L)
        } finally {
            engine.close()
        }
    }
}
