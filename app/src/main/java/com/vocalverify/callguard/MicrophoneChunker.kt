package com.vocalverify.callguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Captures the microphone audio during calls (with speakerphone) or during demo tests. */
class MicrophoneChunker(private val context: Context, private val onChunk: (ByteArray) -> Unit) {
    private val active = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private var recorder: AudioRecord? = null

    fun start() {
        if (active.getAndSet(true)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("MicrophoneChunker", "RECORD_AUDIO permission not granted")
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, 4096) * 2

        // Try VOICE_COMMUNICATION first (best for in-call capture), then fallback to MIC and DEFAULT
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        for (src in sources) {
            try {
                val r = AudioRecord(src, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                if (r.state == AudioRecord.STATE_INITIALIZED) {
                    r.startRecording()
                    recorder = r
                    Log.i("MicrophoneChunker", "AudioRecord initialized successfully with audio source $src")
                    break
                } else {
                    r.release()
                }
            } catch (e: Exception) {
                Log.w("MicrophoneChunker", "Could not init AudioSource $src: ${e.message}")
            }
        }

        worker.execute {
            val chunk = ByteArray(16000 * 2 * 3 / 2) // 1.5 seconds of 16kHz mono PCM16 = 48000 bytes
            while (active.get()) {
                var offset = 0
                while (offset < chunk.size && active.get()) {
                    val n = recorder?.read(chunk, offset, chunk.size - offset) ?: -1
                    if (n > 0) {
                        offset += n
                    } else {
                        Thread.sleep(80)
                        break
                    }
                }
                if (offset == chunk.size) {
                    Log.d("MicrophoneChunker", "Read 1.5s audio chunk (${chunk.size} bytes), forwarding...")
                    onChunk(chunk.copyOf())
                }
            }
        }
    }

    fun stop() {
        active.set(false)
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        worker.shutdownNow()
    }
}
