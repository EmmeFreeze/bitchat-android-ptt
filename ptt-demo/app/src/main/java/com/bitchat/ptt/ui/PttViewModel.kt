package com.bitchat.ptt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.ptt.audio.AudioManager
import com.bitchat.ptt.ble.PttBleService
import com.bitchat.ptt.crypto.NoiseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PttState(
    val isPermissionsGranted: Boolean = false,
    val isRecording: Boolean = false,
    val isTransmitting: Boolean = false,
    val isReceiving: Boolean = false,
    val isPlaying: Boolean = false,
    val connectedDevices: Int = 0,
    val audioQuality: String = "16kHz Mono",
    val latencyMs: Int = 0,
    val statusMessage: String = "Ready"
)

class PttViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(PttState())
    val state: StateFlow<PttState> = _state.asStateFlow()
    
    private lateinit var audioManager: AudioManager
    private lateinit var bleService: PttBleService
    private lateinit var noiseManager: NoiseManager
    
    fun onPermissionsGranted() {
        _state.value = _state.value.copy(isPermissionsGranted = true)
        initializeServices()
    }
    
    fun onPermissionsDenied() {
        _state.value = _state.value.copy(
            isPermissionsGranted = false,
            statusMessage = "Permissions required"
        )
    }
    
    private fun initializeServices() {
        viewModelScope.launch {
            try {
                audioManager = AudioManager()
                bleService = PttBleService()
                noiseManager = NoiseManager()
                
                _state.value = _state.value.copy(statusMessage = "Services initialized")
            } catch (e: Exception) {
                _state.value = _state.value.copy(statusMessage = "Initialization failed: ${e.message}")
            }
        }
    }
    
    fun onPttPressed() {
        if (!_state.value.isPermissionsGranted) return
        
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isRecording = true,
                    statusMessage = "Recording..."
                )
                
                val audioData = audioManager.startRecording()
                
                _state.value = _state.value.copy(
                    isRecording = false,
                    isTransmitting = true,
                    statusMessage = "Transmitting..."
                )
                
                // Encrypt and fragment audio data
                val encryptedData = noiseManager.encrypt(audioData)
                bleService.transmitAudio(encryptedData)
                
                _state.value = _state.value.copy(
                    isTransmitting = false,
                    statusMessage = "Transmitted"
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRecording = false,
                    isTransmitting = false,
                    statusMessage = "Error: ${e.message}"
                )
            }
        }
    }
    
    fun onPttReleased() {
        if (_state.value.isRecording) {
            viewModelScope.launch {
                audioManager.stopRecording()
                _state.value = _state.value.copy(
                    isRecording = false,
                    statusMessage = "Recording stopped"
                )
            }
        }
    }
    
    fun onAudioReceived(encryptedData: ByteArray) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isReceiving = true,
                    statusMessage = "Receiving..."
                )
                
                val audioData = noiseManager.decrypt(encryptedData)
                
                _state.value = _state.value.copy(
                    isReceiving = false,
                    isPlaying = true,
                    statusMessage = "Playing..."
                )
                
                audioManager.playAudio(audioData)
                
                _state.value = _state.value.copy(
                    isPlaying = false,
                    statusMessage = "Ready"
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isReceiving = false,
                    isPlaying = false,
                    statusMessage = "Playback error: ${e.message}"
                )
            }
        }
    }
    
    fun updateConnectedDevices(count: Int) {
        _state.value = _state.value.copy(connectedDevices = count)
    }
    
    fun updateLatency(latencyMs: Int) {
        _state.value = _state.value.copy(latencyMs = latencyMs)
    }
}
