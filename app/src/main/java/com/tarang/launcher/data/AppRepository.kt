package com.tarang.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Discovers installed apps and launches them.
 *
 * Discovery prefers TV apps (CATEGORY_LEANBACK_LAUNCHER) and falls back to phone apps
 * (CATEGORY_LAUNCHER), deduping by package and hiding ourselves. Requires
 * QUERY_ALL_PACKAGES on API 30+ (declared in the manifest — see plan §2.2).
 */
class AppRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        // Leanback first so a dual-purpose app keeps its TV entry.
        val discovered = queryLauncherApps(Intent.CATEGORY_LEANBACK_LAUNCHER, isTv = true) +
            queryLauncherApps(Intent.CATEGORY_LAUNCHER, isTv = false)

        val byPackage = LinkedHashMap<String, AppInfo>()
        for (app in discovered) {
            if (app.packageName == context.packageName) continue // don't list ourselves
            byPackage.putIfAbsent(app.packageName, app)
        }
        byPackage.values.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION") // int-flags overload kept for minSdk 28 compatibility
    private fun queryLauncherApps(category: String, isTv: Boolean): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, 0).mapNotNull { resolveInfo ->
            val activity = resolveInfo.activityInfo ?: return@mapNotNull null
            AppInfo(
                label = resolveInfo.loadLabel(pm).toString().ifBlank { activity.packageName },
                packageName = activity.packageName,
                activityName = activity.name,
                isTvApp = isTv,
            )
        }
    }

    /**
     * Emits whenever an app is installed, removed, replaced or enabled/disabled, so the launcher
     * can refresh its list without a restart. Package broadcasts are protected system broadcasts,
     * so registering NOT_EXPORTED is correct (and required on API 34+).
     */
    fun packageEvents(): Flow<Unit> = callbackFlow {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    private fun launchIntentFor(packageName: String): Intent? =
        pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)

    /** Launches [packageName]; returns false if no launch intent could be resolved. */
    fun launch(packageName: String): Boolean {
        val intent = launchIntentFor(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent == null) {
            Log.w(TAG, "No launch intent for $packageName")
            return false
        }
        return runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Failed to launch $packageName", it) }
            .isSuccess
    }

    /** The system "Watch Next" entries — used as one of the idle screensaver's artwork sources. */
    suspend fun watchNext(): List<WatchNextItem> = TvArtwork.watchNext(context)

    private companion object {
        const val TAG = "AppRepository"
    }
}
