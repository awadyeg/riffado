package com.riffado.meetings.data

import kotlinx.serialization.Serializable

/**
 * User's voice profile for speaker identification.
 * Contains a speaker embedding computed from enrollment audio.
 */
@Serializable
data class VoiceProfile(
    val displayName: String = "Me",
    val embedding: FloatArray, // 192-dimensional speaker embedding
    val enrolledAt: Long = System.currentTimeMillis(),
    val audioSource: AudioSource,
) {
    @Serializable
    sealed class AudioSource {
        @Serializable
        data class Recording(val recordingId: String, val startMs: Long, val endMs: Long) : AudioSource()
        
        @Serializable
        data class Enrollment(val durationMs: Long) : AudioSource()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceProfile
        if (displayName != other.displayName) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (enrolledAt != other.enrolledAt) return false
        if (audioSource != other.audioSource) return false
        return true
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + enrolledAt.hashCode()
        result = 31 * result + audioSource.hashCode()
        return result
    }
}
