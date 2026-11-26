package kr.co.edoli.edolitool.feature.speed

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale

private const val LOCATION_FRESHNESS_MILLIS = 5_000L
private const val MAX_DISTANCE_FIX_ACCURACY_METERS = 30f
private const val MIN_DISTANCE_STEP_METERS = 3f
private const val MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND = 350.0

private enum class LocationPermission {
    None,
    Approximate,
    Precise,
}

private data class SatelliteReading(val detected: Int, val usedInFix: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedometerRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationManager = remember(context) { context.getSystemService(LocationManager::class.java) }
    var permission by remember { mutableStateOf(locationPermission(context)) }
    var reading by remember { mutableStateOf<Location?>(null) }
    var distanceMeters by rememberSaveable { mutableStateOf<Double?>(null) }
    var satellites by remember { mutableStateOf<SatelliteReading?>(null) }
    var monitoringSatellites by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("위치 신호를 기다리는 중") }
    var nowMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permission = locationPermission(context)
    }

    val grantedPermission = permission
    DisposableEffect(lifecycleOwner, locationManager, grantedPermission) {
        var listening = false
        var distanceAnchor: Location? = null
        val listener = LocationListener { location ->
            if (!listening || !location.isFreshAt(SystemClock.elapsedRealtime())) {
                return@LocationListener
            }
            val previous = reading
            if (previous != null && location.elapsedRealtimeNanos <= previous.elapsedRealtimeNanos) {
                return@LocationListener
            }
            if (location.provider == LocationManager.GPS_PROVIDER &&
                location.hasAccuracy() && location.accuracy <= MAX_DISTANCE_FIX_ACCURACY_METERS
            ) {
                val anchor = distanceAnchor?.takeIf {
                    previous != null && previous.isFreshAt(location.elapsedMillis) &&
                        location.isPlausibleAfter(previous)
                }
                if (anchor == null) {
                    distanceAnchor = location
                    if (distanceMeters == null) distanceMeters = 0.0
                } else {
                    // Keep the anchor through small changes so slow movement can accumulate.
                    val step = anchor.distanceTo(location)
                    if (step > maxOf(MIN_DISTANCE_STEP_METERS, anchor.accuracy, location.accuracy)) {
                        distanceMeters = (distanceMeters ?: 0.0) + step
                        distanceAnchor = location
                    }
                }
            } else {
                distanceAnchor = null
            }
            reading = location
        }
        val gnssCallback = object : GnssStatusCompat.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                if (!monitoringSatellites) return
                satellites = SatelliteReading(
                    detected = status.satelliteCount,
                    usedInFix = (0 until status.satelliteCount).count(status::usedInFix),
                )
            }

            override fun onStopped() {
                satellites = null
            }
        }

        fun stop() {
            if (monitoringSatellites) {
                LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssCallback)
            }
            monitoringSatellites = false
            satellites = null
            if (listening) locationManager.removeUpdates(listener)
            listening = false
            distanceAnchor = null
            reading = null
        }

        fun start() {
            if (listening) return
            val currentPermission = locationPermission(context)
            if (currentPermission != grantedPermission) {
                permission = currentPermission
                return
            }
            if (currentPermission == LocationPermission.None) return
            val provider = when {
                currentPermission == LocationPermission.Precise &&
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                status = when {
                    !LocationManagerCompat.isLocationEnabled(locationManager) -> "위치 서비스를 켜 주세요"
                    currentPermission == LocationPermission.Approximate -> "측정하려면 정확한 위치 권한이 필요합니다."
                    else -> "위치 서비스를 사용할 수 없습니다"
                }
                return
            }
            status = "위치 신호를 기다리는 중"
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    1_000L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
                listening = true
                if (provider == LocationManager.GPS_PROVIDER) {
                    monitoringSatellites = LocationManagerCompat.registerGnssStatusCallback(
                        locationManager,
                        ContextCompat.getMainExecutor(context),
                        gnssCallback,
                    )
                }
            } catch (_: SecurityException) {
                stop()
                permission = locationPermission(context)
                status = "위치 서비스를 사용할 수 없습니다"
            } catch (_: IllegalArgumentException) {
                status = "위치 서비스를 사용할 수 없습니다"
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    val currentReading = reading?.takeIf { it.isFreshAt(nowMillis) }
    val speed = currentReading?.takeIf { it.hasSpeed() }?.let { it.speed * 3.6f }
    val message = when {
        permission == LocationPermission.None -> "측정하려면 위치 권한이 필요합니다."
        currentReading == null -> status
        speed == null -> "속도 정보가 포함된 위치 신호를 기다리는 중"
        else -> "현재 이동 속도"
    }
    val hasSeaLevelAltitude = currentReading?.let(LocationCompat::hasMslAltitude) == true
    val altitude = currentReading?.let { location ->
        when {
            hasSeaLevelAltitude -> LocationCompat.getMslAltitudeMeters(location)
            location.hasAltitude() -> location.altitude
            else -> null
        }
    }
    val satelliteSummary = satellites?.let { "${it.detected}개 감지 · ${it.usedInFix}개 사용" }
        ?: when {
            permission != LocationPermission.Precise -> "정확한 위치 권한 필요"
            !monitoringSatellites -> "GPS를 사용할 수 없음"
            else -> "위성 신호 대기 중"
        }
    val distance = distanceMeters?.let { meters ->
        if (meters < 1_000) String.format(Locale.KOREA, "%.0f m", meters)
        else String.format(Locale.KOREA, "%.2f km", meters / 1_000)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("현재 속도") },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = speed?.let { String.format(Locale.KOREA, "%.1f", it) } ?: "--.-",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                permission == LocationPermission.None -> Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) {
                    Text("위치 권한 허용")
                }
                status == "위치 서비스를 켜 주세요" -> Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                ) {
                    Text("위치 설정 열기")
                }
                permission == LocationPermission.Approximate -> Button(
                    onClick = {
                        val appUri = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
                    },
                ) {
                    Text("정확한 위치 설정 열기")
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Measurement(
                        label = "이동거리",
                        value = distance ?: "—",
                        detail = when {
                            distance != null -> "이 화면에서 측정한 누적 거리"
                            permission != LocationPermission.Precise -> "정확한 위치 권한 필요"
                            else -> "정확한 GPS 위치를 기다리는 중"
                        },
                    )
                    HorizontalDivider()
                    Measurement(
                        label = "현재 고도",
                        value = altitude?.let { String.format(Locale.KOREA, "%.0f m", it) } ?: "—",
                        detail = when {
                            altitude == null -> "고도 정보 대기 중"
                            hasSeaLevelAltitude -> "평균 해수면 기준"
                            else -> "GPS 타원체 기준"
                        },
                    )
                    HorizontalDivider()
                    Measurement(label = "위성 정보", value = satelliteSummary)
                }
            }
            Text(
                text = "실내나 위치 신호가 약한 곳에서는 측정값 표시가 늦거나 정확도가 낮을 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Measurement(label: String, value: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val Location.elapsedMillis: Long
    get() = elapsedRealtimeNanos / 1_000_000

private fun Location.isFreshAt(nowMillis: Long): Boolean =
    nowMillis - elapsedMillis <= LOCATION_FRESHNESS_MILLIS

private fun Location.isPlausibleAfter(previous: Location): Boolean {
    val seconds = (elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
    val maxStep = seconds * MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND + previous.accuracy + accuracy
    return previous.distanceTo(this) <= maxStep
}

private fun locationPermission(context: Context): LocationPermission = when {
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED -> LocationPermission.Precise
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED -> LocationPermission.Approximate
    else -> LocationPermission.None
}
