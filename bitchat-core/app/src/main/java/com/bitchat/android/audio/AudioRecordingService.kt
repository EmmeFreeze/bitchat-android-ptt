package com.bitchat.android.audio

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import android.util.Base64

/**
 * Simplified audio recording service using MediaRecorder
 * Handles PTT (Push-to-Talk) audio recording functionality
 */
class AudioRecordingService(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecordingService"
        private const val AUDIO_DIR = "ptt_audio"
        private const val MAX_RECORDING_DURATION_MS = 60000 // 1 minute max
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    
    private val audioDir = File(context.filesDir, AUDIO_DIR).apply {
        if (!exists()) {
            mkdirs()
            Log.d(TAG, "Created audio directory: $absolutePath")
        }
    }
    
    /**
     * Start recording audio
     */
    suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                Log.w(TAG, "Already recording, ignoring start request")
                return@withContext false
            }
            
            // Create unique filename
            val timestamp = System.currentTimeMillis()
            val filename = "ptt_${timestamp}.3gp"
            currentRecordingFile = File(audioDir, filename)
            
            Log.d(TAG, "Starting audio recording to: ${currentRecordingFile?.absolutePath}")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(currentRecordingFile?.absolutePath)
                setMaxDuration(MAX_RECORDING_DURATION_MS)
                
                prepare()
                start()
            }
            
            isRecording = true
            Log.i(TAG, "✅ Audio recording started successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start audio recording: ${e.message}", e)
            cleanup()
            false
        }
    }
    
    /**
     * Stop recording and return audio data as base64
     */
    suspend fun stopRecording(): String? = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                Log.w(TAG, "Not recording, ignoring stop request")
                return@withContext null
            }
            
            Log.d(TAG, "Stopping audio recording")
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            val audioFile = currentRecordingFile
            if (audioFile?.exists() == true && audioFile.length() > 0) {
                Log.i(TAG, "✅ Audio recording completed. File size: ${audioFile.length()} bytes")
                
                // Convert to base64 for transmission
                val audioBytes = audioFile.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.DEFAULT)
                
                Log.d(TAG, "Audio converted to base64, length: ${base64Audio.length}")
                
                // Clean up file after conversion
                audioFile.delete()
                Log.d(TAG, "Temporary audio file deleted")
                
                base64Audio
            } else {
                Log.e(TAG, "❌ Audio file is empty or doesn't exist")
                cleanup()
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to stop audio recording: ${e.message}", e)
            cleanup()
            null
        }
    }
    
    /**
     * Cancel current recording
     */
    fun cancelRecording() {
        Log.d(TAG, "Cancelling audio recording")
        cleanup()
    }
    
    private fun cleanup() {
        try {
            mediaRecorder?.apply {
                if (isRecording) {
                    stop()
                }
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            currentRecordingFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Cleaned up audio file: ${file.name}")
                }
            }
            currentRecordingFile = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
}
