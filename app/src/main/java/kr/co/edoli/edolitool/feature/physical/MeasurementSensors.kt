package kr.co.edoli.edolitool.feature.physical

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import kr.co.edoli.edolitool.ui.component.HistoryPlot
import kr.co.edoli.edolitool.ui.component.PlotAxis
import kr.co.edoli.edolitool.ui.component.PlotTick
import kr.co.edoli.edolitool.ui.component.TimeLine
import kr.co.edoli.edolitool.ui.component.TimePoint
import java.util.Locale
import kotlin.math.exp
import kotlin.math.sqrt

internal const val MEASUREMENT_WINDOW = 15_000L

internal data class SensorReading(val type: Int, val time: Long, val values: FloatArray)

internal data class AxisVector(val x: Float, val y: Float, val z: Float) {
    val length get() = sqrt(x * x + y * y + z * z)

    operator fun minus(other: AxisVector) = AxisVector(x - other.x, y - other.y, z - other.z)

    fun forDisplay(rotation: Int): AxisVector = when (rotation) {
        Surface.ROTATION_90 -> AxisVector(y, -x, z)
        Surface.ROTATION_180 -> AxisVector(-x, -y, z)
        Surface.ROTATION_270 -> AxisVector(-y, x, z)
        else -> this
    }

    fun toArray() = floatArrayOf(x, y, z)
}

internal fun SensorReading.vector() = AxisVector(values[0], values[1], values[2])

internal class GravityFilter {
    private var previous: AxisVector? = null
    private var previousTime = 0L

    fun gravity(acceleration: AxisVector, time: Long): AxisVector {
        val old = previous
        val duration = time - previousTime
        val estimate = if (old == null || duration > 1_000L) {
            acceleration
        } else {
            val weight = exp(-duration.coerceAtLeast(0) / 350f)
            AxisVector(
                old.x * weight + acceleration.x * (1 - weight),
                old.y * weight + acceleration.y * (1 - weight),
                old.z * weight + acceleration.z * (1 - weight),
            )
        }
        previousTime = time
        previous = estimate
        return estimate
    }
}

@Composable
internal fun rememberSensor(type: Int, fallback: Int? = null): Sensor? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    return remember(manager, type, fallback) {
        manager?.getDefaultSensor(type) ?: fallback?.let { manager?.getDefaultSensor(it) }
    }
}

@Composable
internal fun SensorMonitor(
    sensor: Sensor?,
    intervalMicros: Int,
    onReading: (SensorReading) -> Unit,
): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val latestCallback by rememberUpdatedState(onReading)
    var available by remember(manager, sensor) { mutableStateOf(sensor != null && manager != null) }

    LifecycleStartEffect(manager, sensor, intervalMicros) {
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values
                if (values.isNotEmpty() && values.all(Float::isFinite)) {
                    latestCallback(SensorReading(event.sensor.type, event.timestamp / 1_000_000, values.copyOf()))
                }
            }
        }
        val registered = sensor != null && manager != null && manager.registerListener(listener, sensor, intervalMicros)
        available = registered
        onStopOrDispose {
            if (registered) manager.unregisterListener(listener)
        }
    }
    return available
}

internal fun List<TimePoint>.recordMeasurement(time: Long, value: Float): List<TimePoint> {
    val latest = lastOrNull()
    if (latest != null && time - latest.elapsedMillis < 100L) return this
    return (this + TimePoint(time, value)).dropWhile { time - it.elapsedMillis > MEASUREMENT_WINDOW }
}

internal fun number(value: Float, decimals: Int = 1): String =
    String.format(Locale.getDefault(), "%.${decimals}f", if (value == 0f) 0f else value)

@Composable
internal fun SensorUnavailable(name: String) {
    MeasurementCard { Text("이 기기에서는 ${name} 센서를 사용할 수 없습니다.") }
}

@Composable
internal fun MeasurementHistory(
    samples: List<TimePoint>,
    unit: String,
    minimumSpan: Float,
    zeroBased: Boolean = false,
) {
    if (samples.isEmpty()) return
    val lowest = if (zeroBased) 0f else samples.minOf { it.value }
    val highest = samples.maxOf { it.value }
    val padding = ((highest - lowest).coerceAtLeast(minimumSpan)) * 0.12f
    val low = if (zeroBased) 0f else lowest - padding
    val high = maxOf(highest + padding, low + minimumSpan)
    MeasurementCard {
        Text("측정 이력 · ${unit}", style = MaterialTheme.typography.titleMedium)
        HistoryPlot(
            lines = listOf(TimeLine(MaterialTheme.colorScheme.primary, samples)),
            yAxis = PlotAxis(
                low..high,
                listOf(low, (low + high) / 2f, high).map { PlotTick(it, number(it, if (minimumSpan < 1f) 2 else 1)) },
            ),
            description = "최근 15초 측정값 그래프, 단위 ${unit}",
            windowMillis = MEASUREMENT_WINDOW,
            showSamples = samples.size < 12,
        )
    }
}
