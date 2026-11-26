package kr.co.edoli.edolitool.feature.physical

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kr.co.edoli.edolitool.ui.component.TimePoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun VectorMeter(magnetic: Boolean) {
    val sensor = rememberSensor(if (magnetic) Sensor.TYPE_MAGNETIC_FIELD else Sensor.TYPE_ACCELEROMETER)
    var vector by remember { mutableStateOf<AxisVector?>(null) }
    var history by remember { mutableStateOf(emptyList<TimePoint>()) }
    val available = SensorMonitor(sensor, 25_000) { event ->
        val reading = event.vector()
        val updated = history.recordMeasurement(event.time, reading.length)
        if (updated !== history) {
            vector = reading
            history = updated
        }
    }
    if (!available) {
        SensorUnavailable(if (magnetic) "자기장" else "가속도")
        return
    }

    val unit = if (magnetic) "µT" else "m/s²"
    MeasurementCard {
        MeasurementValue(
            value = vector?.let { number(it.length, 2) } ?: "—",
            unit = unit,
            caption = if (magnetic) "전체 자기장 세기" else "전체 가속도 · 중력 포함",
        )
        vector?.let { AxisReadouts(it, unit, 2) }
    }
    MeasurementHistory(history, unit, minimumSpan = if (magnetic) 5f else 0.5f)
}

@Composable
internal fun EnvironmentMeter(pressure: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sensor = rememberSensor(if (pressure) Sensor.TYPE_PRESSURE else Sensor.TYPE_LIGHT)
    var value by remember { mutableStateOf<Float?>(null) }
    var reference by rememberSaveable { mutableStateOf<Float?>(null) }
    var history by remember { mutableStateOf(emptyList<TimePoint>()) }
    val available = SensorMonitor(sensor, 200_000) { event ->
        val reading = event.values[0]
        if (if (pressure) reading > 0f else reading >= 0f) {
            value = reading
        }
    }
    if (!available) {
        SensorUnavailable(if (pressure) "기압" else "조도")
        return
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value?.let { history = history.recordMeasurement(SystemClock.elapsedRealtime(), it) }
                delay(250)
            }
        }
    }

    val unit = if (pressure) "hPa" else "lux"
    val latest = value
    MeasurementCard {
        MeasurementValue(
            value = latest?.let { number(it, if (pressure) 2 else 1) } ?: "—",
            unit = unit,
            caption = if (pressure) "현재 기압" else "주변 밝기",
        )
        if (latest == null) Text("센서 값을 기다리는 중입니다.")
    }
    if (pressure) {
        val baseline = reference
        val altitude = if (baseline != null && latest != null) SensorManager.getAltitude(baseline, latest) else null
        MeasurementCard {
            Text("기준점 대비 고도 변화", style = MaterialTheme.typography.titleMedium)
            if (altitude == null) {
                Text("기준점에서 현재 기압을 기록하면 높이 차이를 추정합니다.")
            } else {
                MeasurementValue(number(altitude), "m", "기준점에서의 높이 변화")
            }
            OutlinedButton(
                onClick = { reference = latest },
                enabled = latest != null,
            ) { Text("현재 위치를 기준점으로 설정") }
        }
    }
    MeasurementHistory(history, unit, minimumSpan = if (pressure) 1f else 10f, zeroBased = !pressure)
}

@Composable
internal fun VibrationMeter() {
    val sensor = rememberSensor(Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER)
    val gravityFilter = remember { GravityFilter() }
    var history by remember { mutableStateOf(emptyList<TimePoint>()) }
    var raw by remember { mutableStateOf<AxisVector?>(null) }
    var offset by remember { mutableStateOf<AxisVector?>(null) }
    var peak by remember { mutableFloatStateOf(0f) }
    var windowPeak by remember { mutableFloatStateOf(0f) }

    val available = SensorMonitor(sensor, 20_000) { event ->
        val acceleration = event.vector()
        val linear = if (event.type == Sensor.TYPE_ACCELEROMETER) {
            acceleration - gravityFilter.gravity(acceleration, event.time)
        } else {
            acceleration
        }
        val magnitude = (offset?.let { linear - it } ?: linear).length
        peak = maxOf(peak, magnitude)
        windowPeak = maxOf(windowPeak, magnitude)
        val updated = history.recordMeasurement(event.time, windowPeak)
        if (updated !== history) {
            raw = linear
            history = updated
            windowPeak = 0f
        }
    }
    if (!available) {
        SensorUnavailable("가속도")
        return
    }

    val current = history.lastOrNull()
    MeasurementCard {
        MeasurementValue(current?.let { number(it.value, 2) } ?: "—", "m/s²", "중력을 뺀 기기 진동 가속도")
        Text("최대 ${number(peak, 2)} m/s² · ${number(peak * 100)} Gal")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    offset = raw
                    history = emptyList()
                    peak = 0f
                    windowPeak = 0f
                },
                enabled = raw != null,
            ) { Text("영점 설정") }
            OutlinedButton(
                onClick = {
                    peak = 0f
                    history = emptyList()
                    windowPeak = 0f
                },
                enabled = current != null,
            ) { Text("기록 초기화") }
        }
    }
    MeasurementHistory(history, "m/s²", minimumSpan = 0.25f, zeroBased = true)
}

@Composable
private fun AxisReadouts(vector: AxisVector, unit: String, decimals: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("X" to vector.x, "Y" to vector.y, "Z" to vector.z).forEach { (label, value) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text("${number(value, decimals)} ${unit}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
