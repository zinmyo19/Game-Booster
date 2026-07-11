// PATH: app/src/main/java/com/gamebooster/monitor/FrameDropDetector.kt
package com.gamebooster.monitor

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import com.gamebooster.booster.BoosterConfig

/**
 * FrameDropDetector — event-driven trigger using Choreographer.
 *
 * Choreographer fires on every VSYNC signal (each display frame).
 * We measure the gap between consecutive frames.
 * If avgFrameMs > FRAME_LAG_THRESHOLD_MS for N consecutive seconds,
 * onFrameDrop() fires → triggers a kill event.
 *
 * This replaces the GPU polling trigger entirely.
 * No sysfs reads needed. Works on all devices.
 */
class FrameDropDetector(
    private val onFrameDrop: () -> Unit
) {
    private val TAG = "FrameDropDetector"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning   = false

    // Per-second averaging
    private var lastFrameNanos  = 0L
    private var secondStartNanos = 0L
    private val frameSamples    = mutableListOf<Long>()

    // Consecutive slow-second counter
    private var slowSeconds = 0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastFrameNanos > 0L) {
                val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000L

                // Filter out junk values (resume gaps, screen-off, etc.)
                if (deltaMs in 1L..500L) {
                    frameSamples.add(deltaMs)

                    val elapsedMs = (frameTimeNanos - secondStartNanos) / 1_000_000L
                    if (elapsedMs >= 1000L) {
                        val avgMs = frameSamples.average().toLong()
                        frameSamples.clear()
                        secondStartNanos = frameTimeNanos

                        if (avgMs > BoosterConfig.FRAME_LAG_THRESHOLD_MS) {
                            slowSeconds++
                            Log.d(TAG, "⚠️ Slow frame avg: ${avgMs}ms ($slowSeconds/${BoosterConfig.FRAME_LAG_TRIGGER_SECONDS})")
                            if (slowSeconds >= BoosterConfig.FRAME_LAG_TRIGGER_SECONDS) {
                                Log.w(TAG, "🔥 Frame drop sustained — triggering optimize")
                                slowSeconds = 0
                                // Post to main thread — caller decides what to do
                                mainHandler.post { onFrameDrop() }
                            }
                        } else {
                            // Smooth — reset counter
                            if (slowSeconds > 0) Log.d(TAG, "✅ Frames smooth again")
                            slowSeconds = 0
                        }
                    }
                }
            } else {
                secondStartNanos = frameTimeNanos
            }

            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning        = true
        lastFrameNanos   = 0L
        slowSeconds      = 0
        secondStartNanos = 0L
        frameSamples.clear()
        Log.d(TAG, "▶ FrameDropDetector started")
        mainHandler.post {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun stop() {
        isRunning = false
        mainHandler.post {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
        frameSamples.clear()
        slowSeconds = 0
        Log.d(TAG, "⏹ FrameDropDetector stopped")
    }

    fun resetCounter() {
        slowSeconds = 0
        frameSamples.clear()
    }
}
