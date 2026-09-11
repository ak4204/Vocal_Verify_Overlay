package com.vocalverify.ngrokguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Captures device microphone only; speakerphone is required for the remote voice to be audible. */
class AudioChunker(private val context: Context, private val onChunk: (ByteArray) -> Unit) {
    private val active = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private var record: AudioRecord? = null
    fun start() {
        if (active.getAndSet(true) || ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val instance = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 4096) * 2)
        if (instance.state != AudioRecord.STATE_INITIALIZED) { active.set(false); instance.release(); return }
        record = instance; instance.startRecording()
        worker.execute {
            val block = ByteArray(48_000) // 16k samples/s × 2 bytes × 1.5 seconds
            while (active.get()) {
                var offset = 0
                while (active.get() && offset < block.size) {
                    val read = record?.read(block, offset, block.size - offset) ?: -1
                    if (read <= 0) break
                    offset += read
                }
                if (offset == block.size) onChunk(block.copyOf())
            }
        }
    }
    fun stop() { active.set(false); record?.runCatching { stop(); release() }; record = null; worker.shutdownNow() }
}
