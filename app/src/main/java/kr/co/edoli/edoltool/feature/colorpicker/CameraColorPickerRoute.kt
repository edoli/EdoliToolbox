package kr.co.edoli.edoltool.feature.colorpicker

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.edoli.edoltool.util.PickedColor
import kr.co.edoli.edoltool.util.colorToCompose
import kr.co.edoli.edoltool.util.decodeBitmapFromUri
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraColorPickerRoute(
    onBackClick: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenImage: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) onOpenCamera()
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                onOpenImage(uri)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "카메라 컬러 피커",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PickerModeCard(
                title = "카메라로 선택",
                description = "실시간 카메라 화면을 전체 화면으로 열고 원하는 픽셀을 탭합니다.",
                icon = Icons.Rounded.PhotoCamera,
                buttonText = if (hasPermission) "카메라 열기" else "권한 허용 후 열기",
                onClick = {
                    if (hasPermission) {
                        onOpenCamera()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
            PickerModeCard(
                title = "이미지에서 선택",
                description = "갤러리 이미지를 불러온 뒤 이미지를 화면 전체에 띄워 픽셀을 분석합니다.",
                icon = Icons.Rounded.Image,
                buttonText = "이미지 불러오기",
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }
    }
}

@Composable
private fun PickerModeCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
fun CameraColorPickerFullscreenRoute(onExit: () -> Unit) {
    var pickedColor by remember { mutableStateOf<PickedColor?>(null) }
    var liveMode by remember { mutableStateOf(true) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 32.dp.toPx() }
    val exitDistancePx = with(density) { 96.dp.toPx() }

    BackHandler(onBack = onExit)

    LaunchedEffect(liveMode, previewView) {
        while (liveMode) {
            previewView?.sampleCenterBitmap()?.let { pickedColor = it }
            delay(120)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .pointerInput(onExit, edgeWidthPx, exitDistancePx) {
                detectLeftEdgeExitGesture(
                    edgeWidthPx = edgeWidthPx,
                    exitDistancePx = exitDistancePx,
                    onExit = onExit,
                )
            },
    ) {
        CameraPreviewPicker(
            modifier = Modifier.fillMaxSize(),
            onPreviewReady = { previewView = it },
            onColorPicked = {
                liveMode = false
                pickedColor = it.copy(source = "카메라 고정")
            },
        )
        FullscreenColorOverlay(
            pickedColor = pickedColor,
            liveMode = liveMode,
            onToggleMode = {
                liveMode = !liveMode
                if (liveMode) {
                    previewView?.sampleCenterBitmap()?.let { pickedColor = it }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        Crosshair(
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun ImageColorPickerFullscreenRoute(
    imageUri: Uri?,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var pickedColor by remember { mutableStateOf<PickedColor?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 32.dp.toPx() }
    val exitDistancePx = with(density) { 96.dp.toPx() }

    DisposableEffect(imageUri) {
        onDispose {
            bitmap?.recycle()
        }
    }

    androidx.compose.runtime.LaunchedEffect(imageUri) {
        bitmap = if (imageUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeBitmapFromUri(context, imageUri)
            }
        }
    }

    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .onSizeChanged { imageSize = it }
            .pointerInput(onExit, edgeWidthPx, exitDistancePx) {
                detectLeftEdgeExitGesture(
                    edgeWidthPx = edgeWidthPx,
                    exitDistancePx = exitDistancePx,
                    onExit = onExit,
                )
            }
            .pointerInput(bitmap, imageSize) {
                detectTapGestures { offset ->
                    bitmap?.let { image ->
                        sampleImageBitmap(image, imageSize, offset)?.let { pickedColor = it }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val imageBitmap = bitmap
        if (imageBitmap == null) {
            Text(
                text = if (imageUri == null) "이미지가 선택되지 않았습니다." else "이미지를 불러오는 중...",
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = "선택된 이미지",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        FullscreenColorOverlay(
            pickedColor = pickedColor,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CameraPreviewPicker(
    modifier: Modifier = Modifier,
    onPreviewReady: (PreviewView) -> Unit,
    onColorPicked: (PickedColor) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val view = previewView
                if (view != null) {
                    preview.setSurfaceProvider(view.surfaceProvider)
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                }
            },
            executor,
        )

        onDispose {
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    Box(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Color.Black)
            .pointerInput(previewView) {
                detectTapGestures { offset ->
                    previewView?.sampleBitmapAt(offset)?.let(onColorPicked)
                }
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    previewView = this
                    onPreviewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FullscreenColorOverlay(
    pickedColor: PickedColor?,
    liveMode: Boolean? = null,
    onToggleMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (pickedColor == null) {
        Text(
            text = "탭해서 픽셀 색상 선택",
            modifier = modifier
                .padding(16.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.48f),
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(colorToCompose(pickedColor.colorInt), MaterialTheme.shapes.medium)
                        .border(
                            width = 1.dp,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                            shape = MaterialTheme.shapes.medium,
                        ),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = pickedColor.hexRgb,
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "RGB ${pickedColor.red}, ${pickedColor.green}, ${pickedColor.blue}",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "HSV ${pickedColor.hsv[0].roundToInt()}°, ${(pickedColor.hsv[1] * 100).roundToInt()}%, ${(pickedColor.hsv[2] * 100).roundToInt()}%",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (liveMode != null && onToggleMode != null) {
                Button(
                    onClick = onToggleMode,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (liveMode) "실시간 모드" else "고정 모드")
                }
            }
        }
    }
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .border(
                width = 2.dp,
                color = androidx.compose.ui.graphics.Color.White,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
            ),
    )
}

private fun PreviewView.sampleBitmapAt(offset: Offset): PickedColor? {
    val bitmap = bitmap ?: return null
    val x = (offset.x / width * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val y = (offset.y / height * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
    return PickedColor(
        colorInt = bitmap.getPixel(x, y),
        source = "카메라",
        x = x,
        y = y,
    )
}

private fun PreviewView.sampleCenterBitmap(): PickedColor? {
    return sampleBitmapAt(
        Offset(
            x = width / 2f,
            y = height / 2f,
        ),
    )?.copy(source = "카메라 실시간")
}

private fun sampleImageBitmap(
    bitmap: Bitmap,
    containerSize: IntSize,
    offset: Offset,
): PickedColor? {
    if (containerSize.width <= 0 || containerSize.height <= 0) return null

    val scale = minOf(
        containerSize.width.toFloat() / bitmap.width,
        containerSize.height.toFloat() / bitmap.height,
    )
    val displayedWidth = bitmap.width * scale
    val displayedHeight = bitmap.height * scale
    val left = (containerSize.width - displayedWidth) / 2f
    val top = (containerSize.height - displayedHeight) / 2f
    val localX = offset.x - left
    val localY = offset.y - top

    if (localX < 0f || localY < 0f || localX > displayedWidth || localY > displayedHeight) {
        return null
    }

    val x = (localX / scale).roundToInt().coerceIn(0, bitmap.width - 1)
    val y = (localY / scale).roundToInt().coerceIn(0, bitmap.height - 1)
    return PickedColor(
        colorInt = bitmap.getPixel(x, y),
        source = "이미지",
        x = x,
        y = y,
    )
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
