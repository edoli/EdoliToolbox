package kr.co.edoli.edolitool.feature.physical

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kr.co.edoli.edolitool.feature.nearby.NearbyPermissionCard
import kr.co.edoli.edolitool.feature.nearby.rememberToolPermissions
import kr.co.edoli.edolitool.ui.component.PlotBar
import kr.co.edoli.edolitool.ui.component.TimePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.log10

private const val SOUND_SAMPLE_RATE = 44_100
private const val SOUND_FLOOR = -100f

@Composable
internal fun SoundMeter() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val (granted, requestPermissions) = rememberToolPermissions(listOf(Manifest.permission.RECORD_AUDIO))
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(emptyList<TimePoint>()) }
    var peak by remember { mutableStateOf<Float?>(null) }
    var referenceInput by rememberSaveable { mutableStateOf("") }
    var correction by rememberSaveable { mutableStateOf<Float?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { running = false }

    LaunchedEffect(running, granted, lifecycle, context) {
        if (!running || !granted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val failure = try {
                microphoneLevels(context).collect { reading ->
                    history = history.recordMeasurement(reading.elapsedMillis, reading.value)
                    peak = peak?.let { maxOf(it, reading.value) } ?: reading.value
                }
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                "마이크 권한을 확인해 주세요."
            } catch (_: IllegalStateException) {
                "마이크를 사용할 수 없습니다."
            } catch (_: IllegalArgumentException) {
                "이 기기에서 마이크 측정을 시작할 수 없습니다."
            }
            if (failure != null) {
                error = failure
                running = false
            }
        }
    }

    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
        MeasurementCard { Text("이 기기에서는 마이크를 사용할 수 없습니다.") }
        return
    }
    if (!granted) {
        NearbyPermissionCard("소음 레벨을 측정하려면 마이크 권한이 필요합니다.", requestPermissions)
        return
    }

    val latest = history.lastOrNull()?.value
    val calibrated = correction
    val offset = calibrated ?: 0f
    val unit = if (calibrated == null) "dBFS" else "dB SPL"
    val reference = referenceInput.trim().replace(',', '.').toFloatOrNull()?.takeIf { it in 20f..130f }
    MeasurementCard {
        MeasurementValue(
            value = latest?.let { number(it + offset) } ?: "—",
            unit = unit,
            caption = if (calibrated == null) "마이크 입력의 상대 레벨" else "참조 소음계로 보정한 추정 음압 레벨",
        )
        if (latest != null) {
            PlotBar(
                label = "마이크 입력",
                value = "${number(latest)} dBFS",
                fraction = (latest - SOUND_FLOOR) / -SOUND_FLOOR,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        peak?.let { Text("최대 RMS ${number(it + offset)} ${unit}") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (running && latest == null) Text("마이크 값을 기다리는 중입니다.")
        Button(
            onClick = {
                if (running) {
                    running = false
                } else {
                    history = emptyList()
                    peak = null
                    error = null
                    running = true
                }
            },
        ) { Text(if (running) "측정 중지" else "마이크 측정 시작") }
    }
    MeasurementHistory(history.map { it.copy(value = it.value + offset) }, unit, minimumSpan = 12f)
    MeasurementCard {
        Text("음압 기준 보정", style = MaterialTheme.typography.titleMedium)
        Text("같은 위치에서 참조 소음계가 표시하는 값을 입력해 주세요.")
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = referenceInput,
            onValueChange = { if (it.length <= 7) referenceInput = it },
            label = { Text("참조값 (dB SPL)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = referenceInput.isNotBlank() && reference == null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = running && latest != null && latest > -85f && reference != null,
                onClick = {
                    if (latest != null && reference != null) correction = reference - latest
                },
            ) { Text("기준 적용") }
            OutlinedButton(onClick = { correction = null }, enabled = calibrated != null) {
                Text("보정 초기화")
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun microphoneLevels(context: Context) = flow {
    val manager = context.getSystemService(AudioManager::class.java)
    val source = if (manager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
        MediaRecorder.AudioSource.UNPROCESSED
    } else {
        MediaRecorder.AudioSource.VOICE_RECOGNITION
    }
    val channels = AudioFormat.CHANNEL_IN_MONO
    val encoding = AudioFormat.ENCODING_PCM_16BIT
    val minimum = AudioRecord.getMinBufferSize(SOUND_SAMPLE_RATE, channels, encoding)
    check(minimum > 0)
    val samples = ShortArray(2_048)
    val recorder = AudioRecord(source, SOUND_SAMPLE_RATE, channels, encoding, maxOf(minimum, 2 * samples.size))
    try {
        check(recorder.state == AudioRecord.STATE_INITIALIZED)
        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        var count = 0
        var sum = 0.0
        var squares = 0.0
        var lastData = SystemClock.elapsedRealtime()

        while (currentCoroutineContext().isActive) {
            val size = recorder.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING)
            check(size >= 0)
            val now = SystemClock.elapsedRealtime()
            if (size > 0) lastData = now
            check(now - lastData < 2_000)
            for (index in 0 until size) {
                val value = samples[index] / 32_768.0
                sum += value
                squares += value * value
            }
            count += size
            if (count >= SOUND_SAMPLE_RATE / 7) {
                val mean = sum / count
                val variance = (squares / count - mean * mean).coerceAtLeast(1e-10)
                emit(TimePoint(now, (10 * log10(variance)).toFloat().coerceIn(SOUND_FLOOR, 0f)))
                count = 0
                sum = 0.0
                squares = 0.0
            }
            delay(15)
        }
    } finally {
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) runCatching { recorder.stop() }
        recorder.release()
    }
}.flowOn(Dispatchers.IO)
