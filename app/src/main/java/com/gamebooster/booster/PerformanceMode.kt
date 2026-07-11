// PATH: app/src/main/java/com/gamebooster/booster/PerformanceMode.kt
package com.gamebooster.booster

/**
 * Behaviour-based kill aggressiveness modes.
 * NO hardware-level control — names reflect what actually happens.
 *
 * All modes apply:
 *   - Background app killing (level varies)
 *   - Bluetooth disable
 *   - WiFi scan disable
 *   - Auto-sync disable
 *   - DND enable
 *   - DNS → Cloudflare 1.1.1.1
 *   - Storage read-ahead 2048KB
 *   - Swappiness lowered to 10
 */
enum class PerformanceMode(
    val displayName: String,
    val description: String,
    val killLevel: Int              // Maps to BoosterConfig.KILL_LEVEL_*
) {
    LIGHT(
        displayName = "Light",
        description = "Only your custom kill list — minimal interference",
        killLevel   = BoosterConfig.KILL_LEVEL_LIGHT
    ),
    BALANCED(
        displayName = "Balanced",
        description = "Custom list + social & browser apps — recommended",
        killLevel   = BoosterConfig.KILL_LEVEL_BALANCED
    ),
    AGGRESSIVE(
        displayName = "Aggressive Background Control",
        description = "Everything in kill list — max free RAM",
        killLevel   = BoosterConfig.KILL_LEVEL_EMERGENCY
    );

    companion object {
        fun fromString(value: String?): PerformanceMode = when (value?.uppercase()) {
            "LIGHT"      -> LIGHT
            "BALANCED"   -> BALANCED
            "AGGRESSIVE" -> AGGRESSIVE
            else         -> BALANCED
        }
    }
}
