package io.github.lucf15.localai.runtime

import io.github.lucf15.localai.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.io.File

data class TaskRequest(val capability: String, val language: String, val text: String)
data class RuntimeInputs(val file: File, val definition: CapabilityDefinition)
class TaskFailure(val code: String) : Exception(code)

/** One lease shared by direct evaluation and IPC. No unbounded queue or cross-request conversation. */
class CapabilityRuntime(
    private val gate: Mutex,
    private val prepare: suspend (TaskRequest) -> RuntimeInputs,
    private val createEngine: () -> InferenceEngine,
) {
    suspend fun execute(request: TaskRequest, onText: (String) -> Unit): GenerationOutput {
        validate(request)
        if (!gate.tryLock()) throw TaskFailure("BUSY")
        var engine: InferenceEngine? = null
        try {
            return withTimeout(300_000) {
                val inputs = prepare(request)
                val variant = when (request.capability) {
                    "summarize" -> "short_paragraph"
                    "rewrite" -> "concise"
                    else -> "minimal_correction"
                }
                val prompt = PromptFactory.create(EvaluationCase("external", request.capability, request.language, variant, request.text), inputs.definition)
                if (prompt.source.toByteArray(Charsets.UTF_8).size > 1500) throw TaskFailure("INPUT_TOO_LARGE")
                val current = createEngine().also { engine = it }
                current.load(inputs.file, "cpu", GenerationSettings())
                current.generate(prompt, GenerationSettings()) { text ->
                    if (text.length > 16_384) throw TaskFailure("OUTPUT_TOO_LARGE")
                    onText(text)
                }
            }
        } finally {
            // Cancellation is terminal only once native cleanup has finished and the lease is free.
            try { withContext(NonCancellable) { engine?.close() } } finally { gate.unlock() }
        }
    }

    companion object {
        const val MAX_INPUT_BYTES = 1024
        fun validate(request: TaskRequest) {
            if (request.capability !in setOf("summarize", "rewrite", "proofread")) throw TaskFailure("UNSUPPORTED_CAPABILITY")
            if (request.language !in setOf("it", "en")) throw TaskFailure("UNSUPPORTED_LANGUAGE")
            if (request.text.isBlank()) throw TaskFailure("INVALID_INPUT")
            if (request.text.toByteArray(Charsets.UTF_8).size > MAX_INPUT_BYTES) throw TaskFailure("INPUT_TOO_LARGE")
        }
    }
}
