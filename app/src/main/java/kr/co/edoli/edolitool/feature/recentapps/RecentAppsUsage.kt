package kr.co.edoli.edolitool.feature.recentapps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.concurrent.TimeUnit

data class RecentApp(
    val packageName: String,
    val label: String,
    val lastUsedTime: Long,
    val icon: Drawable,
)

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

@Suppress("DEPRECATION")
fun hasUsageStatsAccess(context: Context): Boolean {
    val appOpsManager = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    } else {
        appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

fun usageAccessSettingsIntent(): Intent {
    return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

fun readRecentApps(
    context: Context,
    limit: Int = 5,
): List<RecentApp> {
    if (!hasUsageStatsAccess(context)) return emptyList()

    val packageManager = context.packageManager
    val launchableApps = packageManager.queryLaunchableApps()
    val launcherPackages = packageManager.queryHomePackages()
    val excludedPackageNames = RecentAppsWidgetPreferences.excludedPackageNames(context)
    if (launchableApps.isEmpty()) return emptyList()

    val now = System.currentTimeMillis()
    val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    val usageStats = usageStatsManager.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        now - TimeUnit.DAYS.toMillis(7),
        now,
    )

    return usageStats
        .asSequence()
        .filter { stats -> stats.lastTimeUsed > 0L }
        .filter { stats -> stats.packageName != context.packageName }
        .filterNot { stats -> stats.packageName in launcherPackages }
        .filterNot { stats -> stats.packageName in excludedPackageNames }
        .mapNotNull { stats ->
            val resolveInfo = launchableApps[stats.packageName] ?: return@mapNotNull null
            RecentApp(
                packageName = stats.packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                lastUsedTime = stats.lastTimeUsed,
                icon = resolveInfo.loadIcon(packageManager),
            )
        }
        .sortedByDescending { app -> app.lastUsedTime }
        .distinctBy { app -> app.packageName }
        .take(limit)
        .toList()
}

fun readLaunchableAppCandidates(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    val launcherPackages = packageManager.queryHomePackages()
    return packageManager.queryLaunchableApps()
        .values
        .asSequence()
        .filterNot { resolveInfo -> resolveInfo.activityInfo.packageName == context.packageName }
        .filterNot { resolveInfo -> resolveInfo.activityInfo.packageName in launcherPackages }
        .map { resolveInfo ->
            LaunchableApp(
                packageName = resolveInfo.activityInfo.packageName,
                label = resolveInfo.loadLabel(packageManager).toString(),
                icon = resolveInfo.loadIcon(packageManager),
            )
        }
        .sortedBy { app -> app.label.lowercase() }
        .toList()
}

fun formatLastUsedRelative(
    now: Long,
    lastUsedTime: Long,
): String {
    val elapsedMillis = (now - lastUsedTime).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
    val days = TimeUnit.MILLISECONDS.toDays(elapsedMillis)

    return when {
        minutes < 1L -> "방금 전"
        minutes < 60L -> "${minutes}분 전"
        hours < 24L -> "${hours}시간 전"
        days < 7L -> "${days}일 전"
        else -> "최근 7일"
    }
}

private fun android.content.pm.PackageManager.queryLaunchableApps(): Map<String, ResolveInfo> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return queryIntentActivities(launcherIntent, 0)
        .groupBy { resolveInfo -> resolveInfo.activityInfo.packageName }
        .mapValues { (_, resolveInfos) ->
            resolveInfos.first()
        }
}

private fun android.content.pm.PackageManager.queryHomePackages(): Set<String> {
    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    val resolvedHome = resolveActivity(homeIntent, 0)?.activityInfo?.packageName
    val homeCandidates = queryIntentActivities(homeIntent, 0)
        .map { resolveInfo -> resolveInfo.activityInfo.packageName }

    return (homeCandidates + listOfNotNull(resolvedHome)).toSet()
}
