package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import android.util.AtomicFile
import com.example.pepper_person_id_poc.application.contract.AnonymousClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterEngine
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import java.io.ByteArrayOutputStream
import java.io.File

abstract class FileAnonymousClusterRepository(
    context: Context,
    fileName: String,
    private val idPrefix: String,
) : AnonymousClusterRepository {
    private val file = File(context.applicationContext.filesDir, "biometric/$fileName")
    private val atomicFile = AtomicFile(file)
    private var cachedSnapshot: AnonymousClusterSnapshot? = null

    @Synchronized
    override fun getAll(): List<AnonymousCluster> = snapshot().clusters.map(AnonymousCluster::deepCopy)

    @Synchronized
    override fun count(): Int = snapshot().clusters.size

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
        val normalized = AnonymousClusterEngine.normalize(embedding)
        val snapshot = snapshot()
        val match = AnonymousClusterEngine.findBestMatch(
            normalizedEmbedding = normalized,
            clusters = snapshot.clusters,
            modelId = modelId,
            reservedAnonymousIds = reservedAnonymousIds,
        )
        val matched = match.bestCluster?.takeIf { checkNotNull(match.bestScore) >= threshold }
        val isNew = matched == null
        val target = matched ?: AnonymousCluster(
            anonymousId = "$idPrefix-${snapshot.nextId.toString().padStart(3, '0')}",
            modelId = modelId,
            embeddingDimension = normalized.size,
            normalizedEmbeddingSum = FloatArray(normalized.size),
            centroid = normalized.copyOf(),
            updateCount = 0,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
        )
        val updated = AnonymousClusterEngine.addEmbedding(
            target,
            normalized,
            maximumUpdateCount,
            nowMillis,
        )
        val changed = isNew || updated !== target
        val resultingSnapshot = if (changed) {
            val clusters = snapshot.clusters.toMutableList()
            val index = clusters.indexOfFirst { it.anonymousId == target.anonymousId }
            if (index >= 0) clusters[index] = updated else clusters += updated
            AnonymousClusterSnapshot(
                nextId = if (isNew) snapshot.nextId + 1 else snapshot.nextId,
                clusters = clusters,
            ).also {
                save(it)
                cachedSnapshot = it
            }
        } else {
            snapshot
        }
        val currentModelCount = resultingSnapshot.clusters.count {
            it.modelId == modelId && it.embeddingDimension == normalized.size
        }
        return AnonymousIdentificationResult(
            anonymousId = updated.anonymousId,
            modelId = modelId,
            bestExistingScore = match.bestScore,
            threshold = threshold,
            isNewCluster = isNew,
            updateCount = updated.updateCount,
            maximumUpdateCount = maximumUpdateCount,
            currentModelClusterCount = currentModelCount,
            totalClusterCount = resultingSnapshot.clusters.size,
            candidateScores = match.candidates.map {
                it.copy(selected = !isNew && it.anonymousId == updated.anonymousId)
            },
        )
    }

    @Synchronized
    override fun deleteAll() {
        cachedSnapshot = AnonymousClusterSnapshot(1, emptyList())
        atomicFile.delete()
    }

    private fun snapshot(): AnonymousClusterSnapshot =
        cachedSnapshot ?: load().also { cachedSnapshot = it }

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
}

class FileAnonymousFaceClusterRepository(context: Context) :
    FileAnonymousClusterRepository(context, "anonymous-face-clusters.bin", "anonymous-face"),
    AnonymousFaceClusterRepository

class FileAnonymousSpeakerClusterRepository(context: Context) :
    FileAnonymousClusterRepository(context, "anonymous-speaker-clusters.bin", "anonymous-speaker"),
    AnonymousSpeakerClusterRepository
