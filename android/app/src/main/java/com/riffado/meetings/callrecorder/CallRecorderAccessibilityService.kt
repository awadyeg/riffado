package com.riffado.meetings.callrecorder

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.getSystemService

/**
 * Accessibility service that monitors call state to trigger call recording.
 * Required because Android blocks VOICE_CALL audio source for non-system apps.
 */
class CallRecorderAccessibilityService : AccessibilityService() {

    private var telephonyManager: TelephonyManager? = null
    private var callStateCallback: TelephonyCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        
        telephonyManager = getSystemService()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            callStateCallback = callback
            telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for call recording, but required by AccessibilityService
    }

    override fun onInterrupt() {
        // Required by AccessibilityService
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call started")
                startCallRecording()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call ended")
                stopCallRecording()
            }
        }
    }

    private fun startCallRecording() {
        val intent = Intent(this, CallRecorderService::class.java).apply {
            action = CallRecorderService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopCallRecording() {
        val intent = Intent(this, CallRecorderService::class.java).apply {
            action = CallRecorderService.ACTION_STOP
        }
        startService(intent)
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callStateCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRecorderA11y"
    }
}
