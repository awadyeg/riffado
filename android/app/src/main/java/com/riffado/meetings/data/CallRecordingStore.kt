package com.riffado.meetings.data

import android.content.Context
import android.media.MediaRecorder
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CallRecordingSettings(
    val enabled: Boolean,
    val audioSource: Int,
    val autoProcess: Boolean,
    val speakerphoneHint: Boolean,
)

/** Settings for call recording, persisted in SharedPreferences. */
class CallRecordingStore(context: Context) {
    private val prefs = context.getSharedPreferences("call_recordings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<CallRecordingSettings> = state.asStateFlow()

    fun save(value: CallRecordingSettings) {
        prefs.edit {
            putBoolean(K_ENABLED, value.enabled)
            putInt(K_AUDIO_SOURCE, value.audioSource)
            putBoolean(K_AUTO_PROCESS, value.autoProcess)
            putBoolean(K_SPEAKERPHONE_HINT, value.speakerphoneHint)
        }
        state.value = read()
    }

    private fun read(): CallRecordingSettings = CallRecordingSettings(
        enabled = prefs.getBoolean(K_ENABLED, true),
        audioSource = prefs.getInt(K_AUDIO_SOURCE, MediaRecorder.AudioSource.VOICE_RECOGNITION),
        autoProcess = prefs.getBoolean(K_AUTO_PROCESS, true),
        speakerphoneHint = prefs.getBoolean(K_SPEAKERPHONE_HINT, true),
    )

    companion object {
        private const val K_ENABLED = "enabled"
        private const val K_AUDIO_SOURCE = "audio_source"
        private const val K_AUTO_PROCESS = "auto_process"
        private const val K_SPEAKERPHONE_HINT = "speakerphone_hint"
    }
}
