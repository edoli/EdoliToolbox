package kr.co.edoli.edolitool.feature.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kr.co.edoli.edolitool.feature.nearby.NearbyPermissionCard
import kr.co.edoli.edolitool.feature.nearby.NearbyToolCard
import kr.co.edoli.edolitool.feature.nearby.NearbyToolScreen
import kr.co.edoli.edolitool.feature.nearby.rememberToolPermissions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val discoveryTypes = listOf(
    "_http._tcp",
    "_https._tcp",
    "_ipp._tcp",
    "_ipps._tcp",
    "_smb._tcp",
    "_googlecast._tcp",
    "_airplay._tcp",
    "_ssh._tcp",
)

private data class ServiceKey(val type: String, val name: String)

private data class LanService(val key: ServiceKey, val addresses: List<String>, val port: Int)

private sealed interface DiscoveryEvent {
    data class Found(val key: ServiceKey, val service: NsdServiceInfo) : DiscoveryEvent
    data class Lost(val key: ServiceKey) : DiscoveryEvent
    data object Failed : DiscoveryEvent
}

@SuppressLint("MissingPermission")
@Composable
fun NetworkScannerRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { context.getSystemService(NsdManager::class.java) }
    val permissions = if (Build.VERSION.SDK_INT >= 37) listOf(Manifest.permission.ACCESS_LOCAL_NETWORK) else emptyList()
    val (granted, requestPermissions) = rememberToolPermissions(permissions)
    val services = remember { mutableStateMapOf<ServiceKey, LanService>() }
    var scanning by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("같은 네트워크에서 mDNS로 서비스를 알리는 기기를 찾습니다.") }

    LaunchedEffect(granted) {
        if (!granted) {
            scanning = false
            services.clear()
        }
    }

    LaunchedEffect(scanning, granted, lifecycle, manager) {
        if (!scanning || !granted || manager == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            // Older Android versions may filter incoming mDNS packets even in the foreground.
            val multicastLock = if (Build.VERSION.SDK_INT <= 33) {
                wifi?.createMulticastLock("edolitool-mdns")
            } else {
                null
            }
            status = "로컬 기기 검색 중…"
            var failedTypes = 0
            try {
                multicastLock?.acquire()
                withTimeoutOrNull(16_000) {
                    discoveryTypes.map { manager.discovery(it) }.merge().collect { event ->
                        when (event) {
                            is DiscoveryEvent.Found -> {
                                if (!services.containsKey(event.key)) {
                                    val resolved = if (Build.VERSION.SDK_INT >= 34) {
                                        withTimeoutOrNull(2_500) { manager.resolve(event.service) }
                                    } else {
                                        manager.resolve(event.service)
                                    }
                                    val addresses = resolved?.addresses().orEmpty()
                                    if (resolved != null && addresses.isNotEmpty()) {
                                        services[event.key] = LanService(event.key, addresses, resolved.port)
                                    }
                                }
                            }
                            is DiscoveryEvent.Lost -> services.remove(event.key)
                            DiscoveryEvent.Failed -> failedTypes++
                        }
                    }
                }
                status = when {
                    failedTypes == discoveryTypes.size -> "검색을 시작할 수 없습니다. 로컬 네트워크 연결을 확인해 주세요."
                    failedTypes > 0 -> "검색을 마쳤습니다. 일부 서비스 유형을 검색하지 못했습니다."
                    services.isEmpty() -> "감지된 mDNS 서비스가 없습니다."
                    else -> "검색을 마쳤습니다."
                }
            } catch (_: SecurityException) {
                status = "로컬 네트워크 접근 권한을 확인해 주세요."
            } catch (_: IllegalArgumentException) {
                status = "로컬 네트워크 검색을 시작할 수 없습니다."
            } finally {
                if (multicastLock?.isHeld == true) multicastLock.release()
                scanning = false
                if (status == "로컬 기기 검색 중…") status = "검색을 중지했습니다."
            }
        }
    }

    NearbyToolScreen("로컬 기기 검색", onBackClick) {
        if (!granted) {
            item {
                NearbyPermissionCard("로컬 기기를 검색하려면 로컬 네트워크 접근 권한이 필요합니다.", requestPermissions)
            }
        } else {
            item {
                NearbyToolCard {
                    Text(if (manager == null) "이 기기는 로컬 서비스 탐색을 지원하지 않습니다." else status)
                    Text("검색 대상 · 웹, 프린터, 파일 공유, Cast, AirPlay, SSH", style = MaterialTheme.typography.bodySmall)
                    if (scanning) {
                        OutlinedButton(
                            onClick = {
                                status = "검색을 중지했습니다."
                                scanning = false
                            },
                        ) { Text("검색 중지") }
                    } else {
                        Button(
                            enabled = manager != null,
                            onClick = {
                                services.clear()
                                scanning = true
                            },
                        ) { Text("로컬 기기 검색") }
                    }
                }
            }

            val devices = services.values.groupBy { it.addresses.first() }.toSortedMap()
            items(devices.entries.toList(), key = { it.key }) { (_, deviceServices) ->
                NearbyToolCard {
                    val sorted = deviceServices.sortedWith(compareBy({ it.key.name }, { it.key.type }))
                    Text(sorted.first().key.name, style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Text(sorted.flatMap { it.addresses }.distinct().joinToString("\n"))
                    }
                    sorted.forEach { service ->
                        Text("${service.key.name} · ${service.key.type} · 포트 ${service.port} (광고 정보)")
                    }
                }
            }
        }
    }
}

private fun NsdManager.discovery(type: String) = callbackFlow<DiscoveryEvent> {
    val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            trySend(DiscoveryEvent.Failed)
            close()
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            trySend(DiscoveryEvent.Found(ServiceKey(type, serviceInfo.serviceName), serviceInfo))
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            trySend(DiscoveryEvent.Lost(ServiceKey(type, serviceInfo.serviceName)))
        }
    }
    discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
    awaitClose { runCatching { stopServiceDiscovery(listener) } }
}

@Suppress("DEPRECATION")
private suspend fun NsdManager.resolve(service: NsdServiceInfo): NsdServiceInfo? =
    suspendCancellableCoroutine { continuation ->
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                continuation.resume(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                continuation.resume(serviceInfo)
            }
        }
        try {
            resolveService(service, listener)
        } catch (error: SecurityException) {
            continuation.resumeWithException(error)
        } catch (_: IllegalArgumentException) {
            continuation.resume(null)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            continuation.invokeOnCancellation { runCatching { stopServiceResolution(listener) } }
        }
    }

@Suppress("DEPRECATION")
private fun NsdServiceInfo.addresses(): List<String> {
    val hosts = if (Build.VERSION.SDK_INT >= 34) hostAddresses else listOfNotNull(host)
    return hosts.mapNotNull(InetAddress::getHostAddress)
        .distinct()
        .sortedWith(compareBy({ ':' in it }, { it }))
}
