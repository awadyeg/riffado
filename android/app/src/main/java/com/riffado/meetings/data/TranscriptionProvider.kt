package com.riffado.meetings.data

import kotlinx.serialization.Serializable

/**
 * Single transcription provider configuration with fallback support.
 */
@Serializable
data class TranscriptionProvider(
    val id: String, // unique identifier (e.g., "elevenlabs", "munsit", "gemini")
    val name: String, // display name
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = true,
    val order: Int = 0, // position in fallback chain (lower = higher priority)
    val exhaustedUntil: Long? = null, // timestamp when quota will reset, null if not exhausted
    val billingResetDay: Int = 1, // day of month when quota resets (1-28)
) {
    val isExhausted: Boolean
        get() = exhaustedUntil != null && System.currentTimeMillis() < exhaustedUntil
    
    val isAvailable: Boolean
        get() = enabled && !isExhausted && apiKey.isNotBlank()
}

/**
 * Provider types for specialized handling.
 */
enum class ProviderType {
    OPENAI_COMPATIBLE,
    ELEVENLABS,
    SARVAM,
    MUNSIT,
    GEMINI,
}

/**
 * Provider metadata and capabilities.
 */
object ProviderRegistry {
    
    fun getProviderType(baseUrl: String): ProviderType = when {
        baseUrl.contains("elevenlabs.io") -> ProviderType.ELEVENLABS
        baseUrl.contains("sarvam.ai") -> ProviderType.SARVAM
        baseUrl.contains("munsit.com") || baseUrl.contains("faseeh.ai") -> ProviderType.MUNSIT
        baseUrl.contains("generativelanguage.googleapis.com") -> ProviderType.GEMINI
        else -> ProviderType.OPENAI_COMPATIBLE
    }
    
    fun supportsDiarization(baseUrl: String): Boolean {
        val type = getProviderType(baseUrl)
        return type == ProviderType.ELEVENLABS || 
               type == ProviderType.MUNSIT || 
               type == ProviderType.GEMINI
    }
    
    fun getDefaultProviders(): List<TranscriptionProvider> = listOf(
        TranscriptionProvider(
            id = "elevenlabs",
            name = "ElevenLabs",
            baseUrl = "https://api.elevenlabs.io/v1",
            apiKey = "",
            model = "scribe_v2",
            enabled = false,
            order = 0,
        ),
        TranscriptionProvider(
            id = "munsit",
            name = "Munsit",
            baseUrl = "https://api.munsit.com/api/v1",
            apiKey = "",
            model = "munsit-en-ar",
            enabled = false,
            order = 1,
        ),
        TranscriptionProvider(
            id = "gemini",
            name = "Gemini (مجاني)",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiKey = "",
            model = "gemini-2.0-flash-exp", // Best free model as of late 2024
            enabled = false,
            order = 2,
        ),
    )
}
