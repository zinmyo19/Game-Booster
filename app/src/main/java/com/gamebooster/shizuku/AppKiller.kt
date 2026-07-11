// PATH: app/src/main/java/com/gamebooster/shizuku/AppKiller.kt
package com.gamebooster.shizuku

import android.util.Log
import com.gamebooster.booster.WhitelistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppKiller {

    private const val TAG = "AppKiller"

    suspend fun killBackgroundApps(foregroundPackage: String): Int =
        withContext(Dispatchers.IO) {
            val targets = WhitelistManager.getKillTargets()
                .filter { it != foregroundPackage }

            var killedCount = 0
            for (pkg in targets) {
                val success = ShizukuCommandRunner.forceStop(pkg)
                if (success) {
                    killedCount++
                    Log.d(TAG, "💀 Killed: $pkg")
                }
            }

            val estimatedFreedMb = killedCount * 55
            Log.d(TAG, "✅ Killed $killedCount apps, ~${estimatedFreedMb}MB freed")
            estimatedFreedMb
        }

    fun killApp(packageName: String) {
        ShizukuCommandRunner.forceStop(packageName)
    }
}
