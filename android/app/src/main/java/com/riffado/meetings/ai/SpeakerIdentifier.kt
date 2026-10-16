package com.riffado.meetings.ai

import android.content.Context
import android.util.Log
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.Speakers
import com.riffado.meetings.data.VoiceProfile
import java.io.File
import kotlin.math.sqrt

/**
 * Automatic speaker identification using voice embeddings.
 * Matches diarized speakers against enrolled voice profiles.
 */
class SpeakerIdentifier(
    private val context: Context,
    private val embeddingExtractor: SpeakerEmbeddingExtractor,
) {
    private val TAG = "SpeakerIdentifier"
    
    /**
     * Identify speakers in a recording against the user's voice profile.
     * Returns a map of speaker labels to display names.
     * 
     * @param recording Recording with transcript containing speaker labels
     * @param audioFile Audio file for the recording
     * @param userProfile User's enrolled voice profile
     * @param callContactName Contact name for automatic naming in 1:1 calls
     * @param threshold Similarity threshold (0.0 to 1.0)
     * @param existingNames Existing manual speaker renames (never overwritten)
     * @return Map of speaker label → display name
     */
    suspend fun identifySpeakers(
        recording: Recording,
        audioFile: File,
        userProfile: VoiceProfile?,
        callContactName: String?,
        threshold: Float,
        existingNames: Map<String, String>,
    ): Map<String, String> {
        if (recording.transcript.isNullOrBlank()) {
            Log.d(TAG, "No transcript for ${recording.id}, skipping speaker identification")
            return existingNames
        }
        
        // Parse speaker turns from transcript
        val turns = Speakers.parse(recording.transcript)
        val speakerLabels = turns.mapNotNull { it.speaker }.distinct()
        
        if (speakerLabels.isEmpty()) {
            Log.d(TAG, "No speaker labels in transcript for ${recording.id}")
            return existingNames
        }
        
        Log.d(TAG, "Identifying ${speakerLabels.size} speakers in ${recording.id}")
        
        val result = existingNames.toMutableMap()
        
        // Extract embeddings for each speaker
        val speakerEmbeddings = mutableMapOf<String, FloatArray>()
        for (label in speakerLabels) {
            // Skip if already manually named
            if (existingNames.containsKey(label)) {
                Log.d(TAG, "Skipping $label (manually named as '${existingNames[label]}')")
                continue
            }
            
            // Extract audio segments for this speaker
            val speakerTurns = turns.filter { it.speaker == label }
            if (speakerTurns.isEmpty()) continue
            
            try {
                val embedding = embeddingExtractor.extractSpeakerEmbedding(audioFile, speakerTurns)
                speakerEmbeddings[label] = embedding
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract embedding for $label", e)
            }
        }
        
        // Match against user profile
        if (userProfile != null) {
            val matches = speakerEmbeddings.mapValues { (label, embedding) ->
                val similarity = cosineSimilarity(userProfile.embedding, embedding)
                Log.d(TAG, "$label vs '${userProfile.displayName}': similarity = $similarity (threshold = $threshold)")
                similarity
            }
            
            // Best match above threshold gets user's name
            val bestMatch = matches.maxByOrNull { it.value }
            if (bestMatch != null && bestMatch.value >= threshold) {
                result[bestMatch.key] = userProfile.displayName
                Log.d(TAG, "Matched ${bestMatch.key} to '${userProfile.displayName}' (similarity = ${bestMatch.value})")
            }
        }
        
        // For 1:1 calls with exactly one other speaker, auto-name with contact
        if (callContactName != null) {
            val unnamedSpeakers = speakerLabels.filter { !result.containsKey(it) }
            if (unnamedSpeakers.size == 1) {
                result[unnamedSpeakers[0]] = callContactName
                Log.d(TAG, "Auto-named ${unnamedSpeakers[0]} to '$callContactName' (1:1 call)")
            }
        }
        
        return result
    }
    
    /**
     * Compute cosine similarity between two embeddings.
     * Returns value in [0, 1] where 1 = identical.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embeddings must have same dimension" }
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator == 0f) return 0f
        
        return (dotProduct / denominator).coerceIn(0f, 1f)
    }
}

/**
 * Interface for extracting speaker embeddings from audio.
 * 
 * Production implementation uses sherpa-onnx with ECAPA-TDNN or similar model.
 * Test/stub implementation can return dummy embeddings.
 */
interface SpeakerEmbeddingExtractor {
    /**
     * Extract a speaker embedding from audio segments.
     * 
     * @param audioFile Audio file (16kHz mono WAV expected)
     * @param turns Speaker turns to extract audio from
     * @return 192-dimensional embedding vector
     */
    suspend fun extractSpeakerEmbedding(
        audioFile: File,
        turns: List<Speakers.Turn>,
    ): FloatArray
    
    /**
     * Extract embedding from continuous audio segment.
     * 
     * @param audioFile Audio file
     * @param startMs Start time in milliseconds
     * @param endMs End time in milliseconds
     * @return 192-dimensional embedding vector
     */
    suspend fun extractEmbedding(
        audioFile: File,
        startMs: Long,
        endMs: Long,
    ): FloatArray
}

/**
 * Stub implementation for testing/development.
 * Returns deterministic but meaningless embeddings.
 * 
 * TODO: Replace with SherpaOnnxEmbeddingExtractor using actual model.
 */
class StubEmbeddingExtractor : SpeakerEmbeddingExtractor {
    override suspend fun extractSpeakerEmbedding(audioFile: File, turns: List<Speakers.Turn>): FloatArray {
        // Generate deterministic embedding based on turns
        val hash = turns.hashCode()
        return FloatArray(192) { i -> ((hash + i) % 256) / 255f }
    }
    
    override suspend fun extractEmbedding(audioFile: File, startMs: Long, endMs: Long): FloatArray {
        // Generate deterministic embedding based on time range
        val hash = (audioFile.path.hashCode() + startMs + endMs).toInt()
        return FloatArray(192) { i -> ((hash + i) % 256) / 255f }
    }
}
