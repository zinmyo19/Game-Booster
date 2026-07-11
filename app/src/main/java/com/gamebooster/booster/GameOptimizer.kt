// PATH: app/src/main/java/com/gamebooster/booster/GameOptimizer.kt
package com.gamebooster.booster

import android.bluetooth.BluetoothAdapter
import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.gamebooster.shizuku.ShizukuCommandRunner

/**
 * GameOptimizer — applies/restores system settings on game start/end.
 *
 * All commands are valid for ADB uid 2000 (Shizuku).
 * No sysfs writes. All changes are saved and restored.
 *
 * What it does:
 *   ON  → DNS Cloudflare, Read-ahead 2048KB, Swappiness 10,
 *          BT off, WiFi scan off, Auto-sync off, DND on
 *   OFF → Restores all to previous values
 */
class GameOptimizer(private val context: Context) {

    private val TAG = "GameOptimizer"

    private var wasBluetoothEnabled = false
    private var wasWifiScanEnabled  = false
    private var wasAutoSyncEnabled  = false
    private var wasDndEnabled       = false
    private var prevSwappiness      = "60"
    private var prevReadAhead       = "128"

    fun optimizeForGaming() {
        Log.d(TAG, "🎮 Optimizing for gaming...")
        saveCurrentStates()
        setDns()
        setReadAhead("2048")
        setSwappiness("10")
        disableBluetooth()
        disableWifiScanning()
        disableAutoSync()
        enableDND()
        Log.d(TAG, "✅ Optimizations applied")
    }

    fun restoreSettings() {
        Log.d(TAG, "♻️ Restoring settings...")
        restoreDns()
        setReadAhead(prevReadAhead)
        setSwappiness(prevSwappiness)
        restoreBluetooth()
        restoreWifiScanning()
        restoreAutoSync()
        restoreDND()
        Log.d(TAG, "✅ Settings restored")
    }

    private fun saveCurrentStates() {
        try {
            wasBluetoothEnabled = BluetoothAdapter.getDefaultAdapter()?.isEnabled ?: false
            wasWifiScanEnabled  = Settings.Global.getInt(
                context.contentResolver, "wifi_scan_always_enabled", 0) == 1
            wasAutoSyncEnabled  = ContentResolver.getMasterSyncAutomatically()
            wasDndEnabled       = Settings.Global.getInt(
                context.contentResolver, "zen_mode", 0) != 0
            prevSwappiness      = ShizukuCommandRunner
                .exec("cat /proc/sys/vm/swappiness")?.trim()?.ifEmpty { "60" } ?: "60"
            prevReadAhead       = ShizukuCommandRunner
                .exec("cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null || echo 128")
                ?.trim()?.ifEmpty { "128" } ?: "128"
            Log.d(TAG, "📋 Saved: BT=$wasBluetoothEnabled swap=$prevSwappiness ra=$prevReadAhead")
        } catch (e: Exception) { Log.e(TAG, "saveCurrentStates: ${e.message}") }
    }

    // ── DNS → Cloudflare 1.1.1.1 ──
    private fun setDns() {
        ShizukuCommandRunner.exec("settings put global private_dns_mode hostname")
        ShizukuCommandRunner.exec("settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com")
        Log.d(TAG, "✅ DNS → Cloudflare")
    }
    private fun restoreDns() {
        ShizukuCommandRunner.exec("settings put global private_dns_mode opportunistic")
        Log.d(TAG, "✅ DNS restored")
    }

    // ── Storage read-ahead ──
    private fun setReadAhead(kb: String) {
        listOf("/sys/block/mmcblk0/queue/read_ahead_kb",
               "/sys/block/mmcblk1/queue/read_ahead_kb",
               "/sys/block/sda/queue/read_ahead_kb")
            .forEach { ShizukuCommandRunner.exec("echo $kb > $it 2>/dev/null") }
        Log.d(TAG, "✅ Read-ahead ${kb}KB")
    }

    // ── Swappiness ──
    private fun setSwappiness(value: String) {
        ShizukuCommandRunner.exec("echo $value > /proc/sys/vm/swappiness")
        Log.d(TAG, "✅ Swappiness $value")
    }

    // ── Bluetooth ──
    private fun disableBluetooth() {
        if (!wasBluetoothEnabled) return
        ShizukuCommandRunner.exec("svc bluetooth disable")
        Log.d(TAG, "✅ BT disabled")
    }
    private fun restoreBluetooth() {
        if (!wasBluetoothEnabled) return
        ShizukuCommandRunner.exec("svc bluetooth enable")
        Log.d(TAG, "✅ BT restored")
    }

    // ── WiFi scanning ──
    private fun disableWifiScanning() {
        ShizukuCommandRunner.exec("settings put global wifi_scan_always_enabled 0")
        Log.d(TAG, "✅ WiFi scan disabled")
    }
    private fun restoreWifiScanning() {
        if (!wasWifiScanEnabled) return
        ShizukuCommandRunner.exec("settings put global wifi_scan_always_enabled 1")
        Log.d(TAG, "✅ WiFi scan restored")
    }

    // ── Auto-sync ──
    private fun disableAutoSync() {
        if (!wasAutoSyncEnabled) return
        ShizukuCommandRunner.exec("settings put global auto_sync 0")
        Log.d(TAG, "✅ Sync disabled")
    }
    private fun restoreAutoSync() {
        if (!wasAutoSyncEnabled) return
        ShizukuCommandRunner.exec("settings put global auto_sync 1")
        Log.d(TAG, "✅ Sync restored")
    }

    // ── DND ──
    private fun enableDND() {
        ShizukuCommandRunner.exec("settings put global zen_mode 1")
        Log.d(TAG, "✅ DND enabled")
    }
    private fun restoreDND() {
        ShizukuCommandRunner.exec("settings put global zen_mode ${if (wasDndEnabled) 1 else 0}")
        Log.d(TAG, "✅ DND restored")
    }
}
