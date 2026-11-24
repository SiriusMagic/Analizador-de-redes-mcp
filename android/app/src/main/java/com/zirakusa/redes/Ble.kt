package com.zirakusa.redes

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.zirakusa.redes.data.BleDevice

/** What «Descubrir › Bluetooth» can do now. */
enum class BleState { NO_PERMISSION, OFF, UNSUPPORTED, READY }

/**
 * «Descubrir › Bluetooth»: listens to Bluetooth LE adverts nearby while the page is open (never connects, never keeps
 * anything). Android 12+ asks «dispositivos cercanos» (never used for location); before, the location permission.
 */
class Ble(context: Context) {
    private val app = context.applicationContext
    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private var callback: ScanCallback? = null

    val permission: String get() = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
    fun state(): BleState = when {
        adapter == null || !app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) -> BleState.UNSUPPORTED
        ContextCompat.checkSelfPermission(app, permission) != PackageManager.PERMISSION_GRANTED -> BleState.NO_PERMISSION
        !adapter.isEnabled -> BleState.OFF
        else -> BleState.READY
    }

    @SuppressLint("MissingPermission") // only after state() said the permission is there
    fun start(seen: (BleDevice) -> Unit): Boolean {
        if (state() != BleState.READY || callback != null) return callback != null
        val scanner = adapter?.bluetoothLeScanner ?: return false
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                seen(BleDevice(r.device?.address.orEmpty(), r.scanRecord?.deviceName.orEmpty().take(40), r.rssi, System.currentTimeMillis()))
            }
        }
        return runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb); callback = cb; true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val cb = callback ?: return
        callback = null
        runCatching { if (state() == BleState.READY) adapter?.bluetoothLeScanner?.stopScan(cb) }
    }
}
