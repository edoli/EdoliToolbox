package kr.co.edoli.edolitool.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kr.co.edoli.edolitool.MainActivity
import kr.co.edoli.edolitool.R
import kr.co.edoli.edolitool.feature.recentapps.RecentApp
import kr.co.edoli.edolitool.feature.recentapps.RecentAppsWidgetPreferences
import kr.co.edoli.edolitool.feature.recentapps.hasUsageStatsAccess
import kr.co.edoli.edolitool.feature.recentapps.readRecentApps
import kr.co.edoli.edolitool.feature.recentapps.usageAccessSettingsIntent

class RecentAppsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAll(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_REFRESH = "kr.co.edoli.edolitool.action.REFRESH_RECENT_APPS_WIDGET"

        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RecentAppsWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_recent_apps)
            views.setOnClickPendingIntent(R.id.widget_root, openToolPendingIntent(context))
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context, appWidgetId))
            views.removeAllViews(R.id.widget_apps_container)

            if (!hasUsageStatsAccess(context)) {
                showEmptyState(
                    views = views,
                    message = "사용 기록 접근 권한이 필요합니다.\n탭해서 설정을 여세요.",
                    showRefresh = true,
                    messagePendingIntent = usageSettingsPendingIntent(context),
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val showAppNames = RecentAppsWidgetPreferences.showAppNames(context)
            val appNameColor = RecentAppsWidgetPreferences.appNameColor(context)
            val gridSpec = calculateGridSpec(
                options = appWidgetManager.getAppWidgetOptions(appWidgetId),
                showAppNames = showAppNames,
            )
            val recentApps = readRecentApps(context, limit = gridSpec.maxItems)
            if (recentApps.isEmpty()) {
                showEmptyState(
                    views = views,
                    message = "최근 사용한 런처 앱이 없습니다.",
                    showRefresh = false,
                    messagePendingIntent = null,
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            views.setViewVisibility(R.id.widget_apps_container, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_container, View.GONE)
            recentApps.chunked(gridSpec.columns).forEachIndexed { rowIndex, rowApps ->
                val rowViews = RemoteViews(context.packageName, R.layout.widget_recent_app_icon_row)
                rowApps.forEachIndexed { columnIndex, recentApp ->
                    val itemIndex = rowIndex * gridSpec.columns + columnIndex
                    rowViews.addView(
                        R.id.app_icon_row,
                        recentApp.toIconRemoteViews(
                            context = context,
                            index = itemIndex,
                            showAppName = showAppNames,
                            appNameColor = appNameColor,
                        ),
                    )
                }
                views.addView(R.id.widget_apps_container, rowViews)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun showEmptyState(
            views: RemoteViews,
            message: String,
            showRefresh: Boolean,
            messagePendingIntent: PendingIntent?,
        ) {
            views.setViewVisibility(R.id.widget_apps_container, View.GONE)
            views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty_message, message)
            messagePendingIntent?.let { pendingIntent ->
                views.setOnClickPendingIntent(R.id.widget_empty_message, pendingIntent)
            }
            views.setViewVisibility(R.id.widget_refresh, if (showRefresh) View.VISIBLE else View.GONE)
        }

        private fun RecentApp.toIconRemoteViews(
            context: Context,
            index: Int,
            showAppName: Boolean,
            appNameColor: Int,
        ): RemoteViews {
            val rowViews = RemoteViews(context.packageName, R.layout.widget_recent_app_row)
            rowViews.setImageViewBitmap(R.id.app_icon, icon.toBitmap(sizePx = 144))
            rowViews.setTextViewText(R.id.app_name, label)
            rowViews.setTextColor(R.id.app_name, appNameColor)
            rowViews.setViewVisibility(R.id.app_name, if (showAppName) View.VISIBLE else View.GONE)
            launchPendingIntent(context, packageName, index)?.let { pendingIntent ->
                rowViews.setOnClickPendingIntent(R.id.app_icon_cell, pendingIntent)
            }
            return rowViews
        }

        private fun calculateGridSpec(
            options: Bundle,
            showAppNames: Boolean,
        ): GridSpec {
            val widthDp = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                DEFAULT_WIDGET_WIDTH_DP,
            )
            val heightDp = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                DEFAULT_WIDGET_HEIGHT_DP,
            )
            val columns = ((widthDp - WIDGET_HORIZONTAL_PADDING_DP) / ICON_CELL_WIDTH_DP)
                .coerceAtLeast(1)
                .coerceAtMost(MAX_COLUMNS)
            val cellHeight = if (showAppNames) ICON_CELL_HEIGHT_WITH_NAME_DP else ICON_CELL_HEIGHT_DP
            val rows = ((heightDp - WIDGET_VERTICAL_PADDING_DP) / cellHeight)
                .coerceAtLeast(1)
                .coerceAtMost(MAX_ROWS)
            return GridSpec(
                columns = columns,
                maxItems = columns * rows,
            )
        }

        private data class GridSpec(
            val columns: Int,
            val maxItems: Int,
        )

        private fun Drawable.toBitmap(sizePx: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            return bitmap
        }

        private fun openToolPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_START_TOOL_ID, "recent_apps_widget")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                context,
                2000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun usageSettingsPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                2001,
                usageAccessSettingsIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun refreshPendingIntent(
            context: Context,
            appWidgetId: Int,
        ): PendingIntent {
            val intent = Intent(context, RecentAppsWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            return PendingIntent.getBroadcast(
                context,
                2100 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun launchPendingIntent(
            context: Context,
            packageName: String,
            index: Int,
        ): PendingIntent? {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: return null
            return PendingIntent.getActivity(
                context,
                3000 + index,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val DEFAULT_WIDGET_WIDTH_DP = 250
        private const val DEFAULT_WIDGET_HEIGHT_DP = 120
        private const val WIDGET_HORIZONTAL_PADDING_DP = 8
        private const val WIDGET_VERTICAL_PADDING_DP = 8
        private const val ICON_CELL_WIDTH_DP = 72
        private const val ICON_CELL_HEIGHT_DP = 56
        private const val ICON_CELL_HEIGHT_WITH_NAME_DP = 76
        private const val MAX_COLUMNS = 8
        private const val MAX_ROWS = 4
    }
}
