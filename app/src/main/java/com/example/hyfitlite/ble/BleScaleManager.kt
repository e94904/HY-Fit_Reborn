package com.example.hyfitlite.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScaleScanResult(
    val deviceName: String,
    val macAddress: String,
    val weightKg: Double,
    val impedance: Int,
    val isFinalized: Boolean
)

data class DiscoveredScale(
    val deviceName: String,
    val macAddress: String
)

class BleScaleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private val _scanState = MutableStateFlow<ScaleScanResult?>(null)
    val scanState: StateFlow<ScaleScanResult?> = _scanState

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _discoveredScales = MutableStateFlow<List<DiscoveredScale>>(emptyList())
    val discoveredScales: StateFlow<List<DiscoveredScale>> = _discoveredScales

    var targetMacAddress: String? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { parseScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { parseScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) return
        _discoveredScales.value = emptyList()
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        _isScanning.value = true
        scanner.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_isScanning.value) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun parseScanResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord?.bytes ?: return
        val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown Scale"
        val mac = device.address ?: return

        // 1. Is this a valid Chipsea scale? The exact signature from the legacy app is length=0x10, type=0xFF.
        if (isChipseaScale(record)) {
            val parsedData = parseWeightPacket(record)

            // If we don't have a paired scale yet, AUTO-PAIR to the very first one that matches the manufacturer signature!
            // This perfectly emulates the legacy app's "no list" pairing behavior.
            if (targetMacAddress == null) {
                targetMacAddress = mac
                
                // Fire an event to notify MainActivity that a scale was auto-paired
                val currentList = _discoveredScales.value.toMutableList()
                if (currentList.none { it.macAddress == mac }) {
                    currentList.add(DiscoveredScale(name, mac))
                    _discoveredScales.value = currentList
                }
            }

            // 2. Only process weight if this is our paired target scale
            if (mac.equals(targetMacAddress, ignoreCase = true) && parsedData.first > 0) {
                val newState = ScaleScanResult(
                    deviceName = name,
                    macAddress = mac,
                    weightKg = parsedData.first,
                    impedance = parsedData.second,
                    isFinalized = parsedData.third
                )
                _scanState.value = newState
            }
        }
    }

    private fun isChipseaScale(record: ByteArray): Boolean {
        var i = 0
        while (i < record.size - 1) {
            val length = record[i].toInt() and 0xFF
            if (length == 0) break
            if (i + length >= record.size) break
            val type = record[i + 1].toInt() and 0xFF
            if (type == 0xFF && length == 0x10) {
                return true
            }
            i += length + 1
        }
        return false
    }

    private fun parseWeightPacket(record: ByteArray): Triple<Double, Int, Boolean> {
        var i = 0
        while (i < record.size - 1) {
            val length = record[i].toInt() and 0xFF
            if (length == 0) break
            if (i + length >= record.size) break

            val type = record[i + 1].toInt() and 0xFF

            if (type == 0xFF && length == 0x10) {
                // Byte 10 contains decimal and unit info
                val metaByte = record[i + 10].toInt() and 0xFF
                val binaryStr = Integer.toBinaryString(metaByte).padStart(8, '0')
                
                // Bits 4 and 3 for unit
                val unitBits = binaryStr.substring(3, 5)
                
                // Chipsea scales typically use 2 decimal places for KG (divisor 100) 
                // and 1 decimal place for LBS/ST (divisor 10).
                val divisor = if (unitBits == "00") 100.0 else 10.0

                val b1 = record[i + 4].toInt() and 0xFF
                val b2 = record[i + 5].toInt() and 0xFF
                val rawWeight = (b1 shl 8) or b2
                val weight = rawWeight / divisor

                var weightKg = weight
                // If scale is in LB ("10") or ST:LB ("11"), convert back to KG for internal calculation
                if (unitBits == "10" || unitBits == "11") {
                    weightKg = weight * 0.45359237
                }

                var impedance = 0
                val imp1 = record[i + 6].toInt() and 0xFF
                val imp2 = record[i + 7].toInt() and 0xFF
                impedance = (imp1 shl 8) or imp2

                // The scale does not send a finalized flag for weight.
                // It only indicates measurement is complete when impedance is successfully measured (non-zero).
                val isFinalized = impedance > 0 && impedance < 65535

                return Triple(weightKg, impedance, isFinalized)
            }
            i += length + 1
        }
        return Triple(0.0, 0, false)
    }
}
