package io.github.lucf15.localai.runner.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import io.github.lucf15.localai.inference.litert.LiteRtEngine
import io.github.lucf15.localai.runner.RunnerApplication
import io.github.lucf15.localai.runtime.*
import io.github.lucf15.localai.transport.v1.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Service-lifetime work. One operation, one engine lease, one unacknowledged progress event. */
class LocalAiService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val admission = Mutex()
    private val graph get() = (application as RunnerApplication).graph
    private val grants get() = graph.clientGrants
    private val runtime by lazy {
        CapabilityRuntime(graph.inferenceGate, prepare = { request ->
            val inputs = graph.inputs.await()
            // Deliberate experimental configuration, not an evidence-based quality recommendation.
            val model = inputs.catalog.models.first { it.id == MODEL_ID }
            val file = withContext(Dispatchers.IO) { graph.installer.installedFile(model) } ?: throw TaskFailure("MODEL_NOT_INSTALLED")
            RuntimeInputs(file, inputs.definitions.capabilities.first { it.id == request.capability })
        }, createEngine = { LiteRtEngine(File(cacheDir, "service/$MODEL_ID/cpu")) })
    }

    private val endpoint = object : ILocalAiService.Stub() {
        override fun getProtocolVersion(): Int { grants.requireAuthorized(Binder.getCallingUid()); return 2 }

        override fun getCapabilities(): Array<CapabilityAvailability> {
            grants.requireAuthorized(Binder.getCallingUid())
            // No blocking asset read or disk access on a Binder thread; availability is a snapshot.
            val installed = modelInstalled
            return arrayOf("summarize", "rewrite", "proofread").map { id ->
                CapabilityAvailability().apply {
                    capability = id; languages = arrayOf("it", "en")
                    maxInputUtf8Bytes = CapabilityRuntime.MAX_INPUT_BYTES
                    this.installed = installed; experimental = true
                }
            }.toTypedArray()
        }

        override fun submit(request: CapabilityRequest, callback: ICapabilityCallback): ICapabilityOperation {
            val owner = grants.requireAuthorized(Binder.getCallingUid())
            val input = TaskRequest(request.capability ?: "", request.language ?: "", request.text ?: "")
            try { CapabilityRuntime.validate(input) } catch (error: TaskFailure) { throw IllegalArgumentException(error.code) }
            if (!admission.tryLock()) throw IllegalStateException("BUSY")
            val latest = Channel<String>(Channel.CONFLATED)
            val sequence = AtomicInteger(0)
            val acknowledged = AtomicInteger(0)
            fun authorize() { if (grants.requireAuthorized(owner.uid) != owner) throw SecurityException("APP_IDENTITY_CHANGED") }
            fun emit(status: Int, text: String = "", code: String = "") {
                if (status == CapabilityEvent.PROGRESS || status == CapabilityEvent.COMPLETED) authorize()
                callback.onEvent(CapabilityEvent().apply {
                    this.sequence = sequence.incrementAndGet(); this.status = status
                    this.text = text; errorCode = code
                })
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val pump = launch {
                    for (text in latest) {
                        while (acknowledged.get() < sequence.get()) delay(50)
                        delay(100) // Coalesce native callbacks, even when the client can acknowledge quickly.
                        val newest = latest.tryReceive().getOrNull() ?: text
                        emit(CapabilityEvent.PROGRESS, newest)
                    }
                }
                try {
                    val output = runtime.execute(input) { latest.trySend(it) }
                    pump.cancelAndJoin()
                    emit(CapabilityEvent.COMPLETED, output.text)
                } catch (error: TimeoutCancellationException) {
                    pump.cancelAndJoin()
                    runCatching { emit(CapabilityEvent.FAILED, code = "TIMED_OUT") }
                } catch (error: CancellationException) {
                    withContext(NonCancellable) { pump.cancelAndJoin() }
                    runCatching { emit(CapabilityEvent.CANCELLED) }
                } catch (error: Exception) {
                    pump.cancelAndJoin()
                    runCatching { emit(CapabilityEvent.FAILED, code = (error as? TaskFailure)?.code ?: "INFERENCE_FAILED") }
                } finally {
                    latest.close()
                }
            }
            val death = IBinder.DeathRecipient { job.cancel() }
            try { callback.asBinder().linkToDeath(death, 0) }
            catch (error: Exception) { admission.unlock(); job.cancel(); throw error }
            job.invokeOnCompletion {
                runCatching { callback.asBinder().unlinkToDeath(death, 0) }
                admission.unlock()
            }
            val operation = object : ICapabilityOperation.Stub() {
                private fun checkOwner() {
                    if (Binder.getCallingUid() != owner.uid) throw SecurityException("NOT_REQUEST_OWNER")
                    authorize()
                }
                override fun cancel() { checkOwner(); job.cancel() }
                override fun acknowledge(value: Int) {
                    checkOwner()
                    if (value < 1 || value > sequence.get()) throw IllegalArgumentException("INVALID_SEQUENCE")
                    acknowledged.accumulateAndGet(value, ::maxOf)
                }
            }
            job.start()
            return operation
        }
    }

    @Volatile private var modelInstalled = false
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            val inputs = graph.inputs.await()
            modelInstalled = withContext(Dispatchers.IO) { graph.installer.installedFile(inputs.catalog.models.first { it.id == MODEL_ID }) != null }
        }
    }
    override fun onBind(intent: Intent?): IBinder = endpoint
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object { const val MODEL_ID = "qwen3-06b-int4" }
}
