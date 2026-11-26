package kr.co.edoli.edolitool.feature.network

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kr.co.edoli.edolitool.model.ToolItem
import kr.co.edoli.edolitool.ui.component.HistoryPlot
import kr.co.edoli.edolitool.ui.component.PlotAxis
import kr.co.edoli.edolitool.ui.component.PlotLegend
import kr.co.edoli.edolitool.ui.component.PlotTick
import kr.co.edoli.edolitool.ui.component.TimeLine
import kr.co.edoli.edolitool.ui.component.TimePoint
import kr.co.edoli.edolitool.ui.component.networkChartColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetAddress

private val transportLabels = mapOf(
    NetworkCapabilities.TRANSPORT_VPN to "VPN",
    NetworkCapabilities.TRANSPORT_WIFI to "Wi-Fi",
    NetworkCapabilities.TRANSPORT_CELLULAR to "모바일 데이터",
    NetworkCapabilities.TRANSPORT_ETHERNET to "이더넷",
    NetworkCapabilities.TRANSPORT_BLUETOOTH to "블루투스",
)

private val networkTools = listOf(
    ToolItem(
        id = "wifi_analyzer",
        name = "Wi-Fi 분석",
        description = "주변 AP의 채널과 신호 세기",
        icon = Icons.Rounded.Wifi,
        tint = Color(0xFF2563EB),
    ),
    ToolItem(
        id = "bluetooth_scanner",
        name = "블루투스 검색",
        description = "주변 BLE 기기와 RSSI 그래프",
        icon = Icons.AutoMirrored.Rounded.BluetoothSearching,
        tint = Color(0xFF0369A1),
    ),
    ToolItem(
        id = "ble_explorer",
        name = "BLE 탐색기",
        description = "GATT 서비스와 특성 읽기·쓰기",
        icon = Icons.Rounded.SettingsBluetooth,
        tint = Color(0xFF7C3AED),
    ),
    ToolItem(
        id = "network_scanner",
        name = "로컬 기기 검색",
        description = "같은 네트워크의 mDNS 서비스",
        icon = Icons.Rounded.DeviceHub,
        tint = Color(0xFF0F766E),
    ),
)

private data class NetworkConnection(
    val network: Network,
    val capabilities: NetworkCapabilities? = null,
    val linkProperties: LinkProperties? = null,
)

private data class TrafficSample(
    val receivedBytes: Long,
    val sentBytes: Long,
    val elapsedMillis: Long,
)

private const val TRAFFIC_WINDOW_MILLIS = 30_000L

private data class TrafficRates(
    val elapsedMillis: Long,
    val receiveBytesPerSecond: Long?,
    val sendBytesPerSecond: Long?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkAnalyzerRoute(onBackClick: () -> Unit, onOpenTool: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val connectivityManager = remember(context) {
        context.getSystemService(ConnectivityManager::class.java)
    }
    var connection by remember { mutableStateOf<NetworkConnection?>(null) }
    var trafficHistory by remember { mutableStateOf(emptyList<TrafficRates>()) }

    LaunchedEffect(lifecycle, connectivityManager) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (isActive) connection = NetworkConnection(network)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val current = connection
                    if (current?.network == network) {
                        connection = current.copy(capabilities = networkCapabilities)
                    }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    val current = connection
                    if (current?.network == network) {
                        connection = current.copy(linkProperties = linkProperties)
                    }
                }

                override fun onLost(network: Network) {
                    if (connection?.network == network) connection = null
                }
            }

            connectivityManager.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
            try {
                var previous = readTrafficSample()
                while (isActive) {
                    delay(1_000)
                    val current = readTrafficSample()
                    val elapsedMillis = current.elapsedMillis - previous.elapsedMillis
                    val rates = TrafficRates(
                        elapsedMillis = current.elapsedMillis,
                        receiveBytesPerSecond = bytesPerSecond(current.receivedBytes, previous.receivedBytes, elapsedMillis),
                        sendBytesPerSecond = bytesPerSecond(current.sentBytes, previous.sentBytes, elapsedMillis),
                    )
                    trafficHistory = (trafficHistory + rates).dropWhile { current.elapsedMillis - it.elapsedMillis > TRAFFIC_WINDOW_MILLIS }
                    previous = current
                }
            } finally {
                connectivityManager.unregisterNetworkCallback(callback)
                connection = null
                trafficHistory = emptyList()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("네트워크 분석") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로")
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
            ConnectionCard(connection)
            NetworkToolsCard(onOpenTool)
            TrafficCard(trafficHistory)
            connection?.linkProperties?.let { LinkDetailsCard(it) }
        }
    }
}

@Composable
private fun NetworkToolsCard(onOpenTool: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "네트워크 도구",
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            networkTools.forEach { tool ->
                ListItem(
                    headlineContent = { Text(tool.name) },
                    supportingContent = { Text(tool.description) },
                    leadingContent = { Icon(tool.icon, contentDescription = null, tint = tool.tint) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(role = Role.Button) { onOpenTool(tool.id) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(connection: NetworkConnection?) {
    val capabilities = connection?.capabilities
    val status = when {
        connection == null -> "연결 없음"
        capabilities == null -> "연결 상태 확인 중"
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "인터넷 연결 확인됨"
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> "네트워크 로그인 필요"
        else -> "인터넷 연결이 확인되지 않음"
    }

    NetworkCard("기본 연결") {
        Text(status, style = MaterialTheme.typography.titleLarge)
        if (capabilities != null) {
            val transports = transportLabels.asSequence()
                .filter { (transport, _) -> capabilities.hasTransport(transport) }
                .joinToString(" · ") { (_, label) -> label }
            NetworkField("연결 유형", transports.ifEmpty { "기타" })
            val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            NetworkField("종량제 연결", if (metered) "예" else "아니요")
        }
    }
}

@Composable
private fun TrafficCard(history: List<TrafficRates>) {
    val context = LocalContext.current
    val rates = history.lastOrNull()
    val palette = networkChartColors()

    fun formatRate(bytes: Long?): String = when {
        rates == null -> "측정 중"
        bytes == null -> "측정 불가"
        else -> "${Formatter.formatShortFileSize(context, bytes)}/s"
    }

    NetworkCard("기기 전체 실시간 트래픽") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlotLegend("받기", palette[0])
                Text(formatRate(rates?.receiveBytesPerSecond), style = MaterialTheme.typography.bodyLarge)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlotLegend("보내기", palette[1])
                Text(formatRate(rates?.sendBytesPerSecond), style = MaterialTheme.typography.bodyLarge)
            }
        }
        val receive = history.mapNotNull { sample ->
            sample.receiveBytesPerSecond?.let { TimePoint(sample.elapsedMillis, it.toFloat()) }
        }
        val send = history.mapNotNull { sample ->
            sample.sendBytesPerSecond?.let { TimePoint(sample.elapsedMillis, it.toFloat()) }
        }
        if (receive.isNotEmpty() || send.isNotEmpty()) {
            val maxRate = maxOf(1_000f, (receive + send).maxOf { it.value })
            val yAxis = PlotAxis(
                range = 0f..maxRate,
                ticks = listOf(0f, maxRate / 2, maxRate).map { PlotTick(it, formatRate(it.toLong())) },
            )
            Text("최근 30초", style = MaterialTheme.typography.labelLarge)
            HistoryPlot(
                lines = listOf(TimeLine(palette[0], receive), TimeLine(palette[1], send)),
                yAxis = yAxis,
                description = "최근 30초의 기기 전체 수신량과 송신량",
                windowMillis = TRAFFIC_WINDOW_MILLIS,
            )
        }
    }
}

@Composable
private fun LinkDetailsCard(linkProperties: LinkProperties) {
    val addresses = linkProperties.linkAddresses.joinToString("\n")
    val gateways = linkProperties.routes.asSequence()
        .filter { it.isDefaultRoute }
        .mapNotNull { route -> route.gateway?.takeUnless(InetAddress::isAnyLocalAddress)?.hostAddress }
        .distinct()
        .joinToString("\n")
    val dnsServers = linkProperties.dnsServers.mapNotNull(InetAddress::getHostAddress)

    NetworkCard("네트워크 정보") {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NetworkField("인터페이스", linkProperties.interfaceName ?: "—")
                NetworkField("IP 주소", addresses.ifEmpty { "—" })
                NetworkField("기본 게이트웨이", gateways.ifEmpty { "—" })
                NetworkField("DNS 서버", dnsServers.joinToString("\n").ifEmpty { "—" })
            }
        }
    }
}

@Composable
private fun NetworkCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun NetworkField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private suspend fun readTrafficSample(): TrafficSample = withContext(Dispatchers.IO) {
    TrafficSample(
        receivedBytes = TrafficStats.getTotalRxBytes(),
        sentBytes = TrafficStats.getTotalTxBytes(),
        elapsedMillis = SystemClock.elapsedRealtime(),
    )
}

private fun bytesPerSecond(current: Long, previous: Long, elapsedMillis: Long): Long? {
    if (previous < 0 || current < previous || elapsedMillis <= 0) return null
    return ((current - previous) * 1_000.0 / elapsedMillis).toLong()
}
