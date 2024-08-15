package com.example.coba

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var btnConnect: Button
    private lateinit var btnSend: Button
    private lateinit var tvData: TextView
    private lateinit var etData: EditText
    private lateinit var tvMacAddress: TextView
    private lateinit var tvIpAddress: TextView
    private var bluetoothGatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private val serviceUUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")
    private val characteristicUUID = UUID.fromString("abcdefab-1234-5678-1234-56789abcdef1")

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread {
                    tvData.text = "Connected"
                }
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt?.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    tvData.text = "Disconnected"
                }
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(serviceUUID)
                if (service != null) {
                    Log.d("BLE", "Service discovered: ${service.uuid}")
                    characteristic = service.getCharacteristic(characteristicUUID)
                    if (characteristic != null) {
                        Log.d("BLE", "Characteristic discovered: ${characteristic?.uuid}")
                        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt?.setCharacteristicNotification(characteristic, true)
                        }

                        val descriptor = characteristic?.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt?.writeDescriptor(descriptor)
                        }
                    } else {
                        Log.d("BLE", "Characteristic not found")
                    }
                } else {
                    Log.d("BLE", "Service not found")
                }
            } else {
                Log.d("BLE", "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            characteristic?.let {
                if (it.uuid == characteristicUUID) {
                    val data = it.value?.let { String(it) }
                    runOnUiThread {
                        tvData.text = "Data from ESP32: $data"
                        // Assume the data contains the IP address
                        tvIpAddress.text = "IP Address: $data"
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let {
                if (it.uuid == characteristicUUID) {
                    val data = it.value?.let { String(it) }
                    runOnUiThread {
                        tvData.text = "Data from ESP32: $data"
                        // Assume the data contains the IP address
                        tvIpAddress.text = "IP Address: $data"
                    }
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    tvData.text = "Data sent successfully"
                } else {
                    tvData.text = "Failed to send data"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnConnect = findViewById(R.id.btnConnect)
        btnSend = findViewById(R.id.btnSend)
        tvData = findViewById(R.id.tvData)
        etData = findViewById(R.id.etData)
        tvMacAddress = findViewById(R.id.tvMacAddress)
        tvIpAddress = findViewById(R.id.tvIpAddress)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        btnConnect.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1)
                return@setOnClickListener
            }

            val device = bluetoothAdapter.bondedDevices.firstOrNull { it.name == "ESP32_BLE" }
            if (device != null) {
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
                // Display MAC address
                tvMacAddress.text = "MAC Address: ${device.address}"
            } else {
                tvData.text = "Device not found"
            }
        }

        btnSend.setOnClickListener {
            val data = etData.text.toString().toByteArray()
            if (characteristic != null) {
                characteristic?.value = data
                Log.d("BLE", "characteristic found: $characteristic")
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    Log.d("BLE", "Writing characteristic")
                    bluetoothGatt?.writeCharacteristic(characteristic)
                }
            } else {
                tvData.text = "Characteristic not found"
            }

            // Display and save the IP address entered in etData
            val ipAddress = etData.text.toString()
            tvIpAddress.text = ipAddress
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with connecting
                btnConnect.performClick()
            } else {
                // Permission denied, show a message to the user
                tvData.text = "Permission denied to connect Bluetooth"
            }
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_DISCOVERABLE_BT = 2
        private const val REQUEST_PAIRED_DEVICES = 3
        private const val REQUEST_TURN_OFF_BT = 4
        private const val REQUEST_SCAN_DEVICES = 5
        private const val REQUEST_PAIR_DEVICE = 6
    }
}