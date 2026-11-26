package kr.co.edoli.edolitool.feature.physical

import android.content.Context
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_90
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kr.co.edoli.edolitool.util.findActivity

private const val MM_PER_INCH = 25.4f
private const val CARD_SHORT_EDGE_MM = 53.98f
private const val LEGACY_SCALE_KEY = "ruler_scale"
private const val X_SCALE_KEY = "ruler_scale_x"
private const val Y_SCALE_KEY = "ruler_scale_y"
private val rulerInset = 8.dp

private enum class RulerAxis(val label: String) {
    Horizontal("가로"),
    Vertical("세로");

    fun length(vector: Offset): Float = if (this == Horizontal) vector.x else vector.y

    fun point(along: Float, across: Float): Offset =
        if (this == Horizontal) Offset(along, across) else Offset(across, along)
}

@Composable
internal fun ScreenRuler(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("physical_measurements", Context.MODE_PRIVATE) }
    val legacyScale = remember(prefs) { prefs.getFloat(LEGACY_SCALE_KEY, 1f) }
    var scaleX by remember(prefs) { mutableFloatStateOf(prefs.getFloat(X_SCALE_KEY, legacyScale).coerceIn(0.5f, 2f)) }
    var scaleY by remember(prefs) { mutableFloatStateOf(prefs.getFloat(Y_SCALE_KEY, legacyScale).coerceIn(0.5f, 2f)) }
    var inches by rememberSaveable { mutableStateOf(false) }
    var markerX by rememberSaveable { mutableFloatStateOf(0f) }
    var markerY by rememberSaveable { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var calibrationAxis by rememberSaveable { mutableStateOf<RulerAxis?>(null) }

    val rotation = LocalView.current.display?.rotation
    val swapped = rotation == ROTATION_90 || rotation == ROTATION_270
    val metrics = context.resources.displayMetrics
    val dpiX = metrics.xdpi.takeIf { it.isFinite() && it > 0f } ?: metrics.densityDpi.toFloat()
    val dpiY = metrics.ydpi.takeIf { it.isFinite() && it > 0f } ?: metrics.densityDpi.toFloat()
    val basePixels = if (swapped) {
        Offset(dpiY / MM_PER_INCH, dpiX / MM_PER_INCH)
    } else {
        Offset(dpiX / MM_PER_INCH, dpiY / MM_PER_INCH)
    }
    val axis = calibrationAxis
    val calibratingX = (axis == RulerAxis.Horizontal) != swapped
    val storedScale = if (calibratingX) scaleX else scaleY
    var draftScale by rememberSaveable(axis, swapped) { mutableFloatStateOf(storedScale) }
    val horizontalScale = if (axis == RulerAxis.Horizontal) draftScale else if (swapped) scaleY else scaleX
    val verticalScale = if (axis == RulerAxis.Vertical) draftScale else if (swapped) scaleX else scaleY
    val pixelsPerMm = Offset(basePixels.x * horizontalScale, basePixels.y * verticalScale)
    val inset = with(LocalDensity.current) { rulerInset.toPx() }
    val area = Offset(
        (canvasSize.width - 2 * inset).coerceAtLeast(0f),
        (canvasSize.height - 2 * inset).coerceAtLeast(0f),
    )
    val marker = Offset(
        markerX.coerceAtMost(area.x / pixelsPerMm.x),
        markerY.coerceAtMost(area.y / pixelsPerMm.y),
    )

    RulerSystemBars()
    BackHandler(enabled = axis != null) { calibrationAxis = null }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.captionBar)),
    ) {
        RulerCanvas(
            modifier = Modifier.fillMaxSize().onSizeChanged { canvasSize = it },
            area = area,
            pixelsPerMm = pixelsPerMm,
            marker = marker,
            inches = inches && axis == null,
            calibrationAxis = axis,
            onMarkerChange = { point, measuredAxis ->
                if (measuredAxis != RulerAxis.Vertical) markerX = point.x
                if (measuredAxis != RulerAxis.Horizontal) markerY = point.y
            },
        )

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 3.dp,
        ) {
            if (axis == null) {
                RulerControls(
                    marker = marker,
                    inches = inches,
                    onBackClick = onBackClick,
                    onToggleUnit = { inches = !inches },
                    onReset = {
                        markerX = 0f
                        markerY = 0f
                    },
                    onCalibrate = { calibrationAxis = it },
                )
            } else {
                RulerCalibration(
                    axis = axis,
                    scale = draftScale,
                    fits = axis.length(area) >= CARD_SHORT_EDGE_MM * axis.length(pixelsPerMm),
                    onScaleChange = { draftScale = it },
                    onSave = {
                        val key = if (calibratingX) {
                            scaleX = draftScale
                            X_SCALE_KEY
                        } else {
                            scaleY = draftScale
                            Y_SCALE_KEY
                        }
                        prefs.edit().putFloat(key, draftScale).apply()
                        calibrationAxis = null
                    },
                    onCancel = { calibrationAxis = null },
                )
            }
        }
    }
}

@Composable
private fun RulerSystemBars() {
    val window = LocalContext.current.findActivity()?.window ?: return
    LifecycleResumeEffect(window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousBehavior = controller.systemBarsBehavior
        val bars = WindowInsetsCompat.Type.systemBars()
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(bars)
        onPauseOrDispose {
            controller.systemBarsBehavior = previousBehavior
            controller.show(bars)
        }
    }
}

@Composable
private fun RulerControls(
    marker: Offset,
    inches: Boolean,
    onBackClick: () -> Unit,
    onToggleUnit: () -> Unit,
    onReset: () -> Unit,
    onCalibrate: (RulerAxis) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val unit = if (inches) "in" else "mm"
    val divisor = if (inches) MM_PER_INCH else 1f
    val decimals = if (inches) 2 else 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
        }
        Column(Modifier.padding(vertical = 8.dp)) {
            Text("가로 ${number(marker.x / divisor, decimals)} $unit", style = MaterialTheme.typography.labelLarge)
            Text("세로 ${number(marker.y / divisor, decimals)} $unit", style = MaterialTheme.typography.labelLarge)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.Tune, contentDescription = "화면 자 설정")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (inches) "밀리미터로 전환" else "인치로 전환") },
                    onClick = {
                        menuOpen = false
                        onToggleUnit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("표시선 초기화") },
                    onClick = {
                        menuOpen = false
                        onReset()
                    },
                    enabled = marker != Offset.Zero,
                )
                RulerAxis.entries.forEach { axis ->
                    DropdownMenuItem(
                        text = { Text("${axis.label} 길이 보정") },
                        onClick = {
                            menuOpen = false
                            onCalibrate(axis)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RulerCalibration(
    axis: RulerAxis,
    scale: Float,
    fits: Boolean,
    onScaleChange: (Float) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.widthIn(max = 320.dp).padding(12.dp)) {
        Text("${axis.label} 길이 보정", style = MaterialTheme.typography.titleSmall)
        Text(
            if (fits) "카드의 짧은 변(53.98 mm)을 표시선에 맞추세요."
            else "카드가 들어오도록 배율을 낮추세요.",
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = scale,
            onValueChange = onScaleChange,
            valueRange = 0.5f..2f,
            modifier = Modifier.semantics { contentDescription = "보정 배율" },
        )
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCancel) { Text("취소") }
            Button(onClick = onSave, enabled = fits) { Text("보정 저장") }
        }
    }
}

@Composable
private fun RulerCanvas(
    modifier: Modifier,
    area: Offset,
    pixelsPerMm: Offset,
    marker: Offset,
    inches: Boolean,
    calibrationAxis: RulerAxis?,
    onMarkerChange: (Offset, RulerAxis?) -> Unit,
) {
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = line, fontFamily = FontFamily.Monospace)
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val updateMarker by rememberUpdatedState(onMarkerChange)

    val gestures = if (calibrationAxis != null) Modifier else Modifier.pointerInput(area, pixelsPerMm) {
        val inset = rulerInset.toPx()
        val band = inset + 32.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown()
            val axis = when {
                down.position.y <= band && down.position.x > band -> RulerAxis.Horizontal
                down.position.x <= band && down.position.y > band -> RulerAxis.Vertical
                else -> null
            }
            fun move(position: Offset) {
                updateMarker(
                    Offset(
                        (position.x - inset).coerceIn(0f, area.x) / pixelsPerMm.x,
                        (position.y - inset).coerceIn(0f, area.y) / pixelsPerMm.y,
                    ),
                    axis,
                )
            }
            down.consume()
            move(down.position)
            var pointer = down
            while (pointer.pressed) {
                pointer = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (pointer.isConsumed) break
                move(pointer.position)
                pointer.consume()
            }
        }
    }

    Canvas(
        modifier.then(gestures).semantics { contentDescription = "가로·세로 화면 눈금" },
    ) {
        if (area.x == 0f || area.y == 0f) return@Canvas
        val origin = Offset(rulerInset.toPx(), rulerInset.toPx())
        RulerAxis.entries.forEach { axis ->
            drawRulerScale(axis, origin, area, axis.length(pixelsPerMm), inches, line, labelStyle, measurer)
        }
        drawText(measurer.measure("0", labelStyle), topLeft = origin + Offset(6.dp.toPx(), 6.dp.toPx()))

        if (calibrationAxis != null) {
            val axis = calibrationAxis
            val length = CARD_SHORT_EDGE_MM * axis.length(pixelsPerMm)
            if (length <= axis.length(area)) {
                val across = (if (axis == RulerAxis.Horizontal) area.y else area.x) * 0.3f
                val cap = 8.dp.toPx()
                drawLine(accent, origin + axis.point(0f, across), origin + axis.point(length, across), 2.dp.toPx())
                drawLine(accent, origin + axis.point(0f, across - cap), origin + axis.point(0f, across + cap), 2.dp.toPx())
                drawLine(accent, origin + axis.point(length, across - cap), origin + axis.point(length, across + cap), 2.dp.toPx())
            }
        } else if (marker != Offset.Zero) {
            val end = origin + Offset(marker.x * pixelsPerMm.x, marker.y * pixelsPerMm.y)
            drawLine(accent, Offset(end.x, origin.y), end, strokeWidth = 1.5.dp.toPx())
            drawLine(accent, Offset(origin.x, end.y), end, strokeWidth = 1.5.dp.toPx())
            drawCircle(accent, radius = 3.dp.toPx(), center = end)
            drawCircle(accent, radius = 2.dp.toPx(), center = Offset(end.x, origin.y))
            drawCircle(accent, radius = 2.dp.toPx(), center = Offset(origin.x, end.y))
        }
    }
}

private fun DrawScope.drawRulerScale(
    axis: RulerAxis,
    origin: Offset,
    area: Offset,
    pixelsPerMm: Float,
    inches: Boolean,
    color: Color,
    labelStyle: TextStyle,
    measurer: TextMeasurer,
) {
    val length = axis.length(area)
    val tickSize = pixelsPerMm * if (inches) MM_PER_INCH / 16f else 1f
    val ticksPerLabel = if (inches) 16 else 10
    val scaleUnit = if (inches) "in" else "cm"
    drawLine(color, origin, origin + axis.point(length, 0f), strokeWidth = 1.dp.toPx())
    for (tick in 1..(length / tickSize).toInt()) {
        val position = tick * tickSize
        val major = tick % ticksPerLabel == 0
        val mark = when {
            major -> 17.dp.toPx()
            tick % (ticksPerLabel / 2) == 0 -> 12.dp.toPx()
            else -> 6.dp.toPx()
        }
        drawLine(color, origin + axis.point(position, 0f), origin + axis.point(position, mark), strokeWidth = 1.dp.toPx())
        if (major) {
            val text = if (tick == ticksPerLabel) "1 $scaleUnit" else (tick / ticksPerLabel).toString()
            val label = measurer.measure(text, labelStyle)
            val half = axis.length(Offset(label.size.width.toFloat(), label.size.height.toFloat())) / 2f
            if (position + half <= length) {
                drawText(label, topLeft = origin + axis.point(position - half, mark + 3.dp.toPx()))
            }
        }
    }
}
