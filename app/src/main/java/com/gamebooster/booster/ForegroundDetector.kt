// PATH: app/src/main/java/com/gamebooster/booster/ForegroundDetector.kt
package com.gamebooster.booster

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

class ForegroundDetector(private val context: Context) {

    private val TAG = "ForegroundDetector"

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val knownGamePackages = setOf(
        "com.mobile.legends",
        "com.tencent.ig",
        "com.garena.game.freefire",
        "com.vng.pubgmobile",
        "com.roblox.client",
        "com.supercell.clashofclans",
        "com.supercell.clashroyale",
        "com.mojang.minecraftpe",
        "com.dts.freefireth",
        "com.activision.callofduty.shooter",
        "com.proximabeta.mf",
        "com.vng.codmvn",
    )

    fun getForegroundPackage(): String? {
        return try {
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 10_000L,
                now
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "❌ getForegroundPackage failed: ${e.message}")
            null
        }
    }

    fun isGame(packageName: String): Boolean {
        if (packageName in knownGamePackages) return true
        val keywords = listOf("game", "legends", "mobile", "shoot", "battle", "arena", "rpg", "craft")
        return keywords.any { packageName.contains(it, ignoreCase = true) }
    }
}
