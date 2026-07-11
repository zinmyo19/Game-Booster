// PATH: app/src/main/java/com/gamebooster/shizuku/ShizukuManager.kt
package com.gamebooster.shizuku

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 101

    val isAvailable: Boolean
        get() = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    val isGranted: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "✅ Shizuku binder received")
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "⚠️ Shizuku binder died!")
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "✅ Shizuku permission granted")
                } else {
                    Log.e(TAG, "❌ Shizuku permission denied")
                }
            }
        }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun requestPermission() {
        if (!isAvailable) {
            Log.e(TAG, "❌ Shizuku not available — start Shizuku app first")
            return
        }
        if (!isGranted) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun isReady(): Boolean = isAvailable && isGranted
}
