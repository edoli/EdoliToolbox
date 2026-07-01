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
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
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
            val iconSizeDp = RecentAppsWidgetPreferences.iconSizeDp(context)
            val dynamicSizingSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val gridSpec = calculateGridSpec(
                options = appWidgetManager.getAppWidgetOptions(appWidgetId),
                showAppNames = showAppNames,
                iconSizeDp = iconSizeDp,
                dynamicSizingSupported = dynamicSizingSupported,
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
                            iconSizeDp = iconSizeDp,
                            dynamicSizingSupported = dynamicSizingSupported,
                            cellWidthDp = gridSpec.cellWidthDp,
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
            iconSizeDp: Int,
            dynamicSizingSupported: Boolean,
            cellWidthDp: Int,
        ): RemoteViews {
            val rowViews = RemoteViews(context.packageName, R.layout.widget_recent_app_row)
            if (dynamicSizingSupported) {
                rowViews.setViewLayoutWidth(
                    R.id.app_icon_cell,
                    cellWidthDp.toFloat(),
                    TypedValue.COMPLEX_UNIT_DIP,
                )
                rowViews.setViewLayoutWidth(
                    R.id.app_icon,
                    iconSizeDp.toFloat(),
                    TypedValue.COMPLEX_UNIT_DIP,
                )
                rowViews.setViewLayoutHeight(
                    R.id.app_icon,
                    iconSizeDp.toFloat(),
                    TypedValue.COMPLEX_UNIT_DIP,
                )
            }
            rowViews.setImageViewBitmap(
                R.id.app_icon,
                icon.toIconBitmap(
                    context = context,
                    iconSizeDp = iconSizeDp,
                    dynamicSizingSupported = dynamicSizingSupported,
                ),
            )
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
            iconSizeDp: Int,
            dynamicSizingSupported: Boolean,
        ): GridSpec {
            val widthDp = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                DEFAULT_WIDGET_WIDTH_DP,
            )
            val heightDp = options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                DEFAULT_WIDGET_HEIGHT_DP,
            )
            val cellWidthDp = if (dynamicSizingSupported) {
                iconSizeDp + ICON_CELL_HORIZONTAL_PADDING_DP
            } else {
                FALLBACK_ICON_CELL_WIDTH_DP
            }
            val columns = ((widthDp - WIDGET_HORIZONTAL_PADDING_DP) / cellWidthDp)
                .coerceAtLeast(1)
                .coerceAtMost(MAX_COLUMNS)
            val cellHeight = if (showAppNames) {
                iconSizeDp + ICON_CELL_HEIGHT_NAME_EXTRA_DP
            } else {
                iconSizeDp + ICON_CELL_VERTICAL_PADDING_DP
            }
            val rows = ((heightDp - WIDGET_VERTICAL_PADDING_DP) / cellHeight)
                .coerceAtLeast(1)
                .coerceAtMost(MAX_ROWS)
            return GridSpec(
                columns = columns,
                maxItems = columns * rows,
                cellWidthDp = cellWidthDp,
            )
        }

        private data class GridSpec(
            val columns: Int,
            val maxItems: Int,
            val cellWidthDp: Int,
        )

        private fun Drawable.toIconBitmap(
            context: Context,
            iconSizeDp: Int,
            dynamicSizingSupported: Boolean,
        ): Bitmap {
            val density = context.resources.displayMetrics.density
            val canvasSizeDp = if (dynamicSizingSupported) {
                iconSizeDp
            } else {
                RecentAppsWidgetPreferences.MAX_ICON_SIZE_DP
            }
            val canvasSizePx = (canvasSizeDp * density).toInt().coerceAtLeast(1)
            val iconSizePx = (iconSizeDp * density).toInt()
                .coerceIn(1, canvasSizePx)
            val insetPx = ((canvasSizePx - iconSizePx) / 2f).toInt()
            val bitmap = Bitmap.createBitmap(canvasSizePx, canvasSizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(
                insetPx,
                insetPx,
                insetPx + iconSizePx,
                insetPx + iconSizePx,
            )
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
        private const val WIDGET_HORIZONTAL_PADDING_DP = 0
        private const val WIDGET_VERTICAL_PADDING_DP = 0
        private const val ICON_CELL_HORIZONTAL_PADDING_DP = 24
        private const val ICON_CELL_VERTICAL_PADDING_DP = 8
        private const val ICON_CELL_HEIGHT_NAME_EXTRA_DP = 28
        private const val FALLBACK_ICON_CELL_WIDTH_DP = 88
        private const val MAX_COLUMNS = 8
        private const val MAX_ROWS = 4
    }
}
