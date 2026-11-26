package kr.co.edoli.edolitool.feature.speed

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale

private data class SpeedReading(
    val metersPerSecond: Float?,
    val elapsedRealtimeMillis: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedometerRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationManager = remember(context) { context.getSystemService(LocationManager::class.java) }
    var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var reading by remember { mutableStateOf<SpeedReading?>(null) }
    var status by remember { mutableStateOf("위치 신호를 기다리는 중") }
    var nowMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = hasLocationPermission(context)
    }

    DisposableEffect(lifecycleOwner, locationManager, hasPermission) {
        val listener = LocationListener { location: Location ->
            reading = SpeedReading(
                metersPerSecond = location.speed.takeIf { location.hasSpeed() },
                elapsedRealtimeMillis = location.elapsedRealtimeNanos / 1_000_000,
            )
            status = if (location.hasSpeed()) {
                "현재 이동 속도"
            } else {
                "속도 정보가 포함된 위치 신호를 기다리는 중"
            }
        }
        var listening = false

        fun stop() {
            if (listening) locationManager.removeUpdates(listener)
            listening = false
            reading = null
        }

        fun start() {
            if (listening) return
            if (!hasLocationPermission(context)) {
                hasPermission = false
                return
            }
            if (!hasPermission) {
                hasPermission = true
                return
            }
            val provider = when {
                hasFineLocationPermission(context) &&
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                status = "위치 서비스를 켜 주세요"
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
            } catch (_: SecurityException) {
                hasPermission = false
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

    val currentReading = reading?.takeIf { nowMillis - it.elapsedRealtimeMillis <= 5_000 }
    val speed = currentReading?.metersPerSecond?.let { it * 3.6f }
    val message = when {
        !hasPermission -> "현재 속도를 보려면 위치 권한이 필요합니다."
        currentReading == null && status == "현재 이동 속도" -> "새 위치 신호를 기다리는 중"
        else -> status
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
                !hasPermission -> Button(
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
            }
            Text(
                text = "기기의 위치 신호에서 제공하는 속도입니다. 실내나 신호가 약한 곳에서는 표시가 늦거나 정확도가 낮을 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hasFineLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasLocationPermission(context: Context): Boolean =
    hasFineLocationPermission(context) ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
