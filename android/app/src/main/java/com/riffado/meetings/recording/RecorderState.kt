package com.riffado.meetings.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live state of the active recording, published by [RecordingService]. */
data class LiveRecording(
    val recordingId: String? = null,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
) {
    val isActive: Boolean get() = recordingId != null
}

object RecorderState {
    private val state = MutableStateFlow(LiveRecording())
    val live: StateFlow<LiveRecording> = state.asStateFlow()

    internal fun set(value: LiveRecording) {
        state.value = value
    }
}
