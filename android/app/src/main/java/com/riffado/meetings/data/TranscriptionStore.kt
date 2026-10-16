package com.riffado.meetings.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores transcription provider settings with fallback chain.
 * Migrates from old single-provider model on first access.
 */
class TranscriptionStore(context: Context, private val legacySettings: SettingsStore) {
    private val prefs = context.getSharedPreferences("transcription_providers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    
    private val state = MutableStateFlow(read())
    val settings: StateFlow<TranscriptionSettings> = state.asStateFlow()
    
    fun save(value: TranscriptionSettings) {
        prefs.edit {
            putString(K_PROVIDERS, json.encodeToString(value.providers))
            putString(K_ORDER, json.encodeToString(value.providerOrder))
        }
        state.value = value
    }
    
    /**
     * Mark a provider as exhausted until the given timestamp.
     */
    fun markExhausted(providerId: String, until: Long) {
        save(state.value.markExhausted(providerId, until))
    }
    
    /**
     * Mark a provider as recovered (quota refreshed).
     */
    fun markRecovered(providerId: String) {
        save(state.value.markRecovered(providerId))
    }
    
    /**
     * Update provider order (for drag-and-drop reordering in UI).
     */
    fun updateOrder(newOrder: List<String>) {
        save(state.value.copy(providerOrder = newOrder))
    }
    
    /**
     * Update a single provider's configuration.
     */
    fun updateProvider(id: String, update: (TranscriptionProviderConfig) -> TranscriptionProviderConfig) {
        save(state.value.updateProvider(id, update))
    }
    
    private fun read(): TranscriptionSettings {
        val providersJson = prefs.getString(K_PROVIDERS, null)
        val orderJson = prefs.getString(K_ORDER, null)
        
        // Migrate from legacy single-provider settings if needed
        if (providersJson == null || orderJson == null) {
            return migrateFromLegacy()
        }
        
        val providers = json.decodeFromString<List<TranscriptionProviderConfig>>(providersJson)
        val order = json.decodeFromString<List<String>>(orderJson)
        
        // Migrate old Gemini model names to current stable model
        val migratedProviders = providers.map { provider ->
            if (provider.id == "gemini" && 
                (provider.model == "gemini-3.6-flash" || 
                 provider.model == "gemini-1.5-flash-8b" ||
                 provider.model.contains("1.5-flash"))) {
                provider.copy(model = SettingsStore.GEMINI_MODEL)
            } else {
                provider
            }
        }
        
        val settings = TranscriptionSettings(migratedProviders, order)
        
        // Persist migrated settings if any changes were made
        if (migratedProviders != providers) {
            save(settings)
        }
        
        return settings
    }
    
    /**
     * Migrate from old single-provider SettingsStore to multi-provider model.
     * Preserves existing API keys and creates default fallback chain.
     */
    private fun migrateFromLegacy(): TranscriptionSettings {
        val legacy = legacySettings.settings.value.transcription
        
        // Determine which provider the user was using
        val activeProviderId = when {
            legacy.baseUrl.contains("elevenlabs.io") -> "elevenlabs"
            legacy.baseUrl.contains("sarvam.ai") -> "sarvam"
            legacy.baseUrl.contains("munsit.com") || legacy.baseUrl.contains("faseeh.ai") -> "munsit"
            legacy.baseUrl.contains("generativelanguage.googleapis.com") -> "gemini"
            legacy.baseUrl.contains("groq.com") -> "groq"
            else -> "groq" // default
        }
        
        val providers = listOf(
            TranscriptionProviderConfig(
                id = "elevenlabs",
                name = "ElevenLabs",
                baseUrl = SettingsStore.ELEVENLABS_URL,
                apiKey = if (activeProviderId == "elevenlabs") legacy.apiKey else "",
                model = SettingsStore.ELEVENLABS_MODEL,
                enabled = activeProviderId == "elevenlabs",
                billingResetDay = 1,
            ),
            TranscriptionProviderConfig(
                id = "munsit",
                name = "Munsit",
                baseUrl = SettingsStore.MUNSIT_URL,
                apiKey = if (activeProviderId == "munsit") legacy.apiKey else "",
                model = SettingsStore.MUNSIT_EN_AR_MODEL,
                enabled = activeProviderId == "munsit",
                billingResetDay = 1,
            ),
            TranscriptionProviderConfig(
                id = "gemini",
                name = "Gemini",
                baseUrl = SettingsStore.GEMINI_URL,
                apiKey = if (activeProviderId == "gemini") legacy.apiKey else "",
                model = SettingsStore.GEMINI_MODEL,
                enabled = activeProviderId == "gemini",
                billingResetDay = 1,
            ),
            TranscriptionProviderConfig(
                id = "groq",
                name = "Groq Whisper",
                baseUrl = SettingsStore.GROQ_URL,
                apiKey = if (activeProviderId == "groq") legacy.apiKey else "",
                model = SettingsStore.GROQ_TRANSCRIPTION_MODEL,
                enabled = activeProviderId == "groq",
                billingResetDay = 1,
            ),
            TranscriptionProviderConfig(
                id = "sarvam",
                name = "Sarvam",
                baseUrl = SettingsStore.SARVAM_URL,
                apiKey = if (activeProviderId == "sarvam") legacy.apiKey else "",
                model = SettingsStore.SARVAM_MODEL,
                enabled = activeProviderId == "sarvam",
                billingResetDay = 1,
            ),
        )
        
        // Default fallback order: ElevenLabs -> Munsit -> Gemini -> Groq -> Sarvam
        val order = listOf("elevenlabs", "munsit", "gemini", "groq", "sarvam")
        
        val settings = TranscriptionSettings(providers, order)
        save(settings) // Persist migrated settings
        return settings
    }
    
    companion object {
        private const val K_PROVIDERS = "providers"
        private const val K_ORDER = "order"
    }
}
