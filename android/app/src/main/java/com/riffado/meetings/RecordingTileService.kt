package com.riffado.meetings

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.riffado.meetings.data.RecordingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for recording toggle.
 */
@RequiresApi(Build.VERSION_CODES.N)
class RecordingTileService : TileService() {
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        
        // Update tile when recording state changes
        val app = application as MeetingsApp
        app.recordings.currentRecording.onEach {
            updateTile()
        }.launchIn(scope)
    }
    
    override fun onClick() {
        super.onClick()
        
        val app = application as MeetingsApp
        val currentRecording = app.recordings.currentRecording.value
        
        if (currentRecording != null && currentRecording.status == RecordingStatus.RECORDING) {
            stopRecording()
        } else {
            startRecording()
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
        scope.launch {
            val app = application as MeetingsApp
            val currentRecording = app.recordings.currentRecording.value
            val isRecording = currentRecording?.status == RecordingStatus.RECORDING
            
            qsTile?.apply {
                state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = if (isRecording) "Stop recording" else "Start recording"
                updateTile()
            }
        }
    }
    
    companion object {
        private const val TAG = "RecordingTileService"
    }
}
