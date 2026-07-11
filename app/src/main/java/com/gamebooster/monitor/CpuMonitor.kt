// PATH: app/src/main/java/com/gamebooster/monitor/CpuMonitor.kt
package com.gamebooster.monitor

import android.util.Log
import com.gamebooster.shizuku.ShizukuCommandRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CpuMonitor — display only.
 * Reads /proc/stat via Shizuku (uid 2000 can read it).
 * Used purely for the UI gauge. NOT used as a kill trigger.
 */
class CpuMonitor {

    private val TAG = "CpuMonitor"

    private val _cpuUsage = MutableStateFlow(0f)
    val cpuUsage: StateFlow<Float> = _cpuUsage

    private var job: Job? = null
    private var prevTotal = 0L
    private var prevIdle  = 0L

    fun start(scope: CoroutineScope) {
        prevTotal = 0L; prevIdle = 0L
        job = scope.launch(Dispatchers.IO) {
            readProcStat()          // Warmup baseline
            delay(2_000L)
            while (isActive) {
                val usage = readProcStat()
                if (usage >= 0f) _cpuUsage.value = usage
                delay(3_000L)       // 3s — display only, no need to rush
            }
        }
    }

    fun stop() { job?.cancel() }

    private fun readProcStat(): Float {
        return try {
            val output = ShizukuCommandRunner.exec("cat /proc/stat") ?: return _cpuUsage.value
            val cpuLine = output.lines().firstOrNull {
                it.startsWith("cpu ") || it.startsWith("cpu\t")
            } ?: return _cpuUsage.value

            val p       = cpuLine.trim().split(Regex("\\s+"))
            if (p.size < 5) return _cpuUsage.value

            val idle    = (p[4].toLongOrNull() ?: 0L) + (p.getOrNull(5)?.toLongOrNull() ?: 0L)
            val busy    = (p[1].toLongOrNull() ?: 0L) + (p[2].toLongOrNull() ?: 0L) +
                          (p[3].toLongOrNull() ?: 0L) + (p.getOrNull(6)?.toLongOrNull() ?: 0L) +
                          (p.getOrNull(7)?.toLongOrNull() ?: 0L)
            val total   = idle + busy
            val dTotal  = total - prevTotal
            val dIdle   = idle  - prevIdle
            prevTotal = total; prevIdle = idle
            if (dTotal <= 0L) return _cpuUsage.value
            ((dTotal - dIdle).toFloat() / dTotal.toFloat() * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            Log.e(TAG, "readProcStat: ${e.message}")
            _cpuUsage.value
        }
    }
}
