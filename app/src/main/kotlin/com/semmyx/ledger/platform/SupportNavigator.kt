package com.semmyx.ledger.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object SupportNavigator {
    const val HANDLE = "@semmyxth"
    const val WEB_URL = "https://t.me/semmyxth"
    private val officialPackages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")

    fun open(activity: Activity) {
        if (!openTelegram(activity)) activity.startActivity(Intent(activity, SupportWebActivity::class.java))
    }
    fun openTelegram(activity: Activity): Boolean {
        for (packageName in officialPackages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=semmyxth")).setPackage(packageName)
            @Suppress("DEPRECATION")
            val resolved = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            if (resolved == null || !resolved.exported || resolved.packageName != packageName) continue
            intent.component = ComponentName(resolved.packageName, resolved.name) // Explicit, not a hijackable chooser.
            try {
                activity.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // App could have been removed between resolution and launch; try the next official client.
                continue
            } catch (_: SecurityException) { continue }
        }
        return false
    }
}
