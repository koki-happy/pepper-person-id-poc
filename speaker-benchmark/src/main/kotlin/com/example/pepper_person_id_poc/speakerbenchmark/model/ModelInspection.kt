package com.example.pepper_person_id_poc.speakerbenchmark.model

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Serializable
data class ModelInspection(
    val path: String,
    val fileName: String,
    val formatHint: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val lastModifiedUtc: String,
    val leadingBytesHex: String,
    val graphMetadataInspected: Boolean,
    val runtimeVersion: String? = null,
    val inputs: List<TensorInspection> = emptyList(),
    val outputs: List<TensorInspection> = emptyList(),
    val customMetadata: Map<String, String> = emptyMap(),
)

@Serializable
data class TensorInspection(
    val name: String,
    val type: String,
    val shape: List<Long>,
)

class ModelInspectionException(message: String) : IllegalArgumentException(message)

fun interface ModelInspector {
    fun inspect(path: Path): ModelInspection
}

class FileModelInspector : ModelInspector {
    override fun inspect(path: Path): ModelInspection {
        val absolutePath = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(absolutePath)) {
            throw ModelInspectionException("Model does not exist or is not a regular file: $absolutePath")
        }
        val leadingBytes = ByteArray(16)
        val count = Files.newInputStream(absolutePath).use { it.read(leadingBytes) }.coerceAtLeast(0)
        return ModelInspection(
            path = absolutePath.toString(),
            fileName = absolutePath.fileName.toString(),
            formatHint = if (absolutePath.fileName.toString().endsWith(".onnx", ignoreCase = true)) "ONNX" else "UNKNOWN",
            fileSizeBytes = Files.size(absolutePath),
            sha256 = Sha256.digest(absolutePath),
            lastModifiedUtc = Instant.ofEpochMilli(Files.getLastModifiedTime(absolutePath).toMillis()).toString(),
            leadingBytesHex = leadingBytes.copyOf(count).joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            },
            graphMetadataInspected = false,
        )
    }
}

/** Opens the graph with the same CPU runtime used by the benchmark and reports its actual contract. */
class OnnxModelInspector : ModelInspector {
    override fun inspect(path: Path): ModelInspection {
        val file = FileModelInspector().inspect(path)
        require(file.formatHint == "ONNX") { "Model must use the .onnx extension: ${file.path}" }
        val environment = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            environment.createSession(file.path, options).use { session ->
                return file.copy(
                    graphMetadataInspected = true,
                    runtimeVersion = environment.version,
                    inputs = session.inputInfo.map { (name, node) -> node.tensorInspection(name) },
                    outputs = session.outputInfo.map { (name, node) -> node.tensorInspection(name) },
                    customMetadata = session.metadata.customMetadata.toSortedMap(),
                )
            }
        }
    }

    private fun ai.onnxruntime.NodeInfo.tensorInspection(name: String): TensorInspection {
        val tensor = info as? TensorInfo
            ?: throw ModelInspectionException("$name is not a tensor input/output")
        return TensorInspection(
            name = name,
            type = tensor.type.toString(),
            shape = tensor.shape.toList(),
        )
    }
}
