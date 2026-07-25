package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal data class AnonymousClusterSnapshot(
    val nextId: Int,
    val clusters: List<AnonymousCluster>,
)

internal object AnonymousClusterBinaryCodec {
    const val SCHEMA_VERSION = 1
    private const val MAGIC = 0x414E4F4E
    private const val MAX_CLUSTERS = 100_000
    private const val MAX_DIMENSION = 100_000

    fun write(output: OutputStream, snapshot: AnonymousClusterSnapshot) {
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(SCHEMA_VERSION)
            data.writeInt(snapshot.nextId)
            data.writeInt(snapshot.clusters.size)
            snapshot.clusters.forEach { cluster ->
                data.writeUTF(cluster.anonymousId)
                data.writeUTF(cluster.modelId)
                data.writeInt(cluster.embeddingDimension)
                writeVector(data, cluster.normalizedEmbeddingSum)
                writeVector(data, cluster.centroid)
                data.writeInt(cluster.updateCount)
                data.writeLong(cluster.createdAtMillis)
                data.writeLong(cluster.updatedAtMillis)
            }
        }
    }

    fun read(input: InputStream): AnonymousClusterSnapshot {
        DataInputStream(input).use { data ->
            require(data.readInt() == MAGIC) { "Invalid anonymous cluster file" }
            require(data.readInt() == SCHEMA_VERSION) { "Unsupported anonymous cluster schema" }
            val nextId = data.readInt().also { require(it >= 1) }
            val count = data.readInt().also { require(it in 0..MAX_CLUSTERS) }
            val clusters = List(count) {
                val anonymousId = data.readUTF()
                val modelId = data.readUTF()
                val dimension = data.readInt().also { require(it in 1..MAX_DIMENSION) }
                val sum = readVector(data, dimension)
                val centroid = readVector(data, dimension)
                AnonymousCluster(
                    anonymousId = anonymousId,
                    modelId = modelId,
                    embeddingDimension = dimension,
                    normalizedEmbeddingSum = sum,
                    centroid = centroid,
                    updateCount = data.readInt().also { require(it >= 1) },
                    createdAtMillis = data.readLong(),
                    updatedAtMillis = data.readLong(),
                )
            }
            return AnonymousClusterSnapshot(nextId, clusters)
        }
    }

    private fun writeVector(data: DataOutputStream, vector: FloatArray) {
        data.writeInt(vector.size)
        vector.forEach(data::writeFloat)
    }

    private fun readVector(data: DataInputStream, dimension: Int): FloatArray {
        require(data.readInt() == dimension)
        return FloatArray(dimension) { data.readFloat().also { require(it.isFinite()) } }
    }
}
