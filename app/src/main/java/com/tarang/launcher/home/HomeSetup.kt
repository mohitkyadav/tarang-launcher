package com.tarang.launcher.home

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Detection + deep-links for making Tarang the device's home screen.
 *
 * On Google TV the system won't let an app set itself as the default Home, so the reliable mechanism
 * is the [HomeRedirectService] accessibility service (it bounces back to Tarang when the stock
 * launcher surfaces). This object exposes whether each piece is in place and opens the relevant
 * system settings so the user can finish setup.
 */
object HomeSetup {

    /** True when Tarang is the device's preferred (default) Home app. */
    fun isDefaultHome(context: Context): Boolean {
        // API 31+: ask RoleManager whether we hold the HOME role — the authoritative signal. The old
        // resolveActivity(MATCH_DEFAULT_ONLY) check can return the system resolver/chooser activity
        // instead of the real default, which made a correctly-selected Tarang read as "Not set".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return rm.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        // Fallback (older devices / no role manager): resolve the HOME intent and compare the package.
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** True when the [HomeRedirectService] accessibility service is enabled in system settings. */
    fun isRedirectServiceEnabled(context: Context): Boolean {
        val target = ComponentName(context, HomeRedirectService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == target }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Whether the device exposes a Home-app chooser we can open (often absent on Google TV). */
    fun canOpenHomeSettings(context: Context): Boolean =
        Intent(Settings.ACTION_HOME_SETTINGS).resolveActivity(context.packageManager) != null

    fun openHomeSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
