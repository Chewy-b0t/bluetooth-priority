package com.bluetooth_priority

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Main activity for configuring Bluetooth Priority Manager
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BluetoothPriority"
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
        private const val REQUEST_ENABLE_BLUETOOTH = 1002
    }

    private lateinit var deviceAddressInput: EditText
    private lateinit var rssiThresholdInput: EditText
    private lateinit var priorityInput: EditText
    private lateinit var statusText: TextView
    private lateinit var deviceList: ListView
    private val priorityDevices = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main).apply {
            deviceAddressInput = findViewById(R.id.deviceAddressInput)
            rssiThresholdInput = findViewById(R.id.rssiThresholdInput)
            priorityInput = findViewById(R.id.priorityInput)
            statusText = findViewById(R.id.statusText)
            deviceList = findViewById(R.id.deviceList)
            
            findViewById<Button>(R.id.addDeviceButton).setOnClickListener {
                addPriorityDevice()
            }
            
            findViewById<Button>(R.id.startServiceButton).setOnClickListener {
                startMonitoringService()
            }
            
            findViewById<Button>(R.id.stopServiceButton).setOnClickListener {
                stopMonitoringService()
            }
            
            findViewById<Button>(R.id.refreshStatusButton).setOnClickListener {
                updateStatus()
            }
        }
        
        checkPermissions()
    }

    private fun checkPermissions() {
        val requiredPermissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for Bluetooth monitoring", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addPriorityDevice() {
        val address = deviceAddressInput.text.toString().trim()
        val threshold = rssiThresholdInput.text.toString().toIntOrNull() ?: -70
        val priority = priorityInput.text.toString().toIntOrNull() ?: 5
        
        if (address.isBlank()) {
            Toast.makeText(this, "Please enter a device address", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Start service with add action
        val intent = Intent(this, BluetoothPriorityService::class.java).apply {
            action = "ACTION_ADD_DEVICE"
            putExtra("device_address", address)
            putExtra("rssi_threshold", threshold)
            putExtra("priority", priority)
        }
        
        startService(intent)
        priorityDevices.add("$address (priority: $priority)")
        updateDeviceList()
        
        Toast.makeText(this, "Added $address with threshold $threshold dBm", Toast.LENGTH_SHORT).show()
        deviceAddressInput.text.clear()
    }

    private fun startMonitoringService() {
        // Check if Bluetooth is enabled
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
            return
        }
        
        val intent = Intent(this, BluetoothPriorityService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoringService() {
        val intent = Intent(this, BluetoothPriorityService::class.java)
        stopService(intent)
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus() {
        // Get status from native library via service
        statusText.text = "Status: Monitoring active\nThreshold: ${rssiThresholdInput.text.toString().ifBlank { "-70" }} dBm"
    }

    private fun updateDeviceList() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, priorityDevices)
        deviceList.adapter = adapter
    }
}
