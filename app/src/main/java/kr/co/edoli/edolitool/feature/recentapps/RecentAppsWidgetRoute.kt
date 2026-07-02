package kr.co.edoli.edolitool.feature.recentapps

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.co.edoli.edolitool.widget.RecentAppsWidgetProvider
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentAppsWidgetRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(hasUsageStatsAccess(context)) }
    var recentApps by remember { mutableStateOf(readRecentApps(context)) }
    var showAppNames by remember { mutableStateOf(RecentAppsWidgetPreferences.showAppNames(context)) }
    var appNameColor by remember { mutableStateOf(RecentAppsWidgetPreferences.appNameColor(context)) }
    var iconSizeDp by remember { mutableStateOf(RecentAppsWidgetPreferences.iconSizeDp(context)) }
    var excludedPackageNames by remember {
        mutableStateOf(RecentAppsWidgetPreferences.excludedPackageNames(context))
    }
    var showExcludedAppsScreen by remember { mutableStateOf(false) }

    fun refresh() {
        hasPermission = hasUsageStatsAccess(context)
        recentApps = readRecentApps(context)
        excludedPackageNames = RecentAppsWidgetPreferences.excludedPackageNames(context)
    }

    BackHandler(enabled = showExcludedAppsScreen) {
        showExcludedAppsScreen = false
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showExcludedAppsScreen) {
        ExcludedAppsRoute(
            initialExcludedPackageNames = excludedPackageNames,
            onBackClick = { showExcludedAppsScreen = false },
            onExcludedPackageNamesChange = { packageNames ->
                excludedPackageNames = packageNames
                RecentAppsWidgetPreferences.setExcludedPackageNames(context, packageNames)
                recentApps = readRecentApps(context)
                RecentAppsWidgetProvider.updateAll(context)
            },
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "최근 앱 위젯",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            refresh()
                            RecentAppsWidgetProvider.updateAll(context)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "새로고침",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionCard(
                hasPermission = hasPermission,
                onOpenSettings = { openUsageAccessSettings(context) },
            )
            WidgetActionsCard(
                showAppNames = showAppNames,
                appNameColor = appNameColor,
                iconSizeDp = iconSizeDp,
                onShowAppNamesChange = { showNames ->
                    showAppNames = showNames
                    RecentAppsWidgetPreferences.setShowAppNames(context, showNames)
                    RecentAppsWidgetProvider.updateAll(context)
                },
                onAppNameColorChange = { color ->
                    appNameColor = color
                    RecentAppsWidgetPreferences.setAppNameColor(context, color)
                    RecentAppsWidgetProvider.updateAll(context)
                },
                onIconSizeChange = { sizeDp ->
                    iconSizeDp = sizeDp
                    RecentAppsWidgetPreferences.setIconSizeDp(context, sizeDp)
                    RecentAppsWidgetProvider.updateAll(context)
                },
                onPinWidget = { requestPinRecentAppsWidget(context) },
                onRefreshWidget = {
                    RecentAppsWidgetProvider.updateAll(context)
                    Toast.makeText(context, "최근 앱 위젯을 새로고침했습니다.", Toast.LENGTH_SHORT).show()
                },
            )
            ExcludedAppsSummaryCard(
                excludedAppCount = excludedPackageNames.size,
                onManageClick = { showExcludedAppsScreen = true },
            )
            RecentAppsPreviewCard(
                hasPermission = hasPermission,
                recentApps = recentApps,
                onExcludeApp = { app ->
                    val nextPackageNames = excludedPackageNames + app.packageName
                    excludedPackageNames = nextPackageNames
                    RecentAppsWidgetPreferences.setExcludedPackageNames(context, nextPackageNames)
                    recentApps = readRecentApps(context)
                    RecentAppsWidgetProvider.updateAll(context)
                    Toast.makeText(context, "${app.label} 앱을 제외했습니다.", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun PermissionCard(
    hasPermission: Boolean,
    onOpenSettings: () -> Unit,
) {
    InfoCard(title = "사용 기록 접근") {
        Text(
            text = if (hasPermission) {
                "권한이 켜져 있습니다. 위젯에서 최근 사용한 앱을 표시할 수 있습니다."
            } else {
                "Android 설정에서 Edoli Toolbox의 사용 기록 접근을 켜야 최근 앱을 읽을 수 있습니다."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
            )
            Text(
                text = "권한 설정 열기",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun WidgetActionsCard(
    showAppNames: Boolean,
    appNameColor: Int,
    iconSizeDp: Int,
    onShowAppNamesChange: (Boolean) -> Unit,
    onAppNameColorChange: (Int) -> Unit,
    onIconSizeChange: (Int) -> Unit,
    onPinWidget: () -> Unit,
    onRefreshWidget: () -> Unit,
) {
    val context = LocalContext.current
    val canRequestPin = remember {
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }

    InfoCard(title = "홈 화면 위젯") {
        Text(
            text = "홈 화면에 최근 사용 앱 아이콘 위젯을 추가하고, 설치된 위젯을 즉시 새로고침할 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "앱 이름 표시",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "아이콘 아래 앱 이름을 함께 표시합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = showAppNames,
                onCheckedChange = onShowAppNamesChange,
            )
        }
        AppNameColorSelector(
            selectedColor = appNameColor,
            onColorSelected = onAppNameColorChange,
        )
        IconSizeSelector(
            iconSizeDp = iconSizeDp,
            onIconSizeChange = onIconSizeChange,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPinWidget,
                enabled = canRequestPin,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Widgets,
                    contentDescription = null,
                )
                Text(
                    text = "위젯 추가",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(
                onClick = onRefreshWidget,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                )
                Text(
                    text = "새로고침",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (!canRequestPin) {
            Text(
                text = "현재 런처는 앱에서 직접 위젯 추가를 요청하는 기능을 지원하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconSizeSelector(
    iconSizeDp: Int,
    onIconSizeChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "아이콘 크기",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${iconSizeDp}dp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = iconSizeDp.toFloat(),
            onValueChange = { value ->
                onIconSizeChange(value.toIconSizeStep())
            },
            valueRange = RecentAppsWidgetPreferences.MIN_ICON_SIZE_DP.toFloat()..
                RecentAppsWidgetPreferences.MAX_ICON_SIZE_DP.toFloat(),
            steps = 6,
        )
    }
}

@Composable
private fun AppNameColorSelector(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "앱 이름 색상",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            appNameColorOptions.forEach { option ->
                ColorSwatch(
                    option = option,
                    selected = selectedColor == option.colorInt,
                    onClick = { onColorSelected(option.colorInt) },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    option: AppNameColorOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(option.color, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = borderColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "${option.label} 선택됨",
                tint = option.checkColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ExcludedAppsSummaryCard(
    excludedAppCount: Int,
    onManageClick: () -> Unit,
) {
    InfoCard(title = "제외 앱") {
        Text(
            text = if (excludedAppCount == 0) {
                "위젯에 표시하지 않을 앱을 선택합니다."
            } else {
                "${excludedAppCount}개 앱을 위젯에서 숨깁니다."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onManageClick) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
            )
            Text(
                text = "제외 앱 관리",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcludedAppsRoute(
    initialExcludedPackageNames: Set<String>,
    onBackClick: () -> Unit,
    onExcludedPackageNamesChange: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var excludedPackageNames by remember { mutableStateOf(initialExcludedPackageNames) }

    LaunchedEffect(context) {
        apps = readLaunchableAppCandidates(context)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "제외 앱",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (excludedPackageNames.isEmpty()) {
                    "위젯에 표시하지 않을 앱을 선택합니다."
                } else {
                    "${excludedPackageNames.size}개 앱을 위젯에서 숨깁니다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val currentApps = apps) {
                null -> Text(
                    text = "앱 목록을 불러오는 중입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> if (currentApps.isEmpty()) {
                    Text(
                        text = "선택할 수 있는 앱이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    currentApps.forEach { app ->
                        ExcludedAppRow(
                            app = app,
                            excluded = app.packageName in excludedPackageNames,
                            onToggle = {
                                val nextPackageNames = if (app.packageName in excludedPackageNames) {
                                    excludedPackageNames - app.packageName
                                } else {
                                    excludedPackageNames + app.packageName
                                }
                                excludedPackageNames = nextPackageNames
                                onExcludedPackageNamesChange(nextPackageNames)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExcludedAppRow(
    app: LaunchableApp,
    excluded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = remember(app.packageName) { app.icon.toBitmap(96).asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(
            checked = excluded,
            onCheckedChange = { onToggle() },
        )
    }
}

@Composable
private fun RecentAppsPreviewCard(
    hasPermission: Boolean,
    recentApps: List<RecentApp>,
    onExcludeApp: (RecentApp) -> Unit,
) {
    InfoCard(title = "미리보기") {
        when {
            !hasPermission -> Text(
                text = "권한을 켜면 최근 사용 앱이 여기에 표시됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            recentApps.isEmpty() -> Text(
                text = "최근 7일 안에 사용한 런처 앱을 찾지 못했습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                val now = System.currentTimeMillis()
                recentApps.forEach { app ->
                    RecentAppRow(
                        app = app,
                        lastUsedText = formatLastUsedRelative(now, app.lastUsedTime),
                        onExcludeApp = { onExcludeApp(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentAppRow(
    app: RecentApp,
    lastUsedText: String,
    onExcludeApp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = remember(app.packageName) { app.icon.toBitmap(96).asImageBitmap() },
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = lastUsedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onExcludeApp) {
            Text(text = "제외")
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

private fun Drawable.toBitmap(sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

private fun openUsageAccessSettings(context: Context) {
    runCatching {
        context.startActivity(usageAccessSettingsIntent())
    }.recoverCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun requestPinRecentAppsWidget(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val componentName = ComponentName(context, RecentAppsWidgetProvider::class.java)
    if (!appWidgetManager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, "현재 런처에서는 직접 추가 요청을 지원하지 않습니다.", Toast.LENGTH_SHORT).show()
        return
    }

    appWidgetManager.requestPinAppWidget(componentName, null, null)
}

private data class AppNameColorOption(
    val label: String,
    val colorInt: Int,
    val color: Color,
    val checkColor: Color,
)

private val appNameColorOptions = listOf(
    AppNameColorOption(
        label = "진한색",
        colorInt = RecentAppsWidgetPreferences.DEFAULT_APP_NAME_COLOR,
        color = Color(RecentAppsWidgetPreferences.DEFAULT_APP_NAME_COLOR),
        checkColor = Color.White,
    ),
    AppNameColorOption(
        label = "흰색",
        colorInt = -0x1,
        color = Color.White,
        checkColor = Color.Black,
    ),
    AppNameColorOption(
        label = "회색",
        colorInt = -0x767677,
        color = Color(0xFF898989),
        checkColor = Color.White,
    ),
    AppNameColorOption(
        label = "파랑",
        colorInt = -0xd79f01,
        color = Color(0xFF2860FF),
        checkColor = Color.White,
    ),
    AppNameColorOption(
        label = "초록",
        colorInt = -0xe95cb7,
        color = Color(0xFF16A349),
        checkColor = Color.White,
    ),
    AppNameColorOption(
        label = "빨강",
        colorInt = -0x23d9da,
        color = Color(0xFFDC2626),
        checkColor = Color.White,
    ),
)

private fun Float.toIconSizeStep(): Int {
    val min = RecentAppsWidgetPreferences.MIN_ICON_SIZE_DP
    val max = RecentAppsWidgetPreferences.MAX_ICON_SIZE_DP
    val step = 4
    return (((this - min) / step).roundToInt() * step + min)
        .coerceIn(min, max)
}
