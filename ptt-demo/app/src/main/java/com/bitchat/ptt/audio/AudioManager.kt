package com.bitchat.ptt.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Base64

class AudioManager {
    
    companion object {
        private const val TAG = "AudioManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RECORDING_DURATION_MS = 15000 // 15 seconds
    }
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG_IN,
        AUDIO_FORMAT
    )
    
    suspend fun startRecording(): ByteArray = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting audio recording")
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw RuntimeException("AudioRecord initialization failed")
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            val maxSamples = (SAMPLE_RATE * MAX_RECORDING_DURATION_MS / 1000)
            val audioBuffer = ByteArray(maxSamples * 2) // 16-bit samples = 2 bytes each
            val tempBuffer = ByteArray(bufferSize)
            var totalBytesRead = 0
            
            val startTime = System.currentTimeMillis()
            
            while (isRecording && totalBytesRead < audioBuffer.size) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - startTime >= MAX_RECORDING_DURATION_MS) {
                    Log.d(TAG, "Maximum recording duration reached")
                    break
                }
                
                val bytesRead = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                if (bytesRead > 0) {
                    val bytesToCopy = minOf(bytesRead, audioBuffer.size - totalBytesRead)
                    System.arraycopy(tempBuffer, 0, audioBuffer, totalBytesRead, bytesToCopy)
                    totalBytesRead += bytesToCopy
                }
            }
            
            stopRecording()
            
            Log.d(TAG, "Recording completed: $totalBytesRead bytes")
            
            // Return only the actual recorded data
            val recordedData = audioBuffer.copyOf(totalBytesRead)
            
            // DEBUG: Log audio content in base64 for verification (only in debug builds)
            // Note: Using Log.isLoggable to check if debug logging is enabled
            if (Log.isLoggable(TAG, Log.DEBUG) && recordedData.isNotEmpty()) {
                val base64Audio = Base64.encodeToString(recordedData, Base64.NO_WRAP)
                Log.d(TAG, "DEBUG - Recorded audio data (base64): ${base64Audio.take(100)}...")
                Log.d(TAG, "DEBUG - Audio sample rate: $SAMPLE_RATE Hz, format: 16-bit PCM mono")
                Log.d(TAG, "DEBUG - Total audio duration: ${(totalBytesRead / 2) / SAMPLE_RATE.toFloat()} seconds")
                Log.d(TAG, "DEBUG - First 32 bytes (hex): ${recordedData.take(32).joinToString("") { "%02x".format(it) }}")
            }
            
            recordedData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during recording", e)
            stopRecording()
            throw e
        }
    }
    
    fun stopRecording() {
        isRecording = false
        audioRecord?.apply {
            try {
                // Check AudioRecord state before calling stop() to prevent IllegalStateException
                if (state == AudioRecord.STATE_INITIALIZED && recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    Log.d(TAG, "Stopping AudioRecord (state: $state, recordingState: $recordingState)")
                    stop()
                } else {
                    Log.d(TAG, "AudioRecord not in recording state (state: $state, recordingState: $recordingState)")
                }
            } catch (e: IllegalStateException) {
                // Handle edge case where AudioRecord is already stopped
                Log.w(TAG, "AudioRecord already stopped", e)
            }
            release()
        }
        audioRecord = null
        Log.d(TAG, "Recording stopped and resources released")
    }
    
    suspend fun playAudio(audioData: ByteArray) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting audio playback: ${audioData.size} bytes")
            
            // DEBUG: Log playback audio content in base64 for verification (only in debug builds)
            // Note: Using Log.isLoggable to check if debug logging is enabled
            if (Log.isLoggable(TAG, Log.DEBUG) && audioData.isNotEmpty()) {
                val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
                Log.d(TAG, "DEBUG - Playback audio data (base64): ${base64Audio.take(100)}...")
                Log.d(TAG, "DEBUG - Playback duration: ${(audioData.size / 2) / SAMPLE_RATE.toFloat()} seconds")
            }
            
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT
            )
            
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT,
                maxOf(minBufferSize, audioData.size),
                AudioTrack.MODE_STATIC
            )
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                throw RuntimeException("AudioTrack initialization failed")
            }
            
            audioTrack?.write(audioData, 0, audioData.size)
            audioTrack?.play()
            
            // Wait for playback to complete
            while (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                Thread.sleep(100)
            }
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            
            Log.d(TAG, "Playback completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during playback", e)
            audioTrack?.release()
            audioTrack = null
            throw e
        }
    }
    
    fun release() {
        stopRecording()
        audioTrack?.release()
        audioTrack = null
    }
}
