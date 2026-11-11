package io.github.lucf15.localai.reader

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.lucf15.localai.sdk.*
import kotlinx.coroutines.*

@Stable interface ExampleUiState {
    val source: String
    val output: String
    val phase: String
    val connected: Boolean
    val completed: Boolean
    val busy: Boolean
    val capability: Capability
    val language: Language
}
@Stable private class MutableExampleUiState(source: String, override val capability: Capability, language: Language) : ExampleUiState {
    override var source by mutableStateOf(source)
    override var output by mutableStateOf("")
    override var phase by mutableStateOf("Ready to process on this device.")
    override var connected by mutableStateOf(false)
    override var completed by mutableStateOf(false)
    override var busy by mutableStateOf(false)
    override var language by mutableStateOf(language)
}
class ExampleViewModel(app: Application, exampleId: String, private val savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val example = examples.first { it.id == exampleId }
    private val mutable = MutableExampleUiState(
        savedState["source"] ?: example.source, example.capability,
        savedState.get<String>("language")?.let(Language::valueOf) ?: Language.ITALIAN,
    )
    val state: ExampleUiState = mutable
    val client = LocalAiClient(app)
    private var job: Job? = null
    fun source(value: String) {
        if (!mutable.busy) { mutable.source = value; savedState["source"] = value }
    }
    fun language(value: Language) {
        if (!mutable.busy) { mutable.language = value; savedState["language"] = value.name }
    }
    private var pendingPermissionRequest: TransformRequest? = null
    fun preparePermissionRun() { pendingPermissionRequest = TransformRequest(mutable.capability, mutable.source, mutable.language) }
    fun accessResult(granted: Boolean) {
        val pending = pendingPermissionRequest
        pendingPermissionRequest = null
        if (!mutable.busy) mutable.phase = if (granted) "Access granted" else "Permission denied. You can change it in Android settings."
        if (granted && pending != null) start(pending)
    }
    fun run() = start(TransformRequest(mutable.capability, mutable.source, mutable.language))
    private fun start(request: TransformRequest) {
        if (mutable.busy) return
        Snapshot.withMutableSnapshot { mutable.busy = true; mutable.output = ""; mutable.connected = false; mutable.completed = false; mutable.phase = "Preparing on-device processing…" }
        job = viewModelScope.launch {
            try {
                client.transform(request).collect { update ->
                    when (update) {
                        is TransformUpdate.Ready -> Snapshot.withMutableSnapshot { mutable.connected = true; mutable.phase = "Preparing on-device processing…" }
                        is TransformUpdate.Text -> Snapshot.withMutableSnapshot {
                            mutable.output = update.value
                            mutable.completed = update.complete
                            mutable.phase = if (update.complete) "Completed on device" else "Generating on device…"
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                mutable.phase = "Cancelled"; throw cancel
            } catch (error: LocalAiException) { mutable.phase = when (error.error) {
                LocalAiError.PERMISSION_DENIED -> "Allow local AI access in this app’s Android settings."
                LocalAiError.MODEL_NOT_INSTALLED -> "Install Qwen3 0.6B in Local AI to continue."
                LocalAiError.BUSY -> "Local AI is busy. Try again shortly."
                LocalAiError.MANAGER_UNAVAILABLE -> "Open Local AI to reconnect."
                LocalAiError.INPUT_TOO_LARGE -> "Text is too long. Try a shorter passage."
                LocalAiError.INVALID_INPUT -> "Add some text to get started."
                LocalAiError.CANCELLED -> "Cancelled"
                else -> "Could not process this text. Please try again."
            } }
            catch (_: Exception) { mutable.phase = "Could not complete the request" }
            finally { mutable.busy = false }
        }
    }
    fun cancel() {
        if (mutable.busy) { mutable.phase = "Stopping local inference…"; job?.cancel() }
    }
}
