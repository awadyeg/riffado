package com.riffado.meetings.ai

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Google Gemini API client for audio transcription with speaker diarization.
 * Uses the Gemini API v1beta endpoint.
 */
object GeminiTranscriber {
    
    private const val TAG = "GeminiTranscriber"
    private const val MAX_INLINE_AUDIO_MB = 10 // Inline audio limit
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Serializable
    private data class GenerateContentRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null,
    )
    
    @Serializable
    private data class Content(
        val parts: List<Part>,
    )
    
    @Serializable
    private data class Part(
        val text: String? = null,
        val inlineData: InlineData? = null,
    )
    
    @Serializable
    private data class InlineData(
        val mimeType: String,
        val data: String, // base64-encoded
    )
    
    @Serializable
    private data class GenerationConfig(
        val temperature: Double = 0.1,
        val maxOutputTokens: Int = 8192,
    )
    
    @Serializable
    private data class GenerateContentResponse(
        val candidates: List<Candidate>? = null,
        val error: ErrorResponse? = null,
    )
    
    @Serializable
    private data class Candidate(
        val content: Content,
    )
    
    @Serializable
    private data class ErrorResponse(
        val code: Int,
        val message: String,
        val status: String,
    )
    
    /**
     * Transcribe audio file using Gemini API with speaker diarization.
     * 
     * @param baseUrl API base URL (e.g., https://generativelanguage.googleapis.com/v1beta)
     * @param apiKey Gemini API key
     * @param model Model name (e.g., gemini-2.0-flash-exp)
     * @param audioFile Audio file to transcribe
     * @param language Optional language hint
     * @return Diarized transcript in "Speaker N: text" format
     * @throws TranscriptionException on API errors including quota exhaustion
     */
    suspend fun transcribe(
        baseUrl: String,
        apiKey: String,
        model: String,
        audioFile: File,
        language: String = "",
    ): String = withContext(Dispatchers.IO) {
        val audioSizeMb = audioFile.length() / (1024.0 * 1024.0)
        
        if (audioSizeMb > MAX_INLINE_AUDIO_MB) {
            throw TranscriptionException("Audio file too large for Gemini inline API (${audioSizeMb.toInt()}MB > ${MAX_INLINE_AUDIO_MB}MB)")
        }
        
        // Prepare prompt for diarized transcription
        val prompt = buildPrompt(language)
        
        // Read and encode audio
        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
        
        // Build request
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(
                            mimeType = "audio/mpeg", // M4A is MPEG-4 audio
                            data = base64Audio,
                        )),
                    ),
                ),
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1,
                maxOutputTokens = 8192,
            ),
        )
        
        val requestBody = json.encodeToString(GenerateContentRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())
        
        val url = "${baseUrl.trimEnd('/')}/models/${model}:generateContent"
        val httpRequest = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("x-goog-api-key", apiKey)
            .build()
        
        Log.d(TAG, "Sending Gemini transcription request: $url (${audioSizeMb.toInt()}MB)")
        
        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string() ?: ""
        
        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini API error ${response.code}: $responseBody")
            
            // Check for quota exhaustion
            if (response.code == 429 || responseBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                throw QuotaExhaustedException("Gemini quota exhausted")
            }
            
            throw TranscriptionException("Gemini API error ${response.code}: ${response.message}")
        }
        
        try {
            val result = json.decodeFromString<GenerateContentResponse>(responseBody)
            
            result.error?.let { error ->
                if (error.status == "RESOURCE_EXHAUSTED") {
                    throw QuotaExhaustedException("Gemini quota exhausted: ${error.message}")
                }
                throw TranscriptionException("Gemini error: ${error.message}")
            }
            
            val transcript = result.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: throw TranscriptionException("Empty response from Gemini")
            
            Log.d(TAG, "Gemini transcription complete: ${transcript.length} chars")
            return@withContext transcript.trim()
        } catch (e: Exception) {
            if (e is QuotaExhaustedException || e is TranscriptionException) throw e
            Log.e(TAG, "Failed to parse Gemini response", e)
            throw TranscriptionException("Failed to parse Gemini response: ${e.message}")
        }
    }
    
    private fun buildPrompt(language: String): String {
        val langHint = if (language.isNotBlank()) {
            " The audio may contain $language language."
        } else {
            ""
        }
        
        return """
            Transcribe this audio recording with speaker diarization.
            
            Format the output as:
            Speaker 1: [first utterance]
            Speaker 2: [response]
            Speaker 1: [next utterance]
            ...
            
            Important instructions:
            - Separate speakers consistently throughout the recording
            - Use "Speaker 1", "Speaker 2", etc. as labels
            - Preserve the exact words spoken, including mixed languages
            - Each speaker's turn should be on a new line
            - Do not add timestamps or additional formatting
            - Transcribe everything verbatim without summarizing$langHint
        """.trimIndent()
    }
}
