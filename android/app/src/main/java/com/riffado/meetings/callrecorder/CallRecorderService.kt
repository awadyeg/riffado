package com.riffado.meetings.callrecorder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.riffado.meetings.MainActivity
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.R
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import com.riffado.meetings.ai.ProcessingWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CallRecorderService : Service() {

    private var recorder: MediaRecorder? = null
    private var recordingId: String? = null
    private var startTime: Long = 0
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        CallRecorderDiagnostics.log(this, "startRecording() called")
        
        if (recorder != null) {
            CallRecorderDiagnostics.log(this, "Already recording, ignoring")
            return
        }

        val app = application as MeetingsApp
        val callSettings = app.callRecordings.settings.value
        
        CallRecorderDiagnostics.log(this, "Call recording enabled: ${callSettings.enabled}")
        
        if (!callSettings.enabled) {
            CallRecorderDiagnostics.log(this, "Call recording disabled in settings, stopping service")
            stopSelf()
            return
        }

        val id = UUID.randomUUID().toString()
        recordingId = id
        startTime = System.currentTimeMillis()

        createNotificationChannel()
        
        try {
            // Use MICROPHONE type only - PHONE_CALL requires special permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                CallRecorderDiagnostics.log(this, "Foreground service started with MICROPHONE type")
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
                CallRecorderDiagnostics.log(this, "Foreground service started (legacy)")
            }
        } catch (e: Exception) {
            CallRecorderDiagnostics.log(this, "ERROR starting foreground: ${e.javaClass.simpleName}: ${e.message}")
            stopSelf()
            return
        }

        val audioSource = callSettings.audioSource
        val audioSourceName = when (audioSource) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
            MediaRecorder.AudioSource.MIC -> "MIC"
            else -> "UNKNOWN($audioSource)"
        }
        CallRecorderDiagnostics.log(this, "Audio source: $audioSourceName")
        
        val outputFile = File(filesDir, "recordings/$id-0.m4a").apply { parentFile?.mkdirs() }

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                setOutputFile(outputFile.path)
                prepare()
                start()
            }

            CallRecorderDiagnostics.log(this, "MediaRecorder started successfully: $id")

            scope.launch {
                delay(60000)
                if (recorder != null) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
        } catch (e: IllegalStateException) {
            CallRecorderDiagnostics.log(this, "ERROR: Mic in use: ${e.message}")
            savePlaceholder(id, "Microphone in use by another app")
            stopSelf()
        } catch (e: Exception) {
            CallRecorderDiagnostics.log(this, "ERROR: MediaRecorder failed: ${e.javaClass.simpleName}: ${e.message}")
            savePlaceholder(id, "Recording failed: ${e.message}")
            stopSelf()
        }
    }

    private fun savePlaceholder(id: String, errorMsg: String) {
        val app = application as MeetingsApp
        val recording = Recording(
            id = id,
            title = getString(R.string.call_recording_unknown),
            titleIsDefault = true,
            createdAt = startTime,
            durationMs = 0,
            status = RecordingStatus.FAILED,
            segments = emptyList(),
            transcript = null,
            summary = null,
            speakerNames = emptyMap(),
            error = errorMsg,
            tags = setOf("call"),
        )
        scope.launch {
            app.recordings.upsert(recording)
        }
    }

    private fun stopRecording() {
        val id = recordingId ?: return
        val duration = System.currentTimeMillis() - startTime

        val outputFile = File(filesDir, "recordings/$id-0.m4a")

        try {
            recorder?.apply {
                try {
                    stop()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaRecorder stop failed, preserving partial file", e)
                }
                release()
            }
            recorder = null

            val app = application as MeetingsApp
            
            // Resolve call participant info (number and contact name)
            val (phoneNumber, contactName) = CallParticipantResolver.resolveCallParticipant(this, startTime)
            
            // Determine title: contact name, phone number, or fallback
            val title = when {
                !contactName.isNullOrBlank() -> contactName
                !phoneNumber.isNullOrBlank() -> phoneNumber
                else -> getString(R.string.call_recording_unknown)
            }
            
            val hasAudio = outputFile.exists() && outputFile.length() > 0
            val recording = Recording(
                id = id,
                title = title,
                titleIsDefault = contactName == null && phoneNumber == null,
                createdAt = startTime,
                durationMs = duration,
                status = if (hasAudio) RecordingStatus.RECORDED else RecordingStatus.FAILED,
                segments = if (hasAudio) listOf("$id-0.m4a") else emptyList(),
                transcript = null,
                summary = null,
                speakerNames = emptyMap(),
                error = if (!hasAudio) "Recording interrupted" else null,
                tags = setOf("call"),
                callNumber = phoneNumber,
                callContactName = contactName,
            )

            scope.launch {
                app.recordings.upsert(recording)
                
                if (hasAudio && app.callRecordings.settings.value.autoProcess && app.settings.settings.value.canTranscribe) {
                    ProcessingWorker.enqueue(app, id, false)
                }
            }

            CallRecorderDiagnostics.log(this, "Call recording saved: $id (contact: ${contactName ?: "none"}, number: ${phoneNumber ?: "none"})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop call recording", e)
            if (outputFile.exists() && outputFile.length() > 0) {
                savePlaceholder(id, "Partial recording saved")
            }
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(): android.app.Notification {
        val duration = (System.currentTimeMillis() - startTime) / 1000
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.call_recording_active))
            .setContentText(getString(R.string.call_recording_duration, formatDuration(duration)))
            .setContentIntent(intent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_call_recording),
            NotificationManager.IMPORTANCE_LOW,
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        recorder?.release()
        recorder = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRecorderService"
        private const val CHANNEL_ID = "call_recording"
        private const val NOTIFICATION_ID = 3001
        
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
    }
}
