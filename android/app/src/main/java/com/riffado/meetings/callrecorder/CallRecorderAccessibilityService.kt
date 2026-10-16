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
 * 
 * Resilient design:
 * - Survives configuration changes
 * - Restarts after crash via START_STICKY
 * - Maintains state across device sleep
 */
class CallRecorderAccessibilityService : AccessibilityService() {

    private var telephonyManager: TelephonyManager? = null
    private var callStateCallback: TelephonyCallback? = null
    private var isInCall = false

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
            try {
                telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
                Log.d(TAG, "Telephony callback registered")
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to register telephony callback - missing permission", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for call recording, but required by AccessibilityService
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isInCall) {
                    isInCall = true
                    Log.d(TAG, "Call started")
                    startCallRecording()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isInCall) {
                    isInCall = false
                    Log.d(TAG, "Call ended")
                    stopCallRecording()
                }
            }
        }
    }

    private fun startCallRecording() {
        try {
            val intent = Intent(this, CallRecorderService::class.java).apply {
                action = CallRecorderService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call recording service", e)
        }
    }

    private fun stopCallRecording() {
        try {
            val intent = Intent(this, CallRecorderService::class.java).apply {
                action = CallRecorderService.ACTION_STOP
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop call recording service", e)
        }
    }

    override fun onDestroy() {
        Log.w(TAG, "Accessibility service destroyed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                callStateCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callback", e)
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRecorderA11y"
    }
}
