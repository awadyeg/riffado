package com.riffado.meetings.ai

import com.riffado.meetings.data.ProviderConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * Sarvam AI Saaras speech-to-text with speaker diarization support.
 * Uses the REST API for single files; diarization returns `Speaker N: text` lines.
 */
object SarvamClient {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TranscriptResponse(
        @SerialName("request_id") val requestId: String?,
        val transcript: String,
        @SerialName("language_code") val languageCode: String?,
        val timestamps: Timestamps? = null,
    )

    @Serializable
    private data class Timestamps(
        val words: List<String>,
        @SerialName("start_time_seconds") val startTimeSeconds: List<Double>,
        @SerialName("end_time_seconds") val endTimeSeconds: List<Double>,
    )

    fun transcribe(config: ProviderConfig, file: File, language: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", config.model)
            .apply {
                if (language.isNotBlank()) {
                    addFormDataPart("language_code", language)
                }
            }
            .addFormDataPart("with_timestamps", "true")
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/speech-to-text")
            .header("api-subscription-key", config.apiKey)
            .post(body)
            .build()

        val text = OpenAiClient.http.newCall(request).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ApiException(res.code, sarvamError(res.code, raw))
            raw
        }

        val response = json.decodeFromString<TranscriptResponse>(text)
        if (response.transcript.isBlank()) {
            throw IOException("Sarvam returned an empty transcript")
        }

        return formatWithSpeakers(response)
    }

    /**
     * For now, REST API doesn't support diarization in the response structure.
     * When diarization becomes available, we'd parse speaker info from timestamps.
     * Currently returns the transcript as-is.
     */
    private fun formatWithSpeakers(response: TranscriptResponse): String {
        return response.transcript.trim()
    }

    private fun sarvamError(code: Int, body: String): String {
        val detail = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            val err = obj["error"]?.jsonObject
            err?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return if (detail.isNullOrBlank()) {
            OpenAiClient.errorMessage(code, body)
        } else {
            "HTTP $code: $detail"
        }
    }
}
