package kr.co.edoli.edolitool.feature.recentapps

import android.content.Context

object RecentAppsWidgetPreferences {
    private const val PREFERENCES_NAME = "recent_apps_widget"
    private const val KEY_SHOW_APP_NAMES = "show_app_names"
    private const val KEY_APP_NAME_COLOR = "app_name_color"
    private const val KEY_ICON_SIZE_DP = "icon_size_dp"
    private const val KEY_PROMOTED_PACKAGE_NAME = "promoted_package_name"
    private const val KEY_PROMOTED_AT = "promoted_at"
    const val DEFAULT_APP_NAME_COLOR = -0xeeeeef
    const val MIN_ICON_SIZE_DP = 36
    const val DEFAULT_ICON_SIZE_DP = 48
    const val MAX_ICON_SIZE_DP = 64

    fun showAppNames(context: Context): Boolean {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_APP_NAMES, false)
    }

    fun appNameColor(context: Context): Int {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_APP_NAME_COLOR, DEFAULT_APP_NAME_COLOR)
    }

    fun iconSizeDp(context: Context): Int {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ICON_SIZE_DP, DEFAULT_ICON_SIZE_DP)
            .coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP)
    }

    fun promotedPackageName(context: Context): String? {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val promotedAt = preferences.getLong(KEY_PROMOTED_AT, 0L)
        if (System.currentTimeMillis() - promotedAt > PROMOTION_MAX_AGE_MILLIS) {
            return null
        }
        return preferences.getString(KEY_PROMOTED_PACKAGE_NAME, null)
    }

    fun setShowAppNames(
        context: Context,
        showAppNames: Boolean,
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_APP_NAMES, showAppNames)
            .apply()
    }

    fun setAppNameColor(
        context: Context,
        color: Int,
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_APP_NAME_COLOR, color)
            .apply()
    }

    fun setIconSizeDp(
        context: Context,
        iconSizeDp: Int,
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ICON_SIZE_DP, iconSizeDp.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP))
            .apply()
    }

    fun promotePackage(
        context: Context,
        packageName: String,
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROMOTED_PACKAGE_NAME, packageName)
            .putLong(KEY_PROMOTED_AT, System.currentTimeMillis())
            .apply()
    }

    private const val PROMOTION_MAX_AGE_MILLIS = 5 * 60 * 1000L
}
