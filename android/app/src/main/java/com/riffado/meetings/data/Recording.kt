package com.riffado.meetings.data

import kotlinx.serialization.Serializable

@Serializable
enum class RecordingStatus {
    RECORDING,
    RECORDED,
    QUEUED,
    TRANSCRIBING,
    SUMMARIZING,
    DONE,
    FAILED,
    INTERRUPTED,
}

/** A meeting recording. Audio may be split across several segment files to stay under provider upload limits. */
@Serializable
data class Recording(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    val segments: List<String> = emptyList(),
    val status: RecordingStatus = RecordingStatus.RECORDING,
    val transcript: String? = null,
    val summary: String? = null,
    val error: String? = null,
    val titleIsDefault: Boolean = true,
    /** Display names keyed by speaker number ("1", "2", ...) from a diarized transcript. */
    val speakerNames: Map<String, String> = emptyMap(),
    /** Tags for categorization (e.g., "call" for call recordings). */
    val tags: Set<String> = emptySet(),
) {
    val isCall: Boolean get() = "call" in tags
}
