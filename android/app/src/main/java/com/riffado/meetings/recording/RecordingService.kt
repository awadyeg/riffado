package com.riffado.meetings.recording

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.riffado.meetings.MainActivity
import com.riffado.meetings.MeetingsApp
import com.riffado.meetings.R
import com.riffado.meetings.ai.ProcessingWorker
import com.riffado.meetings.data.Recording
import com.riffado.meetings.data.RecordingStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Foreground service that records the meeting with [MediaRecorder], rolling over to a new segment file near [MAX_SEGMENT_BYTES]. */
class RecordingService : LifecycleService() {
    private val app get() = application as MeetingsApp
    private val prefs by lazy { getSharedPreferences("quick_record_state", MODE_PRIVATE) }

    private var recorder: MediaRecorder? = null
    private var recordingId: String? = null
    private val segments = mutableListOf<String>()
    private var pendingSegment: String? = null
    private var accumulatedMs = 0L
    private var resumedAt = 0L
    private var paused = false
    private var ticker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (recorder != null) return
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        recordingId = id
        segments.clear()
        accumulatedMs = 0
        paused = false
        
        // Track recording state for Quick Record toggle
        prefs.edit().putBoolean("is_recording", true).apply()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )

        val first = segmentName(id, 0)
        try {
            recorder = createRecorder(first).also { it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recorder", e)
            releaseRecorder()
            app.recordings.segmentFile(first).delete()
            recordingId = null
            prefs.edit().putBoolean("is_recording", false).apply()
            RecorderState.set(LiveRecording())
            stopSelfCompletely()
            return
        }
        segments += first
        resumedAt = SystemClock.elapsedRealtime()
        acquireWakeLock()

        val title = getString(
            R.string.default_title,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(now)),
        )
        lifecycleScope.launch {
            app.recordings.upsert(
                Recording(id = id, title = title, createdAt = now, segments = segments.toList()),
            )
        }
        startTicker()
    }

    private fun createRecorder(segment: String): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else legacyRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioChannels(1)
        r.setAudioSamplingRate(SAMPLE_RATE)
        r.setAudioEncodingBitRate(BIT_RATE)
        r.setMaxFileSize(MAX_SEGMENT_BYTES)
        r.setOutputFile(app.recordings.segmentFile(segment))
        r.setOnInfoListener { mr, what, _ -> onRecorderInfo(mr, what) }
        r.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaRecorder error what=$what extra=$extra")
            stop()
        }
        r.prepare()
        return r
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder() = MediaRecorder()

    private fun onRecorderInfo(mr: MediaRecorder, what: Int) {
        val id = recordingId ?: return
        when (what) {
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> {
                val next = segmentName(id, segments.size + (if (pendingSegment != null) 1 else 0))
                try {
                    mr.setNextOutputFile(app.recordings.segmentFile(next))
                    pendingSegment = next
                } catch (e: Exception) {
                    Log.e(TAG, "setNextOutputFile failed", e)
                }
            }
            MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> {
                pendingSegment?.let { segments += it }
                pendingSegment = null
                val list = segments.toList()
                lifecycleScope.launch { app.recordings.update(id) { it.copy(segments = list) } }
            }
            MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> stop()
        }
    }

    private fun pause() {
        val r = recorder ?: return
        if (paused) return
        r.pause()
        accumulatedMs += SystemClock.elapsedRealtime() - resumedAt
        paused = true
        publish()
        notifyUpdate()
    }

    private fun resume() {
        val r = recorder ?: return
        if (!paused) return
        r.resume()
        resumedAt = SystemClock.elapsedRealtime()
        paused = false
        publish()
        notifyUpdate()
    }

    private fun stop() {
        val id = recordingId
        val duration = elapsed()
        ticker?.cancel()
        
        // Track recording state for Quick Record toggle
        prefs.edit().putBoolean("is_recording", false).apply()
        
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Recorder stop failed; segment may be empty", e)
        }
        releaseRecorder()
        releaseWakeLock()
        recordingId = null
        RecorderState.set(LiveRecording())

        if (id != null) {
            val files = segments.filter { app.recordings.segmentFile(it).length() > 0 }
            app.appScope.launch {
                app.recordings.update(id) {
                    it.copy(
                        durationMs = duration,
                        segments = files,
                        status = if (files.isEmpty()) RecordingStatus.FAILED else RecordingStatus.RECORDED,
                        error = if (files.isEmpty()) "No audio was captured" else null,
                    )
                }
                val settings = app.settings.settings.value
                if (files.isNotEmpty() && settings.autoProcess && settings.canTranscribe) {
                    ProcessingWorker.enqueue(applicationContext, id, summaryOnly = false)
                }
            }
        }
        stopSelfCompletely()
    }

    private fun stopSelfCompletely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseRecorder() {
        recorder?.release()
        recorder = null
        pendingSegment = null
    }

    private fun elapsed(): Long =
        if (paused || recorder == null) accumulatedMs else accumulatedMs + SystemClock.elapsedRealtime() - resumedAt

    private fun startTicker() {
        ticker?.cancel()
        ticker = lifecycleScope.launch {
            while (isActive) {
                publish()
                delay(TICK_MS)
            }
        }
    }

    private fun publish() {
        val r = recorder
        val level = if (r != null && !paused) {
            runCatching { r.maxAmplitude / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)
        } else {
            0f
        }
        RecorderState.set(
            LiveRecording(recordingId = recordingId, isPaused = paused, elapsedMs = elapsed(), level = level),
        )
    }

    private fun notifyUpdate() {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission revoked", e)
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = if (paused) {
            NotificationCompat.Action(0, getString(R.string.action_resume), serviceIntent(ACTION_RESUME, 1))
        } else {
            NotificationCompat.Action(0, getString(R.string.action_pause), serviceIntent(ACTION_PAUSE, 2))
        }
        val stop = NotificationCompat.Action(0, getString(R.string.action_stop), serviceIntent(ACTION_STOP, 3))
        val builder = NotificationCompat.Builder(this, MeetingsApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(if (paused) R.string.notif_paused else R.string.notif_recording))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggle)
            .addAction(stop)
        if (!paused) {
            builder.setUsesChronometer(true).setWhen(System.currentTimeMillis() - elapsed())
        }
        return builder.build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun acquireWakeLock() {
        val pm = getSystemService<PowerManager>() ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RiffadoMeetings:recording").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        if (recorder != null) stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val SAMPLE_RATE = 16_000
        private const val BIT_RATE = 32_000
        private const val MAX_SEGMENT_BYTES = 20L * 1024 * 1024
        private const val TICK_MS = 200L
        private const val MAX_WAKE_MS = 6L * 60 * 60 * 1000

        const val ACTION_START = "com.riffado.meetings.START"
        const val ACTION_PAUSE = "com.riffado.meetings.PAUSE"
        const val ACTION_RESUME = "com.riffado.meetings.RESUME"
        const val ACTION_STOP = "com.riffado.meetings.STOP"

        fun segmentName(id: String, index: Int) = "$id-$index.m4a"

        fun send(context: Context, action: String) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
