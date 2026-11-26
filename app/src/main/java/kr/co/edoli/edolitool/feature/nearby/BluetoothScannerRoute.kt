package kr.co.edoli.edolitool.feature.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private data class RssiSample(val elapsedMillis: Long, val rssi: Int)

private data class BleReading(
    val device: BluetoothDevice,
    val name: String,
    val rssi: Int,
    val connectable: Boolean,
    val samples: List<RssiSample>,
)

@SuppressLint("MissingPermission")
@Composable
fun BluetoothScannerRoute(onBackClick: () -> Unit, explorer: Boolean = false) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapter = remember(context) { context.getSystemService(BluetoothManager::class.java)?.adapter }
    val permissions = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val (granted, requestPermissions) = rememberToolPermissions(permissions)
    val devices = remember { mutableStateMapOf<String, BleReading>() }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("검색을 시작하면 주변 BLE 기기가 표시됩니다.") }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var connectedDevice by remember { mutableStateOf<BleReading?>(null) }

    LaunchedEffect(granted) {
        if (!granted) {
            scanning = false
            connectedDevice = null
            devices.clear()
            selectedAddress = null
        }
    }

    LaunchedEffect(scanning, granted, lifecycle, adapter) {
        if (!scanning || !granted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            status = "BLE 기기 검색 중…"
            try {
                val scanner = adapter?.bluetoothLeScanner
                if (scanner == null) {
                    status = "블루투스가 켜져 있는지 확인해 주세요."
                    return@repeatOnLifecycle
                }

                withTimeoutOrNull(20_000) {
                    scanner.results().collect { result ->
                        val address = result.device.address
                        val previous = devices[address]
                        val samples = previous?.samples.orEmpty()
                        val sample = RssiSample(result.timestampNanos / 1_000_000, result.rssi)
                        if (samples.lastOrNull()?.let { sample.elapsedMillis - it.elapsedMillis < 250 } == true) {
                            return@collect
                        }
                        devices[address] = BleReading(
                            device = result.device,
                            name = result.scanRecord?.deviceName ?: previous?.name ?: "이름 없는 기기",
                            rssi = result.rssi,
                            connectable = result.isConnectable,
                            samples = (samples + sample).takeLast(80),
                        )
                    }
                }
                status = if (devices.isEmpty()) "감지된 BLE 기기가 없습니다." else "BLE 기기 ${devices.size}개 감지"
            } catch (error: CancellationException) {
                throw error
            } catch (_: SecurityException) {
                status = "블루투스 검색 권한을 확인해 주세요."
            } catch (error: IllegalStateException) {
                status = error.message ?: "BLE 검색을 시작할 수 없습니다."
            } finally {
                scanning = false
                if (status == "BLE 기기 검색 중…") status = "검색이 중지되었습니다."
            }
        }
    }

    val title = if (explorer) "BLE 탐색기" else "블루투스 검색"
    val selectedDevice = connectedDevice
    if (selectedDevice != null && granted) {
        BackHandler { connectedDevice = null }
        GattExplorer(
            device = selectedDevice.device,
            deviceName = selectedDevice.name,
            onBackClick = { connectedDevice = null },
        )
        return
    }

    NearbyToolScreen(title, onBackClick) {
        if (!granted) {
            item {
                val message = if (Build.VERSION.SDK_INT >= 31) {
                    "BLE 기기를 검색하고 연결하려면 주변 기기 권한이 필요합니다."
                } else {
                    "BLE 기기를 검색하려면 정확한 위치 권한이 필요합니다."
                }
                NearbyPermissionCard(message, requestPermissions)
            }
        } else {
            item {
                NearbyToolCard {
                    Text(if (adapter == null) "이 기기는 블루투스를 지원하지 않습니다." else status)
                    if (scanning) {
                        OutlinedButton(
                            onClick = {
                                status = "검색이 중지되었습니다."
                                scanning = false
                            },
                        ) { Text("검색 중지") }
                    } else {
                        Button(
                            enabled = adapter != null,
                            onClick = {
                                devices.clear()
                                selectedAddress = null
                                scanning = true
                            },
                        ) { Text("주변 BLE 기기 검색") }
                    }
                }
            }
            val orderedDevices = devices.entries.sortedByDescending { it.value.rssi }
            items(orderedDevices, key = { it.key }) { (address, reading) ->
                NearbyToolCard {
                    Text(reading.name, style = MaterialTheme.typography.titleMedium)
                    Text(address)
                    Text("${reading.rssi} dBm")
                    if (explorer) {
                        if (reading.connectable) {
                            OutlinedButton(
                                onClick = {
                                    scanning = false
                                    connectedDevice = reading
                                },
                            ) { Text("GATT 연결") }
                        } else {
                            Text("연결할 수 없는 광고")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { selectedAddress = address.takeUnless { selectedAddress == address } },
                        ) {
                            Text(if (selectedAddress == address) "그래프 닫기" else "RSSI 그래프")
                        }
                        if (selectedAddress == address) RssiGraph(reading.samples)
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun BluetoothLeScanner.results() = callbackFlow<ScanResult> {
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            trySend(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { trySend(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            close(IllegalStateException("BLE 검색에 실패했습니다. (코드 ${errorCode})"))
        }
    }
    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    startScan(null, settings, callback)
    awaitClose { runCatching { stopScan(callback) } }
}

@Composable
private fun RssiGraph(samples: List<RssiSample>) {
    if (samples.size < 2) {
        Text("그래프를 표시하려면 RSSI 표본이 두 개 이상 필요합니다.")
        return
    }
    val start = samples.first().elapsedMillis
    val duration = (samples.last().elapsedMillis - start).coerceAtLeast(1)
    val signalColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.height(100.dp), verticalArrangement = Arrangement.SpaceBetween) {
            for (tick in listOf("−20", "−65", "−110")) {
                Text(tick, style = MaterialTheme.typography.labelSmall)
            }
        }
        Canvas(modifier = Modifier.weight(1f).height(100.dp)) {
            fun position(sample: RssiSample): Offset {
                val x = (sample.elapsedMillis - start).toFloat() / duration * size.width
                val y = (-20 - sample.rssi.coerceIn(-110, -20)) / 90f * size.height
                return Offset(x, y)
            }

            for (fraction in listOf(0f, 0.5f, 1f)) {
                val y = size.height * fraction
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            samples.zipWithNext().forEach { (previous, current) ->
                drawLine(signalColor, position(previous), position(current), strokeWidth = 2.dp.toPx())
            }
        }
    }
    val seconds = String.format(Locale.KOREA, "%.1f", duration / 1_000.0)
    Text(
        "시간 ${seconds}초 · 신호 ${samples.minOf { it.rssi }} ~ ${samples.maxOf { it.rssi }} dBm",
        style = MaterialTheme.typography.bodySmall,
    )
}
