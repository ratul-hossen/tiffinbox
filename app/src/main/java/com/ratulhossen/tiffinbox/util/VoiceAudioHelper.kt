package com.ratulhossen.tiffinbox.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var tempFile: File? = null
    private var startTimeMillis: Long = 0L

    fun start(): Boolean {
        return try {
            cancel()
            val file = File.createTempFile("voice_record_", ".m4a", context.cacheDir)
            tempFile = file
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32000)
                setAudioSamplingRate(22050)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            startTimeMillis = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            false
        }
    }

    /**
     * Stops recording and returns Pair(base64AudioString, durationSeconds)
     */
    fun stopAndGetAudio(): Pair<String, Int>? {
        val mr = recorder ?: return null
        val file = tempFile ?: return null
        return try {
            val durationSec = (((System.currentTimeMillis() - startTimeMillis) / 1000L).coerceAtLeast(1L)).toInt()
            try {
                mr.stop()
            } catch (e: Exception) {
                // Ignore if recording was too short
            }
            mr.release()
            recorder = null

            if (file.exists() && file.length() > 0) {
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Pair(base64, durationSec)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            cleanup()
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        cleanup()
    }

    private fun cleanup() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            // Ignore
        }
        recorder = null
        try {
            tempFile?.delete()
        } catch (e: Exception) {
            // Ignore
        }
        tempFile = null
    }
}

class VoicePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var currentMessageId: String? = null

    fun play(
        messageId: String,
        base64Audio: String,
        onPlaying: () -> Unit,
        onCompletion: () -> Unit
    ) {
        stop()
        try {
            val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val file = File.createTempFile("voice_play_", ".m4a", context.cacheDir)
            FileOutputStream(file).use { it.write(bytes) }

            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener {
                currentMessageId = null
                onCompletion()
                file.delete()
            }
            mp.start()
            player = mp
            currentMessageId = messageId
            onPlaying()
        } catch (e: Exception) {
            e.printStackTrace()
            currentMessageId = null
            onCompletion()
        }
    }

    fun isPlaying(messageId: String): Boolean {
        return currentMessageId == messageId && player?.isPlaying == true
    }

    fun stop() {
        try {
            if (player?.isPlaying == true) {
                player?.stop()
            }
            player?.release()
        } catch (e: Exception) {
            // Ignore
        }
        player = null
        currentMessageId = null
    }
}
