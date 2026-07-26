package com.example.pepper_person_id_poc.infrastructure.litert

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.google.ai.edge.litert.TensorType
import java.util.concurrent.atomic.AtomicBoolean

data class LiteRtFloatTensorSpec(
    val name: String,
    val dimensions: List<Int>,
) {
    init {
        require(name.isNotBlank())
        require(dimensions.isNotEmpty())
        require(dimensions.all { it > 0 })
    }

    val elementCount: Int = dimensions.fold(1) { count, dimension ->
        Math.multiplyExact(count, dimension)
    }
}

data class LiteRtModelContract(
    val artifactId: String,
    val runtimeId: String = LiteRtRuntime.RUNTIME_ID,
    val assetPath: String,
    val inputs: List<LiteRtFloatTensorSpec>,
    val outputs: List<LiteRtFloatTensorSpec>,
) {
    init {
        require(artifactId.isNotBlank())
        require(runtimeId == LiteRtRuntime.RUNTIME_ID) {
            "Unsupported LiteRT runtimeId=$runtimeId; expected ${LiteRtRuntime.RUNTIME_ID}; no fallback"
        }
        require(assetPath.startsWith("models/") && assetPath.endsWith(".tflite")) {
            "LiteRT assetPath must identify an exact models/*.tflite asset"
        }
        require(inputs.isNotEmpty())
        require(outputs.isNotEmpty())
        require((inputs + outputs).map { it.name }.size == (inputs + outputs).map { it.name }.toSet().size) {
            "LiteRT tensor names must be unique"
        }
    }
}

internal interface LiteRtSession : AutoCloseable {
    fun infer(inputs: List<FloatArray>): List<FloatArray>
}

class LiteRtRuntime internal constructor(
    val contract: LiteRtModelContract,
    private val session: LiteRtSession,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    fun infer(inputs: List<FloatArray>): List<FloatArray> {
        check(!closed.get()) { "LiteRT runtime for ${contract.artifactId} is closed" }
        require(inputs.size == contract.inputs.size) {
            "Expected ${contract.inputs.size} input tensors, received ${inputs.size}"
        }
        inputs.zip(contract.inputs).forEachIndexed { index, (values, spec) ->
            require(values.size == spec.elementCount) {
                "Input[$index] ${spec.name} expected ${spec.elementCount} values, received ${values.size}"
            }
            require(values.all(Float::isFinite)) {
                "Input[$index] ${spec.name} contains a non-finite value"
            }
        }
        val outputs = session.infer(inputs)
        require(outputs.size == contract.outputs.size) {
            "Expected ${contract.outputs.size} output tensors, received ${outputs.size}"
        }
        outputs.zip(contract.outputs).forEachIndexed { index, (values, spec) ->
            require(values.size == spec.elementCount) {
                "Output[$index] ${spec.name} expected ${spec.elementCount} values, received ${values.size}"
            }
            require(values.all(Float::isFinite)) {
                "Output[$index] ${spec.name} contains a non-finite value"
            }
        }
        return outputs
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            session.close()
        }
    }

    companion object {
        const val VERSION = "2.1.6"
        const val RUNTIME_ID = "litert-2.1.6-android-cpu"

        fun open(
            context: Context,
            contract: LiteRtModelContract,
            cpuThreadCount: Int? = null,
        ): LiteRtRuntime {
            require(cpuThreadCount == null || cpuThreadCount > 0)
            val options = CompiledModel.Options(Accelerator.CPU).apply {
                cpuOptions = CompiledModel.CpuOptions(numThreads = cpuThreadCount)
            }
            val model = CompiledModel.create(
                context.assets,
                contract.assetPath,
                options,
            )
            return try {
                LiteRtRuntime(
                    contract = contract,
                    session = CompiledModelLiteRtSession(model, contract),
                )
            } catch (failure: Throwable) {
                model.close()
                throw failure
            }
        }
    }
}

private class CompiledModelLiteRtSession(
    private val model: CompiledModel,
    contract: LiteRtModelContract,
) : LiteRtSession {
    private val inputBuffers: List<TensorBuffer>
    private val outputBuffers: List<TensorBuffer>
    private val closed = AtomicBoolean(false)

    init {
        validateTensorTypes(model, contract)
        inputBuffers = createBuffers(contract.inputs) { model.createInputBuffer(it.name) }
        outputBuffers = try {
            createBuffers(contract.outputs) { model.createOutputBuffer(it.name) }
        } catch (failure: Throwable) {
            inputBuffers.closeAll()
            throw failure
        }
    }

    override fun infer(inputs: List<FloatArray>): List<FloatArray> {
        check(!closed.get()) { "LiteRT compiled model session is closed" }
        inputs.zip(inputBuffers).forEach { (values, buffer) -> buffer.writeFloat(values) }
        model.run(inputBuffers, outputBuffers)
        return outputBuffers.map(TensorBuffer::readFloat)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var firstFailure: Throwable? = null
        (outputBuffers + inputBuffers).forEach { resource ->
            runCatching(resource::close).onFailure { failure ->
                if (firstFailure == null) firstFailure = failure else firstFailure?.addSuppressed(failure)
            }
        }
        runCatching(model::close).onFailure { failure ->
            if (firstFailure == null) firstFailure = failure else firstFailure?.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
    }
}

private fun validateTensorTypes(
    model: CompiledModel,
    contract: LiteRtModelContract,
) {
    contract.inputs.forEach { expected ->
        requireExactFloatTensor(
            owner = "input",
            expected = expected,
            actual = model.getInputTensorType(expected.name),
        )
    }
    contract.outputs.forEach { expected ->
        requireExactFloatTensor(
            owner = "output",
            expected = expected,
            actual = model.getOutputTensorType(expected.name),
        )
    }
}

private fun requireExactFloatTensor(
    owner: String,
    expected: LiteRtFloatTensorSpec,
    actual: TensorType,
) {
    require(actual.elementType == TensorType.ElementType.FLOAT) {
        "LiteRT $owner ${expected.name} must be FLOAT, actual=${actual.elementType}"
    }
    val actualDimensions = actual.layout?.dimensions
    require(actualDimensions == expected.dimensions) {
        "LiteRT $owner ${expected.name} shape mismatch: " +
            "expected=${expected.dimensions}, actual=$actualDimensions"
    }
}

private inline fun createBuffers(
    specs: List<LiteRtFloatTensorSpec>,
    create: (LiteRtFloatTensorSpec) -> TensorBuffer,
): List<TensorBuffer> {
    val buffers = ArrayList<TensorBuffer>(specs.size)
    try {
        specs.forEach { buffers += create(it) }
        return buffers
    } catch (failure: Throwable) {
        buffers.closeAll()
        throw failure
    }
}

private fun List<TensorBuffer>.closeAll() {
    forEach { runCatching(it::close) }
}
