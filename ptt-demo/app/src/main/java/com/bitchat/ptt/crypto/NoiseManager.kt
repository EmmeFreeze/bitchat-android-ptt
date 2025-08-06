package com.bitchat.ptt.crypto

import android.util.Log
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class NoiseManager {
    
    companion object {
        private const val TAG = "NoiseManager"
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
    
    private val secureRandom = SecureRandom()
    private var sharedKey: SecretKeySpec? = null
    
    init {
        // For Phase A demo, generate a simple shared key
        // In production, this would be derived from Noise XX handshake
        generateSharedKey()
    }
    
    private fun generateSharedKey() {
        try {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(AES_KEY_SIZE)
            val key = keyGenerator.generateKey()
            sharedKey = SecretKeySpec(key.encoded, "AES")
            Log.d(TAG, "Shared key generated for demo")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate shared key", e)
            throw e
        }
    }
    
    fun encrypt(data: ByteArray): ByteArray {
        return try {
            val key = sharedKey ?: throw IllegalStateException("No shared key available")
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(GCM_IV_LENGTH)
            secureRandom.nextBytes(iv)
            
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
            
            val encryptedData = cipher.doFinal(data)
            
            // Prepend IV to encrypted data
            val result = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(encryptedData, 0, result, iv.size, encryptedData.size)
            
            Log.d(TAG, "Data encrypted: ${data.size} -> ${result.size} bytes")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            throw e
        }
    }
    
    fun decrypt(encryptedData: ByteArray): ByteArray {
        return try {
            val key = sharedKey ?: throw IllegalStateException("No shared key available")
            
            if (encryptedData.size < GCM_IV_LENGTH) {
                throw IllegalArgumentException("Encrypted data too short")
            }
            
            // Extract IV and encrypted data
            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherData = ByteArray(encryptedData.size - GCM_IV_LENGTH)
            
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(encryptedData, GCM_IV_LENGTH, cipherData, 0, cipherData.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            
            val decryptedData = cipher.doFinal(cipherData)
            
            Log.d(TAG, "Data decrypted: ${encryptedData.size} -> ${decryptedData.size} bytes")
            decryptedData
            
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            throw e
        }
    }
    
    fun performHandshake(): Boolean {
        // Placeholder for Noise XX handshake implementation
        // In Phase A, we use a pre-shared key for simplicity
        Log.d(TAG, "Handshake completed (demo mode)")
        return true
    }
}
