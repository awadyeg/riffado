package com.riffado.meetings.ai

import android.content.Context
import android.util.Log
import com.riffado.meetings.data.ProviderRegistry
import com.riffado.meetings.data.ProviderType
import com.riffado.meetings.data.TranscriptionProvider
import java.io.File
import java.util.Calendar

/**
 * Manages transcription provider fallback chain with automatic quota recovery.
 */
class TranscriptionCoordinator(
    private val context: Context,
    private val providers: List<TranscriptionProvider>,
    private val onProviderExhausted: (String, Long) -> Unit, // (providerId, exhaustedUntil)
    private val onTranscriptionComplete: (String, String) -> Unit, // (providerId, transcript)
) {
    
    private val TAG = "TranscriptionCoordinator"
    
    /**
     * Transcribe audio with automatic provider fallback.
     * Tries providers in order until one succeeds or all fail.
     * 
     * @param audioFile Audio file to transcribe
     * @param language Optional language hint
     * @param usedProviderId Optional provider ID that was previously used (for retry after exhaustion)
     * @return Pair of (transcript, providerId)
     * @throws TranscriptionException if all providers fail
     */
    suspend fun transcribe(
        audioFile: File,
        language: String = "",
        usedProviderId: String? = null,
    ): Pair<String, String> {
        val sortedProviders = providers
            .filter { it.isAvailable }
            .sortedBy { it.order }
        
        if (sortedProviders.isEmpty()) {
            throw TranscriptionException("No transcription providers configured")
        }
        
        // If retrying with a specific provider that's now available, try it first
        val orderedProviders = if (usedProviderId != null) {
            val preferred = sortedProviders.find { it.id == usedProviderId }
            if (preferred != null) {
                listOf(preferred) + sortedProviders.filter { it.id != usedProviderId }
            } else {
                sortedProviders
            }
        } else {
            sortedProviders
        }
        
        val errors = mutableListOf<String>()
        
        for (provider in orderedProviders) {
            try {
                Log.d(TAG, "Trying provider: ${provider.name} (${provider.id})")
                val transcript = transcribeWithProvider(provider, audioFile, language)
                
                Log.i(TAG, "Transcription successful with ${provider.name}")
                onTranscriptionComplete(provider.id, transcript)
                return Pair(transcript, provider.id)
                
            } catch (e: QuotaExhaustedException) {
                Log.w(TAG, "Provider ${provider.name} quota exhausted: ${e.message}")
                
                // Calculate exhaustion period until next billing cycle
                val exhaustedUntil = calculateExhaustionEnd(provider.billingResetDay)
                onProviderExhausted(provider.id, exhaustedUntil)
                
                errors.add("${provider.name}: Quota exhausted")
                
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
        provider: TranscriptionProvider,
        audioFile: File,
        language: String,
    ): String {
        val providerType = ProviderRegistry.getProviderType(provider.baseUrl)
        
        return when (providerType) {
            ProviderType.GEMINI -> {
                GeminiTranscriber.transcribe(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    model = provider.model,
                    audioFile = audioFile,
                    language = language,
                )
            }
            
            ProviderType.ELEVENLABS -> {
                ElevenLabsTranscriber.transcribe(
                    apiKey = provider.apiKey,
                    audioFile = audioFile,
                    language = language,
                )
            }
            
            ProviderType.MUNSIT -> {
                MunsitTranscriber.transcribe(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    model = provider.model,
                    audioFile = audioFile,
                )
            }
            
            ProviderType.SARVAM -> {
                SarvamTranscriber.transcribe(
                    apiKey = provider.apiKey,
                    audioFile = audioFile,
                    language = language,
                )
            }
            
            ProviderType.OPENAI_COMPATIBLE -> {
                val config = com.riffado.meetings.data.ProviderConfig(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    model = provider.model,
                )
                try {
                    OpenAiClient.transcribe(config, audioFile, language)
                } catch (e: ApiException) {
                    // Check if it's a quota error
                    if (QuotaErrorDetector.isQuotaExhausted(e.code, e.message ?: "", provider.baseUrl)) {
                        throw QuotaExhaustedException("API quota exhausted", e)
                    }
                    throw TranscriptionException("API error ${e.code}: ${e.message}", e)
                }
            }
        }
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
}
