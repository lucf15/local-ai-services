package io.github.lucf15.localai.runner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lucf15.localai.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class RunnerSmokeTest {
    @Test fun bundledInputsKeepReviewDataOutOfModelPrompts() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RunnerApplication
        val inputs = app.graph.inputs.await()
        assertEquals(3, inputs.catalog.models.size)
        assertEquals(24, inputs.corpus.cases.size)
        inputs.corpus.cases.forEach { case ->
            val definition = inputs.definitions.capabilities.first { it.id == case.capability }
            val prompt = PromptFactory.create(case, definition)
            val source = EvaluationJson.parseToJsonElement(prompt.source).jsonObject
            assertEquals(setOf("text"), source.keys)
            assertEquals(case.input, source.getValue("text").jsonPrimitive.content)
        }
    }

    @Test fun exportPreservesFrozenInputsAndInterruptedRunsRemainUnrated() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RunnerApplication
        val root = File(app.cacheDir, "store-smoke-${System.nanoTime()}")
        try {
            val inputs = app.graph.inputs.await()
            val store = EvaluationStore(root)
            val dir = store.create(inputs, inputs.catalog.models.first(), "cpu", GenerationSettings(), listOf(inputs.corpus.cases.first().id))
            store.recoverInterrupted()
            val manifest = EvaluationJson.parseToJsonElement(File(dir, "run.json").readText()).jsonObject
            assertEquals("interrupted_process_death", manifest.getValue("execution_status").jsonPrimitive.content)
            val zip = store.export(dir, File(root, "export"))
            ZipFile(zip).use { archive ->
                manifest.getValue("input_sha256").jsonObject.forEach { (name, hash) ->
                    assertEquals(hash.jsonPrimitive.content, sha256(archive.getInputStream(archive.getEntry(name)).readBytes()))
                }
                val rows = archive.getInputStream(archive.getEntry("results.jsonl")).bufferedReader().readLines()
                assertEquals(24, rows.size)
                rows.forEach { line ->
                    val row = EvaluationJson.parseToJsonElement(line).jsonObject
                    assertEquals("pending", row.getValue("status").jsonPrimitive.content)
                    assertEquals(JsonNull, row["review"])
                    assertEquals(JsonNull, row["output"])
                }
            }
        } finally { root.deleteRecursively() }
    }
}
