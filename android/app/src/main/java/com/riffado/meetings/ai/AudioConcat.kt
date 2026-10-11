package com.riffado.meetings.ai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/** Joins m4a segments recorded with identical settings into one file without re-encoding. */
object AudioConcat {
    private const val TAG = "AudioConcat"
    private const val FRAME_GAP_US = 64_000L

    fun concat(inputs: List<File>, output: File): File {
        output.delete()
        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_FORMAT_MPEG_4)
        val buffer = ByteBuffer.allocate(1 shl 20)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var offsetUs = 0L
        try {
            for (file in inputs) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.path)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping unreadable segment ${file.name}", e)
                    extractor.release()
                    continue
                }
                val index = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                }
                if (index == null) {
                    extractor.release()
                    continue
                }
                extractor.selectTrack(index)
                if (track < 0) {
                    track = muxer.addTrack(extractor.getTrackFormat(index))
                    muxer.start()
                }
                var lastUs = 0L
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    lastUs = extractor.sampleTime
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    info.set(0, size, offsetUs + lastUs, flags)
                    muxer.writeSampleData(track, buffer, info)
                    extractor.advance()
                }
                offsetUs += lastUs + FRAME_GAP_US
                extractor.release()
            }
            if (track < 0) throw IllegalStateException("No readable audio in recording")
            muxer.stop()
        } finally {
            muxer.release()
        }
        return output
    }
}
