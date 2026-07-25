package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import android.util.AtomicFile
import com.example.pepper_person_id_poc.application.contract.AnonymousClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.sqrt

abstract class FileAnonymousClusterRepository(
    context: Context,
    fileName: String,
    private val idPrefix: String,
) : AnonymousClusterRepository {
    private val file = File(context.applicationContext.filesDir, "biometric/$fileName")
    private val atomicFile = AtomicFile(file)

    @Synchronized
    override fun getAll(): List<AnonymousCluster> = load().clusters.map(AnonymousCluster::deepCopy)

    @Synchronized
    override fun count(): Int = load().clusters.size

    @Synchronized
    override fun identify(
        modelId: String,
        embedding: FloatArray,
        threshold: Float,
        maximumUpdateCount: Int,
        nowMillis: Long,
        reservedAnonymousIds: Set<String>,
    ): AnonymousIdentificationResult {
        require(modelId.isNotBlank())
        require(threshold in 0f..1f)
        require(maximumUpdateCount in 1..100)
        val normalized = l2Normalize(embedding)
        val snapshot = load()
        val scored = snapshot.clusters.asSequence()
            .filter { it.modelId == modelId && it.embeddingDimension == normalized.size }
            .filterNot { it.anonymousId in reservedAnonymousIds }
            .map { it to cosine(normalized, it.centroid) }
            .sortedByDescending { it.second }
            .toList()
        val best = scored.firstOrNull()
        val matched = best?.takeIf { it.second >= threshold }
        val isNew = matched == null
        val target = matched?.first ?: AnonymousCluster(
            anonymousId = "$idPrefix-${snapshot.nextId.toString().padStart(3, '0')}",
            modelId = modelId,
            embeddingDimension = normalized.size,
            normalizedEmbeddingSum = FloatArray(normalized.size),
            centroid = normalized.copyOf(),
            updateCount = 0,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        val updated = if (target.updateCount < maximumUpdateCount) {
            val sum = target.normalizedEmbeddingSum.copyOf()
            sum.indices.forEach { sum[it] += normalized[it] }
            target.copy(
                normalizedEmbeddingSum = sum,
                centroid = l2Normalize(sum),
                updateCount = target.updateCount + 1,
                updatedAtMillis = nowMillis,
            )
        } else {
            target
        }
        val clusters = snapshot.clusters.toMutableList()
        val index = clusters.indexOfFirst { it.anonymousId == target.anonymousId }
        if (index >= 0) clusters[index] = updated else clusters += updated
        save(
            AnonymousClusterSnapshot(
                nextId = if (isNew) snapshot.nextId + 1 else snapshot.nextId,
                clusters = clusters,
            ),
        )
        return AnonymousIdentificationResult(
            anonymousId = updated.anonymousId,
            modelId = modelId,
            score = matched?.second ?: 1f,
            threshold = threshold,
            isNewCluster = isNew,
            updateCount = updated.updateCount,
            maximumUpdateCount = maximumUpdateCount,
            clusterCount = clusters.size,
            candidateScores = (
                scored.map { (cluster, score) ->
                    AnonymousClusterScore(cluster.anonymousId, score, cluster.anonymousId == updated.anonymousId)
                } + if (isNew) {
                    listOf(AnonymousClusterScore(updated.anonymousId, 1f, true))
                } else {
                    emptyList()
                }
            ).sortedByDescending(AnonymousClusterScore::score),
        )
    }

    @Synchronized
    override fun deleteAll() {
        atomicFile.delete()
    }

    private fun load(): AnonymousClusterSnapshot {
        if (!file.exists()) return AnonymousClusterSnapshot(1, emptyList())
        return runCatching { atomicFile.openRead().use(AnonymousClusterBinaryCodec::read) }
            .getOrElse {
                atomicFile.delete()
                AnonymousClusterSnapshot(1, emptyList())
            }
    }

    private fun save(snapshot: AnonymousClusterSnapshot) {
        file.parentFile?.mkdirs()
        val bytes = ByteArrayOutputStream().also {
            AnonymousClusterBinaryCodec.write(it, snapshot)
        }.toByteArray()
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (throwable: Throwable) {
            atomicFile.failWrite(output)
            throw throwable
        }
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty())
        require(vector.all(Float::isFinite))
        val norm = sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
        require(norm > 0.0)
        return FloatArray(vector.size) { (vector[it] / norm).toFloat() }
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float =
        left.indices.sumOf { left[it].toDouble() * right[it].toDouble() }.toFloat().coerceIn(-1f, 1f)
}

class FileAnonymousFaceClusterRepository(context: Context) :
    FileAnonymousClusterRepository(context, "anonymous-face-clusters.bin", "anonymous-face"),
    AnonymousFaceClusterRepository

class FileAnonymousSpeakerClusterRepository(context: Context) :
    FileAnonymousClusterRepository(context, "anonymous-speaker-clusters.bin", "anonymous-speaker"),
    AnonymousSpeakerClusterRepository
