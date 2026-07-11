// PATH: app/src/main/java/com/gamebooster/shizuku/ShellService.kt
package com.gamebooster.shizuku

import android.util.Log

/**
 * ShellService runs INSIDE the Shizuku server process
 * as ADB shell user (uid 2000).
 *
 * When this class calls Runtime.getRuntime().exec(),
 * it executes with ADB permissions — can read /proc/stat,
 * run am force-stop, dumpsys etc.
 *
 * This is the proper Shizuku v13 replacement for newProcess()
 */
class ShellService : IShellService.Stub() {

    private val TAG = "ShellService"

    override fun exec(command: String): String? {
        return try {
            Log.d(TAG, "📟 Executing: $command")
            // This runs as ADB shell uid=2000 inside Shizuku process
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output  = process.inputStream.bufferedReader().readText()
            val error   = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (error.isNotBlank()) Log.w(TAG, "stderr: $error")
            val result = output.trim()
            Log.d(TAG, "✅ Result: ${result.take(80)}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ exec failed: ${e.message}")
            null
        }
    }
}
