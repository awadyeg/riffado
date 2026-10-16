package com.riffado.meetings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.content.getSystemService
import com.riffado.meetings.data.RecordingStatus

/**
 * Transparent launcher activity for quick-record toggle via Samsung power button double-press,
 * Quick Settings tile, or home screen shortcut.
 * 
 * Shows no UI - toggles recording state, vibrates, and finishes.
 */
class QuickRecordActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Work from lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        toggleRecording()
        finish()
    }
    
    private fun toggleRecording() {
        val app = application as MeetingsApp
        val recordings = app.recordings
        
        // Check if currently recording - we look at the in-memory state
        // Since we don't have direct access to currentRecording flow here synchronously,
        // we check if the service is running by attempting to stop
        // This is a simple toggle: if a recording is active, stop it; otherwise start
        val prefs = getSharedPreferences("quick_record_state", MODE_PRIVATE)
        val wasRecording = prefs.getBoolean("is_recording", false)
        
        if (wasRecording) {
            // Stop recording
            stopRecording()
            prefs.edit().putBoolean("is_recording", false).apply()
            vibrate(longArrayOf(0, 100, 100, 100))
            Log.d(TAG, "Quick-record: stopped")
        } else {
            // Start recording
            startRecording()
            prefs.edit().putBoolean("is_recording", true).apply()
            vibrate(longArrayOf(0, 200))
            Log.d(TAG, "Quick-record: started")
        }
    }
    
    private fun startRecording() {
        val intent = Intent(this, com.riffado.meetings.recording.RecordingService::class.java).apply {
            action = com.riffado.meetings.recording.RecordingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopRecording() {
        val intent = Intent(this, com.riffado.meetings.recording.RecordingService::class.java).apply {
            action = com.riffado.meetings.recording.RecordingService.ACTION_STOP
        }
        startService(intent)
    }
    
    private fun vibrate(pattern: LongArray) {
        try {
            val vibrator = getSystemService<Vibrator>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
    
    companion object {
        private const val TAG = "QuickRecordActivity"
    }
}
