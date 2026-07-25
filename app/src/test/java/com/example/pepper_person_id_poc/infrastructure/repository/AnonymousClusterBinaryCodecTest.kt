package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Test

class AnonymousClusterBinaryCodecTest {
    @Test
    fun roundTrip_preservesSchemaCounterAndCluster() {
        val cluster = AnonymousCluster(
            "anonymous-face-001", "face-model", 2,
            floatArrayOf(1f, 1f), floatArrayOf(0.70710677f, 0.70710677f),
            2, 10L, 20L,
        )
        val output = ByteArrayOutputStream()
        AnonymousClusterBinaryCodec.write(output, AnonymousClusterSnapshot(2, listOf(cluster)))

        val decoded = AnonymousClusterBinaryCodec.read(ByteArrayInputStream(output.toByteArray()))

        assertThat(decoded.nextId).isEqualTo(2)
        assertThat(decoded.clusters.single().anonymousId).isEqualTo("anonymous-face-001")
        assertThat(decoded.clusters.single().normalizedEmbeddingSum.asList()).containsExactly(1f, 1f).inOrder()
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownSchema_isRejected() {
        val bytes = ByteArrayOutputStream().also { output ->
            java.io.DataOutputStream(output).use {
                it.writeInt(0x414E4F4E)
                it.writeInt(999)
            }
        }.toByteArray()
        AnonymousClusterBinaryCodec.read(ByteArrayInputStream(bytes))
    }
}
