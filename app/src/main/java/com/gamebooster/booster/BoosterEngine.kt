// PATH: app/src/main/java/com/gamebooster/booster/BoosterEngine.kt
package com.gamebooster.booster

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.gamebooster.monitor.*
import com.gamebooster.shizuku.ShizukuCommandRunner
import com.gamebooster.shizuku.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * BoosterEngine — refactored into 3 layers:
 *
 * 1. EVENT DETECTION LAYER
 *    - FrameDropDetector (Choreographer)  → primary trigger
 *    - RamMonitor pressure callbacks      → secondary trigger
 *
 * 2. DECISION LAYER
 *    - Cooldown system prevents over-kill
 *    - Priority-based kill level selection
 *    - Shizuku availability check before every action
 *
 * 3. ACTION LAYER
 *    - performKill(level) — tiered kill logic
 *    - gameOptimizer.optimizeForGaming()
 *
 * Removed:
 *   - GPU usage polling as trigger
 *   - Battery thermal estimation
 *   - Fixed 60s re-kill loop
 *   - CPU governor control
 */
class BoosterEngine(private val context: Context) {

    lateinit var onThermalCritical: Any
    private val TAG = "BoosterEngine"

    // ── Monitors (display only, not used as triggers) ──
    val cpuMonitor      = CpuMonitor()
    val ramMonitor      = RamMonitor(context)
    val thermalMonitor  = ThermalMonitor(context)
    val fpsMonitor      = FpsMonitor()

    // ── Event Detection Layer ──
    val frameDropDetector = FrameDropDetector { onFrameDropDetected() }

    // ── Supporting components ──
    val foregroundDetector = ForegroundDetector(context)
    val gameOptimizer      = GameOptimizer(context)

    private var engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── State ──
    var isBoostActive      = false; private set
    var activeGamePackage  = "";    private set
    var performanceMode    = PerformanceMode.BALANCED; private set

    // ── Cooldown — prevents oscillation ──
    private var lastKillTimeMs = 0L

    // ── Installed packages cache for smart kill ──
    private val installedPackages: Set<String> by lazy {
        try {
            context.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    private val prefs = context.getSharedPreferences("GameBoosterPrefs", Context.MODE_PRIVATE)

    // ── Callbacks → UI ──
    var onBoostActivated:   ((String) -> Unit)?      = null
    var onBoostDeactivated: (() -> Unit)?             = null
    var onRamFreed:         ((Int) -> Unit)?          = null
    var onCpuUpdate:        ((Float) -> Unit)?        = null
    var onRamUpdate:        ((RamInfo) -> Unit)?      = null
    var onThermalUpdate:    ((ThermalInfo) -> Unit)?  = null
    var onFpsUpdate:        ((Float) -> Unit)?        = null

    // Keep kill list in sync with what user saved
    var userKillList: List<String> = emptyList()

    // ════════════════════════════════════════════════
    // START
    // ════════════════════════════════════════════════
    fun start() {
        Log.d(TAG, "🚀 BoosterEngine starting")
        loadPerformanceMode()
        ShizukuCommandRunner.connect()

        // CPU → UI only
        cpuMonitor.start(engineScope)
        engineScope.launch {
            cpuMonitor.cpuUsage.collectLatest { v ->
                withContext(Dispatchers.Main) { onCpuUpdate?.invoke(v) }
            }
        }

        // RAM → UI + event callbacks
        ramMonitor.onMemoryPressure = {
            if (isBoostActive) triggerKill(BoosterConfig.KILL_LEVEL_BALANCED, "RAM pressure")
        }
        ramMonitor.onMemoryCritical = {
            if (isBoostActive) triggerKill(BoosterConfig.KILL_LEVEL_EMERGENCY, "RAM critical")
        }
        ramMonitor.start(engineScope)
        engineScope.launch {
            ramMonitor.ramInfo.collectLatest { v ->
                withContext(Dispatchers.Main) { onRamUpdate?.invoke(v) }
            }
        }

        // Thermal → UI only (display, no auto-action)
        thermalMonitor.start(engineScope)
        engineScope.launch {
            thermalMonitor.thermalInfo.collectLatest { v ->
                withContext(Dispatchers.Main) { onThermalUpdate?.invoke(v) }
            }
        }

        // FrameDropDetector started only when game is active (in activateBoost)
        Log.d(TAG, "✅ BoosterEngine started — mode: ${performanceMode.displayName}")
    }

    // ════════════════════════════════════════════════
    // STOP
    // ════════════════════════════════════════════════
    fun stop() {
        frameDropDetector.stop()
        fpsMonitor.stop()
        ShizukuCommandRunner.disconnect()
        engineScope.cancel()
        cpuMonitor.stop()
        ramMonitor.stop()
        thermalMonitor.stop()
        engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        Log.d(TAG, "🛑 BoosterEngine stopped")
    }

    // ════════════════════════════════════════════════
    // EVENT DETECTION → DECISION LAYER
    // ════════════════════════════════════════════════

    /** Called by FrameDropDetector when sustained lag detected */
    private fun onFrameDropDetected() {
        if (!isBoostActive) return
        triggerKill(performanceMode.killLevel, "frame drop")
    }

    /**
     * Core decision gate.
     * Checks cooldown, then executes kill at given level.
     */
    private fun triggerKill(level: Int, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastKillTimeMs < BoosterConfig.KILL_COOLDOWN_MS) {
            Log.d(TAG, "⏳ Kill cooldown active, skipping ($reason)")
            return
        }
        if (!ShizukuManager.isReady()) {
            Log.w(TAG, "⚠️ Shizuku not ready — skipping kill ($reason)")
            return
        }
        lastKillTimeMs = now
        Log.d(TAG, "🎯 Kill triggered: $reason [Level $level]")
        engineScope.launch(Dispatchers.IO) {
            val freed = performKill(level, activeGamePackage)
            if (freed > 0) {
                frameDropDetector.resetCounter()    // Reset after successful kill
                withContext(Dispatchers.Main) { onRamFreed?.invoke(freed) }
            }
        }
    }

    // ════════════════════════════════════════════════
    // ACTION LAYER — TIERED KILL LOGIC
    // ════════════════════════════════════════════════

    /**
     * Level 1 — Light: only user's custom kill list
     * Level 2 — Balanced: user list + social + webview
     * Level 3 — Emergency: everything in whitelist
     */
    private fun performKill(level: Int, foregroundPkg: String): Int {
        val neverKill = WhitelistManager.NEVER_KILL + setOf(foregroundPkg, context.packageName)

        val toKill: Set<String> = when (level) {
            BoosterConfig.KILL_LEVEL_LIGHT ->
                userKillList.filter { it.isNotEmpty() }.toSet() - neverKill

            BoosterConfig.KILL_LEVEL_BALANCED ->
                (userKillList.filter { it.isNotEmpty() }.toSet() +
                 WhitelistManager.getInstalledKillTargets(installedPackages)) - neverKill

            else -> // EMERGENCY — everything
                (userKillList.filter { it.isNotEmpty() }.toSet() +
                 WhitelistManager.getSmartKillTargets()) - neverKill
        }.filter { it.isNotEmpty() }.toSet()

        if (toKill.isEmpty()) { Log.d(TAG, "ℹ️ Nothing to kill [L$level]"); return 0 }

        var killed = 0
        for (pkg in toKill) {
            if (ShizukuCommandRunner.forceStop(pkg)) {
                killed++
                Log.d(TAG, "💀 Killed [$level]: $pkg")
            }
        }
        val mb = killed * 55
        Log.d(TAG, "✅ Kill L$level: $killed apps, ~${mb}MB freed")
        return mb
    }

    // ════════════════════════════════════════════════
    // BOOST ACTIVATE / DEACTIVATE
    // ════════════════════════════════════════════════

    fun activateBoostForGame(gamePackage: String) = activateBoost(gamePackage)

    private fun activateBoost(gamePackage: String) {
        if (isBoostActive && activeGamePackage == gamePackage) return
        isBoostActive     = true
        activeGamePackage = gamePackage
        lastKillTimeMs    = 0L  // Reset cooldown on new game launch

        withMain { onBoostActivated?.invoke(gamePackage) }

        engineScope.launch(Dispatchers.IO) {
            // Apply system-level optimizations
            gameOptimizer.optimizeForGaming()
            // Initial kill at selected level
            val freed = performKill(performanceMode.killLevel, gamePackage)
            withContext(Dispatchers.Main) { if (freed > 0) onRamFreed?.invoke(freed) }
            // Start FPS display monitor
            withContext(Dispatchers.Main) {
                fpsMonitor.stop()
                fpsMonitor.start(engineScope, gamePackage)
                engineScope.launch {
                    fpsMonitor.fps.collectLatest { v ->
                        withContext(Dispatchers.Main) { onFpsUpdate?.invoke(v) }
                    }
                }
            }
        }
        // Start frame drop detection
        frameDropDetector.start()
        Log.d(TAG, "⚡ Boost activated for $gamePackage [${performanceMode.displayName}]")
    }

    fun deactivateBoost() {
        if (!isBoostActive) return
        isBoostActive     = false
        activeGamePackage = ""
        frameDropDetector.stop()
        fpsMonitor.stop()
        engineScope.launch(Dispatchers.IO) { gameOptimizer.restoreSettings() }
        withMain { onBoostDeactivated?.invoke() }
        Log.d(TAG, "✅ Boost deactivated")
    }

    // ════════════════════════════════════════════════
    // MANUAL ACTIONS (from UI buttons)
    // ════════════════════════════════════════════════

    fun manualBoost() {
        lastKillTimeMs = 0L   // Manual boost bypasses cooldown
        engineScope.launch(Dispatchers.IO) {
            val freed = performKill(performanceMode.killLevel, activeGamePackage)
            withContext(Dispatchers.Main) { onRamFreed?.invoke(freed) }
        }
    }

    fun manualKillAll() = manualBoost()

    // ════════════════════════════════════════════════
    // PERFORMANCE MODE
    // ════════════════════════════════════════════════

    fun setPerformanceMode(mode: PerformanceMode) {
        performanceMode = mode
        prefs.edit().putString("performance_mode", mode.name).apply()
        Log.d(TAG, "⚙️ Mode: ${mode.displayName}")
    }

    private fun loadPerformanceMode() {
        performanceMode = PerformanceMode.fromString(
            prefs.getString("performance_mode", "BALANCED")
        )
    }

    private fun withMain(block: () -> Unit) {
        engineScope.launch(Dispatchers.Main) { block() }
    }
}
