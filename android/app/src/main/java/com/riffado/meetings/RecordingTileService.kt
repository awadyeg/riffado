package com.riffado.meetings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Quick Settings tile for recording toggle.
 */
@RequiresApi(Build.VERSION_CODES.N)
class RecordingTileService : TileService() {
    
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("quick_record_state", MODE_PRIVATE)
    }
    
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "is_recording") {
            updateTile()
        }
    }
    
    override fun onStartListening() {
        super.onStartListening()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        updateTile()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }
    
    override fun onClick() {
        super.onClick()
        
        val isRecording = prefs.getBoolean("is_recording", false)
        
        if (isRecording) {
            stopRecording()
            prefs.edit().putBoolean("is_recording", false).apply()
        } else {
            startRecording()
            prefs.edit().putBoolean("is_recording", true).apply()
        }
        
        updateTile()
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
        Log.d(TAG, "Recording started from tile")
    }
    
    private fun stopRecording() {
        val intent = Intent(this, com.riffado.meetings.recording.RecordingService::class.java).apply {
            action = com.riffado.meetings.recording.RecordingService.ACTION_STOP
        }
        startService(intent)
        Log.d(TAG, "Recording stopped from tile")
    }
    
    private fun updateTile() {
        val isRecording = prefs.getBoolean("is_recording", false)
        
        qsTile?.apply {
            state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (isRecording) "Stop recording" else "Start recording"
            updateTile()
        }
    }
    
    companion object {
        private const val TAG = "RecordingTileService"
    }
}
