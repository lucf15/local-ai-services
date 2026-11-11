package io.github.lucf15.localai.runner

import io.github.lucf15.localai.core.*
import io.github.lucf15.localai.inference.litert.LiteRtEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class RunProgress(
    val busy: Boolean = false, val phase: String = "idle", val caseId: String = "",
    val completed: Int = 0, val total: Int = 0, val output: String = "",
    val error: String? = null, val directory: File? = null,
)

/** Application-owned user action. Screen observation never starts a second run. */
class EvaluationController(private val graph: RunnerGraph) {
    private val mutableProgress = MutableStateFlow(RunProgress())
    val progress = mutableProgress.asStateFlow()
    private var job: Job? = null

    // Called from Main; selections are immutable captures, not live UI state.
    fun start(model: ModelDescriptor, backend: String, capability: String?, language: String?, firstOnly: Boolean) {
        check(job?.isActive != true)
        mutableProgress.value = RunProgress(busy = true, phase = "preparing")
        job = graph.scope.launch {
            if (!graph.inferenceGate.tryLock()) {
                mutableProgress.value = RunProgress(phase = "busy", error = "Local AI is serving another request")
                return@launch
            }
            var engine: InferenceEngine? = null
            var directory: File? = null
            var rows = mutableListOf<EvaluationResult>()
            var currentCase: String? = null
            try {
                val inputs = graph.inputs.await()
                val installed = withContext(Dispatchers.IO) { graph.installer.installedFile(model) }
                    ?: error("Install and verify the selected model first")
                val selected = inputs.corpus.cases.filter { (capability == null || it.capability == capability) && (language == null || it.language == language) }
                    .let { if (firstOnly) it.take(1) else it }
                require(selected.isNotEmpty())
                directory = withContext(Dispatchers.IO) { graph.store.create(inputs, model, backend, GenerationSettings(), selected.map { it.id }) }
                rows = inputs.corpus.cases.map { EvaluationResult(it.id) }.toMutableList()
                mutableProgress.value = RunProgress(busy = true, phase = "loading", total = selected.size, directory = directory)
                engine = LiteRtEngine(File(graph.app.cacheDir, "inference/${model.id}/$backend"))
                val loadMs = engine.load(installed, backend, GenerationSettings())
                withContext(Dispatchers.IO) { graph.store.update(directory, "running", loadMs) }
                for ((index, case) in selected.withIndex()) {
                    ensureActive()
                    currentCase = case.id
                    mutableProgress.value = mutableProgress.value.copy(phase = "generating", caseId = case.id, completed = index, output = "")
                    val definition = inputs.definitions.capabilities.first { it.id == case.capability }
                    val output = withTimeout(180_000) {
                        engine.generate(PromptFactory.create(case, definition), GenerationSettings()) { text ->
                            // Cumulative text is replaceable UI state; persisted results retain the full output.
                            mutableProgress.value = mutableProgress.value.copy(output = text)
                        }
                    }
                    val rowIndex = rows.indexOfFirst { it.caseId == case.id }
                    rows[rowIndex] = EvaluationResult(case.id, "success", output.text, output.finishReason, metrics = output.metrics)
                    withContext(Dispatchers.IO) { graph.store.saveResults(directory, rows) }
                    currentCase = null
                    mutableProgress.value = mutableProgress.value.copy(completed = index + 1)
                }
                withContext(Dispatchers.IO) { graph.store.update(directory, "completed") }
                mutableProgress.value = mutableProgress.value.copy(phase = "completed")
            } catch (cancel: CancellationException) {
                val persistenceError = withContext(NonCancellable + Dispatchers.IO) { runCatching {
                    if (directory != null) {
                        currentCase?.let { id -> rows[rows.indexOfFirst { it.caseId == id }] = EvaluationResult(id, "cancelled", finishReason = "cancelled") }
                        graph.store.saveResults(directory, rows)
                        graph.store.update(directory, if (cancel is TimeoutCancellationException) "timed_out" else "cancelled")
                    }
                }.exceptionOrNull() }
                mutableProgress.value = mutableProgress.value.copy(
                    phase = if (cancel is TimeoutCancellationException) "timed_out" else "cancelled",
                    error = persistenceError?.let { "Could not save the interrupted run: ${it.message}" },
                )
            } catch (error: Exception) {
                val persistenceError = withContext(NonCancellable + Dispatchers.IO) { runCatching {
                    if (directory != null) {
                        currentCase?.let { id -> rows[rows.indexOfFirst { it.caseId == id }] = EvaluationResult(id, "error", finishReason = "error", error = error.message ?: error.javaClass.simpleName) }
                        graph.store.saveResults(directory, rows)
                        graph.store.update(directory, "failed", error = error.message ?: error.javaClass.simpleName)
                    }
                }.exceptionOrNull() }
                val message = error.message ?: error.javaClass.simpleName
                mutableProgress.value = mutableProgress.value.copy(phase = "failed", error =
                    if (persistenceError == null) message else "$message. Results could not be saved: ${persistenceError.message}")
            } finally {
                val closeFailure = withContext(NonCancellable) { runCatching { engine?.close() }.exceptionOrNull() }
                graph.inferenceGate.unlock()
                mutableProgress.value = mutableProgress.value.copy(busy = false, error = closeFailure?.message ?: mutableProgress.value.error)
            }
        }
    }

    fun cancel() {
        if (job?.isActive == true) {
            mutableProgress.value = mutableProgress.value.copy(phase = "stopping")
            job?.cancel()
        }
    }
}
