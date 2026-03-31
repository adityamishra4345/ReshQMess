package com.example.reshqmess

import android.annotation.SuppressLint
import android.media.*

class AudioHelper {
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var recorder: AudioRecord? = null
    private var isRecording = false
    private val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

    @SuppressLint("MissingPermission")
    fun startStreaming(onAudioChunk: (ByteArray) -> Unit) {
        if (isRecording) return
        recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufSize)
        isRecording = true
        recorder?.startRecording()

        Thread {
            val buffer = ByteArray(minBufSize)
            while (isRecording) {
                val readBytes = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (readBytes > 0) {
                    val chunk = buffer.copyOf(readBytes)
                    onAudioChunk(chunk)
                }
            }
        }.start()
    }

    fun stopStreaming() {
        isRecording = false
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        recorder = null
    }

    private var audioTrack: AudioTrack? = null

    fun playStream(audioData: ByteArray) {
        if (audioTrack == null) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // FIX: Changed from VOICE_COMMUNICATION to MEDIA to force the Loudspeaker
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        }
        try {
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}