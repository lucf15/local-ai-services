package io.github.lucf15.localai.runner

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test

/** Explicit setup for cross-app probes; routine tests never discover this class. */
class InstallProbeModel {
    @Test fun installPinnedSmallModel() = runBlocking<Unit> {
        check(InstrumentationRegistry.getArguments().getString("install_probe_model") == "true")
        val graph = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RunnerApplication).graph
        val model = graph.inputs.await().catalog.models.first { it.id == "qwen3-06b-int4" }
        withTimeout(600_000) { withContext(Dispatchers.IO) { graph.installer.install(model) {} } }
    }
}
