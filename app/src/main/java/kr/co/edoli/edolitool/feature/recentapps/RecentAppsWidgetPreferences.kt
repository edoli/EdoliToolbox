package kr.co.edoli.edolitool.feature.recentapps

import android.content.Context

object RecentAppsWidgetPreferences {
    private const val PREFERENCES_NAME = "recent_apps_widget"
    private const val KEY_SHOW_APP_NAMES = "show_app_names"
    private const val KEY_APP_NAME_COLOR = "app_name_color"
    private const val KEY_ICON_SIZE_DP = "icon_size_dp"
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
}
