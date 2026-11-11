package io.github.lucf15.localai.runtime

import io.github.lucf15.localai.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.*
import org.junit.Test

class CapabilityRuntimeTest {
    private val definition = CapabilityDefinition("summarize", 1, mapOf("it" to "Summarize the supplied text"), listOf("short_paragraph"))
    private val request = TaskRequest("summarize", "it", "First source")
    private val output = GenerationOutput("Actual test engine output", InferenceMetrics(1.0, 0.5))
    private open inner class Engine : InferenceEngine {
        var closed = false
        var prompt: CapabilityPrompt? = null
        override suspend fun load(file: File, backend: String, settings: GenerationSettings): Double = 1.0
        override suspend fun generate(prompt: CapabilityPrompt, settings: GenerationSettings, onText: (String) -> Unit): GenerationOutput {
            this.prompt = prompt; onText(output.text); return output
        }
        override suspend fun close() { closed = true }
    }
    private fun runtime(gate: Mutex, engine: () -> InferenceEngine) = CapabilityRuntime(gate, { RuntimeInputs(File("unused-test-file"), definition) }, engine)

    @Test fun validationPreventsOversizedAndUnsupportedRequestsBeforeLoading() = runTest {
        val runtime = runtime(Mutex()) { error("Must not create an engine") }
        assertEquals("INPUT_TOO_LARGE", assertFailsWith<TaskFailure> { runtime.execute(request.copy(text = "é".repeat(513))) {} }.code)
        assertEquals("UNSUPPORTED_LANGUAGE", assertFailsWith<TaskFailure> { runtime.execute(request.copy(language = "fr")) {} }.code)
    }
    @Test fun sharedLeasePreventsParallelEvaluationAndIpc() = runTest {
        val gate = Mutex(locked = true)
        assertEquals("BUSY", assertFailsWith<TaskFailure> { runtime(gate) { error("No engine") }.execute(request) {} }.code)
        assertTrue(gate.isLocked)
    }
    @Test fun cancellationWaitsForCleanupBeforeReusingLease() = runTest {
        val gate = Mutex()
        val entered = CompletableDeferred<Unit>()
        val closing = CompletableDeferred<Unit>()
        val permitClose = CompletableDeferred<Unit>()
        val engine = object : Engine() {
            override suspend fun generate(prompt: CapabilityPrompt, settings: GenerationSettings, onText: (String) -> Unit): GenerationOutput {
                entered.complete(Unit); awaitCancellation()
            }
            override suspend fun close() { closing.complete(Unit); permitClose.await(); super.close() }
        }
        val job = launch { runtime(gate) { engine }.execute(request) {} }
        entered.await(); job.cancel(); closing.await()
        assertTrue(gate.isLocked)
        permitClose.complete(Unit); job.join()
        assertTrue(engine.closed); assertFalse(gate.isLocked)
        val fresh = Engine()
        runtime(gate) { fresh }.execute(request.copy(text = "Fresh source")) {}
        assertContains(checkNotNull(fresh.prompt).source, "Fresh source")
        assertFalse(checkNotNull(fresh.prompt).source.contains("First source"))
    }
    @Test fun loadFailureAndCloseFailureStillReleaseLease() = runTest {
        val gate = Mutex()
        val engine = object : Engine() {
            override suspend fun load(file: File, backend: String, settings: GenerationSettings): Double = error("Native initialization failed")
            override suspend fun close() { super.close(); error("Close failed") }
        }
        assertFailsWith<IllegalStateException> { runtime(gate) { engine }.execute(request) {} }
        assertTrue(engine.closed); assertFalse(gate.isLocked)
    }
}
