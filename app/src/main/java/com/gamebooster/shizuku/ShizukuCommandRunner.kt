// PATH: app/src/main/java/com/gamebooster/shizuku/ShizukuCommandRunner.kt
package com.gamebooster.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * ShizukuCommandRunner — safe Shizuku IPC layer.
 *
 * Changes from original:
 * - setCpuGovernor() REMOVED — sysfs locked on non-rooted devices
 * - exec() returns null immediately if disconnected (no blind wait)
 * - Binder death handled cleanly via onServiceDisconnected
 */
object ShizukuCommandRunner {

    private const val TAG = "ShizukuCmd"

    @Volatile private var shellService: IShellService? = null
    @Volatile private var isBinding    = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.gamebooster", ShellService::class.java.name)
    ).daemon(false).processNameSuffix("shell_service").debuggable(false).version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shellService = if (binder != null && binder.pingBinder())
                IShellService.Stub.asInterface(binder).also { Log.d(TAG, "✅ ShellService connected") }
            else
                null.also { Log.e(TAG, "❌ Binder null or dead") }
            isBinding = false
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "⚠️ ShellService disconnected — binder died")
            shellService = null
            isBinding    = false
        }
    }

    fun connect() {
        if (shellService != null || isBinding) return
        if (!ShizukuManager.isReady()) { Log.w(TAG, "⚠️ Shizuku not ready"); return }
        try {
            isBinding = true
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            Log.d(TAG, "🔌 Binding ShellService...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ bindUserService: ${e.message}")
            isBinding = false
        }
    }

    fun disconnect() {
        try {
            if (shellService != null) {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
                shellService = null
                isBinding    = false
                Log.d(TAG, "🔌 ShellService unbound")
            }
        } catch (e: Exception) { Log.e(TAG, "❌ unbindUserService: ${e.message}") }
    }

    /**
     * Execute a shell command.
     * Returns null immediately if Shizuku is not connected — no blocking wait.
     * Caller must handle null gracefully.
     */
    fun exec(command: String): String? {
        val svc = shellService
        if (svc == null) {
            // Attempt reconnect for next call — don't block this call
            if (!isBinding) connect()
            Log.w(TAG, "⚠️ ShellService not ready, skipping: ${command.take(30)}")
            return null
        }
        return try {
            svc.exec(command).also {
                Log.d(TAG, "exec[${command.take(30)}] → ${it?.take(40)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ exec failed [${command.take(30)}]: ${e.message}")
            // Binder likely dead — reset so reconnect happens next call
            shellService = null
            isBinding    = false
            null
        }
    }

    fun forceStop(pkg: String):    Boolean = exec("am force-stop $pkg") != null
    fun compactApp(pkg: String):   Boolean = exec("am compact $pkg") != null
    fun isConnected():             Boolean = shellService != null
}
