package io.github.lucf15.localai.runner

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import io.github.lucf15.localai.core.ModelDescriptor
import kotlinx.coroutines.*

@Stable
interface ModelUiState {
    val id: String
    val title: String
    val downloadBytes: Long
    val phase: String
    val fraction: Float
    val installed: Boolean
    val active: Boolean
    val error: String?
}

@Stable
private class MutableModelUiState(val descriptor: ModelDescriptor) : ModelUiState {
    override val id = descriptor.id
    override val title = descriptor.displayName
    override val downloadBytes = descriptor.artifact.sizeBytes
    override var phase by mutableStateOf("not_installed")
    override var fraction by mutableFloatStateOf(0f)
    override var installed by mutableStateOf(false)
    override var active by mutableStateOf(false)
    override var error by mutableStateOf<String?>(null)
}

@Stable
interface RunnerUiState {
    val ready: Boolean
    val models: List<ModelUiState>
    val wifiOnly: Boolean
    val error: String?
}

@Stable
private class MutableRunnerUiState : RunnerUiState {
    override var ready by mutableStateOf(false)
    override var models by mutableStateOf<List<ModelUiState>>(emptyList())
    override var wifiOnly by mutableStateOf(true)
    override var error by mutableStateOf<String?>(null)
}

class RunnerViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as RunnerApplication).graph
    private val work = WorkManager.getInstance(app)
    private val mutable = MutableRunnerUiState()
    val state: RunnerUiState = mutable
    private var models = emptyList<MutableModelUiState>()
    private val readyModels = CompletableDeferred<List<MutableModelUiState>>()

    init {
        viewModelScope.launch {
            try {
                val inputs = graph.inputs.await()
                models = inputs.catalog.models.map(::MutableModelUiState)
                val installedIds = withContext(Dispatchers.IO) {
                    models.filter { graph.installer.installedFile(it.descriptor) != null }.map { it.id }.toSet()
                }
                Snapshot.withMutableSnapshot {
                    models.forEach { model ->
                        model.installed = model.id in installedIds
                        if (model.installed) model.phase = "installed"
                    }
                    mutable.models = models
                    mutable.ready = true
                }
                readyModels.complete(models)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.error = error.message ?: "Catalog unavailable"
                readyModels.completeExceptionally(error)
            }
        }
    }

    /** Acquired only while the screen is STARTED; application jobs have separate ownership. */
    suspend fun observe(): Unit = coroutineScope {
        val ready = try { readyModels.await() } catch (error: Exception) {
            if (error is CancellationException) throw error
            return@coroutineScope
        }
        ready.forEach { model ->
            launch {
                work.getWorkInfosForUniqueWorkFlow("model-${model.id}").collect { infos ->
                    val installed = withContext(Dispatchers.IO) { graph.installer.installedFile(model.descriptor) != null }
                    val info = infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull()
                    Snapshot.withMutableSnapshot {
                        model.installed = installed
                        model.active = info?.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING)
                        model.phase = when {
                            installed -> "installed"
                            info?.state == WorkInfo.State.RUNNING -> info.progress.getString("phase") ?: "starting"
                            info?.state == WorkInfo.State.ENQUEUED -> "queued"
                            info?.state == WorkInfo.State.CANCELLED -> "paused"
                            info?.state == WorkInfo.State.FAILED -> "failed"
                            else -> "not_installed"
                        }
                        model.fraction = (info?.progress?.getLong("received", 0)?.toDouble()?.div(model.downloadBytes) ?: 0.0).toFloat().coerceIn(0f, 1f)
                        model.error = info?.outputData?.getString("error")

                    }
                }
            }
        }
    }

    fun setWifiOnly(value: Boolean) { mutable.wifiOnly = value }

    fun install(id: String) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf("model_id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (mutable.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniqueWork("model-$id", ExistingWorkPolicy.KEEP, request)
    }

    fun pause(id: String) { work.cancelUniqueWork("model-$id") }

}
