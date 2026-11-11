package io.github.lucf15.localai.sdk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import io.github.lucf15.localai.transport.v1.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow

enum class Capability(val id: String) { SUMMARIZE("summarize"), REWRITE("rewrite"), PROOFREAD("proofread") }
enum class Language(val id: String) { ITALIAN("it"), ENGLISH("en") }
data class TransformRequest(val capability: Capability, val text: String, val language: Language = Language.ITALIAN)
data class Availability(val capability: String, val languages: List<String>, val installed: Boolean, val experimental: Boolean, val maxInputUtf8Bytes: Int)
sealed interface TransformUpdate {
    data class Ready(val capabilities: List<Availability>) : TransformUpdate
    data class Text(val value: String, val complete: Boolean) : TransformUpdate
}
enum class LocalAiError { PERMISSION_DENIED, MANAGER_UNAVAILABLE, INCOMPATIBLE_PROTOCOL, MODEL_NOT_INSTALLED, BUSY,
    INVALID_INPUT, INPUT_TOO_LARGE, UNSUPPORTED_CAPABILITY, UNSUPPORTED_LANGUAGE, OUTPUT_TOO_LARGE, TIMED_OUT, CANCELLED, INFERENCE_FAILED }
class LocalAiException(val error: LocalAiError) : Exception(error.name)

/** Each collected Flow owns one binding and operation. Cancellation drains native cleanup, no replay. */
class LocalAiClient(context: Context) {
    private val app = context.applicationContext
    fun hasAccess(): Boolean = app.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    fun accessSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))

    fun transform(request: TransformRequest): Flow<TransformUpdate> = channelFlow {
        if (request.text.isBlank()) throw LocalAiException(LocalAiError.INVALID_INPUT)
        if (request.text.toByteArray(Charsets.UTF_8).size > 1024) throw LocalAiException(LocalAiError.INPUT_TOO_LARGE)
        if (!hasAccess()) throw LocalAiException(LocalAiError.PERMISSION_DENIED)
        val connected = CompletableDeferred<ILocalAiService>()
        val events = Channel<CapabilityEvent>(Channel.CONFLATED)
        var bound = false
        var operation: ICapabilityOperation? = null
        var terminal = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { connected.complete(ILocalAiService.Stub.asInterface(service)) }
            private fun disconnected() {
                val error = LocalAiException(LocalAiError.MANAGER_UNAVAILABLE)
                connected.completeExceptionally(error); events.close(error)
            }
            override fun onServiceDisconnected(name: ComponentName) = disconnected()
            override fun onBindingDied(name: ComponentName) = disconnected()
            override fun onNullBinding(name: ComponentName) = disconnected()
        }
        try {
            withContext(Dispatchers.Main.immediate) {
                bound = app.bindService(Intent().setComponent(ComponentName(MANAGER, "$MANAGER.service.LocalAiService")), connection, Context.BIND_AUTO_CREATE)
            }
            if (!bound) throw LocalAiException(LocalAiError.MANAGER_UNAVAILABLE)
            val service = withTimeoutOrNull(10_000) { connected.await() } ?: throw LocalAiException(LocalAiError.MANAGER_UNAVAILABLE)
            val capabilities = withContext(Dispatchers.IO) {
                if (service.protocolVersion != 2) throw LocalAiException(LocalAiError.INCOMPATIBLE_PROTOCOL)
                service.capabilities.map { Availability(it.capability, it.languages.toList(), it.installed, it.experimental, it.maxInputUtf8Bytes) }
            }
            send(TransformUpdate.Ready(capabilities))
            // NonCancellable only for short admission: cancellation must still receive and cancel its handle.
            withContext(NonCancellable + Dispatchers.IO) {
                operation = service.submit(CapabilityRequest().apply {
                    capability = request.capability.id; language = request.language.id; text = request.text
                }, object : ICapabilityCallback.Stub() {
                    override fun onEvent(event: CapabilityEvent) {
                        events.trySend(event)
                        if (event.status != CapabilityEvent.PROGRESS) events.close()
                    }
                })
            }
            val handle = checkNotNull(operation)
            var lastSequence = 0
            for (event in events) {
                if (event.sequence <= lastSequence || event.text == null || event.text.length > 16_384) throw LocalAiException(LocalAiError.INCOMPATIBLE_PROTOCOL)
                lastSequence = event.sequence
                when (event.status) {
                    CapabilityEvent.PROGRESS -> {
                        send(TransformUpdate.Text(event.text, false))
                        withContext(Dispatchers.IO) { handle.acknowledge(event.sequence) }
                    }
                    CapabilityEvent.COMPLETED -> { terminal = true; send(TransformUpdate.Text(event.text, true)); return@channelFlow }
                    CapabilityEvent.CANCELLED -> { terminal = true; throw LocalAiException(LocalAiError.CANCELLED) }
                    CapabilityEvent.FAILED -> { terminal = true; throw LocalAiException(parseError(event.errorCode)) }
                    else -> throw LocalAiException(LocalAiError.INCOMPATIBLE_PROTOCOL)
                }
            }
            throw LocalAiException(LocalAiError.MANAGER_UNAVAILABLE)
        } catch (error: CancellationException) { throw error }
        catch (error: SecurityException) { throw LocalAiException(LocalAiError.PERMISSION_DENIED) }
        catch (error: IllegalStateException) { throw LocalAiException(if (error.message == "BUSY") LocalAiError.BUSY else LocalAiError.INFERENCE_FAILED) }
        catch (error: IllegalArgumentException) { throw LocalAiException(parseError(error.message)) }
        catch (error: android.os.RemoteException) { throw LocalAiException(LocalAiError.MANAGER_UNAVAILABLE) }
        finally {
            withContext(NonCancellable) {
                val handle = operation
                if (!terminal && handle != null) {
                    withContext(Dispatchers.IO) { runCatching { handle.cancel() } }
                    // Keep binding alive until the manager releases native work; a dead manager closes events.
                    withTimeoutOrNull(310_000) { runCatching { for (event in events) if (event.status != CapabilityEvent.PROGRESS) break } }
                }
                if (bound) withContext(Dispatchers.Main.immediate) { app.unbindService(connection) }
                events.cancel()
            }
        }
    }.buffer(Channel.CONFLATED)

    private fun parseError(value: String?): LocalAiError = LocalAiError.entries.firstOrNull { it.name == value } ?: LocalAiError.INFERENCE_FAILED
    companion object {
        const val MANAGER = "io.github.lucf15.localai.runner"
        const val PERMISSION = "$MANAGER.permission.USE_LOCAL_AI"
    }
}
