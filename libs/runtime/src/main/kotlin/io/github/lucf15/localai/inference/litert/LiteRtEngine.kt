package io.github.lucf15.localai.inference.litert

import android.os.Debug
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import io.github.lucf15.localai.core.CapabilityPrompt
import io.github.lucf15.localai.core.GenerationOutput
import io.github.lucf15.localai.core.GenerationSettings
import io.github.lucf15.localai.core.InferenceEngine
import io.github.lucf15.localai.core.InferenceMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One engine owner, fresh conversation per case, no cross-case context reuse. */
class LiteRtEngine(private val cacheDirectory: File) : InferenceEngine {
    private val owner = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "local-ai-inference") }.asCoroutineDispatcher()
    private val lease = Mutex()
    private var engine: Engine? = null

    override suspend fun load(file: File, backend: String, settings: GenerationSettings): Double = lease.withLock {
        withContext(owner) {
            check(engine == null) { "Engine is already loaded" }
            require(file.isFile)
            val nativeBackend = when (backend) {
                "cpu" -> Backend.CPU(threadCount = 4)
                "gpu" -> Backend.GPU()
                else -> error("Unsupported backend")
            }
            val start = System.nanoTime()
            val candidate = Engine(EngineConfigCompat.create(file, nativeBackend, settings, cacheDirectory))
            // Publishing happens only after successful initialization. No close of a failed init.
            candidate.initialize()
            engine = candidate
            coroutineContext.ensureActive()
            (System.nanoTime() - start) / 1_000_000.0
        }
    }

    override suspend fun generate(prompt: CapabilityPrompt, settings: GenerationSettings, onText: (String) -> Unit): GenerationOutput = lease.withLock {
        withContext(owner) {
            coroutineContext.ensureActive()
            val current = checkNotNull(engine)
            val conversation = current.createConversation(ConversationConfig(
                systemInstruction = Contents.of(prompt.system),
                samplerConfig = SamplerConfig(settings.topK, settings.topP, settings.temperature, settings.seed),
                automaticToolCalling = false,
                maxOutputToken = settings.maxOutputTokens,
                thinkingConfig = ThinkingConfig(enableThinking = settings.enableThinking),
                extraContext = mapOf("enable_thinking" to settings.enableThinking),
            ))
            val memoryBefore = Debug.getPss().toInt()
            val start = System.nanoTime()
            var firstTextMs: Double? = null
            val text = StringBuilder()
            val nativeFinished = CompletableDeferred<Unit>()
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    // The runtime's Flow wrapper has an empty awaitClose in 0.19.1.
                    // Use callbacks so cancellation explicitly reaches native inference.
                    continuation.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
                    val callback = object : MessageCallback {
                        override fun onMessage(message: Message) {
                            if (!continuation.isActive) return
                            val chunk = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                            if (chunk.isNotEmpty()) {
                                if (firstTextMs == null) firstTextMs = (System.nanoTime() - start) / 1_000_000.0
                                if (text.length + chunk.length > 64 * 1024) {
                                    continuation.resumeWithException(IllegalStateException("Visible output exceeded the runner limit"))
                                    runCatching { conversation.cancelProcess() }
                                    return
                                }
                                text.append(chunk)
                                try { onText(text.toString()) } catch (error: Exception) {
                                    if (continuation.isActive) continuation.resumeWithException(error)
                                    runCatching { conversation.cancelProcess() }
                                }
                            }
                        }
                        override fun onDone() {
                            nativeFinished.complete(Unit)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(throwable: Throwable) {
                            nativeFinished.complete(Unit)
                            if (continuation.isActive) continuation.resumeWithException(throwable)
                        }
                    }
                    try {
                        if (continuation.isActive) {
                            conversation.sendMessageAsync(prompt.source, callback, extraContext = mapOf("enable_thinking" to settings.enableThinking))
                            if (!continuation.isActive) runCatching { conversation.cancelProcess() }
                        } else {
                            nativeFinished.complete(Unit)
                        }
                    } catch (error: Exception) {
                        nativeFinished.complete(Unit)
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
                coroutineContext.ensureActive()
                check(text.isNotBlank()) { "Model returned no visible text" }
                GenerationOutput(text.toString(), InferenceMetrics(
                    generationMs = (System.nanoTime() - start) / 1_000_000.0,
                    firstTextMs = firstTextMs,
                    processPssBeforeKib = memoryBefore,
                    processPssAfterKib = Debug.getPss().toInt(),
                ))
            } finally {
                // Do not close/reuse the native context while its callback is still active.
                withContext(NonCancellable) {
                    nativeFinished.await()
                    conversation.close()
                }
            }
        }
    }

    override suspend fun close() {
        try {
            lease.withLock { withContext(owner + NonCancellable) { engine?.close(); engine = null } }
        } finally { owner.close() }
    }
}

private object EngineConfigCompat {
    fun create(file: File, backend: Backend, settings: GenerationSettings, cache: File) = com.google.ai.edge.litertlm.EngineConfig(
        modelPath = file.absolutePath,
        backend = backend,
        maxNumTokens = settings.contextTokens,
        cacheDir = cache.apply { mkdirs() }.absolutePath,
    )
}
