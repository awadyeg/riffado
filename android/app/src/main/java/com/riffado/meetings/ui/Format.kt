package com.riffado.meetings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.riffado.meetings.R
import com.riffado.meetings.data.RecordingStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}

fun formatDate(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

@Composable
fun statusLabel(status: RecordingStatus): String = stringResource(
    when (status) {
        RecordingStatus.RECORDING -> R.string.status_recording
        RecordingStatus.RECORDED -> R.string.status_recorded
        RecordingStatus.QUEUED -> R.string.status_queued
        RecordingStatus.TRANSCRIBING -> R.string.status_transcribing
        RecordingStatus.SUMMARIZING -> R.string.status_summarizing
        RecordingStatus.DONE -> R.string.status_done
        RecordingStatus.FAILED -> R.string.status_failed
        RecordingStatus.INTERRUPTED -> R.string.status_interrupted
    },
)

val RecordingStatus.isBusy: Boolean
    get() = this == RecordingStatus.QUEUED ||
        this == RecordingStatus.TRANSCRIBING ||
        this == RecordingStatus.SUMMARIZING
