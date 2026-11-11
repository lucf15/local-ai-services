package io.github.lucf15.localai.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class InstallationException(val kind: String, message: String) : IOException(message)

data class InstallationProgress(val phase: String, val received: Long, val total: Long)

/** Owns one installation writer. A verified marker is created only after atomic activation. */
class ArtifactInstaller(
    private val root: File,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
    private val allowHttpForTests: Boolean = false,
) {
    private val lock = Mutex()

    private fun directory(model: ModelDescriptor) = File(root, "${model.id}/${model.revision}")
    private fun marker(model: ModelDescriptor) = File(directory(model), ".verified")
    fun partialBytes(model: ModelDescriptor): Long = File(directory(model), "${model.artifact.filename}.part").length()

    fun installedFile(model: ModelDescriptor): File? {
        val file = File(directory(model), model.artifact.filename)
        return file.takeIf {
            it.isFile && it.length() == model.artifact.sizeBytes &&
                runCatching { marker(model).readText() == model.artifact.sha256 }.getOrDefault(false)
        }
    }

    suspend fun install(model: ModelDescriptor, progress: (InstallationProgress) -> Unit): File = lock.withLock {
        withContext(Dispatchers.IO) {
            installedFile(model)?.let { return@withContext it }
            val url = java.net.URI(model.artifact.downloadUrl)
            require((url.scheme == "https" && url.host == "huggingface.co") || (allowHttpForTests && url.scheme == "http" && url.host in setOf("localhost", "127.0.0.1")))
            if (!allowHttpForTests) {
                require(url.path == "/${model.repository}/resolve/${model.revision}/${model.artifact.filename}")
            }
            val dir = directory(model).apply { mkdirs() }
            val partial = File(dir, "${model.artifact.filename}.part")
            val target = File(dir, model.artifact.filename)
            if (partial.length() > model.artifact.sizeBytes) partial.delete()
            val needed = model.artifact.sizeBytes - partial.length()
            if (dir.usableSpace < needed + 32L * 1024 * 1024) throw InstallationException("storage", "Not enough free storage for this model")
            if (partial.length() < model.artifact.sizeBytes) download(model, partial, progress)
            coroutineContext.ensureActive()
            progress(InstallationProgress("verifying", partial.length(), model.artifact.sizeBytes))
            if (partial.length() != model.artifact.sizeBytes || hash(partial) != model.artifact.sha256) {
                partial.delete()
                throw InstallationException("integrity", "The model failed its size or SHA-256 check. Retry the download.")
            }
            coroutineContext.ensureActive()
            java.nio.file.Files.move(partial.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            atomicWrite(marker(model), model.artifact.sha256.toByteArray())
            progress(InstallationProgress("installed", target.length(), model.artifact.sizeBytes))
            target
        }
    }

    private suspend fun download(model: ModelDescriptor, partial: File, progress: (InstallationProgress) -> Unit) {
        val offset = partial.length()
        val request = Request.Builder().url(model.artifact.downloadUrl).header("Accept-Encoding", "identity").apply {
            if (offset > 0) header("Range", "bytes=$offset-")
        }.build()
        val call = client.newCall(request)
        // Both awaiting headers and consuming the response body are bound to cancellation.
        val writerFinished = CompletableDeferred<Unit>()
        try {
        suspendCancellableCoroutine<Unit> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                    writerFinished.complete(Unit)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (response.code !in setOf(200, 206)) {
                                val kind = if (response.code in setOf(401, 403)) "access" else "http"
                                throw InstallationException(kind, "Download failed (HTTP ${response.code})")
                            }
                            val append = response.code == 206
                            if (append) {
                                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(response.header("Content-Range").orEmpty())
                                if (range == null || range.groupValues[1].toLong() != offset || range.groupValues[3].toLong() != model.artifact.sizeBytes || range.groupValues[2].toLong() != model.artifact.sizeBytes - 1) {
                                    throw InstallationException("range", "The server returned an incompatible partial response")
                                }
                            }
                            var received = if (append) offset else 0L
                            val body = response.body ?: throw IOException("Missing response body")
                            var lastUpdate = 0L
                            body.byteStream().use { input ->
                                java.io.FileOutputStream(partial, append).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (continuation.isActive) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        received += count
                                        if (received > model.artifact.sizeBytes) throw InstallationException("size", "Download exceeded expected size")
                                        output.write(buffer, 0, count)
                                        val now = System.nanoTime()
                                        if (now - lastUpdate > 150_000_000) {
                                            progress(InstallationProgress("downloading", received, model.artifact.sizeBytes))
                                            lastUpdate = now
                                        }
                                    }
                                    output.fd.sync()
                                }
                            }
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    } finally {
                        writerFinished.complete(Unit)
                    }
                }
            })
        }
        } finally {
            // Keep the installation lease until the old callback has closed its file.
            withContext(NonCancellable) { writerFinished.await() }
        }
    }

    private suspend fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
