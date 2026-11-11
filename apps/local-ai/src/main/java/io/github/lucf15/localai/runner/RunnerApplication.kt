package io.github.lucf15.localai.runner

import android.app.Application
import io.github.lucf15.localai.core.ArtifactInstaller
import io.github.lucf15.localai.core.CapabilityCatalog
import io.github.lucf15.localai.core.EvaluationCorpus
import io.github.lucf15.localai.core.EvaluationJson
import io.github.lucf15.localai.core.ModelCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File
import kotlinx.coroutines.sync.Mutex
import io.github.lucf15.localai.runner.service.ClientGrants

class RunnerApplication : Application() {
    lateinit var graph: RunnerGraph
        private set
    override fun onCreate() { super.onCreate(); graph = RunnerGraph(this) }
}

class RunnerGraph(val app: Application) {
    val inferenceGate = Mutex()
    val clientGrants = ClientGrants(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val installer = ArtifactInstaller(File(app.filesDir, "models"))
    val store = EvaluationStore(File(app.filesDir, "evaluations"))
    val inputs = scope.async(Dispatchers.IO) {
        fun asset(name: String) = app.assets.open(name).bufferedReader().use { it.readText() }
        val models = asset("models.json")
        val capabilities = asset("definitions.json")
        val cases = asset("cases.json")
        store.recoverInterrupted()
        RunnerInputs(models, capabilities, cases,
            EvaluationJson.decodeFromString<ModelCatalog>(models),
            EvaluationJson.decodeFromString<CapabilityCatalog>(capabilities),
            EvaluationJson.decodeFromString<EvaluationCorpus>(cases))
    }
    val controller = EvaluationController(this)
}

data class RunnerInputs(
    val modelsRaw: String, val capabilitiesRaw: String, val casesRaw: String,
    val catalog: ModelCatalog, val definitions: CapabilityCatalog, val corpus: EvaluationCorpus,
)
