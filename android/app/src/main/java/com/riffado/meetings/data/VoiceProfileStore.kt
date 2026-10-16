package com.riffado.meetings.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stores the user's voice profile for automatic speaker identification.
 */
class VoiceProfileStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voice_profile", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _profile = MutableStateFlow<VoiceProfile?>(read())
    val profile: StateFlow<VoiceProfile?> = _profile.asStateFlow()
    
    /**
     * Similarity threshold for speaker identification (0.0 to 1.0).
     * Higher = more strict matching.
     */
    private val _threshold = MutableStateFlow(prefs.getFloat(K_THRESHOLD, DEFAULT_THRESHOLD))
    val threshold: StateFlow<Float> = _threshold.asStateFlow()
    
    fun save(profile: VoiceProfile) {
        prefs.edit().putString(K_PROFILE, json.encodeToString(profile)).apply()
        _profile.value = profile
    }
    
    fun delete() {
        prefs.edit().remove(K_PROFILE).apply()
        _profile.value = null
    }
    
    fun setThreshold(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(K_THRESHOLD, clamped).apply()
        _threshold.value = clamped
    }
    
    private fun read(): VoiceProfile? {
        val json = prefs.getString(K_PROFILE, null) ?: return null
        return try {
            this.json.decodeFromString<VoiceProfile>(json)
        } catch (e: Exception) {
            null
        }
    }
    
    companion object {
        private const val K_PROFILE = "profile"
        private const val K_THRESHOLD = "threshold"
        private const val DEFAULT_THRESHOLD = 0.5f
    }
}
