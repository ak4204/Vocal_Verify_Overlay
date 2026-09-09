package com.vocalverify.callguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Captures the local microphone only. For a call, the user must choose speakerphone. */
class MicrophoneChunker(private val context: Context, private val onChunk: (ByteArray) -> Unit) {
    private val active = AtomicBoolean(false); private val worker = Executors.newSingleThreadExecutor(); private var recorder: AudioRecord? = null
    fun start() {
        if (active.getAndSet(true) || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096) * 2).also { it.startRecording() }
        worker.execute {
            val chunk = ByteArray(16000 * 2 * 3 / 2) // 1.5 seconds, PCM16 mono
            while (active.get()) {
                var offset = 0
                while (offset < chunk.size && active.get()) { val n = recorder?.read(chunk, offset, chunk.size - offset) ?: -1; if (n > 0) offset += n else break }
                if (offset == chunk.size) onChunk(chunk.copyOf())
            }
        }
    }
    fun stop() { active.set(false); recorder?.runCatching { stop(); release() }; recorder = null; worker.shutdownNow() }
}
