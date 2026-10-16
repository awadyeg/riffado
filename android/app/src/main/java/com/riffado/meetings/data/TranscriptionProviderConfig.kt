package com.riffado.meetings.data

import kotlinx.serialization.Serializable

/**
 * Single transcription provider with fallback metadata.
 */
@Serializable
data class TranscriptionProviderConfig(
    val id: String, // unique: "elevenlabs", "munsit", "gemini", "groq", etc.
    val name: String, // display name
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = true,
    val billingResetDay: Int = 1, // day of month (1-28)
    val exhaustedUntil: Long? = null, // timestamp when quota resets, null if not exhausted
) {
    val isExhausted: Boolean
        get() = exhaustedUntil != null && System.currentTimeMillis() < exhaustedUntil
    
    val isAvailable: Boolean
        get() = enabled && !isExhausted && apiKey.isNotBlank()
    
    fun withExhausted(until: Long): TranscriptionProviderConfig =
        copy(exhaustedUntil = until)
    
    fun withRecovered(): TranscriptionProviderConfig =
        copy(exhaustedUntil = null)
}

/**
 * Multi-provider transcription settings with ordered fallback chain.
 */
@Serializable
data class TranscriptionSettings(
    val providers: List<TranscriptionProviderConfig>,
    val providerOrder: List<String>, // ordered list of provider IDs (first = highest priority)
) {
    /**
     * Get providers in fallback order, filtering to only available ones.
     */
    fun availableProvidersInOrder(): List<TranscriptionProviderConfig> {
        val providerMap = providers.associateBy { it.id }
        return providerOrder
            .mapNotNull { providerMap[it] }
            .filter { it.isAvailable }
    }
    
    /**
     * Find a provider by ID.
     */
    fun findProvider(id: String): TranscriptionProviderConfig? =
        providers.find { it.id == id }
    
    /**
     * Update a single provider in the list.
     */
    fun updateProvider(id: String, update: (TranscriptionProviderConfig) -> TranscriptionProviderConfig): TranscriptionSettings =
        copy(providers = providers.map { if (it.id == id) update(it) else it })
    
    /**
     * Mark a provider as exhausted until the given timestamp.
     */
    fun markExhausted(id: String, until: Long): TranscriptionSettings =
        updateProvider(id) { it.withExhausted(until) }
    
    /**
     * Mark a provider as recovered (quota refreshed).
     */
    fun markRecovered(id: String): TranscriptionSettings =
        updateProvider(id) { it.withRecovered() }
}
