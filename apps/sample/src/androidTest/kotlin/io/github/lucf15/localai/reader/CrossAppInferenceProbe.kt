package io.github.lucf15.localai.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lucf15.localai.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File

/** Opt-in only. Uses the manager's already installed model; this app never downloads weights. */
@RunWith(AndroidJUnit4::class)
class CrossAppInferenceProbe {
    @Test fun realCrossAppInferenceAndNativeCancellation() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("run_local_inference") == "true") { "Explicit opt-in required" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = LocalAiClient(context)
        var cancelledAt = ""
        val started = System.nanoTime()
        withTimeout(600_000) {
            client.transform(TransformRequest(Capability.SUMMARIZE, "Il museo riaprirà martedì alle 10:00. I biglietti già acquistati rimangono validi."))
                .takeWhile { update ->
                    if (update is TransformUpdate.Text) {
                        assertFalse("Need a live generation to cancel", update.complete)
                        cancelledAt = update.value
                        false
                    } else true
                }.collect()
        }
        assertTrue("Must receive native text before cancellation", cancelledAt.isNotBlank())
        var output: String? = null
        var progress = 0
        withTimeout(600_000) {
            client.transform(TransformRequest(Capability.SUMMARIZE, SAMPLE_TEXT)).collect { update ->
                if (update is TransformUpdate.Text) {
                    if (update.complete) output = update.value else progress++
                }
            }
        }
        assertFalse(output.isNullOrBlank())
        assertFalse(checkNotNull(output).contains("TRANSPORT FIXTURE"))
        val record = JSONObject().put("source", SAMPLE_TEXT).put("output", output)
            .put("capability", "summarize").put("language", "it").put("experimental", true)
            .put("cancelled_previous_request_after_text", true).put("progress_events", progress)
            .put("functional_emulator_wall_ms", (System.nanoTime() - started) / 1_000_000)
            .put("semantic_review", JSONObject.NULL)
        File(context.filesDir, "cross-app-functional-probe.json").writeText(record.toString(2))
    }
}
