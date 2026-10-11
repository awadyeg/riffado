package com.riffado.meetings.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.ai.ProcessingWorker
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.recording.RecorderState
import com.riffado.meetings.recording.RecordingService
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeetingsApp

    val recordings = app.recordings.recordings
    val settings = app.settings.settings
    val live = RecorderState.live

    fun startRecording() = RecordingService.send(app, RecordingService.ACTION_START)
    fun pauseRecording() = RecordingService.send(app, RecordingService.ACTION_PAUSE)
    fun resumeRecording() = RecordingService.send(app, RecordingService.ACTION_RESUME)
    fun stopRecording() = RecordingService.send(app, RecordingService.ACTION_STOP)

    fun process(id: String, summaryOnly: Boolean) {
        viewModelScope.launch { ProcessingWorker.enqueue(app, id, summaryOnly) }
    }

    fun rename(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            app.recordings.update(id) { it.copy(title = trimmed, titleIsDefault = false) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { app.recordings.delete(id) }
    }

    fun saveSettings(value: AppSettings) = app.settings.save(value)

    fun segmentPaths(id: String): List<String> =
        app.recordings.get(id)?.let { rec -> app.recordings.segmentFiles(rec).map { it.absolutePath } }
            ?: emptyList()
}
