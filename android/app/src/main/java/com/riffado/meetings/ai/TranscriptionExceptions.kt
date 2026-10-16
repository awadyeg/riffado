package com.riffado.meetings.ai

import java.io.IOException

/**
 * Base exception for transcription errors.
 */
open class TranscriptionException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Exception indicating provider quota or credits have been exhausted.
 * Triggers automatic fallback to next provider in chain.
 */
class QuotaExhaustedException(message: String, cause: Throwable? = null) : TranscriptionException(message, cause)

/**
 * Analyzes API responses to detect quota exhaustion.
 */
object QuotaErrorDetector {
    
    /**
     * Check if an HTTP response indicates quota exhaustion.
     * 
     * @param code HTTP status code
     * @param body Response body
     * @param baseUrl Provider base URL for type detection
     * @return true if quota exhausted, false otherwise
     */
    fun isQuotaExhausted(code: Int, body: String, baseUrl: String): Boolean {
        // ElevenLabs: 401/402/429 with quota_exceeded or insufficient credits
        if (baseUrl.contains("elevenlabs.io")) {
            if (code in listOf(401, 402, 429)) {
                return body.contains("quota_exceeded", ignoreCase = true) ||
                       body.contains("insufficient", ignoreCase = true) ||
                       body.contains("character limit", ignoreCase = true)
            }
        }
        
        // Munsit/Faseeh: credit error messages
        if (baseUrl.contains("munsit.com") || baseUrl.contains("faseeh.ai")) {
            return body.contains("credit", ignoreCase = true) ||
                   body.contains("insufficient balance", ignoreCase = true) ||
                   body.contains("quota", ignoreCase = true)
        }
        
        // Gemini: 429 with RESOURCE_EXHAUSTED
        if (baseUrl.contains("generativelanguage.googleapis.com")) {
            return code == 429 || body.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
        }
        
        // Generic 429 rate limiting
        return code == 429 && (
            body.contains("quota", ignoreCase = true) ||
            body.contains("rate limit", ignoreCase = true) ||
            body.contains("exceeded", ignoreCase = true)
        )
    }
}
