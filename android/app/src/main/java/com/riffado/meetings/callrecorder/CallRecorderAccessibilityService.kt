package com.riffado.meetings.callrecorder

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

/**
 * Accessibility service that monitors call state to trigger call recording.
 * Uses both TelephonyCallback (Android 12+) and PHONE_STATE broadcast for reliability.
 */
class CallRecorderAccessibilityService : AccessibilityService() {

    private var telephonyManager: TelephonyManager? = null
    private var callStateCallback: TelephonyCallback? = null
    private var phoneStateReceiver: BroadcastReceiver? = null
    private var isInCall = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        CallRecorderDiagnostics.log(this, "Accessibility service connected")
        
        // Check permission
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        CallRecorderDiagnostics.log(this, "READ_PHONE_STATE permission: $hasPermission")
        
        if (!hasPermission) {
            CallRecorderDiagnostics.log(this, "ERROR: Missing READ_PHONE_STATE permission")
            return
        }
        
        telephonyManager = getSystemService()
        
        // Primary: TelephonyCallback (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            callStateCallback = callback
            try {
                telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
                CallRecorderDiagnostics.log(this, "TelephonyCallback registered (Android 12+)")
            } catch (e: SecurityException) {
                CallRecorderDiagnostics.log(this, "ERROR: TelephonyCallback registration failed: ${e.message}")
            }
        } else {
            // Fallback: PhoneStateListener (Android < 12)
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
            try {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
                CallRecorderDiagnostics.log(this, "PhoneStateListener registered (Android < 12)")
            } catch (e: SecurityException) {
                CallRecorderDiagnostics.log(this, "ERROR: PhoneStateListener registration failed: ${e.message}")
            }
        }
        
        // Secondary: PHONE_STATE broadcast receiver (more reliable on some OEMs)
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                CallRecorderDiagnostics.log(context, "PHONE_STATE broadcast: $state")
                when (state) {
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> handleCallState(TelephonyManager.CALL_STATE_OFFHOOK)
                    TelephonyManager.EXTRA_STATE_IDLE -> handleCallState(TelephonyManager.CALL_STATE_IDLE)
                }
            }
        }
        registerReceiver(phoneStateReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
        CallRecorderDiagnostics.log(this, "PHONE_STATE broadcast receiver registered")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for call recording
    }

    override fun onInterrupt() {
        CallRecorderDiagnostics.log(this, "Accessibility service interrupted")
    }

    private fun handleCallState(state: Int) {
        val stateName = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            else -> "UNKNOWN($state)"
        }
        CallRecorderDiagnostics.log(this, "Call state: $stateName")
        
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isInCall) {
                    isInCall = true
                    CallRecorderDiagnostics.log(this, "Call started - attempting to start recording")
                    startCallRecording()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isInCall) {
                    isInCall = false
                    CallRecorderDiagnostics.log(this, "Call ended - stopping recording")
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
                CallRecorderDiagnostics.log(this, "startForegroundService called")
            } else {
                startService(intent)
                CallRecorderDiagnostics.log(this, "startService called")
            }
        } catch (e: Exception) {
            CallRecorderDiagnostics.log(this, "ERROR starting service: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun stopCallRecording() {
        try {
            val intent = Intent(this, CallRecorderService::class.java).apply {
                action = CallRecorderService.ACTION_STOP
            }
            startService(intent)
            CallRecorderDiagnostics.log(this, "Stop recording service called")
        } catch (e: Exception) {
            CallRecorderDiagnostics.log(this, "ERROR stopping service: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun onDestroy() {
        CallRecorderDiagnostics.log(this, "Accessibility service destroyed")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                callStateCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering callback", e)
            }
        }
        
        try {
            phoneStateReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallRecorderA11y"
    }
}
