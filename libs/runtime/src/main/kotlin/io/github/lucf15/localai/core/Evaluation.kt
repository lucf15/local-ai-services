package io.github.lucf15.localai.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest

val EvaluationJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

@Serializable
data class ModelCatalog(val models: List<ModelDescriptor>)

@Serializable
data class ModelDescriptor(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val repository: String,
    val revision: String,
    val artifact: ModelArtifact,
) {
    init {
        require(id.matches(Regex("[a-z0-9-]+")))
        require(revision.matches(Regex("[0-9a-f]{40}")))
    }
}

@Serializable
data class ModelArtifact(
    val filename: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    @SerialName("download_url") val downloadUrl: String,
) {
    init {
        require(filename.isNotBlank() && '/' !in filename && '\\' !in filename && filename !in setOf(".", ".."))
        require(sizeBytes > 0)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

@Serializable
data class CapabilityCatalog(val capabilities: List<CapabilityDefinition>)

@Serializable
data class CapabilityDefinition(val id: String, val version: Int, val instruction: Map<String, String>, val variants: List<String>)

@Serializable
data class EvaluationCorpus(val cases: List<EvaluationCase>)

@Serializable
data class EvaluationCase(
    val id: String, val capability: String, val language: String, val variant: String, val input: String,
)

data class CapabilityPrompt(val system: String, val source: String)

object PromptFactory {
    fun create(case: EvaluationCase, definition: CapabilityDefinition): CapabilityPrompt {
        require(case.capability == definition.id)
        require(case.variant in definition.variants)
        val instruction = requireNotNull(definition.instruction[case.language])
        val format = when (case.variant) {
            "three_bullets" -> if (case.language == "it") "Esattamente tre punti elenco." else "Exactly three bullet points."
            "short_paragraph" -> if (case.language == "it") "Un solo paragrafo breve." else "One short paragraph."
            "formal" -> if (case.language == "it") "Tono formale." else "Formal tone."
            "concise" -> if (case.language == "it") "Forma concisa." else "Concise style."
            "minimal_correction" -> if (case.language == "it") "Solo le correzioni necessarie." else "Necessary corrections only."
            else -> error("Unsupported variant")
        }
        // The JSON string is source data, not an interpolated instruction or review rubric.
        return CapabilityPrompt("$instruction\n$format\nOutput language: ${case.language}.", EvaluationJson.encodeToString(SourceText(case.input)))
    }

    @Serializable private data class SourceText(val text: String)
}

@Serializable
data class GenerationSettings(
    @SerialName("context_tokens") val contextTokens: Int = 2048,
    @SerialName("max_output_tokens") val maxOutputTokens: Int = 256,
    @SerialName("top_k") val topK: Int = 1,
    @SerialName("top_p") val topP: Double = 1.0,
    val temperature: Double = 0.0,
    val seed: Int = 42,
    @SerialName("enable_thinking") val enableThinking: Boolean = false,
) {
    init { require(contextTokens > maxOutputTokens && maxOutputTokens > 0) }
}

@Serializable
data class EvaluationResult(
    @SerialName("case_id") val caseId: String,
    val status: String = "pending",
    val output: String? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    val error: String? = null,
    val metrics: InferenceMetrics? = null,
    val review: JsonObject? = null,
)

@Serializable
data class InferenceMetrics(
    @SerialName("generation_ms") val generationMs: Double,
    @SerialName("time_to_first_token_ms") val firstTextMs: Double?,
    @SerialName("first_token_scope") val firstTokenScope: String = "first_visible_text_callback_excludes_load_and_queue",
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
    @SerialName("process_pss_before_kib") val processPssBeforeKib: Int? = null,
    @SerialName("process_pss_after_kib") val processPssAfterKib: Int? = null,
    @SerialName("memory_scope") val memoryScope: String = "process_pss_samples_not_peak_and_not_total_gpu_memory",
)

data class GenerationOutput(val text: String, val metrics: InferenceMetrics, val finishReason: String = "unknown")

interface InferenceEngine {
    suspend fun load(file: File, backend: String, settings: GenerationSettings): Double
    suspend fun generate(prompt: CapabilityPrompt, settings: GenerationSettings, onText: (String) -> Unit): GenerationOutput
    suspend fun close()
}

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun atomicWrite(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.writing")
    temp.outputStream().use { it.write(bytes); it.fd.sync() }
    java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
}
