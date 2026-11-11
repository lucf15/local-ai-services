package io.github.lucf15.localai.runner

import android.os.Build
import io.github.lucf15.localai.core.EvaluationJson
import io.github.lucf15.localai.core.EvaluationResult
import io.github.lucf15.localai.core.GenerationSettings
import io.github.lucf15.localai.core.ModelDescriptor
import io.github.lucf15.localai.core.atomicWrite
import io.github.lucf15.localai.core.sha256
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A run is single-writer owned by EvaluationController. Each completed row is durable. */
class EvaluationStore(private val root: File) {
    fun create(inputs: RunnerInputs, model: ModelDescriptor, backend: String, settings: GenerationSettings, selectedCases: List<String>): File {
        val dir = File(root, UUID.randomUUID().toString()).apply { check(mkdirs()) }
        val modelSnapshot = EvaluationJson.parseToJsonElement(inputs.modelsRaw).jsonObject.getValue("models").jsonArray
            .first { it.jsonObject.getValue("id").jsonPrimitive.content == model.id }.toString()
        val snapshots = mapOf("cases.json" to inputs.casesRaw, "capabilities.json" to inputs.capabilitiesRaw, "model.json" to modelSnapshot)
        snapshots.forEach { (name, text) -> atomicWrite(File(dir, name), text.toByteArray()) }
        val manifest = buildJsonObject {
            put("schema_version", 1)
            put("created_utc", Instant.now().toString())
            put("execution_status", "running")
            put("requested_backend", backend)
            put("selected_cases", JsonArray(selectedCases.map(::JsonPrimitive)))
            put("environment", buildJsonObject {
                put("platform", "Android")
                put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("android_version", "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                put("runtime_version", "0.19.1")
                put("app_build", "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}/${BuildConfig.BUILD_TYPE}")
                put("source_fingerprint_sha256", BuildConfig.SOURCE_FINGERPRINT)
                put("effective_backend", backend)
                put("backend_policy", "explicit_requested_backend_no_application_fallback; internal_runtime_delegation_not_instrumented")
                put("generation_settings", EvaluationJson.encodeToJsonElement(settings))
                put("cpu_threads", if (backend == "cpu") JsonPrimitive(4) else JsonNull)
                put("sampling_scope", "fresh_native_conversation_per_case_shared_engine_per_run")
            })
            put("input_sha256", buildJsonObject { snapshots.forEach { (name, text) -> put(name, sha256(text.toByteArray())) } })
            put("load_ms", JsonNull)
        }
        atomicWrite(File(dir, "run.json"), manifest.toString().toByteArray())
        saveResults(dir, inputs.corpus.cases.map { EvaluationResult(it.id) })
        return dir
    }

    fun saveResults(dir: File, rows: List<EvaluationResult>) {
        val compact = Json(EvaluationJson) { prettyPrint = false }
        atomicWrite(File(dir, "results.jsonl"), rows.joinToString("\n", postfix = "\n") { compact.encodeToString(it) }.toByteArray())
    }

    fun update(dir: File, status: String, loadMs: Double? = null, error: String? = null) {
        val old = EvaluationJson.parseToJsonElement(File(dir, "run.json").readText()).jsonObject
        val updated = JsonObject(old.toMutableMap().apply {
            put("execution_status", JsonPrimitive(status))
            if (loadMs != null) put("load_ms", JsonPrimitive(loadMs))
            if (error != null) put("execution_error", JsonPrimitive(error))
        })
        atomicWrite(File(dir, "run.json"), updated.toString().toByteArray())
    }

    fun recoverInterrupted() {
        root.listFiles()?.filter(File::isDirectory)?.forEach { dir ->
            runCatching {
                val manifest = EvaluationJson.parseToJsonElement(File(dir, "run.json").readText()).jsonObject
                if (manifest["execution_status"]?.jsonPrimitive?.content == "running") update(dir, "interrupted_process_death")
            }
        }
    }

    fun latest(): File? = root.listFiles()?.filter { File(it, "results.jsonl").isFile }?.maxByOrNull(File::lastModified)

    fun export(dir: File, exportRoot: File): File {
        val out = File(exportRoot.apply { mkdirs() }, "local-ai-${dir.name}.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            listOf("run.json", "model.json", "cases.json", "capabilities.json", "results.jsonl").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                File(dir, name).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }
}
