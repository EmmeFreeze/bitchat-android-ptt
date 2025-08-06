package com.bitchat.ptt.ble

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.bitchat.ptt.PttApplication
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.HashMap

class PttBleService {
    
    companion object {
        private const val TAG = "PttBleService"
        
        // PTT BLE Service and Characteristic UUIDs
        val PTT_SERVICE_UUID: UUID = UUID.fromString("0000B1FC-0000-1000-8000-00805F9B34FB")
        val PTT_DATA_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000B1FD-0000-1000-8000-00805F9B34FB")
        
        // Packet fragmentation constants
        private const val MAX_PACKET_SIZE = 180
        private const val HEADER_SIZE = 8 // seqId(2) + offset(2) + length(2) + flags(2)
        private const val MAX_PAYLOAD_SIZE = MAX_PACKET_SIZE - HEADER_SIZE
        private const val SLIDING_WINDOW_SIZE = 5
        private const val SLIDING_WINDOW_INTERVAL_MS = 25L
    }
    
    private val context: Context = PttApplication.instance
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val pendingTransmissions = HashMap<Int, TransmissionState>()
    private var sequenceId = 0
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    data class TransmissionState(
        val data: ByteArray,
        val fragments: List<ByteArray>,
        var currentFragment: Int = 0,
        val startTime: Long = System.currentTimeMillis()
    )
    
    data class PacketHeader(
        val sequenceId: Int,
        val offset: Int,
        val length: Int,
        val isLast: Boolean
    )
    
    init {
        setupGattServer()
        startAdvertising()
        startScanning()
    }
    
    private fun setupGattServer() {
        try {
            val service = BluetoothGattService(
                PTT_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            
            val characteristic = BluetoothGattCharacteristic(
                PTT_DATA_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            
            service.addCharacteristic(characteristic)
            
            bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            bluetoothGattServer?.addService(service)
            
            Log.d(TAG, "GATT server setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup GATT server", e)
        }
    }
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices.add(device)
                    Log.d(TAG, "Device connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device)
                    Log.d(TAG, "Device disconnected: ${device.address}")
                }
            }
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == PTT_DATA_CHARACTERISTIC_UUID) {
                handleIncomingPacket(device, value)
                
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        null
                    )
                }
            }
        }
    }
    
    private fun startAdvertising() {
        try {
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()
            
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(PTT_SERVICE_UUID))
                .build()
            
            advertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.d(TAG, "Started advertising")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising started successfully")
        }
        
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error: $errorCode")
        }
    }
    
    private fun startScanning() {
        try {
            scanner = bluetoothAdapter.bluetoothLeScanner
            
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(PTT_SERVICE_UUID))
                .build()
            
            scanner?.startScan(listOf(filter), settings, scanCallback)
            Log.d(TAG, "Started scanning")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scanning", e)
        }
    }
    
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!connectedDevices.contains(device)) {
                connectToDevice(device)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }
    
    private fun connectToDevice(device: BluetoothDevice) {
        // Connection logic would be implemented here
        // For Phase A demo, we focus on the server side
        Log.d(TAG, "Would connect to device: ${device.address}")
    }
    
    fun transmitAudio(encryptedData: ByteArray) {
        serviceScope.launch {
            try {
                val seqId = ++sequenceId
                val fragments = fragmentData(encryptedData, seqId)
                
                val transmissionState = TransmissionState(
                    data = encryptedData,
                    fragments = fragments
                )
                
                pendingTransmissions[seqId] = transmissionState
                
                Log.d(TAG, "Starting transmission: seqId=$seqId, fragments=${fragments.size}")
                
                // Send fragments using sliding window
                sendFragmentsWithSlidingWindow(seqId, fragments)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transmit audio", e)
            }
        }
    }
    
    private fun fragmentData(data: ByteArray, sequenceId: Int): List<ByteArray> {
        val fragments = mutableListOf<ByteArray>()
        var offset = 0
        
        while (offset < data.size) {
            val remainingBytes = data.size - offset
            val payloadSize = minOf(remainingBytes, MAX_PAYLOAD_SIZE)
            val isLast = (offset + payloadSize) >= data.size
            
            val header = PacketHeader(
                sequenceId = sequenceId,
                offset = offset,
                length = payloadSize,
                isLast = isLast
            )
            
            val packet = ByteArray(HEADER_SIZE + payloadSize)
            
            // Write header
            packet[0] = (sequenceId shr 8).toByte()
            packet[1] = (sequenceId and 0xFF).toByte()
            packet[2] = (offset shr 8).toByte()
            packet[3] = (offset and 0xFF).toByte()
            packet[4] = (payloadSize shr 8).toByte()
            packet[5] = (payloadSize and 0xFF).toByte()
            packet[6] = if (isLast) 1 else 0
            packet[7] = 0 // Reserved
            
            // Write payload
            System.arraycopy(data, offset, packet, HEADER_SIZE, payloadSize)
            
            fragments.add(packet)
            offset += payloadSize
        }
        
        return fragments
    }
    
    private suspend fun sendFragmentsWithSlidingWindow(seqId: Int, fragments: List<ByteArray>) {
        var windowStart = 0
        
        while (windowStart < fragments.size) {
            val windowEnd = minOf(windowStart + SLIDING_WINDOW_SIZE, fragments.size)
            
            // Send fragments in current window
            for (i in windowStart until windowEnd) {
                sendPacketToAllDevices(fragments[i])
                delay(5) // Small delay between packets
            }
            
            windowStart = windowEnd
            
            if (windowStart < fragments.size) {
                delay(SLIDING_WINDOW_INTERVAL_MS)
            }
        }
        
        pendingTransmissions.remove(seqId)
        Log.d(TAG, "Transmission completed: seqId=$seqId")
    }
    
    private fun sendPacketToAllDevices(packet: ByteArray) {
        connectedDevices.forEach { device ->
            try {
                val characteristic = bluetoothGattServer
                    ?.getService(PTT_SERVICE_UUID)
                    ?.getCharacteristic(PTT_DATA_CHARACTERISTIC_UUID)
                
                characteristic?.value = packet
                // In a real implementation, we would write to connected GATT clients
                Log.d(TAG, "Sent packet to ${device.address}: ${packet.size} bytes")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send packet to ${device.address}", e)
            }
        }
    }
    
    private fun handleIncomingPacket(device: BluetoothDevice, packet: ByteArray) {
        try {
            if (packet.size < HEADER_SIZE) {
                Log.w(TAG, "Received packet too small: ${packet.size} bytes")
                return
            }
            
            val header = parsePacketHeader(packet)
            val payload = ByteArray(header.length)
            System.arraycopy(packet, HEADER_SIZE, payload, 0, header.length)
            
            Log.d(TAG, "Received packet: seqId=${header.sequenceId}, offset=${header.offset}, length=${header.length}, isLast=${header.isLast}")
            
            // In a complete implementation, we would reassemble fragments here
            // For Phase A demo, we'll just log the reception
            
            if (header.isLast) {
                Log.d(TAG, "Received complete transmission: seqId=${header.sequenceId}")
                // Trigger audio playback callback
                onAudioReceived(payload) // This would be the reassembled data
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle incoming packet", e)
        }
    }
    
    private fun parsePacketHeader(packet: ByteArray): PacketHeader {
        val sequenceId = ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
        val offset = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        val length = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        val isLast = packet[6] != 0.toByte()
        
        return PacketHeader(sequenceId, offset, length, isLast)
    }
    
    private fun onAudioReceived(audioData: ByteArray) {
        // This would trigger the ViewModel callback
        Log.d(TAG, "Audio received for playback: ${audioData.size} bytes")
    }
    
    fun getConnectedDeviceCount(): Int = connectedDevices.size
    
    fun release() {
        serviceScope.cancel()
        advertiser?.stopAdvertising(advertiseCallback)
        scanner?.stopScan(scanCallback)
        bluetoothGattServer?.close()
        connectedDevices.clear()
        pendingTransmissions.clear()
        Log.d(TAG, "BLE service released")
    }
}
