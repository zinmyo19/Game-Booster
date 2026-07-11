// PATH: app/src/main/java/com/gamebooster/ui/MainActivity.kt
package com.gamebooster.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gamebooster.booster.BoosterEngine
import com.gamebooster.service.BoosterForegroundService
import com.gamebooster.shizuku.ShizukuManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var boosterEngine: BoosterEngine
    private lateinit var prefs: SharedPreferences

    // Session tracking
    private var sessionStartTime: Long = 0L
    private var sessionGameName: String = ""
    private var sessionGamePkg:  String = ""

    companion object {
        const val PREFS_NAME     = "GameBoosterPrefs"
        const val KEY_KILL_LIST  = "kill_list"
        const val KEY_GAMES      = "games_list"
        const val KEY_AGGRO      = "aggro"
        const val KEY_AUTO_BOOST = "auto_boost"
        const val KEY_SESSIONS   = "sessions_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess   = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                boosterEngine.start()
                // JS tryLoad() handles all data loading safely
                
                // FIX: Actually trigger the UI to load the saved Kill List and settings on startup
                loadSavedDataIntoUI() 
            }
        }

        webView.addJavascriptInterface(GameBoosterBridge(), "GameBooster")

        boosterEngine = BoosterEngine(this)

        boosterEngine.userKillList = getSavedKillList()
            .map { it.getString("pkg") ?: "" }
            .filter { it.isNotEmpty() }

        // ── Callbacks ──
        boosterEngine.onCpuUpdate = { cpu ->
            runOnUiThread {
                webView.evaluateJavascript(
                    "if(typeof updateCpu==='function')updateCpu(${cpu.toInt()})", null)
            }
        }

        boosterEngine.onRamUpdate = { ram ->
            runOnUiThread {
                webView.evaluateJavascript(
                    "if(typeof updateRam==='function')updateRam(${ram.availableMb},${ram.totalMb})", null)
            }
        }

        boosterEngine.onThermalUpdate = { t ->
            runOnUiThread {
                webView.evaluateJavascript(
                    "if(typeof updateBatTemp==='function')updateBatTemp(${t.batteryTempC.toInt()},'${t.level}')", null)
            }
        }

        boosterEngine.onFpsUpdate = { fps ->
            runOnUiThread {
                webView.evaluateJavascript(
                    "if(typeof updateFps==='function')updateFps(${fps.toInt()})", null)
            }
        }

        boosterEngine.onBoostActivated = { pkg ->
            // Record session start in Kotlin
            sessionStartTime  = System.currentTimeMillis()
            sessionGamePkg    = pkg
            sessionGameName   = getAppName(pkg)
            val name = sessionGameName.replace("'", "\\'")
            runOnUiThread {
                webView.evaluateJavascript(
                    "if(typeof onBoostActivated==='function')onBoostActivated('$name','$pkg')", null)
            }
        }

        boosterEngine.onBoostDeactivated = {
            // Record session end — save to SharedPreferences
            recordSessionEnd()
            runOnUiThread {
                webView.evaluateJavascript(
                    "if(typeof onBoostDeactivated==='function')onBoostDeactivated()", null)
                // Push fresh sessions + games data to UI
                pushSessionsToUI()
                pushGamesToUI()
            }
        }

        boosterEngine.onRamFreed = { mb ->
            runOnUiThread {
                webView.evaluateJavascript(
                    "if(typeof onRamFreed==='function')onRamFreed($mb)", null)
            }
        }

        webView.loadUrl("file:///android_asset/ui/dashboard.html")
        ShizukuManager.init()
        startForegroundService(Intent(this, BoosterForegroundService::class.java))
        checkPermissions()
    }

    // ══════════════════════════════════════════════════
    // SESSION TRACKING — stored in SharedPreferences
    // ══════════════════════════════════════════════════

    private fun recordSessionEnd() {
        if (sessionStartTime == 0L || sessionGameName.isEmpty()) return

        val now        = System.currentTimeMillis()
        val durationMs = now - sessionStartTime
        val durationSec = (durationMs / 1000).toInt()
        val mins = durationSec / 60
        val secs = durationSec % 60
        val durationStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

        val df   = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val tf   = SimpleDateFormat("hh:mm:ss a",  Locale.getDefault())
        val startCal = Calendar.getInstance().apply { timeInMillis = sessionStartTime }
        val endCal   = Calendar.getInstance().apply { timeInMillis = now }

        val session = JSONObject().apply {
            put("gameName",  sessionGameName)
            put("gamePkg",   sessionGamePkg)
            put("date",      df.format(startCal.time))
            put("startTime", tf.format(startCal.time))
            put("endTime",   tf.format(endCal.time))
            put("duration",  durationStr)
        }

        // Save session
        val sessions = getSavedSessions()
        sessions.put(session)
        prefs.edit().putString(KEY_SESSIONS, sessions.toString()).apply()

        // Increment session count in games list
        try {
            val gamesArr = JSONArray(prefs.getString(KEY_GAMES, "[]") ?: "[]")
            for (i in 0 until gamesArr.length()) {
                val g = gamesArr.getJSONObject(i)
                if (g.optString("pkg") == sessionGamePkg) {
                    g.put("sessions", g.optInt("sessions", 0) + 1)
                    break
                }
            }
            prefs.edit().putString(KEY_GAMES, gamesArr.toString()).apply()
        } catch (e: Exception) { }

        // Reset tracking
        sessionStartTime = 0L
        sessionGameName  = ""
        sessionGamePkg   = ""
    }

    private fun getSavedSessions(): JSONArray {
        return try {
            JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
        } catch (e: Exception) { JSONArray() }
    }

    private fun pushSessionsToUI() {
        webView.evaluateJavascript(
            "if(typeof loadSessions==='function')loadSessions()", null)
    }

    private fun pushGamesToUI() {
        webView.evaluateJavascript(
            "if(typeof loadGames==='function')loadGames()", null)
    }

    // ══════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════

    private fun getSavedKillList(): List<JSONObject> {
        return try {
            val arr = JSONArray(prefs.getString(KEY_KILL_LIST, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveKillList(list: String) {
        prefs.edit().putString(KEY_KILL_LIST, list).apply()
        try {
            val arr = JSONArray(list)
            boosterEngine.userKillList = (0 until arr.length())
                .map { arr.getJSONObject(it).getString("pkg") }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) { }
    }

    private fun getAppName(packageName: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) { packageName.substringAfterLast('.') }
    }

    private fun getKillableApps(): JSONArray {
        val pm  = packageManager
        val arr = JSONArray()
        val neverKill = setOf(
            "android", "com.android.systemui", "com.android.phone",
            "com.android.settings", "com.google.android.gms",
            "com.google.android.gsf", "com.android.inputmethod.latin",
            "com.samsung.android.inputmethod",
            "com.google.android.inputmethod.latin",
            "moe.shizuku.privileged.api", packageName
        )
        try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    app.packageName !in neverKill &&
                    pm.getLaunchIntentForPackage(app.packageName) != null
                }
                .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                .forEach { app ->
                    try {
                        arr.put(JSONObject().apply {
                            put("name", pm.getApplicationLabel(app).toString())
                            put("pkg",  app.packageName)
                        })
                    } catch (e: Exception) { }
                }
        } catch (e: Exception) { }
        return arr
    }

    private fun getAllUserApps():    JSONArray = getKillableApps()
    private fun getInstalledGames(): JSONArray = getKillableApps()

    private fun loadSavedDataIntoUI() {
        val aggro     = prefs.getInt(KEY_AGGRO, 2)
        val autoBoost = prefs.getBoolean(KEY_AUTO_BOOST, true)
        // JS pulls games/sessions/killList directly via bridge methods — no escaping needed
        webView.evaluateJavascript(
            "if(typeof loadSavedData==='function')loadSavedData($aggro,$autoBoost)", null)
    }

    private fun checkPermissions() {
        val ao = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        if (ao.checkOpNoThrow("android:get_usage_stats", Process.myUid(), packageName)
            != AppOpsManager.MODE_ALLOWED) {
            Toast.makeText(this, "Please grant Usage Access permission", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Display over other apps", Toast.LENGTH_LONG).show()
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
    }

    // ══════════════════════════════════════════════════
    // JavaScript Bridge
    // ══════════════════════════════════════════════════
    inner class GameBoosterBridge {

        @JavascriptInterface
        fun boostNow() {
            runOnUiThread {
                if (ShizukuManager.isReady()) boosterEngine.manualBoost()
                else Toast.makeText(this@MainActivity, "⚠️ Shizuku not ready!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun killAllApps() {
            runOnUiThread {
                if (ShizukuManager.isReady()) boosterEngine.manualKillAll()
                else Toast.makeText(this@MainActivity, "⚠️ Shizuku not ready!", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun killApp(pkg: String) {
            if (ShizukuManager.isReady())
                com.gamebooster.shizuku.ShizukuCommandRunner.forceStop(pkg)
        }

        @JavascriptInterface
        fun launchApp(pkg: String) {
            runOnUiThread {
                try {
                    val intent = packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        // Activate boost BEFORE launching
                        boosterEngine.activateBoostForGame(pkg)
                        android.os.Handler(mainLooper).postDelayed({
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }, 800L)
                    } else {
                        Toast.makeText(this@MainActivity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Launch failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Called from JS when user returns to app — ends the session
        @JavascriptInterface
        fun endSession() {
            recordSessionEnd()
            runOnUiThread {
                pushSessionsToUI()
                pushGamesToUI()
            }
        }

        @JavascriptInterface
        fun saveKillList(jsonList: String) { this@MainActivity.saveKillList(jsonList) }

        @JavascriptInterface
        fun saveGames(jsonList: String) {
            prefs.edit().putString(KEY_GAMES, jsonList).apply()
        }

        @JavascriptInterface
        fun saveSetting(key: String, value: String) {
            when (key) {
                "aggro"     -> prefs.edit().putInt(KEY_AGGRO, value.toIntOrNull() ?: 2).apply()
                "autoBoost" -> prefs.edit().putBoolean(KEY_AUTO_BOOST, value == "true").apply()
            }
        }

        // ── Data getters — JS pulls directly, no string escaping issues ──
        @JavascriptInterface
        fun getGames(): String = prefs.getString(KEY_GAMES, "[]") ?: "[]"

        @JavascriptInterface
        fun getKillList(): String = prefs.getString(KEY_KILL_LIST, "[]") ?: "[]"

        @JavascriptInterface
        fun getSessions(): String = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"

        @JavascriptInterface
        fun getSetting(key: String): String = when (key) {
            "aggro"     -> prefs.getInt(KEY_AGGRO, 2).toString()
            "autoBoost" -> prefs.getBoolean(KEY_AUTO_BOOST, true).toString()
            else        -> ""
        }

        @JavascriptInterface
        fun saveSessionsJson(json: String) {
            prefs.edit().putString(KEY_SESSIONS, json).apply()
        }

        @JavascriptInterface
        fun resetGameSessions(pkg: String) {
            try {
                val gamesArr = JSONArray(prefs.getString(KEY_GAMES, "[]") ?: "[]")
                for (i in 0 until gamesArr.length()) {
                    val g = gamesArr.getJSONObject(i)
                    if (g.optString("pkg") == pkg) { g.put("sessions", 0); break }
                }
                prefs.edit().putString(KEY_GAMES, gamesArr.toString()).apply()
            } catch (e: Exception) { }
        }

        @JavascriptInterface fun isShizukuReady(): Boolean = ShizukuManager.isReady()
        @JavascriptInterface fun requestShizuku() { runOnUiThread { ShizukuManager.requestPermission() } }
        @JavascriptInterface fun setAutoBoost(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUTO_BOOST, enabled).apply() }
        @JavascriptInterface fun setAggro(level: Int) { prefs.edit().putInt(KEY_AGGRO, level).apply() }
        @JavascriptInterface fun addGame(pkg: String, profile: String) { }
        @JavascriptInterface fun setGameProfile(pkg: String, profile: String) { }
        @JavascriptInterface fun getInstalledGames(): String = this@MainActivity.getInstalledGames().toString()
        @JavascriptInterface fun getKillableApps():   String = this@MainActivity.getKillableApps().toString()
        @JavascriptInterface fun getAllUserApps():     String = this@MainActivity.getAllUserApps().toString()

        @JavascriptInterface
        fun getAppIcon(pkg: String): String {
            return try {
                val drawable = packageManager.getApplicationIcon(pkg)
                val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bmp = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth.takeIf { it > 0 } ?: 48,
                        drawable.intrinsicHeight.takeIf { it > 0 } ?: 48,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
                val stream = java.io.ByteArrayOutputStream()
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 96, 96, true)
                scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, stream)
                android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface fun getPerformanceMode(): String = boosterEngine.performanceMode.name

        @JavascriptInterface
        fun setPerformanceMode(mode: String) {
            runOnUiThread {
                val pm = com.gamebooster.booster.PerformanceMode.fromString(mode)
                boosterEngine.setPerformanceMode(pm)
                Toast.makeText(this@MainActivity, "⚙️ Mode: ${pm.displayName}", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun requestPerformanceMode() {
            runOnUiThread {
                val mode = boosterEngine.performanceMode.name
                webView.evaluateJavascript(
                    "if(typeof setPerformanceMode==='function')setPerformanceMode('$mode')", null)
            }
        }
    }

    // End session when user comes back to the app
    override fun onResume() {
        super.onResume()
        if (sessionStartTime > 0L) {
            recordSessionEnd()
            webView.evaluateJavascript(
                "if(typeof onBoostDeactivated==='function')onBoostDeactivated()", null)
            pushSessionsToUI()
            pushGamesToUI()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (sessionStartTime > 0L) recordSessionEnd()
        boosterEngine.stop()
        ShizukuManager.destroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
