// PATH: app/src/main/java/com/gamebooster/monitor/RamMonitor.kt
package com.gamebooster.monitor

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.gamebooster.booster.BoosterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RamInfo(
    val totalMb: Long,
    val availableMb: Long,
    val usedMb: Long,
    val usedPercent: Float
)

class RamMonitor(private val context: Context) {

    private val TAG = "RamMonitor"

    private val _ramInfo = MutableStateFlow(RamInfo(0L, 0L, 0L, 0f))
    val ramInfo: StateFlow<RamInfo> = _ramInfo

    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var job: Job? = null

    // Callbacks for pressure events — only fired when threshold crossed
    var onMemoryPressure:  (() -> Unit)? = null   // < 300MB
    var onMemoryCritical:  (() -> Unit)? = null   // < 150MB

    private var lastPressureState = 0  // 0=ok, 1=pressure, 2=critical

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val info = readRamInfo()
                _ramInfo.value = info

                // Event-driven — only fire callback when state CHANGES
                val newState = when {
                    info.availableMb < BoosterConfig.RAM_CRITICAL_THRESHOLD_MB -> 2
                    info.availableMb < BoosterConfig.RAM_PRESSURE_THRESHOLD_MB -> 1
                    else -> 0
                }
                if (newState != lastPressureState) {
                    lastPressureState = newState
                    when (newState) {
                        1 -> { Log.w(TAG, "⚠️ Memory pressure: ${info.availableMb}MB")
                               withContext(Dispatchers.Main) { onMemoryPressure?.invoke() } }
                        2 -> { Log.e(TAG, "🚨 Memory critical: ${info.availableMb}MB")
                               withContext(Dispatchers.Main) { onMemoryCritical?.invoke() } }
                        0 -> Log.d(TAG, "✅ Memory normal: ${info.availableMb}MB")
                    }
                }

                // Poll every 3s — only for UI display, not kill trigger
                delay(3_000L)
            }
        }
    }

    fun stop() { job?.cancel() }

    fun readRamInfo(): RamInfo {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total   = mi.totalMem  / (1024 * 1024)
        val avail   = mi.availMem  / (1024 * 1024)
        val used    = total - avail
        return RamInfo(total, avail, used, (used.toFloat() / total.toFloat()) * 100f)
    }
}
