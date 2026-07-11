// PATH: app/src/main/java/com/gamebooster/service/ShizukuKeepAlive.kt
package com.gamebooster.service

import android.util.Log
import com.gamebooster.shizuku.ShizukuManager
import kotlinx.coroutines.*

class ShizukuKeepAlive(
    private val onShizukuDied: () -> Unit,
    private val onShizukuRestored: () -> Unit
) {
    private val TAG = "ShizukuKeepAlive"
    private var job: Job? = null
    private var wasAlive = true

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val isAlive = ShizukuManager.isReady()

                if (!isAlive && wasAlive) {
                    Log.w(TAG, "⚠️ Shizuku died!")
                    wasAlive = false
                    withContext(Dispatchers.Main) { onShizukuDied() }
                } else if (isAlive && !wasAlive) {
                    Log.d(TAG, "✅ Shizuku restored!")
                    wasAlive = true
                    withContext(Dispatchers.Main) { onShizukuRestored() }
                }

                delay(30_000L)
            }
        }
    }

    fun stop() { job?.cancel() }
}
