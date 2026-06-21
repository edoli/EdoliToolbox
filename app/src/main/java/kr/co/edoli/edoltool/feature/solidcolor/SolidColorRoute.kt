package kr.co.edoli.edoltool.feature.solidcolor

import android.app.Activity
import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kr.co.edoli.edoltool.util.colorToCompose
import kr.co.edoli.edoltool.util.commonNamedColors
import kr.co.edoli.edoltool.util.findActivity
import kr.co.edoli.edoltool.util.parseColorQuery

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolidColorRoute(onBackClick: () -> Unit) {
    var selectedColor by remember { mutableIntStateOf(Color.BLACK) }
    var query by remember { mutableStateOf("#000000") }
    var fullscreen by remember { mutableStateOf(false) }

    if (fullscreen) {
        SolidFullscreen(
            colorInt = selectedColor,
            onExit = { fullscreen = false },
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "단색화면 배경",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로",
                        )
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
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "색상 선택",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { value ->
                            query = value
                            parseColorQuery(value)?.let { selectedColor = it }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("색상 이름 또는 HEX") },
                        supportingText = { Text("예: black, 검정, white, #00FF00") },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(colorToCompose(selectedColor), MaterialTheme.shapes.medium)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                        )
                        Column {
                            Text(
                                text = "#%02X%02X%02X".format(
                                    Color.red(selectedColor),
                                    Color.green(selectedColor),
                                    Color.blue(selectedColor),
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "전체 화면에서는 시스템 UI를 숨깁니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(
                        onClick = { fullscreen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = null,
                        )
                        Text(
                            text = "전체화면 표시",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            Text(
                text = "빠른 색상",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            commonNamedColors.chunked(3).forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowColors.forEach { color ->
                        ColorPresetButton(
                            name = color.name,
                            colorInt = color.colorInt,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedColor = color.colorInt
                                query = color.name
                            },
                        )
                    }
                    repeat(3 - rowColors.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPresetButton(
    name: String,
    colorInt: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(height = 36.dp, width = 1.dp)
                    .background(colorToCompose(colorInt), MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SolidFullscreen(
    colorInt: Int,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 32.dp.toPx() }
    val exitDistancePx = with(density) { 96.dp.toPx() }

    BackHandler(onBack = onExit)
    FullscreenSystemBars(activity = activity, colorInt = colorInt)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorToCompose(colorInt))
            .pointerInput(onExit, edgeWidthPx, exitDistancePx) {
                detectLeftEdgeExitGesture(
                    edgeWidthPx = edgeWidthPx,
                    exitDistancePx = exitDistancePx,
                    onExit = onExit,
                )
            },
    )
}

@Composable
@Suppress("DEPRECATION")
private fun FullscreenSystemBars(
    activity: Activity?,
    colorInt: Int,
) {
    DisposableEffect(activity, colorInt) {
        val window = activity?.window
        if (window == null) {
            onDispose {}
        } else {
            val previousStatusBarColor = window.statusBarColor
            val previousNavigationBarColor = window.navigationBarColor
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val previousBehavior = controller.systemBarsBehavior

            window.statusBarColor = colorInt
            window.navigationBarColor = colorInt
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())

            onDispose {
                window.statusBarColor = previousStatusBarColor
                window.navigationBarColor = previousNavigationBarColor
                controller.systemBarsBehavior = previousBehavior
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private suspend fun PointerInputScope.detectLeftEdgeExitGesture(
    edgeWidthPx: Float,
    exitDistancePx: Float,
    onExit: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x > edgeWidthPx) return@awaitEachGesture

        var totalDrag = 0f
        do {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            totalDrag += change.positionChange().x
            if (totalDrag >= exitDistancePx) {
                change.consume()
                onExit()
                break
            }
        } while (event.changes.any { it.pressed })
    }
}
