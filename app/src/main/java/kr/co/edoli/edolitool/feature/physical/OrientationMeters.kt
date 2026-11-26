package kr.co.edoli.edolitool.feature.physical

import android.hardware.Sensor
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun LevelMeter() {
    val view = LocalView.current
    val sensor = rememberSensor(Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)
    val gravityFilter = remember { GravityFilter() }
    var raw by remember { mutableStateOf<Offset?>(null) }
    var rotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    var zeroX by rememberSaveable { mutableFloatStateOf(0f) }
    var zeroY by rememberSaveable { mutableFloatStateOf(0f) }
    val available = SensorMonitor(sensor, 40_000) { event ->
        val input = event.vector()
        val gravity = if (event.type == Sensor.TYPE_ACCELEROMETER) gravityFilter.gravity(input, event.time) else input
        val (x, y, z) = gravity
        if (gravity.length > 0.1f) {
            rotation = view.display?.rotation ?: Surface.ROTATION_0
            raw = Offset(
                degrees(atan2(x, sqrt(y * y + z * z))),
                degrees(atan2(y, sqrt(x * x + z * z))),
            )
        }
    }

    if (!available) {
        SensorUnavailable("중력 또는 가속도")
        return
    }

    val tilt = raw?.let {
        val display = AxisVector(it.x - zeroX, it.y - zeroY, 0f).forDisplay(rotation)
        Offset(display.x, display.y)
    }
    val level = tilt != null && abs(tilt.x) < 0.4f && abs(tilt.y) < 0.4f
    MeasurementCard {
        Text(
            text = when {
                tilt == null -> "센서 값을 기다리는 중입니다."
                level -> "수평"
                else -> "기울어짐"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        LevelDial(tilt, level)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("가로 기울기", style = MaterialTheme.typography.labelLarge)
                Text(tilt?.let { "${number(it.x)}°" } ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.weight(1f)) {
                Text("세로 기울기", style = MaterialTheme.typography.labelLarge)
                Text(tilt?.let { "${number(it.y)}°" } ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    raw?.let {
                        zeroX = it.x
                        zeroY = it.y
                    }
                },
                enabled = raw != null,
            ) { Text("영점 설정") }
            OutlinedButton(
                onClick = {
                    zeroX = 0f
                    zeroY = 0f
                },
                enabled = zeroX != 0f || zeroY != 0f,
            ) { Text("보정 초기화") }
        }
    }
}

@Composable
private fun LevelDial(tilt: Offset?, level: Boolean) {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val accent = if (level) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(222.dp)
            .semantics { contentDescription = "기울기를 표시하는 수평계 원형 눈금" },
    ) {
        val radius = size.minDimension * 0.45f
        val bubbleRadius = radius * 0.18f
        drawCircle(surface, radius)
        drawCircle(outline, radius, style = Stroke(1.dp.toPx()))
        drawCircle(outline.copy(alpha = 0.6f), radius * 0.64f, style = Stroke(1.dp.toPx()))
        drawCircle(outline, radius * 0.22f, style = Stroke(1.dp.toPx()))
        drawLine(outline.copy(alpha = 0.55f), center - Offset(radius, 0f), center + Offset(radius, 0f))
        drawLine(outline.copy(alpha = 0.55f), center - Offset(0f, radius), center + Offset(0f, radius))
        if (tilt != null) {
            val position = Offset(tilt.x, -tilt.y)
            val distance = position.getDistance()
            val bubble = center + position * ((radius - bubbleRadius) / maxOf(distance, 12f))
            drawCircle(accent.copy(alpha = 0.28f), bubbleRadius, bubble)
            drawCircle(accent, bubbleRadius, bubble, style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
internal fun CompassMeter() {
    val view = LocalView.current
    val rotationSensor = rememberSensor(Sensor.TYPE_ROTATION_VECTOR)
    val gravitySensor = rememberSensor(Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)
    val magneticSensor = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD)
    val gravityFilter = remember { GravityFilter() }
    val matrix = remember { FloatArray(9) }
    val remapped = remember { FloatArray(9) }
    val orientation = remember { FloatArray(3) }
    var gravity by remember { mutableStateOf<AxisVector?>(null) }
    var magnetism by remember { mutableStateOf<AxisVector?>(null) }
    var heading by remember { mutableStateOf<Float?>(null) }

    fun readHeading() {
        val (x, y) = when (view.display?.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (SensorManager.remapCoordinateSystem(matrix, x, y, remapped)) {
            SensorManager.getOrientation(remapped, orientation)
            heading = (degrees(orientation[0]) + 360f) % 360f
        }
    }

    fun readFromField() {
        val down = gravity ?: return
        val field = magnetism ?: return
        if (SensorManager.getRotationMatrix(matrix, null, down.toArray(), field.toArray())) {
            readHeading()
        }
    }

    val available = if (rotationSensor != null) {
        SensorMonitor(rotationSensor, 40_000) { event ->
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            readHeading()
        }
    } else {
        val hasGravity = SensorMonitor(gravitySensor, 40_000) { event ->
            val input = event.vector()
            gravity = if (event.type == Sensor.TYPE_ACCELEROMETER) gravityFilter.gravity(input, event.time) else input
            readFromField()
        }
        val hasMagnetism = SensorMonitor(magneticSensor, 40_000) { event ->
            magnetism = event.vector()
            readFromField()
        }
        hasGravity && hasMagnetism
    }

    if (!available) {
        SensorUnavailable("방향")
        return
    }

    val current = heading
    val directionNames = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
    val headingText = current?.let { "${it.roundToInt() % 360}° ${directionNames[((it + 22.5f) / 45f).toInt() % 8]}" } ?: "—"
    MeasurementCard {
        Text(headingText, style = MaterialTheme.typography.displaySmall)
        Text("자북 기준 · 기기 윗부분이 향하는 방향")
        CompassDial(current)
    }
}

@Composable
private fun CompassDial(heading: Float?) {
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val dial = MaterialTheme.colorScheme.surface
    val style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(258.dp)
            .semantics { contentDescription = "나침반 방향 눈금" },
    ) {
        val radius = size.minDimension * 0.46f
        drawCircle(dial, radius)
        drawCircle(outline, radius, style = Stroke(1.dp.toPx()))
        for (bearing in 0 until 360 step 5) {
            val outer = polar(bearing.toFloat() - 90f, radius)
            val inner = polar(bearing.toFloat() - 90f, radius * if (bearing % 30 == 0) 0.86f else 0.94f)
            drawLine(outline, center + inner, center + outer, strokeWidth = if (bearing % 30 == 0) 2.dp.toPx() else 1.dp.toPx())
        }
        listOf("N", "E", "S", "W").forEachIndexed { index, letter ->
            val layout = measurer.measure(letter, style)
            val point = center + polar(index * 90f - 90f, radius * 0.74f)
            drawText(layout, topLeft = point - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
        if (heading != null) {
            val azimuth = heading - 90f
            val tip = center + polar(azimuth, radius * 0.61f)
            val tail = center - polar(azimuth, radius * 0.24f)
            val wing = polar(azimuth + 90f, radius * 0.09f)
            drawPath(
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tail.x + wing.x, tail.y + wing.y)
                    lineTo(center.x, center.y)
                    lineTo(tail.x - wing.x, tail.y - wing.y)
                    close()
                },
                color,
            )
            drawCircle(color, 4.dp.toPx(), center)
        }
    }
}

@Composable
internal fun ScreenProtractor() {
    var angle by rememberSaveable { mutableFloatStateOf(45f) }
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()

    MeasurementCard {
        Text("${angle.roundToInt()}°", style = MaterialTheme.typography.displaySmall)
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .pointerInput(Unit) {
                    detectTapGestures { angle = angleAt(it, size.width.toFloat(), size.height - 20.dp.toPx()) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { angle = angleAt(it, size.width.toFloat(), size.height - 20.dp.toPx()) },
                    ) { change, _ ->
                        angle = angleAt(change.position, size.width.toFloat(), size.height - 20.dp.toPx())
                        change.consume()
                    }
                }
                .semantics { contentDescription = "터치로 각도를 조절하는 각도기" },
        ) {
            val origin = Offset(size.width / 2f, size.height - 20.dp.toPx())
            val radius = minOf(size.width * 0.46f, origin.y - 5.dp.toPx())
            if (radius <= 0f) return@Canvas
            val arcTop = origin - Offset(radius, radius)
            val arcSize = Size(radius * 2, radius * 2)
            drawArc(surface, 180f, 180f, useCenter = true, topLeft = arcTop, size = arcSize)
            drawArc(outline, 180f, 180f, useCenter = false, topLeft = arcTop, size = arcSize, style = Stroke(1.dp.toPx()))
            val baseline = Offset(radius, 0f)
            drawLine(outline, origin - baseline, origin + baseline, strokeWidth = 1.dp.toPx())
            val angleRadius = radius * 0.34f
            drawArc(
                color.copy(alpha = 0.20f), -angle, angle, useCenter = true,
                topLeft = origin - Offset(angleRadius, angleRadius),
                size = Size(angleRadius * 2, angleRadius * 2),
            )
            for (tick in 0..180 step 5) {
                val major = tick % 30 == 0
                val inner = if (major) 0.87f else 0.94f
                drawLine(
                    outline,
                    origin + polar(-tick.toFloat(), radius * inner),
                    origin + polar(-tick.toFloat(), radius),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                )
                if (major) {
                    val label = measurer.measure(tick.toString(), style)
                    val point = origin + polar(-tick.toFloat(), radius * 0.76f)
                    drawText(label, topLeft = point - Offset(label.size.width / 2f, label.size.height / 2f))
                }
            }
            drawLine(color, origin, origin + polar(-angle, radius), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color, 4.dp.toPx(), origin)
        }
        Text("각도 조절", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = angle,
            onValueChange = { angle = it },
            valueRange = 0f..180f,
            steps = 179,
            modifier = Modifier.semantics { contentDescription = "각도 조절" },
        )
    }
}

private fun angleAt(point: Offset, width: Float, baseline: Float): Float {
    val x = point.x - width / 2f
    val y = (baseline - point.y).coerceAtLeast(0f)
    return degrees(atan2(y, x)).roundToInt().toFloat()
}

private fun polar(degrees: Float, radius: Float): Offset {
    val radians = (degrees * PI / 180.0).toFloat()
    return Offset(cos(radians) * radius, sin(radians) * radius)
}

private fun degrees(radians: Float): Float = (radians * 180.0 / PI).toFloat()
