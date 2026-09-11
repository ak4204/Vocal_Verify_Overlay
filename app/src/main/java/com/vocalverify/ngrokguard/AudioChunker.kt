package com.vocalverify.ngrokguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Captures device microphone only; speakerphone is required for the remote voice to be audible. */
class AudioChunker(private val context: Context, private val onChunk: (ByteArray) -> Unit) {
    private val active = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private var record: AudioRecord? = null

    fun start() {
        if (active.getAndSet(true)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("AudioChunker", "RECORD_AUDIO permission missing")
            active.set(false)
            return
        }
        try {
            val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val instance = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096) * 2)
            if (instance.state != AudioRecord.STATE_INITIALIZED) {
                active.set(false)
                instance.release()
                return
            }
            record = instance
            instance.startRecording()
        } catch (e: Throwable) {
            Log.e("AudioChunker", "Failed to start AudioRecord", e)
            active.set(false)
            return
        }

        worker.execute {
            val block = ByteArray(48_000) // 16k samples/s * 2 bytes * 1.5 seconds
            try {
                while (active.get()) {
                    var offset = 0
                    while (active.get() && offset < block.size) {
                        val read = try {
                            record?.read(block, offset, block.size - offset) ?: -1
                        } catch (e: Throwable) {
                            -1
                        }
                        if (read <= 0) {
                            Thread.sleep(100)
                            break
                        }
                        offset += read
                    }
                    if (offset == block.size && active.get()) {
                        try {
                            onChunk(block.copyOf())
                        } catch (e: Throwable) {
                            Log.e("AudioChunker", "onChunk callback error", e)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("AudioChunker", "Worker thread error", e)
            }
        }
    }

    fun stop() {
        active.set(false)
        try {
            record?.runCatching { stop(); release() }
        } catch (e: Throwable) {
            Log.e("AudioChunker", "Error stopping record", e)
        }
        record = null
        try {
            worker.shutdownNow()
        } catch (e: Throwable) {
            Log.e("AudioChunker", "Error shutting down worker", e)
        }
    }
}
