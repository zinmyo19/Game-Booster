// PATH: app/src/main/java/com/gamebooster/booster/WhitelistManager.kt
package com.gamebooster.booster

/**
 * WhitelistManager — defines what to kill and what to NEVER kill
 *
 * Strategy based on:
 * 1. High-Entropy Background Services (social media sync engines)
 * 2. Memory-Resident Cached Processes (webview/browser heavy apps)
 * 3. Non-Critical Third-Party Bloat (analytics, secondary app stores)
 *
 * Goal: Minimize interrupt latency and give 100% high-performance
 * CPU cores to the game's PID
 */
object WhitelistManager {

    // ══════════════════════════════════════════════════════════
    // NEVER KILL — System stability & game functionality
    // Killing these causes kernel panic, soft reboot, or
    // game login/license verification failures
    // ══════════════════════════════════════════════════════════
    val NEVER_KILL = setOf(
        // Core Android system
        "android",
        "com.android.systemui",          // Status bar & navigation — kills UI if stopped
        "com.android.phone",             // Phone calls
        "com.android.settings",          // Settings app
        "com.android.server",            // Android server process

        // Google Play Services — killing causes game login/license failures
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.gms.persistent",

        // Input Method Editors — keyboard
        "com.android.inputmethod.latin",
        "com.samsung.android.inputmethod",
        "com.google.android.inputmethod.latin",
        "com.miui.inputmethod",
        "com.touchtype.swiftkey",
        "com.swiftkey.swiftkeyapp",
        "com.nuance.swype.input",

        // Shizuku — killing this stops the booster working!
        "moe.shizuku.privileged.api",
        "rikka.shizuku",

        // Connectivity & telephony
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.wifi",
        "com.android.providers.telephony",
        "com.android.providers.settings",

        // MediaTek / Qualcomm HAL daemons
        "com.mediatek.batterywarning",
        "com.mediatek.bluetooth",

        // Samsung critical
        "com.samsung.android.app.omcagent",
        "com.samsung.android.sm.policy",
    )

    // ══════════════════════════════════════════════════════════
    // SMART KILL TARGETS
    // Category 1: High-Entropy Background Services
    // Social media sync engines — frequent CPU wakeups via
    // AlarmManager/JobScheduler causing frame jitter
    // ══════════════════════════════════════════════════════════
    private val CATEGORY_SOCIAL_SYNC = setOf(
        "com.facebook.katana",           // Facebook — notorious CPU hog
        "com.facebook.orca",             // Messenger
        "com.facebook.lite",
        "com.instagram.android",         // Instagram sync
        "com.twitter.android",
        "com.twitter.lite",
        "com.snapchat.android",          // Snapchat persistent wakelock
        "com.zhiliaoapp.musically",      // TikTok
        "com.ss.android.ugc.trill",      // TikTok alt
        "com.whatsapp",                  // WhatsApp sync
        "com.whatsapp.w4b",
        "org.telegram.messenger",        // Telegram
        "com.viber.voip",
        "kik.android",
        "com.skype.raider",
        "com.linkedin.android",
        "com.pinterest",
        "com.reddit.frontpage",
        "com.tumblr",
    )

    // ══════════════════════════════════════════════════════════
    // Category 2: Memory-Resident Cached Processes
    // Webview-heavy apps with large heap allocations
    // causing memory fragmentation & GC pauses
    // ══════════════════════════════════════════════════════════
    private val CATEGORY_WEBVIEW_HEAVY = setOf(
        "com.android.chrome",            // Chrome browser
        "org.mozilla.firefox",           // Firefox
        "com.opera.browser",
        "com.opera.mini.native",
        "com.UCMobile.intl",             // UC Browser
        "com.uc.browser.en",
        "com.brave.browser",
        "com.microsoft.emmx",            // Edge
        "com.sec.android.app.sbrowser",  // Samsung Browser
        "com.mi.globalbrowser",          // MIUI Browser
    )

    // ══════════════════════════════════════════════════════════
    // Category 3: Non-Critical Third-Party Bloat
    // Analytics tools, secondary app stores, cleaners
    // that cause context switching overhead
    // ══════════════════════════════════════════════════════════
    private val CATEGORY_BLOAT = setOf(
        // Google apps (non-essential during gaming)
        "com.google.android.googlequicksearchbox",
        "com.google.android.youtube",
        "com.google.android.apps.photos",
        "com.google.android.gm",         // Gmail
        "com.google.android.apps.maps",
        "com.google.android.videos",
        "com.google.android.music",
        "com.google.android.podcasts",

        // App stores
        "com.android.vending",           // Play Store UI (not services)
        "com.IDO.MeatBall",              // iDUS AppStore
        "com.ido.meatball",
        "com.aptoide.partners",
        "cm.aptoide.pt",
        "com.amazon.mShop.android.shopping",

        // OEM Analytics & Ads (high CPU on MIUI/Samsung)
        "com.miui.analytics",
        "com.miui.msa.global",
        "com.miui.daemon",
        "com.samsung.android.app.tips",
        "com.samsung.android.messaging",

        // Cleaners & battery savers (ironic — they use CPU)
        "com.cleanmaster.mguard",
        "com.cleanmaster.sdk",
        "com.dianxinos.dxbs",
        "com.iobit.mobilecare",
        "com.idea.backup.smscontacts",

        // Shopping / streaming
        "com.flipkart.android",
        "in.amazon.mShop.android.shopping",
        "com.netflix.mediaclient",
        "com.spotify.music",
        "com.amazon.mp3",
        "com.gaana",
        "com.jio.media.jiocinema",
    )

    // ══════════════════════════════════════════════════════════
    // User's custom additions from UI
    // ══════════════════════════════════════════════════════════
    private val userAddedTargets  = mutableSetOf<String>()
    private val userProtectedApps = mutableSetOf<String>()

    /**
     * Get the full smart kill list — all 3 categories combined
     * minus never-kill list and user-protected apps
     */
    fun getSmartKillTargets(): Set<String> {
        val all = CATEGORY_SOCIAL_SYNC +
                  CATEGORY_WEBVIEW_HEAVY +
                  CATEGORY_BLOAT +
                  userAddedTargets
        return all - NEVER_KILL - userProtectedApps
    }

    /**
     * Get kill targets filtered to only what's actually installed
     * Pass the list of installed packages to filter against
     */
    fun getInstalledKillTargets(installedPackages: Set<String>): Set<String> {
        return getSmartKillTargets().intersect(installedPackages)
    }

    // Legacy — used as fallback
    fun getKillTargets(): Set<String> = getSmartKillTargets()
    fun getNeverKillList(): Set<String> = NEVER_KILL + userProtectedApps

    fun addUserTarget(pkg: String) {
        if (pkg !in NEVER_KILL) userAddedTargets.add(pkg)
    }

    fun protectApp(pkg: String) {
        userProtectedApps.add(pkg)
        userAddedTargets.remove(pkg)
    }

    fun isProtected(pkg: String) = pkg in NEVER_KILL || pkg in userProtectedApps
}
