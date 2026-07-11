// PATH: app/src/main/java/com/gamebooster/service/BoosterForegroundService.kt
package com.gamebooster.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gamebooster.booster.BoosterEngine
import com.gamebooster.shizuku.ShizukuManager
import com.gamebooster.ui.MainActivity

class BoosterForegroundService : LifecycleService() {

    companion object {
        const val CHANNEL_ID    = "gamebooster_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP   = "com.gamebooster.STOP"
    }

    private lateinit var boosterEngine: BoosterEngine
    private lateinit var keepAlive: ShizukuKeepAlive

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("⚡ GameBooster running..."))

        ShizukuManager.init()

        boosterEngine = BoosterEngine(this)
        boosterEngine.onBoostActivated = { pkg ->
            updateNotification("🔥 Boosting: ${pkg.substringAfterLast('.')}")
        }
        boosterEngine.onBoostDeactivated = {
            updateNotification("⚡ GameBooster running...")
        }
        boosterEngine.onRamFreed = { mb ->
            updateNotification("💀 Freed ${mb}MB RAM")
        }
        boosterEngine.start()

        keepAlive = ShizukuKeepAlive(
            onShizukuDied     = { updateNotification("⚠️ Shizuku stopped — reopen Shizuku app") },
            onShizukuRestored = { updateNotification("✅ Shizuku reconnected") }
        )
        keepAlive.start(lifecycleScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        boosterEngine.stop()
        keepAlive.stop()
        ShizukuManager.destroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GameBooster",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "GameBooster background service"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, BoosterForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GameBooster")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_delete, "Stop", pendingStop)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
