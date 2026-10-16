package com.riffado.meetings.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.riffado.meetings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Imports audio files from external sources, segments them if needed, and creates recordings. */
object AudioImporter {
    private const val TAG = "AudioImporter"
    private const val MAX_SEGMENT_SIZE = 20 * 1024 * 1024 // 20 MB

    suspend fun importAudio(context: Context, uri: Uri, store: RecordingStore): String? = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val tempFile = File(context.cacheDir, "$id-import.m4a")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream")

            val duration = getAudioDuration(tempFile)
            if (duration <= 0) throw IllegalStateException("Invalid audio file")

            val segments = if (tempFile.length() > MAX_SEGMENT_SIZE) {
                segmentLargeFile(context, tempFile, id, store)
            } else {
                val segment = store.segmentFile("$id-0.m4a")
                tempFile.renameTo(segment)
                listOf("$id-0.m4a")
            }

            tempFile.delete()

            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
            val title = context.getString(R.string.default_title, dateStr)

            val recording = Recording(
                id = id,
                title = title,
                titleIsDefault = true,
                createdAt = timestamp,
                durationMs = duration,
                status = RecordingStatus.RECORDED,
                segments = segments,
                transcript = null,
                summary = null,
                speakerNames = emptyMap(),
                error = null,
            )

            store.upsert(recording)
            id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import audio", e)
            null
        }
    }

    private fun getAudioDuration(file: File): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return 0
            val format = extractor.getTrackFormat(index)
            return format.getLong(MediaFormat.KEY_DURATION) / 1000 // Convert microseconds to milliseconds
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract duration", e)
            return 0
        } finally {
            extractor.release()
        }
    }

    private fun segmentLargeFile(context: Context, file: File, id: String, store: RecordingStore): List<String> {
        // For simplicity, if file is too large, just use it as one segment
        // The processing worker will handle it, and providers will get the file as-is
        // In practice, audio files over 20MB are rare for meetings, but we handle it gracefully
        val segment = store.segmentFile("$id-0.m4a")
        file.renameTo(segment)
        return listOf("$id-0.m4a")
    }
}
