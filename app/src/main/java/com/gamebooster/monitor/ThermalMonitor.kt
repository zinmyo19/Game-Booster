// PATH: app/src/main/java/com/gamebooster/monitor/ThermalMonitor.kt
package com.gamebooster.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ThermalMonitor — real battery temperature ONLY.
 * CPU temperature estimation removed — it was fake.
 *
 * Battery temp is used for UI display and user awareness.
 * It does NOT trigger any automated action.
 *
 * Thresholds:
 *   < 37°C  → Cool
 *   37–42°C → Warm
 *   > 42°C  → Hot
 */
data class ThermalInfo(
    val batteryTempC: Float,
    val level: ThermalLevel
)

enum class ThermalLevel { COOL, WARM, HOT }

class ThermalMonitor(private val context: Context) {

    private val TAG = "ThermalMonitor"

    private val _thermalInfo = MutableStateFlow(ThermalInfo(0f, ThermalLevel.COOL))
    val thermalInfo: StateFlow<ThermalInfo> = _thermalInfo

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val temp  = readBatteryTemp()
                val level = when {
                    temp >= 42f -> ThermalLevel.HOT
                    temp >= 37f -> ThermalLevel.WARM
                    else        -> ThermalLevel.COOL
                }
                _thermalInfo.value = ThermalInfo(temp, level)
                Log.d(TAG, "🔋 Battery: ${temp}°C [$level]")
                delay(5_000L)   // 5s — display only, low priority
            }
        }
    }

    fun stop() { job?.cancel() }

    private fun readBatteryTemp(): Float {
        return try {
            val intent = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        } catch (e: Exception) {
            Log.e(TAG, "readBatteryTemp: ${e.message}")
            0f
        }
    }
}
