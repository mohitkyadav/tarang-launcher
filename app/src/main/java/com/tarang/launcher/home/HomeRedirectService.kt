package com.tarang.launcher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Fallback "be the home screen" mechanism (plan §2.1 / §5.2).
 *
 * The clean path — `cmd package set-home-activity` — does not reliably stick on Google TV, so
 * this service watches for the stock launcher coming to the foreground and bounces straight back
 * to Tarang.
 *
 * NOTE: this does NOT (and cannot) intercept the HOME key — HOME is consumed by the system's
 * window policy before any service sees it. We react to the resulting foreground change instead.
 */
class HomeRedirectService : AccessibilityService() {

    private var lastRedirectAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in STOCK_LAUNCHERS) return

        // Debounce: the launcher can emit several window events in a burst.
        val now = SystemClock.uptimeMillis()
        if (now - lastRedirectAt < DEBOUNCE_MS) return
        lastRedirectAt = now

        val intent = Intent(this, HomeActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "Home redirect failed", it) }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "HomeRedirect"
        private const val DEBOUNCE_MS = 800L

        /** The live service instance while it's connected, so the launcher can drive global actions
         *  (e.g. the "Sleep" shortcut) through it. Null when the service isn't enabled/connected. */
        @Volatile
        var instance: HomeRedirectService? = null
            private set

        /**
         * Best-effort "put the TV to sleep": performs the accessibility lock-screen global action,
         * which on most Android TV devices turns the display off. Returns false if the accessibility
         * service isn't connected (the caller can then fall back — e.g. to Frame Art).
         */
        fun requestSleep(): Boolean {
            val svc = instance ?: return false
            return runCatching {
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }.getOrDefault(false)
        }

        /** Stock launcher packages we bounce away from. */
        private val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx", // Google TV (Chromecast w/ Google TV)
            "com.google.android.tvlauncher", // AOSP / older Android TV Leanback launcher
        )
    }
}
