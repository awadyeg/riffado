package com.riffado.meetings.callrecorder

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log

/**
 * Resolves call participant information from system call log and contacts.
 */
object CallParticipantResolver {
    
    private const val TAG = "CallParticipantResolver"
    
    /**
     * Find the phone number for a call recording by matching call log entries near the recording start time.
     * @param context Android context for content resolver
     * @param recordingStartMs Recording start time in milliseconds
     * @param toleranceMs Time tolerance window (default 5 seconds)
     * @return Phone number if found, null otherwise
     */
    fun findCallNumber(context: Context, recordingStartMs: Long, toleranceMs: Long = 5000): String? {
        try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
            )
            
            val selection = "${CallLog.Calls.DATE} BETWEEN ? AND ?"
            val selectionArgs = arrayOf(
                (recordingStartMs - toleranceMs).toString(),
                (recordingStartMs + toleranceMs).toString(),
            )
            
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    if (numberIndex >= 0) {
                        val number = cursor.getString(numberIndex)
                        if (!number.isNullOrBlank()) {
                            Log.d(TAG, "Found call number: $number for recording at $recordingStartMs")
                            return number
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read call log", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading call log", e)
        }
        
        return null
    }
    
    /**
     * Look up contact name for a phone number.
     * @param context Android context for content resolver
     * @param phoneNumber Phone number to look up
     * @return Contact display name if found, null otherwise
     */
    fun lookupContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber),
            )
            
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            Log.d(TAG, "Found contact name: $name for number: $phoneNumber")
                            return name
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read contacts", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact", e)
        }
        
        return null
    }
    
    /**
     * Resolve both number and contact name for a call recording.
     * @return Pair of (number, contactName) - both may be null
     */
    fun resolveCallParticipant(context: Context, recordingStartMs: Long): Pair<String?, String?> {
        val number = findCallNumber(context, recordingStartMs)
        val contactName = number?.let { lookupContactName(context, it) }
        return Pair(number, contactName)
    }
    
    /**
     * Try to detect the foreground VoIP app name at recording start time.
     * This is a best-effort fallback for VoIP calls (Teams, WhatsApp, etc.) that don't appear in call log.
     * Note: This requires usage stats permission and may not work on all devices.
     */
    fun detectVoipAppName(context: Context, recordingStartMs: Long): String? {
        // TODO: Implement usage stats query if needed
        // For now, return null - VoIP detection is complex and may require additional permissions
        return null
    }
}
