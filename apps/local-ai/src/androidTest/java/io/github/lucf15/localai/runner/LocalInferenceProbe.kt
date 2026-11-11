package io.github.lucf15.localai.runner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lucf15.localai.core.*
import io.github.lucf15.localai.inference.litert.LiteRtEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit opt-in: downloads real public weights and performs actual local inference. */
@RunWith(AndroidJUnit4::class)
class LocalInferenceProbe {
    @Test fun selectedModelRunsLocally() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("run_local_inference") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RunnerApplication
        val graph = app.graph
        val inputs = graph.inputs.await()
        val modelId = InstrumentationRegistry.getArguments().getString("model_id") ?: "qwen3-06b-int4"
        val model = inputs.catalog.models.first { it.id == modelId }
        val file = withTimeout(600_000) { graph.installer.install(model) { } }
        val directory = graph.store.create(inputs, model, "cpu", GenerationSettings(), listOf(inputs.corpus.cases.first().id))
        val rows = inputs.corpus.cases.map { EvaluationResult(it.id) }.toMutableList()
        val engine = LiteRtEngine(File(app.cacheDir, "probe/${model.id}"))
        try {
            val loadMs = engine.load(file, "cpu", GenerationSettings())
            val case = inputs.corpus.cases.first()
            val definition = inputs.definitions.capabilities.first { it.id == case.capability }
            val output = withTimeout(180_000) { engine.generate(PromptFactory.create(case, definition), GenerationSettings()) { } }
            check(output.text.isNotBlank())
            rows[0] = EvaluationResult(case.id, "success", output.text, output.finishReason, metrics = output.metrics)
            graph.store.saveResults(directory, rows)
            graph.store.update(directory, "completed_functional_probe", loadMs)
        } catch (error: Exception) {
            rows[0] = EvaluationResult(rows[0].caseId, "error", finishReason = "error", error = error.message ?: error.javaClass.simpleName)
            graph.store.saveResults(directory, rows)
            graph.store.update(directory, "failed_functional_probe", error = error.message ?: error.javaClass.simpleName)
            throw error
        } finally { withContext(NonCancellable) { engine.close() } }
    }
}
