// PATH: app/src/main/java/com/gamebooster/booster/BoosterConfig.kt
package com.gamebooster.booster

/**
 * All configurable thresholds in one place.
 * Change these without touching any logic.
 */
object BoosterConfig {

    // ── Frame drop detection ──
    // How many consecutive slow seconds before triggering a kill
    const val FRAME_LAG_TRIGGER_SECONDS = 3
    // Frame time above this (ms) is considered slow (16ms = 60fps)
    const val FRAME_LAG_THRESHOLD_MS    = 20L

    // ── Memory pressure ──
    // Available RAM below this → trigger Level 2 kill (MB)
    const val RAM_PRESSURE_THRESHOLD_MB = 300L
    // Available RAM below this → trigger Level 3 emergency kill (MB)
    const val RAM_CRITICAL_THRESHOLD_MB = 150L

    // ── Cooldown — prevents over-aggressive behaviour ──
    // Minimum ms between any two kill events
    const val KILL_COOLDOWN_MS          = 45_000L   // 45 seconds

    // ── Kill levels ──
    // Level 1: only user-added kill list (lightest)
    // Level 2: user list + social/webview apps
    // Level 3: everything in whitelist (emergency)
    const val KILL_LEVEL_LIGHT     = 1
    const val KILL_LEVEL_BALANCED  = 2
    const val KILL_LEVEL_EMERGENCY = 3
}
