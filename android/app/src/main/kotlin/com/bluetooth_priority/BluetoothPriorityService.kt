package com.bluetooth_priority

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Background service that monitors Bluetooth proximity and disables
 * local Bluetooth when a priority device is detected nearby.
 */
class BluetoothPriorityService : Service() {

    companion object {
        private const val TAG = "BluetoothPriority"
        private const val NOTIFICATION_CHANNEL_ID = "bluetooth_priority_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PROXIMITY_THRESHOLD_DBM = -70
    }

    // Native library binding
    external fun initializeNative()
    external fun addPriorityDevice(deviceAddress: String, rssiThreshold: Int, priority: Int): Boolean
    external fun processRssiUpdate(deviceAddress: String, rssi: Int): Boolean
    external fun isBluetoothDisabled(): Boolean
    external fun getStatus(): String
    external fun setProximityThreshold(thresholdDbm: Int)
    external fun removePriorityDevice(deviceAddress: String): Boolean

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: android.bluetooth.le.BluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    
    private var isScanning = false
    private var isBluetoothDisabledLocal = false
    
    // Debounce handling
    private val debounceDelayMs = 2000L
    private var lastStateChange = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Bluetooth Priority Service Created")
        
        initializeNative()
        initializeBluetooth()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started")
        
        when (intent?.action) {
            "ACTION_ADD_DEVICE" -> {
                val address = intent.getStringExtra("device_address")
                val threshold = intent.getIntExtra("rssi_threshold", PROXIMITY_THRESHOLD_DBM)
                val priority = intent.getIntExtra("priority", 5)
                address?.let { addPriorityDevice(it, threshold, priority) }
            }
            "ACTION_REMOVE_DEVICE" -> {
                val address = intent.getStringExtra("device_address")
                address?.let { removePriorityDevice(it) }
            }
            "ACTION_SET_THRESHOLD" -> {
                val threshold = intent.getIntExtra("threshold", PROXIMITY_THRESHOLD_DBM)
                setProximityThreshold(threshold)
            }
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        startScanning()
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        }
        
        Log.i(TAG, "Bluetooth initialized: ${bluetoothAdapter.isEnabled}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Bluetooth Priority Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors Bluetooth proximity and manages priority access"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val status = getStatus()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Bluetooth Priority Monitor")
            .setContentText(if (isBluetoothDisabledLocal) "Bluetooth disabled - Priority device nearby" else "Monitoring for priority devices")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startScanning() {
        if (isScanning) return
        
        val filters = listOf<ScanFilter>()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()
        
        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback)
            isScanning = true
            Log.i(TAG, "BLE scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for BLE scanning", e)
        }
    }

    private fun stopScanning() {
        if (!isScanning) return
        
        try {
            bluetoothLeScanner.stopScan(scanCallback)
            isScanning = false
            Log.i(TAG, "BLE scanning stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error: $errorCode")
        }
    }

    private fun processScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        
        // Only process if we have a name or it's a known device
        if (device.name.isNullOrBlank()) return
        
        val address = device.address
        Log.d(TAG, "Found device: ${device.name} ($address) RSSI: $rssi dBm")
        
        // Process through native library
        val shouldDisable = processRssiUpdate(address, rssi)
        
        // Apply state change with debounce
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStateChange > debounceDelayMs) {
            if (shouldDisable && !isBluetoothDisabledLocal) {
                disableBluetooth()
                lastStateChange = currentTime
            } else if (!shouldDisable && isBluetoothDisabledLocal) {
                enableBluetooth()
                lastStateChange = currentTime
            }
            
            isBluetoothDisabledLocal = shouldDisable
            updateNotification()
        }
    }

    private fun disableBluetooth() {
        Log.i(TAG, "Disabling local Bluetooth (priority device detected)")
        try {
            bluetoothAdapter.disable()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to disable Bluetooth", e)
        }
    }

    private fun enableBluetooth() {
        Log.i(TAG, "Enabling local Bluetooth (no priority conflict)")
        try {
            if (!bluetoothAdapter.isEnabled) {
                bluetoothAdapter.enable()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to enable Bluetooth", e)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    // Load native library
    init {
        System.loadLibrary("bluetooth_priority")
    }
}
