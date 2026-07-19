package kr.co.edoli.edolitool.feature.pattern

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kr.co.edoli.edolitool.util.colorToCompose
import kr.co.edoli.edolitool.util.findActivity
import kr.co.edoli.edolitool.util.parseColorQuery
import kr.co.edoli.edolitool.ui.component.ScrubbableValueField
import java.util.Locale
import kotlin.math.ceil

private const val minPatternSizeMm = 1f
private const val maxPatternSizeMm = 50f
private const val minGridLineThicknessMm = 0.01f
private const val maxGridLineThicknessMm = 5f

private enum class ScreenPattern(val label: String) {
    Checkerboard("체커보드"),
    Grid("그리드"),
    VerticalStripes("세로 줄무늬"),
    HorizontalStripes("가로 줄무늬"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternScreenRoute(onBackClick: () -> Unit) {
    var pattern by remember { mutableStateOf(ScreenPattern.Checkerboard) }
    var foregroundColor by remember { mutableIntStateOf(AndroidColor.WHITE) }
    var backgroundColor by remember { mutableIntStateOf(AndroidColor.BLACK) }
    var foregroundQuery by remember { mutableStateOf("#FFFFFF") }
    var backgroundQuery by remember { mutableStateOf("#000000") }
    var cellSize by remember { mutableFloatStateOf(10f) }
    var gridLineThickness by remember { mutableFloatStateOf(0.5f) }
    var fullscreen by remember { mutableStateOf(false) }

    if (fullscreen) {
        PatternFullscreen(
            pattern = pattern,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            cellSizeMm = cellSize,
            gridLineThicknessMm = gridLineThickness,
            onExit = { fullscreen = false },
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("패턴 화면", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("패턴", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ScreenPattern.entries.chunked(2).forEach { options ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            options.forEach { option ->
                                FilterChip(
                                    selected = pattern == option,
                                    onClick = { pattern = option },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    label = { Text(option.label) },
                                )
                            }
                        }
                    }
                    PatternColorField(
                        label = "패턴 색상",
                        query = foregroundQuery,
                        color = foregroundColor,
                        onQueryChange = { value ->
                            foregroundQuery = value
                            parseColorQuery(value)?.let { foregroundColor = it }
                        },
                    )
                    PatternColorField(
                        label = "배경 색상",
                        query = backgroundQuery,
                        color = backgroundColor,
                        onQueryChange = { value ->
                            backgroundQuery = value
                            parseColorQuery(value)?.let { backgroundColor = it }
                        },
                    )
                    MmValueSetting(
                        label = "크기",
                        value = cellSize,
                        valueRange = minPatternSizeMm..maxPatternSizeMm,
                        onValueChange = { cellSize = it },
                    )
                    if (pattern == ScreenPattern.Grid) {
                        MmValueSetting(
                            label = "선 두께",
                            value = gridLineThickness,
                            valueRange = minGridLineThicknessMm..maxGridLineThicknessMm,
                            onValueChange = { gridLineThickness = it },
                        )
                    }
                    Button(onClick = { fullscreen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Fullscreen, contentDescription = null)
                        Text("전체화면 표시", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MmValueSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    ScrubbableValueField(
        label = label,
        value = value,
        valueRange = valueRange,
        unit = "mm",
        modifier = Modifier.fillMaxWidth(),
        formatValue = ::formatMm,
        parseValue = String::toMmOrNull,
        onValueChange = onValueChange,
    )
}

private fun String.toMmOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()

private fun formatMm(value: Float): String = "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')

@Composable
private fun PatternColorField(
    label: String,
    query: String,
    color: Int,
    onQueryChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colorToCompose(color), MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            supportingText = { Text("색상 이름 또는 HEX") },
            singleLine = true,
        )
    }
}

@Composable
private fun PatternFullscreen(
    pattern: ScreenPattern,
    foregroundColor: Int,
    backgroundColor: Int,
    cellSizeMm: Float,
    gridLineThicknessMm: Float,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val displayMetrics = context.resources.displayMetrics
    val cellWidthPx = cellSizeMm / 25.4f * displayMetrics.xdpi
    val cellHeightPx = cellSizeMm / 25.4f * displayMetrics.ydpi
    val lineWidthPx = gridLineThicknessMm / 25.4f * displayMetrics.xdpi
    val lineHeightPx = gridLineThicknessMm / 25.4f * displayMetrics.ydpi
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 32.dp.toPx() }
    val exitDistancePx = with(density) { 96.dp.toPx() }

    BackHandler(onBack = onExit)
    PatternFullscreenSystemBars(activity, backgroundColor)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onExit, edgeWidthPx, exitDistancePx) {
                detectLeftEdgeExitGesture(edgeWidthPx, exitDistancePx, onExit)
            },
    ) {
        drawRect(colorToCompose(backgroundColor))
        when (pattern) {
            ScreenPattern.Checkerboard -> drawCheckerboard(colorToCompose(foregroundColor), cellWidthPx, cellHeightPx)
            ScreenPattern.Grid -> drawGrid(
                colorToCompose(foregroundColor),
                cellWidthPx,
                cellHeightPx,
                lineWidthPx,
                lineHeightPx,
            )
            ScreenPattern.VerticalStripes -> drawVerticalStripes(colorToCompose(foregroundColor), cellWidthPx)
            ScreenPattern.HorizontalStripes -> drawHorizontalStripes(colorToCompose(foregroundColor), cellHeightPx)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard(
    color: Color,
    cellWidth: Float,
    cellHeight: Float,
) {
    val columns = ceil(this.size.width / cellWidth).toInt()
    val rows = ceil(this.size.height / cellHeight).toInt()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 0) {
                drawRect(
                    color,
                    Offset(column * cellWidth, row * cellHeight),
                    androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    color: Color,
    cellWidth: Float,
    cellHeight: Float,
    lineWidth: Float,
    lineHeight: Float,
) {
    for (x in 0..ceil(this.size.width / cellWidth).toInt()) {
        drawLine(color, Offset(x * cellWidth, 0f), Offset(x * cellWidth, this.size.height), lineWidth)
    }
    for (y in 0..ceil(this.size.height / cellHeight).toInt()) {
        drawLine(color, Offset(0f, y * cellHeight), Offset(this.size.width, y * cellHeight), lineHeight)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVerticalStripes(color: Color, stripeWidth: Float) {
    for (column in 0..ceil(this.size.width / stripeWidth).toInt()) {
        if (column % 2 == 0) {
            drawRect(color, Offset(column * stripeWidth, 0f), androidx.compose.ui.geometry.Size(stripeWidth, this.size.height))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHorizontalStripes(color: Color, stripeHeight: Float) {
    for (row in 0..ceil(this.size.height / stripeHeight).toInt()) {
        if (row % 2 == 0) {
            drawRect(color, Offset(0f, row * stripeHeight), androidx.compose.ui.geometry.Size(this.size.width, stripeHeight))
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun PatternFullscreenSystemBars(activity: Activity?, color: Int) {
    DisposableEffect(activity, color) {
        val window = activity?.window
        if (window == null) onDispose {}
        else {
            val oldStatusBarColor = window.statusBarColor
            val oldNavigationBarColor = window.navigationBarColor
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val oldBehavior = controller.systemBarsBehavior
            window.statusBarColor = color
            window.navigationBarColor = color
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                window.statusBarColor = oldStatusBarColor
                window.navigationBarColor = oldNavigationBarColor
                controller.systemBarsBehavior = oldBehavior
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private suspend fun PointerInputScope.detectLeftEdgeExitGesture(
    edgeWidthPx: Float,
    exitDistancePx: Float,
    onExit: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x > edgeWidthPx) return@awaitEachGesture
        var totalDrag = 0f
        do {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            totalDrag += change.positionChange().x
            if (totalDrag >= exitDistancePx) {
                change.consume()
                onExit()
                break
            }
        } while (event.changes.any { it.pressed })
    }
}
