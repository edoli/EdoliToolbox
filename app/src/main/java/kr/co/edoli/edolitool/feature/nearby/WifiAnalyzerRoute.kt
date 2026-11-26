package kr.co.edoli.edolitool.feature.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import java.util.Date

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
@Composable
fun WifiAnalyzerRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val wifi = remember(context) { context.applicationContext.getSystemService(WifiManager::class.java) }
    val location = remember(context) { context.getSystemService(LocationManager::class.java) }
    val (granted, requestPermissions) = rememberToolPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION))
    var results by remember { mutableStateOf(emptyList<ScanResult>()) }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("주변 액세스 포인트를 검색할 수 있습니다.") }

    fun refresh(): Boolean {
        val unavailable = when {
            wifi == null -> "이 기기는 Wi-Fi 검색을 지원하지 않습니다."
            !wifi.isWifiEnabled -> "Wi-Fi를 켜 주세요."
            location == null || !LocationManagerCompat.isLocationEnabled(location) -> "기기의 위치 서비스를 켜 주세요."
            else -> null
        }
        if (unavailable != null) {
            results = emptyList()
            status = unavailable
            return false
        }
        return try {
            results = checkNotNull(wifi).scanResults.distinctBy { it.BSSID to it.frequency }.sortedByDescending { it.level }
            status = if (results.isEmpty()) "주변 액세스 포인트가 아직 감지되지 않았습니다." else "액세스 포인트 ${results.size}개"
            true
        } catch (_: SecurityException) {
            results = emptyList()
            status = "Wi-Fi 결과를 읽으려면 정확한 위치를 허용해 주세요."
            false
        }
    }

    LifecycleStartEffect(wifi, granted) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val failed = intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION &&
                    !intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                scanning = false
                if (refresh() && failed) status = "새 검색 결과를 받지 못했습니다. 잠시 후 다시 시도해 주세요."
            }
        }
        if (granted && wifi != null) {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION).apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            refresh()
        }
        onStopOrDispose {
            if (granted && wifi != null) context.unregisterReceiver(receiver)
            scanning = false
        }
    }

    LaunchedEffect(scanning) {
        if (scanning) {
            delay(12_000)
            scanning = false
            if (refresh()) status = "새 검색 결과를 기다리는 시간이 초과되었습니다."
        }
    }

    NearbyToolScreen("Wi-Fi 분석", onBackClick) {
        if (!granted) {
            item {
                NearbyPermissionCard("주변 Wi-Fi를 검색하려면 정확한 위치 권한이 필요합니다.", requestPermissions)
            }
        } else {
            item {
                NearbyToolCard {
                    Text(if (scanning) "검색 중…" else status)
                    results.maxOfOrNull { it.timestamp }?.takeIf { it > 0 }?.let { timestamp ->
                        val ageMillis = (SystemClock.elapsedRealtime() - timestamp / 1_000).coerceAtLeast(0)
                        val lastSeen = DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() - ageMillis))
                        Text("마지막 감지 시간 ${lastSeen}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = !scanning && wifi != null,
                        onClick = {
                            if (wifi != null && refresh()) {
                                scanning = try {
                                    wifi.startScan()
                                } catch (_: SecurityException) {
                                    false
                                }
                                if (!scanning) status = "검색을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요."
                            }
                        },
                    ) {
                        Text("주변 Wi-Fi 검색")
                    }
                }
            }
            if (results.isNotEmpty()) {
                item {
                    NearbyToolCard {
                        Text("채널별 AP 수", style = MaterialTheme.typography.titleMedium)
                        results.groupBy { wifiBand(it.frequency) }.toSortedMap().forEach { (band, accessPoints) ->
                            val counts = accessPoints.groupingBy { it.frequency }.eachCount().toSortedMap()
                            val channels = counts.entries.joinToString(" · ") { (frequency, count) ->
                                "${wifiChannel(frequency) ?: "${frequency} MHz"} (${count}개)"
                            }
                            Text("${band}  ${channels}")
                        }
                    }
                }
            }
            items(results, key = { "${it.BSSID}/${it.frequency}" }) { accessPoint ->
                NearbyToolCard {
                    Text(wifiName(accessPoint), style = MaterialTheme.typography.titleMedium)
                    Text(accessPoint.BSSID.orEmpty())
                    val channel = wifiChannel(accessPoint.frequency)?.let { "채널 ${it} · " }.orEmpty()
                    Text("${wifiBand(accessPoint.frequency)} · ${channel}${accessPoint.frequency} MHz")
                    Text("${accessPoint.level} dBm${wifiChannelWidth(accessPoint.channelWidth)}")
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun wifiName(result: ScanResult): String {
    val name = if (Build.VERSION.SDK_INT >= 33) {
        result.wifiSsid?.toString()?.removeSurrounding("\"")
    } else {
        result.SSID
    }
    return name?.takeIf { it.isNotBlank() } ?: "숨겨진 네트워크"
}

private fun wifiBand(frequency: Int): String = when (frequency) {
    in 2400..2500 -> "2.4 GHz"
    in 4900..5895 -> "5 GHz"
    in 5925..7125 -> "6 GHz"
    in 57_000..71_000 -> "60 GHz"
    else -> "기타 대역"
}

private fun wifiChannel(frequency: Int): Int? = if (Build.VERSION.SDK_INT >= 31) {
    ScanResult.convertFrequencyMhzToChannelIfSupported(frequency).takeIf { it > 0 }
} else {
    when (frequency) {
        2484 -> 14
        in 2412..2472 -> (frequency - 2407) / 5
        in 4910..4980 -> (frequency - 4000) / 5
        in 5000..5895 -> (frequency - 5000) / 5
        else -> null
    }
}

private fun wifiChannelWidth(width: Int): String = when (width) {
    ScanResult.CHANNEL_WIDTH_20MHZ -> " · 폭 20 MHz"
    ScanResult.CHANNEL_WIDTH_40MHZ -> " · 폭 40 MHz"
    ScanResult.CHANNEL_WIDTH_80MHZ -> " · 폭 80 MHz"
    ScanResult.CHANNEL_WIDTH_160MHZ -> " · 폭 160 MHz"
    ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> " · 폭 80+80 MHz"
    ScanResult.CHANNEL_WIDTH_320MHZ -> " · 폭 320 MHz"
    else -> ""
}
