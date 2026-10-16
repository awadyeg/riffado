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
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return

        val app = application as MeetingsApp
        val callSettings = app.callRecordings.settings.value
        
        if (!callSettings.enabled) {
            stopSelf()
            return
        }

        val id = UUID.randomUUID().toString()
        recordingId = id
        startTime = System.currentTimeMillis()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val audioSource = callSettings.audioSource
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

            Log.d(TAG, "Call recording started: $id")

            scope.launch {
                delay(60000) // Update notification every minute
                if (recorder != null) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call recording", e)
            stopSelf()
        }
    }

    private fun stopRecording() {
        val id = recordingId ?: return
        val duration = System.currentTimeMillis() - startTime

        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null

            val app = application as MeetingsApp
            val phoneNumber = getPhoneNumber()
            val contactName = phoneNumber?.let { getContactName(it) }
            val title = contactName ?: phoneNumber ?: getString(R.string.call_recording_unknown)

            val recording = Recording(
                id = id,
                title = title,
                titleIsDefault = contactName == null && phoneNumber == null,
                createdAt = startTime,
                durationMs = duration,
                status = RecordingStatus.RECORDED,
                segments = listOf("$id-0.m4a"),
                transcript = null,
                summary = null,
                speakerNames = emptyMap(),
                error = null,
                tags = setOf("call"),
            )

            scope.launch {
                app.recordings.upsert(recording)
                
                if (app.callRecordings.settings.value.autoProcess && app.settings.settings.value.canTranscribe) {
                    ProcessingWorker.enqueue(app, id, false)
                }
            }

            Log.d(TAG, "Call recording saved: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop call recording", e)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun getPhoneNumber(): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        
        val telephonyManager = getSystemService<TelephonyManager>()
        return null // Simplified - real implementation would query call log
    }

    private fun getContactName(phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(phoneNumber)
            .build()

        contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
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
