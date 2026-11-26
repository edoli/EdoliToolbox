package kr.co.edoli.edolitool.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class PlotTick(val value: Float, val label: String)

internal data class PlotAxis(val range: ClosedFloatingPointRange<Float>, val ticks: List<PlotTick>)

internal data class PlotLine(val color: Color, val points: List<Offset>)

internal data class TimePoint(val elapsedMillis: Long, val value: Float)

internal data class TimeLine(val color: Color, val samples: List<TimePoint>)

private val defaultRssiAxis = PlotAxis(
    range = -110f..-20f,
    ticks = listOf(
        PlotTick(-110f, "−110"),
        PlotTick(-80f, "−80"),
        PlotTick(-50f, "−50"),
        PlotTick(-20f, "−20"),
    ),
)

internal fun rssiAxis(values: List<Float>): PlotAxis {
    val defaultRange = defaultRssiAxis.range
    val low = minOf(defaultRange.start, values.minOrNull() ?: defaultRange.start)
    val high = maxOf(defaultRange.endInclusive, values.maxOrNull() ?: defaultRange.endInclusive)
    if (low == defaultRange.start && high == defaultRange.endInclusive) return defaultRssiAxis
    return PlotAxis(
        low..high,
        listOf(low, (low + high) / 2, high).map { value ->
            PlotTick(value, value.roundToInt().toString().replace('-', '−'))
        },
    )
}

@Composable
internal fun networkChartColors(): List<Color> =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        listOf(Color(0xFF245BCC), Color(0xFF007F6E), Color(0xFFB6412B), Color(0xFF7447AD), Color(0xFF926100), Color(0xFFAD306A))
    } else {
        listOf(Color(0xFF91B1FF), Color(0xFF63DAC6), Color(0xFFFFA08B), Color(0xFFCEACFF), Color(0xFFFFCD70), Color(0xFFFF99C8))
    }

@Composable
internal fun NetworkPlot(
    lines: List<PlotLine>,
    xAxis: PlotAxis,
    yAxis: PlotAxis,
    description: String,
    shaded: Boolean = false,
    showSamples: Boolean = false,
    height: Dp = 172.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        val xLabels = xAxis.ticks.map { tick -> tick to measurer.measure(tick.label, labelStyle) }
        val yLabels = yAxis.ticks.map { tick -> tick to measurer.measure(tick.label, labelStyle) }
        val labelHeight = (xLabels + yLabels).maxOfOrNull { it.second.size.height } ?: 0
        val left = (yLabels.maxOfOrNull { it.second.size.width } ?: 0) + 8.dp.toPx()
        val right = size.width - maxOf(4.dp.toPx(), (xLabels.lastOrNull()?.second?.size?.width ?: 0) / 2f)
        val top = labelHeight / 2f + 4.dp.toPx()
        val bottom = size.height - labelHeight - 8.dp.toPx()
        if (right <= left || bottom <= top) return@Canvas

        fun x(value: Float): Float = left +
            (value - xAxis.range.start) / (xAxis.range.endInclusive - xAxis.range.start) * (right - left)

        fun y(value: Float): Float = bottom -
            (value.coerceIn(yAxis.range) - yAxis.range.start) /
            (yAxis.range.endInclusive - yAxis.range.start) * (bottom - top)

        yLabels.forEach { (tick, layout) ->
            val yPosition = y(tick.value)
            drawLine(gridColor, Offset(left, yPosition), Offset(right, yPosition))
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), yPosition - layout.size.height / 2f))
        }

        var labelRight = 0f
        xLabels.forEach { (tick, layout) ->
            val xPosition = x(tick.value)
            val labelLeft = (xPosition - layout.size.width / 2f).coerceAtLeast(0f)
            if (labelLeft >= labelRight && labelLeft + layout.size.width <= size.width) {
                drawLine(gridColor.copy(alpha = 0.5f), Offset(xPosition, top), Offset(xPosition, bottom))
                drawText(layout, topLeft = Offset(labelLeft, bottom + 4.dp.toPx()))
                labelRight = labelLeft + layout.size.width + 6.dp.toPx()
            }
        }

        clipRect(left - 3.dp.toPx(), top - 3.dp.toPx(), right + 3.dp.toPx(), bottom + 3.dp.toPx()) {
            lines.forEach { line ->
                val first = line.points.firstOrNull() ?: return@forEach
                if (line.points.size > 1) {
                    val path = Path().apply {
                        moveTo(x(first.x), y(first.y))
                        line.points.drop(1).forEach { lineTo(x(it.x), y(it.y)) }
                    }
                    if (shaded) {
                        val area = Path().apply {
                            addPath(path)
                            lineTo(x(line.points.last().x), bottom)
                            lineTo(x(first.x), bottom)
                            close()
                        }
                        drawPath(area, line.color.copy(alpha = 0.13f))
                    }
                    drawPath(path, line.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (showSamples || line.points.size == 1) {
                    line.points.forEach { sample ->
                        drawCircle(line.color, radius = 3.dp.toPx(), center = Offset(x(sample.x), y(sample.y)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryPlot(
    lines: List<TimeLine>,
    yAxis: PlotAxis,
    description: String,
    windowMillis: Long? = null,
    showSamples: Boolean = false,
    height: Dp = 172.dp,
) {
    val samples = lines.flatMap { it.samples }
    val latest = samples.maxOfOrNull { it.elapsedMillis } ?: return
    val span = windowMillis ?: (latest - samples.minOf { it.elapsedMillis }).coerceAtLeast(1_000)
    val start = latest - span
    val duration = span / 1_000f
    val ticks = if (windowMillis == null) {
        listOf(PlotTick(0f, "0초"), PlotTick(duration / 2, elapsedLabel(duration / 2)), PlotTick(duration, elapsedLabel(duration)))
    } else {
        listOf(PlotTick(0f, "−${elapsedLabel(duration)}"), PlotTick(duration / 2, "−${elapsedLabel(duration / 2)}"), PlotTick(duration, "현재"))
    }
    NetworkPlot(
        lines = lines.map { line ->
            PlotLine(
                color = line.color,
                points = line.samples.filter { it.elapsedMillis >= start }.map {
                    Offset((it.elapsedMillis - start) / 1_000f, it.value)
                },
            )
        },
        xAxis = PlotAxis(0f..duration, ticks),
        yAxis = yAxis,
        description = description,
        showSamples = showSamples,
        height = height,
    )
}

private fun elapsedLabel(seconds: Float): String {
    if (seconds < 10f && seconds % 1f != 0f) return String.format(Locale.KOREA, "%.1f초", seconds)
    val whole = seconds.roundToLong()
    return if (whole < 60) "${whole}초" else "${whole / 60}분 ${whole % 60}초"
}

@Composable
internal fun PlotLegend(label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PlotBar(label: String, value: String, fraction: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
        }
    }
}
