package com.riffado.meetings.ai

import android.content.Context
import android.util.Log
import com.riffado.meetings.data.ProviderConfig
import com.riffado.meetings.data.TranscriptionProviderConfig
import com.riffado.meetings.data.TranscriptionSettings
import com.riffado.meetings.data.TranscriptionStore
import java.io.File
import java.util.Calendar

/**
 * Coordinates transcription across multiple providers with automatic fallback.
 * Handles quota exhaustion, recovery, and provider ordering.
 */
class TranscriptionCoordinator(
    private val context: Context,
    private val transcriptionStore: TranscriptionStore,
) {
    
    private val TAG = "TranscriptionCoordinator"
    
    /**
     * Transcribe audio with automatic provider fallback.
     * Tries providers in order until one succeeds or all fail.
     * 
     * @param audioFile Audio file to transcribe
     * @param language Optional language hint
     * @return Triple of (transcript, providerId, settings)
     * @throws TranscriptionException if all providers fail
     */
    suspend fun transcribe(
        audioFile: File,
        language: String = "",
    ): Triple<String, String, TranscriptionSettings> {
        // Check if any exhausted providers should be recovered
        val settings = checkAndRecoverProviders(transcriptionStore.settings.value)
        
        val availableProviders = settings.availableProvidersInOrder()
        
        if (availableProviders.isEmpty()) {
            throw TranscriptionException("No transcription providers configured or available")
        }
        
        val errors = mutableListOf<String>()
        
        for (provider in availableProviders) {
            try {
                Log.d(TAG, "Trying provider: ${provider.name} (${provider.id})")
                val transcript = transcribeWithProvider(provider, audioFile, language)
                
                Log.i(TAG, "Transcription successful with ${provider.name}")
                return Triple(transcript, provider.id, settings)
                
            } catch (e: QuotaExhaustedException) {
                Log.w(TAG, "Provider ${provider.name} quota exhausted: ${e.message}")
                
                // Calculate exhaustion period until next billing cycle
                val exhaustedUntil = calculateExhaustionEnd(provider.billingResetDay)
                transcriptionStore.markExhausted(provider.id, exhaustedUntil)
                
                errors.add("${provider.name}: Quota exhausted until ${formatDate(exhaustedUntil)}")
                
                // Continue to next provider
                continue
                
            } catch (e: TranscriptionException) {
                Log.e(TAG, "Provider ${provider.name} failed: ${e.message}")
                errors.add("${provider.name}: ${e.message}")
                
                // Continue to next provider
                continue
            }
        }
        
        // All providers failed
        val errorMessage = "All transcription providers failed:\n${errors.joinToString("\n")}"
        throw TranscriptionException(errorMessage)
    }
    
    /**
     * Transcribe using a specific provider.
     */
    private suspend fun transcribeWithProvider(
        provider: TranscriptionProviderConfig,
        audioFile: File,
        language: String,
    ): String {
        return when {
            provider.baseUrl.contains("generativelanguage.googleapis.com") -> {
                GeminiTranscriber.transcribe(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    model = provider.model,
                    audioFile = audioFile,
                    language = language,
                )
            }
            
            provider.baseUrl.contains("elevenlabs.io") -> {
                val config = ProviderConfig(provider.baseUrl, provider.apiKey, provider.model)
                try {
                    ElevenLabsClient.transcribe(config, audioFile, language)
                } catch (e: ApiException) {
                    handleApiException(e, provider)
                }
            }
            
            provider.baseUrl.contains("munsit.com") || provider.baseUrl.contains("faseeh.ai") -> {
                val config = ProviderConfig(provider.baseUrl, provider.apiKey, provider.model)
                try {
                    MunsitClient.transcribe(config, audioFile, language)
                } catch (e: ApiException) {
                    handleApiException(e, provider)
                }
            }
            
            provider.baseUrl.contains("sarvam.ai") -> {
                val config = ProviderConfig(provider.baseUrl, provider.apiKey, provider.model)
                try {
                    SarvamClient.transcribe(config, audioFile, language)
                } catch (e: ApiException) {
                    handleApiException(e, provider)
                }
            }
            
            else -> {
                // OpenAI-compatible (Groq, OpenAI, etc.)
                val config = ProviderConfig(provider.baseUrl, provider.apiKey, provider.model)
                try {
                    OpenAiClient.transcribe(config, audioFile, language)
                } catch (e: ApiException) {
                    handleApiException(e, provider)
                }
            }
        }
    }
    
    /**
     * Handle API exceptions and convert to quota exhaustion if applicable.
     */
    private fun handleApiException(e: ApiException, provider: TranscriptionProviderConfig): Nothing {
        if (QuotaErrorDetector.isQuotaExhausted(e.code, e.message ?: "", provider.baseUrl)) {
            throw QuotaExhaustedException("API quota exhausted", e)
        }
        throw TranscriptionException("API error ${e.code}: ${e.message}", e)
    }
    
    /**
     * Calculate when quota exhaustion ends based on billing reset day.
     * Returns timestamp of midnight on the reset day of next month.
     */
    private fun calculateExhaustionEnd(billingResetDay: Int): Long {
        val now = Calendar.getInstance()
        val exhaustionEnd = Calendar.getInstance()
        
        // Set to the billing reset day of current month
        exhaustionEnd.set(Calendar.DAY_OF_MONTH, billingResetDay.coerceIn(1, 28))
        exhaustionEnd.set(Calendar.HOUR_OF_DAY, 0)
        exhaustionEnd.set(Calendar.MINUTE, 0)
        exhaustionEnd.set(Calendar.SECOND, 0)
        exhaustionEnd.set(Calendar.MILLISECOND, 0)
        
        // If reset day already passed this month, move to next month
        if (exhaustionEnd.timeInMillis <= now.timeInMillis) {
            exhaustionEnd.add(Calendar.MONTH, 1)
        }
        
        return exhaustionEnd.timeInMillis
    }
    
    /**
     * Check all providers and recover any that have passed their reset date.
     * Returns updated settings if any changes were made.
     */
    fun checkAndRecoverProviders(settings: TranscriptionSettings): TranscriptionSettings {
        val now = System.currentTimeMillis()
        var updated = settings
        var anyRecovered = false
        
        for (provider in settings.providers) {
            if (provider.exhaustedUntil != null && now >= provider.exhaustedUntil) {
                Log.i(TAG, "Provider ${provider.name} quota recovered after reset date")
                updated = updated.markRecovered(provider.id)
                anyRecovered = true
            }
        }
        
        if (anyRecovered) {
            transcriptionStore.save(updated)
        }
        
        return updated
    }
    
    private fun formatDate(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    }
}
