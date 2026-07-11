// PATH: app/src/main/java/com/gamebooster/monitor/FpsMonitor.kt
package com.gamebooster.monitor

import android.util.Log
import com.gamebooster.shizuku.ShizukuCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FpsMonitor — display only, via dumpsys SurfaceFlinger.
 * Used only while a game session is active.
 * NOT used as a trigger — FrameDropDetector handles that.
 */
class FpsMonitor {

    private val TAG = "FpsMonitor"

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps

    private var job: Job? = null

    fun start(scope: CoroutineScope, packageName: String) {
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                _fps.value = readFps(packageName)
                delay(2_000L)
            }
        }
    }

    fun stop() { job?.cancel(); _fps.value = 0f }

    private fun readFps(packageName: String): Float {
        return try {
            val layers = ShizukuCommandRunner.exec("dumpsys SurfaceFlinger --list") ?: return 0f
            val layer  = layers.lines().firstOrNull {
                it.contains(packageName, ignoreCase = true)
            } ?: return 0f

            val latency = ShizukuCommandRunner.exec(
                "dumpsys SurfaceFlinger --latency \"$layer\""
            ) ?: return 0f

            val timestamps = latency.lines().drop(1)
                .mapNotNull { it.trim().split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull() }
                .filter { it > 0 }

            if (timestamps.size < 2) return 0f

            val intervals = timestamps.zipWithNext { a, b -> b - a }
                .filter { it in 1_000_000L..100_000_000L }

            if (intervals.isEmpty()) return 0f

            (1_000_000_000.0 / intervals.average()).toFloat().coerceIn(0f, 240f)
        } catch (e: Exception) {
            Log.e(TAG, "readFps: ${e.message}")
            0f
        }
    }
}
