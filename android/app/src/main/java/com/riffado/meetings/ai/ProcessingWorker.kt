package com.riffado.meetings.ai

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.riffado.meetings.MainActivity
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.R
import com.riffado.meetings.data.AppSettings
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.data.Speakers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Transcribes every segment of a recording, then generates meeting minutes. */
class ProcessingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app = context.applicationContext as MeetingsApp

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val summaryOnly = inputData.getBoolean(KEY_SUMMARY_ONLY, false)
        val recording = app.recordings.get(id) ?: return Result.failure()
        val settings = app.settings.settings.value

        runCatching { setForeground(foregroundInfo(recording.title)) }
            .onFailure { Log.w(TAG, "Could not promote worker to foreground", it) }

        return try {
            var transcript = recording.transcript
            if (!summaryOnly || transcript.isNullOrBlank()) {
                if (!settings.canTranscribe) throw IllegalStateException("Transcription provider is not configured")
                app.recordings.update(id) { it.copy(status = RecordingStatus.TRANSCRIBING, error = null) }
                val files = app.recordings.segmentFiles(recording).filter { it.exists() && it.length() > 0 }
                transcript = withContext(Dispatchers.IO) {
                    when {
                        settings.usesElevenLabs -> transcribeWithSpeakers(id, files, settings)
                        settings.usesSarvam -> transcribeSarvam(files, settings)
                        settings.usesMunsit -> transcribeMunsit(files, settings)
                        else -> files.map { OpenAiClient.transcribe(settings.transcription, it, settings.language) }
                            .filter { it.isNotBlank() }
                            .joinToString("\n\n")
                    }
                }
                app.recordings.update(id) { it.copy(transcript = transcript, speakerNames = emptyMap()) }
            }

            if (settings.canSummarize && !transcript.isNullOrBlank()) {
                app.recordings.update(id) { it.copy(status = RecordingStatus.SUMMARIZING) }
                val summary = withContext(Dispatchers.IO) {
                    val names = app.recordings.get(id)?.speakerNames ?: emptyMap()
                    OpenAiClient.chat(
                        settings.effectiveSummary,
                        Prompts.MEETING_SYSTEM,
                        Speakers.applyNames(transcript, names),
                    )
                }
                val generatedTitle = Prompts.titleFrom(summary)
                app.recordings.update(id) {
                    it.copy(
                        summary = summary,
                        title = if (it.titleIsDefault && generatedTitle != null) generatedTitle else it.title,
                        titleIsDefault = it.titleIsDefault && generatedTitle == null,
                    )
                }
            }

            val done = app.recordings.update(id) { it.copy(status = RecordingStatus.DONE, error = null) }
            notifyResult(id.hashCode(), applicationContext.getString(R.string.notif_done, done?.title ?: ""))
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed for $id", e)
            val retryable = (e is ApiException && e.retryable) || (e is IOException && e !is ApiException)
            if (retryable && runAttemptCount < MAX_ATTEMPTS) {
                app.recordings.update(id) { it.copy(status = RecordingStatus.QUEUED, error = e.message) }
                Result.retry()
            } else {
                val failed = app.recordings.update(id) {
                    it.copy(status = RecordingStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
                }
                notifyResult(id.hashCode(), applicationContext.getString(R.string.notif_failed, failed?.title ?: ""))
                Result.failure()
            }
        }
    }

    /** ElevenLabs numbers speakers per upload, so segments are joined first to keep speaker numbers consistent. */
    private fun transcribeWithSpeakers(id: String, files: List<File>, settings: AppSettings): String {
        if (files.size <= 1) {
            return files.firstOrNull()?.let { ElevenLabsClient.transcribe(settings.transcription, it, settings.language) }
                ?: throw IllegalStateException("Recording has no audio")
        }
        val joined = File(applicationContext.cacheDir, "$id-joined.m4a")
        try {
            AudioConcat.concat(files, joined)
            return ElevenLabsClient.transcribe(settings.transcription, joined, settings.language)
        } finally {
            joined.delete()
        }
    }

    /** Sarvam transcribes each segment individually, then joins the results. */
    private fun transcribeSarvam(files: List<File>, settings: AppSettings): String {
        if (files.isEmpty()) throw IllegalStateException("Recording has no audio")
        return files.map { SarvamClient.transcribe(settings.transcription, it, settings.language) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    /** Munsit transcribes with diarization, each segment individually, then joins the results. */
    private fun transcribeMunsit(files: List<File>, settings: AppSettings): String {
        if (files.isEmpty()) throw IllegalStateException("Recording has no audio")
        return files.map { MunsitClient.transcribe(settings.transcription, it, settings.language) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(app.recordings.get(inputData.getString(KEY_ID) ?: "")?.title ?: "")

    private fun foregroundInfo(title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MeetingsApp.CHANNEL_PROCESSING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(applicationContext.getString(R.string.notif_processing, title))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, notification)
        }
    }

    private fun notifyResult(notificationId: Int, text: String) {
        val nm = NotificationManagerCompat.from(applicationContext)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, MeetingsApp.CHANNEL_PROCESSING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission revoked", e)
        }
    }

    companion object {
        private const val TAG = "ProcessingWorker"
        private const val KEY_ID = "id"
        private const val KEY_SUMMARY_ONLY = "summary_only"
        private const val PROGRESS_ID = 2001
        private const val MAX_ATTEMPTS = 3

        /** Queues processing for [id]; replaces any pending run for the same recording. */
        suspend fun enqueue(context: Context, id: String, summaryOnly: Boolean) {
            val app = context.applicationContext as MeetingsApp
            app.recordings.update(id) { it.copy(status = RecordingStatus.QUEUED, error = null) }
            val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
                .setInputData(workDataOf(KEY_ID to id, KEY_SUMMARY_ONLY to summaryOnly))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("process-$id", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
