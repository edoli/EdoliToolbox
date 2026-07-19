package kr.co.edoli.edolitool.feature.workbench

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kr.co.edoli.edolitool.ui.component.ScrubbableValueField
import kr.co.edoli.edolitool.util.colorToCompose
import kr.co.edoli.edolitool.util.findActivity
import kr.co.edoli.edolitool.util.parseColorQuery
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private enum class WorkbenchPanel {
    None,
    Background,
    Add,
    Measure,
}

private enum class WorkbenchBackground(val label: String) {
    Solid("단색"),
    Checkerboard("체커보드"),
    Grid("그리드"),
    VerticalStripes("세로 줄무늬"),
    HorizontalStripes("가로 줄무늬"),
}

private enum class WorkbenchObjectType(val label: String, val isMeasurement: Boolean = false) {
    Rectangle("사각형"),
    Circle("원"),
    Line("선"),
    Text("텍스트"),
    Photo("사진"),
    Ruler("자", true),
    Protractor("각도기", true),
    MeasureRectangle("사각 측정", true),
}

private enum class MeasurementTool(val label: String) {
    Ruler("자"),
    Protractor("각도기"),
    Rectangle("사각 측정"),
}

private enum class ColorPickerMode(val label: String) {
    Rgb("RGB"),
    Hsv("HSV"),
}

private enum class ResizeHandle {
    TopLeft,
    Top,
    TopRight,
    Right,
    BottomRight,
    Bottom,
    BottomLeft,
    Left,
    CircleRadius,
    LineStart,
    LineEnd,
    TextSize,
    Rotate,
}

private data class MeasurementState(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

private data class WorkbenchBackgroundState(
    val type: WorkbenchBackground = WorkbenchBackground.Solid,
    val foregroundColor: Int = AndroidColor.WHITE,
    val backgroundColor: Int = AndroidColor.BLACK,
    val sizeMm: Float = 10f,
    val lineThicknessMm: Float = 0.1f,
)

private data class WorkbenchObject(
    val id: Long,
    val type: WorkbenchObjectType,
    val color: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val text: String = "텍스트",
    val textSize: Float = 24f,
    val lineStartX: Float? = null,
    val lineStartY: Float? = null,
    val lineEndX: Float? = null,
    val lineEndY: Float? = null,
    val imageUri: String? = null,
    val imageAspectRatio: Float = 1f,
    val rotationDegrees: Float = 0f,
)

private data class SavedWorkbench(
    val id: Long,
    val name: String,
    val background: WorkbenchBackgroundState,
    val objects: List<WorkbenchObject>,
)

private val objectColors = listOf(
    AndroidColor.rgb(239, 68, 68),
    AndroidColor.rgb(249, 115, 22),
    AndroidColor.rgb(234, 179, 8),
    AndroidColor.rgb(34, 197, 94),
    AndroidColor.rgb(6, 182, 212),
    AndroidColor.rgb(59, 130, 246),
    AndroidColor.rgb(139, 92, 246),
    AndroidColor.rgb(236, 72, 153),
)

private const val rotationHandleOffsetPx = 112f
private const val editHandleTouchRadiusPx = 64f

@Composable
fun WorkbenchRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var background by remember { mutableStateOf(WorkbenchBackgroundState()) }
    var objects by remember { mutableStateOf(emptyList<WorkbenchObject>()) }
    var panel by remember { mutableStateOf(WorkbenchPanel.None) }
    var foregroundQuery by remember { mutableStateOf("#FFFFFF") }
    var backgroundQuery by remember { mutableStateOf("#000000") }
    var saveDialogVisible by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var loadDialogVisible by remember { mutableStateOf(false) }
    var selectedObjectId by remember { mutableStateOf<Long?>(null) }
    var measurementTool by remember { mutableStateOf<MeasurementTool?>(null) }
    var measurementState by remember { mutableStateOf<MeasurementState?>(null) }
    var photoSourceDialogVisible by remember { mutableStateOf(false) }
    var propertiesDialogObjectId by remember { mutableStateOf<Long?>(null) }
    val currentObjects by rememberUpdatedState(objects)
    val addPhoto: (Uri) -> Unit = { sourceUri ->
        val storedUri = copyPhotoToWorkbench(context, sourceUri) ?: sourceUri
        val newObject = createPhotoWorkbenchObject(storedUri, imageAspectRatio(context, storedUri), objects, context)
        objects = objects + newObject
        selectedObjectId = newObject.id
        measurementTool = null
        measurementState = null
        panel = WorkbenchPanel.None
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(addPhoto)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(addPhoto)
    }
    val contentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(addPhoto)
    }

    BackHandler(onBack = onBackClick)
    WorkbenchSystemBars(activity)

    Box(modifier = Modifier.fillMaxSize()) {
        WorkbenchCanvas(
            background = background,
            objects = objects,
            selectedObjectId = selectedObjectId,
            measurementTool = measurementTool,
            measurementState = measurementState,
            onMoveObject = { id, deltaX, deltaY ->
                objects = currentObjects.map { item ->
                    if (item.id == id) {
                        item.moveBy(deltaX, deltaY)
                    } else {
                        item
                    }
                }
            },
            onSelectObject = { selectedObjectId = it },
            onResizeObject = { resizedObject ->
                objects = currentObjects.map { item ->
                    if (item.id == resizedObject.id) resizedObject else item
                }
            },
            onMeasurementChange = { measurementState = it },
        )

        WorkbenchToolbar(
            modifier = Modifier.align(Alignment.TopCenter),
            panel = panel,
            measurementActive = measurementTool != null,
            onBackClick = onBackClick,
            onPanelClick = { selectedPanel ->
                panel = if (panel == selectedPanel) WorkbenchPanel.None else selectedPanel
            },
            onSaveClick = {
                saveName = "작업대 ${WorkbenchStore.load(context).size + 1}"
                saveDialogVisible = true
            },
            onLoadClick = { loadDialogVisible = true },
        )

        when (panel) {
            WorkbenchPanel.Background -> BackgroundPanel(
                background = background,
                foregroundQuery = foregroundQuery,
                backgroundQuery = backgroundQuery,
                onForegroundQueryChange = { value ->
                    foregroundQuery = value
                    parseColorQuery(value)?.let { color -> background = background.copy(foregroundColor = color) }
                },
                onBackgroundQueryChange = { value ->
                    backgroundQuery = value
                    parseColorQuery(value)?.let { color -> background = background.copy(backgroundColor = color) }
                },
                onBackgroundChange = { background = it },
            )

            WorkbenchPanel.Add -> ObjectPanel(
                onAdd = { type ->
                    val newObject = createWorkbenchObject(type, objects)
                    objects = objects + newObject
                    selectedObjectId = newObject.id
                    measurementTool = null
                    measurementState = null
                    panel = WorkbenchPanel.None
                },
                onAddPhoto = { photoSourceDialogVisible = true },
            )

            WorkbenchPanel.Measure -> MeasurementPanel(
                activeTool = measurementTool,
                onSelect = { tool ->
                    measurementTool = if (measurementTool == tool) null else tool
                    measurementState = null
                    panel = WorkbenchPanel.None
                },
            )

            WorkbenchPanel.None -> Unit
        }

        if (saveDialogVisible) {
            AlertDialog(
                onDismissRequest = { saveDialogVisible = false },
                title = { Text("작업대 저장") },
                text = {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("작업대 이름") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            WorkbenchStore.save(
                                context = context,
                                workbench = SavedWorkbench(
                                    id = System.currentTimeMillis(),
                                    name = saveName.ifBlank { "작업대" },
                                    background = background,
                                    objects = objects,
                                ),
                            )
                            saveDialogVisible = false
                        },
                    ) { Text("저장") }
                },
                dismissButton = { TextButton(onClick = { saveDialogVisible = false }) { Text("취소") } },
            )
        }

        if (loadDialogVisible) {
            LoadWorkbenchDialog(
                workbenches = WorkbenchStore.load(context),
                onDismiss = { loadDialogVisible = false },
                onLoad = { saved ->
                    background = saved.background
                    foregroundQuery = colorToHex(saved.background.foregroundColor)
                    backgroundQuery = colorToHex(saved.background.backgroundColor)
                    objects = saved.objects
                    selectedObjectId = null
                    measurementState = null
                    loadDialogVisible = false
                },
            )
        }

        if (photoSourceDialogVisible) {
            PhotoSourceDialog(
                onDismiss = { photoSourceDialogVisible = false },
                onGallery = {
                    photoSourceDialogVisible = false
                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onFile = {
                    photoSourceDialogVisible = false
                    filePicker.launch(arrayOf("image/*"))
                },
                onOtherApps = {
                    photoSourceDialogVisible = false
                    contentPicker.launch("image/*")
                },
            )
        }

        objects.firstOrNull { it.id == selectedObjectId }?.let { selectedObject ->
            FloatingActionButton(
                onClick = { propertiesDialogObjectId = selectedObject.id },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "${selectedObject.type.label} 수치 편집")
            }
        }

        objects.firstOrNull { it.id == propertiesDialogObjectId }?.let { selectedObject ->
            ShapePropertiesDialog(
                objectToEdit = selectedObject,
                context = context,
                onDismiss = { propertiesDialogObjectId = null },
                onApply = { updatedObject ->
                    objects = objects.map { if (it.id == updatedObject.id) updatedObject else it }
                    propertiesDialogObjectId = null
                },
            )
        }

    }
}

@Composable
private fun WorkbenchToolbar(
    modifier: Modifier = Modifier,
    panel: WorkbenchPanel,
    measurementActive: Boolean,
    onBackClick: () -> Unit,
    onPanelClick: (WorkbenchPanel) -> Unit,
    onSaveClick: () -> Unit,
    onLoadClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(top = 8.dp)
            .wrapContentWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1F2937),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "뒤로",
                    tint = Color.White,
                )
            }
            ToolbarAction("배경", Icons.Rounded.FormatColorFill, panel == WorkbenchPanel.Background) {
                onPanelClick(WorkbenchPanel.Background)
            }
            ToolbarAction("추가", Icons.Rounded.Add, panel == WorkbenchPanel.Add) {
                onPanelClick(WorkbenchPanel.Add)
            }
            ToolbarAction("측정", Icons.Rounded.Straighten, panel == WorkbenchPanel.Measure || measurementActive) {
                onPanelClick(WorkbenchPanel.Measure)
            }
            ToolbarAction("저장", Icons.Rounded.Save, false, onSaveClick)
            ToolbarAction("불러오기", Icons.Rounded.FolderOpen, false, onLoadClick)
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) Color(0xFF93C5FD) else Color.White,
        )
    }
}

@Composable
private fun BackgroundPanel(
    background: WorkbenchBackgroundState,
    foregroundQuery: String,
    backgroundQuery: String,
    onForegroundQueryChange: (String) -> Unit,
    onBackgroundQueryChange: (String) -> Unit,
    onBackgroundChange: (WorkbenchBackgroundState) -> Unit,
) {
    WorkbenchPanelSurface {
        Text("배경", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkbenchBackground.entries.forEach { type ->
                FilterChip(
                    selected = background.type == type,
                    onClick = { onBackgroundChange(background.copy(type = type)) },
                    label = { Text(type.label) },
                )
            }
        }
        if (background.type == WorkbenchBackground.Solid) {
            CompactColorField(
                label = "배경",
                value = backgroundQuery,
                color = background.backgroundColor,
                onValueChange = onBackgroundQueryChange,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactColorField(
                    label = "패턴",
                    value = foregroundQuery,
                    color = background.foregroundColor,
                    onValueChange = onForegroundQueryChange,
                    modifier = Modifier.weight(1f),
                )
                CompactColorField(
                    label = "배경",
                    value = backgroundQuery,
                    color = background.backgroundColor,
                    onValueChange = onBackgroundQueryChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (background.type != WorkbenchBackground.Solid) {
            ScrubbableValueField(
                label = "간격",
                value = background.sizeMm,
                unit = "mm",
                valueRange = 1f..50f,
                formatValue = ::formatMm,
                parseValue = String::toMmOrNull,
                onValueChange = { onBackgroundChange(background.copy(sizeMm = it)) },
            )
        }
        if (background.type == WorkbenchBackground.Grid) {
            ScrubbableValueField(
                label = "선 두께",
                value = background.lineThicknessMm,
                unit = "mm",
                valueRange = 0.01f..5f,
                formatValue = ::formatMm,
                parseValue = String::toMmOrNull,
                onValueChange = { onBackgroundChange(background.copy(lineThicknessMm = it)) },
            )
        }
    }
}

@Composable
private fun ObjectPanel(
    onAdd: (WorkbenchObjectType) -> Unit,
    onAddPhoto: () -> Unit,
) {
    WorkbenchPanelSurface {
        Text("도형 추가", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkbenchObjectType.entries.filterNot { it.isMeasurement }.forEach { type ->
                if (type != WorkbenchObjectType.Photo) {
                    Button(onClick = { onAdd(type) }) { Text(type.label) }
                }
            }
            Button(onClick = onAddPhoto) { Text("사진") }
        }
    }
}

@Composable
private fun MeasurementPanel(
    activeTool: MeasurementTool?,
    onSelect: (MeasurementTool) -> Unit,
) {
    WorkbenchPanelSurface {
        Text("측정 도구", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementTool.entries.forEach { tool ->
                FilterChip(
                    selected = activeTool == tool,
                    onClick = { onSelect(tool) },
                    label = { Text(tool.label) },
                )
            }
        }
    }
}

@Composable
private fun WorkbenchPanelSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, start = 8.dp, end = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1F2937).copy(alpha = 0.98f),
        contentColor = Color.White,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun CompactColorField(
    label: String,
    value: String,
    color: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    var pickerVisible by remember { mutableStateOf(false) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable { pickerVisible = true },
            shape = RoundedCornerShape(12.dp),
            color = colorToCompose(color),
        ) {}
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }
    if (pickerVisible) {
        RgbColorPickerDialog(
            label = label,
            initialColor = color,
            onDismiss = { pickerVisible = false },
            onColorSelected = {
                onValueChange(colorToHex(it))
                pickerVisible = false
            },
        )
    }
}

@Composable
private fun PhotoSourceDialog(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    onOtherApps: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("사진 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                    Text("갤러리")
                }
                Button(onClick = onFile, modifier = Modifier.fillMaxWidth()) {
                    Text("파일")
                }
                Button(onClick = onOtherApps, modifier = Modifier.fillMaxWidth()) {
                    Text("기타 앱에서 가져오기")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ShapePropertiesDialog(
    objectToEdit: WorkbenchObject,
    context: Context,
    onDismiss: () -> Unit,
    onApply: (WorkbenchObject) -> Unit,
) {
    val metrics = context.resources.displayMetrics
    val pixelsPerMmX = metrics.xdpi / 25.4f
    val pixelsPerMmY = metrics.ydpi / 25.4f
    val canvasWidth = metrics.widthPixels.toFloat()
    val canvasHeight = metrics.heightPixels.toFloat()
    var firstValue by remember(objectToEdit.id) { mutableStateOf("") }
    var secondValue by remember(objectToEdit.id) { mutableStateOf("") }
    var thirdValue by remember(objectToEdit.id) { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(objectToEdit.id) {
        when (objectToEdit.type) {
            WorkbenchObjectType.Rectangle -> {
                firstValue = formatMm(objectToEdit.width * canvasWidth / pixelsPerMmX)
                secondValue = formatMm(objectToEdit.height * canvasHeight / pixelsPerMmY)
                thirdValue = formatMm(objectToEdit.rotationDegrees)
            }

            WorkbenchObjectType.Circle -> {
                firstValue = formatMm(objectToEdit.width * canvasWidth / pixelsPerMmX / 2f)
            }

            WorkbenchObjectType.Line -> {
                val start = objectToEdit.lineStart()
                val end = objectToEdit.lineEnd()
                val deltaXmm = (end.x - start.x) * canvasWidth / pixelsPerMmX
                val deltaYmm = (end.y - start.y) * canvasHeight / pixelsPerMmY
                firstValue = formatMm(sqrt(deltaXmm * deltaXmm + deltaYmm * deltaYmm))
                secondValue = formatMm(Math.toDegrees(atan2((-deltaYmm).toDouble(), deltaXmm.toDouble())).toFloat())
            }

            WorkbenchObjectType.Text -> firstValue = formatMm(objectToEdit.textSize)
            WorkbenchObjectType.Photo -> firstValue = formatMm(objectToEdit.width * canvasWidth / pixelsPerMmX)
            else -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${objectToEdit.type.label} 수치 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (objectToEdit.type) {
                    WorkbenchObjectType.Rectangle -> {
                        NumericInput("가로 (mm)", firstValue) { firstValue = it }
                        NumericInput("세로 (mm)", secondValue) { secondValue = it }
                        NumericInput("회전 (°)", thirdValue) { thirdValue = it }
                    }

                    WorkbenchObjectType.Circle -> NumericInput("반지름 (mm)", firstValue) { firstValue = it }
                    WorkbenchObjectType.Line -> {
                        NumericInput("길이 (mm)", firstValue) { firstValue = it }
                        NumericInput("각도 (°)", secondValue) { secondValue = it }
                    }

                    WorkbenchObjectType.Text -> NumericInput("글자 크기 (sp)", firstValue) { firstValue = it }
                    WorkbenchObjectType.Photo -> NumericInput("가로 (mm)", firstValue) { firstValue = it }
                    else -> Text("이 객체는 수치 편집을 지원하지 않습니다.")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updatedObject = objectToEdit.applyNumericProperties(
                        firstValue = firstValue.toNumberOrNull(),
                        secondValue = secondValue.toNumberOrNull(),
                        thirdValue = thirdValue.toNumberOrNull(),
                        pixelsPerMmX = pixelsPerMmX,
                        pixelsPerMmY = pixelsPerMmY,
                        canvasWidth = canvasWidth,
                        canvasHeight = canvasHeight,
                    )
                    onApply(updatedObject)
                },
            ) { Text("적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun NumericInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

private fun WorkbenchObject.applyNumericProperties(
    firstValue: Float?,
    secondValue: Float?,
    thirdValue: Float?,
    pixelsPerMmX: Float,
    pixelsPerMmY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
): WorkbenchObject = when (type) {
    WorkbenchObjectType.Rectangle -> copy(
        width = firstValue?.let { (it * pixelsPerMmX / canvasWidth).coerceIn(0.05f, 1f) } ?: width,
        height = secondValue?.let { (it * pixelsPerMmY / canvasHeight).coerceIn(0.05f, 1f) } ?: height,
        rotationDegrees = thirdValue ?: rotationDegrees,
    )

    WorkbenchObjectType.Circle -> firstValue?.let { radiusMm ->
        copy(
            width = (radiusMm * 2f * pixelsPerMmX / canvasWidth).coerceIn(0.05f, 1f),
            height = (radiusMm * 2f * pixelsPerMmY / canvasHeight).coerceIn(0.05f, 1f),
        )
    } ?: this

    WorkbenchObjectType.Line -> {
        if (firstValue == null || secondValue == null) this
        else {
            val radians = Math.toRadians(secondValue.toDouble())
            val halfLength = firstValue / 2f
            val deltaX = halfLength * cos(radians).toFloat() * pixelsPerMmX / canvasWidth
            val deltaY = halfLength * sin(radians).toFloat() * pixelsPerMmY / canvasHeight
            copy(
                lineStartX = (centerX - deltaX).coerceIn(0f, 1f),
                lineStartY = (centerY + deltaY).coerceIn(0f, 1f),
                lineEndX = (centerX + deltaX).coerceIn(0f, 1f),
                lineEndY = (centerY - deltaY).coerceIn(0f, 1f),
            ).withLineBounds()
        }
    }

    WorkbenchObjectType.Text -> firstValue?.let { copy(textSize = it.coerceIn(8f, 160f)) } ?: this
    WorkbenchObjectType.Photo -> firstValue?.let { widthMm ->
        copy(
            width = (widthMm * pixelsPerMmX / canvasWidth).coerceIn(0.08f, 1f),
            height = (widthMm / imageAspectRatio * pixelsPerMmY / canvasHeight).coerceIn(0.08f, 1f),
        )
    } ?: this

    else -> this
}

@Composable
private fun RgbColorPickerDialog(
    label: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    var mode by remember { mutableStateOf(ColorPickerMode.Rgb) }
    var selectedColor by remember(initialColor) { mutableIntStateOf(initialColor) }
    val hsv = FloatArray(3).also { AndroidColor.colorToHSV(selectedColor, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$label 색상") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colorToCompose(selectedColor),
                ) {}
                Text(colorToHex(selectedColor), fontFamily = FontFamily.Monospace)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorPickerMode.entries.forEach { pickerMode ->
                        FilterChip(
                            selected = mode == pickerMode,
                            onClick = { mode = pickerMode },
                            label = { Text(pickerMode.label) },
                        )
                    }
                }
                when (mode) {
                    ColorPickerMode.Rgb -> {
                        RgbChannelField("R", AndroidColor.red(selectedColor)) { red ->
                            selectedColor = AndroidColor.rgb(red, AndroidColor.green(selectedColor), AndroidColor.blue(selectedColor))
                        }
                        RgbChannelField("G", AndroidColor.green(selectedColor)) { green ->
                            selectedColor = AndroidColor.rgb(AndroidColor.red(selectedColor), green, AndroidColor.blue(selectedColor))
                        }
                        RgbChannelField("B", AndroidColor.blue(selectedColor)) { blue ->
                            selectedColor = AndroidColor.rgb(AndroidColor.red(selectedColor), AndroidColor.green(selectedColor), blue)
                        }
                    }

                    ColorPickerMode.Hsv -> {
                        HsvChannelField("H", hsv[0], 0f..360f) { hue ->
                            selectedColor = AndroidColor.HSVToColor(floatArrayOf(hue, hsv[1], hsv[2]))
                        }
                        HsvChannelField("S", hsv[1] * 100f, 0f..100f) { saturation ->
                            selectedColor = AndroidColor.HSVToColor(floatArrayOf(hsv[0], saturation / 100f, hsv[2]))
                        }
                        HsvChannelField("V", hsv[2] * 100f, 0f..100f) { value ->
                            selectedColor = AndroidColor.HSVToColor(floatArrayOf(hsv[0], hsv[1], value / 100f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onColorSelected(selectedColor) }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun RgbChannelField(label: String, value: Int, onValueChange: (Int) -> Unit) {
    ScrubbableValueField(
        label = label,
        value = value.toFloat(),
        unit = "",
        valueRange = 0f..255f,
        formatValue = { it.toInt().toString() },
        onValueChange = { onValueChange(it.toInt()) },
    )
}

@Composable
private fun HsvChannelField(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    ScrubbableValueField(
        label = label,
        value = value,
        unit = "",
        valueRange = valueRange,
        formatValue = { "%.1f".format(Locale.US, it).trimEnd('0').trimEnd('.') },
        onValueChange = onValueChange,
    )
}

@Composable
private fun LoadWorkbenchDialog(
    workbenches: List<SavedWorkbench>,
    onDismiss: () -> Unit,
    onLoad: (SavedWorkbench) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("작업대 불러오기") },
        text = {
            if (workbenches.isEmpty()) {
                Text("저장된 작업대가 없습니다.")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    workbenches.reversed().forEach { workbench ->
                        Card(
                            onClick = { onLoad(workbench) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(workbench.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${workbench.objects.size}개 항목 · ${workbench.background.type.label} 배경",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun WorkbenchCanvas(
    background: WorkbenchBackgroundState,
    objects: List<WorkbenchObject>,
    selectedObjectId: Long?,
    measurementTool: MeasurementTool?,
    measurementState: MeasurementState?,
    onMoveObject: (Long, Float, Float) -> Unit,
    onSelectObject: (Long?) -> Unit,
    onResizeObject: (WorkbenchObject) -> Unit,
    onMeasurementChange: (MeasurementState?) -> Unit,
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val currentObjects by rememberUpdatedState(objects)
    val currentSelectedObjectId by rememberUpdatedState(selectedObjectId)
    val photoUris = objects.mapNotNull { it.imageUri }
    val photoBitmaps = remember(photoUris) {
        photoUris.associateWith { uri -> loadImageBitmap(context, Uri.parse(uri)) }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(measurementTool) {
                detectTapGestures { touchOffset ->
                    if (measurementTool == null) {
                        onSelectObject(
                            currentObjects.asReversed().firstOrNull { item ->
                                item.contains(touchOffset.x / size.width, touchOffset.y / size.height)
                            }?.id,
                        )
                    }
                }
            }
            .pointerInput(measurementTool) {
                if (measurementTool != null) {
                    var startX = 0f
                    var startY = 0f
                    detectDragGestures(
                        onDragStart = { touchOffset ->
                            startX = touchOffset.x / size.width.coerceAtLeast(1)
                            startY = touchOffset.y / size.height.coerceAtLeast(1)
                            onMeasurementChange(MeasurementState(startX, startY, startX, startY))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            onMeasurementChange(
                                MeasurementState(
                                    startX,
                                    startY,
                                    (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                                ),
                            )
                        },
                    )
                } else {
                    var draggedObjectId: Long? = null
                    var resizeHandle: ResizeHandle? = null
                    var resizeBaseObject: WorkbenchObject? = null
                    var resizeStartTouch: Offset? = null
                    var resizeStartHandlePosition: Offset? = null
                    detectDragGestures(
                        onDragStart = { touchOffset ->
                            val x = touchOffset.x / size.width.coerceAtLeast(1)
                            val y = touchOffset.y / size.height.coerceAtLeast(1)
                            val selectedObject = currentObjects.firstOrNull { it.id == currentSelectedObjectId }
                            resizeHandle = selectedObject?.handleAt(
                                x = x,
                                y = y,
                                canvasAspectRatio = size.width.toFloat() / size.height.coerceAtLeast(1),
                                canvasWidth = size.width.toFloat(),
                                canvasHeight = size.height.toFloat(),
                            )
                            resizeBaseObject = if (resizeHandle != null) selectedObject else null
                            resizeStartTouch = if (resizeHandle != null) Offset(x, y) else null
                            resizeStartHandlePosition = resizeHandle?.let { handle ->
                                resizeBaseObject?.handlePosition(
                                    handle = handle,
                                    canvasAspectRatio = size.width.toFloat() / size.height.coerceAtLeast(1),
                                    canvasHeight = size.height.toFloat(),
                                )
                            }
                            draggedObjectId = when {
                                resizeHandle != null -> selectedObject?.id
                                else -> currentObjects.asReversed().firstOrNull { item -> item.contains(x, y) }?.id
                            }
                            if (draggedObjectId != null) onSelectObject(draggedObjectId)
                        },
                        onDragEnd = {
                            draggedObjectId = null
                            resizeHandle = null
                            resizeBaseObject = null
                            resizeStartTouch = null
                            resizeStartHandlePosition = null
                        },
                        onDragCancel = {
                            draggedObjectId = null
                            resizeHandle = null
                            resizeBaseObject = null
                            resizeStartTouch = null
                            resizeStartHandlePosition = null
                        },
                        onDrag = { change, dragAmount ->
                            val id = draggedObjectId ?: return@detectDragGestures
                            change.consume()
                            val handle = resizeHandle
                            if (handle != null) {
                                val baseObject = resizeBaseObject ?: return@detectDragGestures
                                val startTouch = resizeStartTouch ?: return@detectDragGestures
                                val startHandlePosition = resizeStartHandlePosition ?: return@detectDragGestures
                                val currentX = change.position.x / size.width.coerceAtLeast(1)
                                val currentY = change.position.y / size.height.coerceAtLeast(1)
                                onResizeObject(
                                    baseObject.resize(
                                        handle = handle,
                                        targetX = (startHandlePosition.x + currentX - startTouch.x).coerceIn(0f, 1f),
                                        targetY = (startHandlePosition.y + currentY - startTouch.y).coerceIn(0f, 1f),
                                        canvasAspectRatio = size.width.toFloat() / size.height.coerceAtLeast(1),
                                    ),
                                )
                            } else {
                                onMoveObject(
                                    id,
                                    dragAmount.x / size.width.coerceAtLeast(1),
                                    dragAmount.y / size.height.coerceAtLeast(1),
                                )
                            }
                        },
                    )
                }
            },
    ) {
        drawWorkbenchBackground(background, metrics.xdpi / 25.4f, metrics.ydpi / 25.4f)
        objects.forEach { item ->
            drawWorkbenchObject(
                item,
                metrics.xdpi / 25.4f,
                metrics.ydpi / 25.4f,
                photoBitmaps[item.imageUri],
            )
        }
        objects.firstOrNull { it.id == selectedObjectId }?.let { drawResizeHandles(it) }
        if (measurementTool != null && measurementState != null) {
            drawInteractiveMeasurement(
                tool = measurementTool,
                measurement = measurementState,
                pixelsPerMmX = metrics.xdpi / 25.4f,
                pixelsPerMmY = metrics.ydpi / 25.4f,
            )
        }
    }
}

private fun WorkbenchObject.contains(x: Float, y: Float): Boolean {
    val horizontalPadding = if (type == WorkbenchObjectType.Line) 0.04f else 0f
    return abs(x - centerX) <= width / 2f + horizontalPadding && abs(y - centerY) <= height / 2f + 0.04f
}

private fun WorkbenchObject.handleAt(
    x: Float,
    y: Float,
    canvasAspectRatio: Float,
    canvasWidth: Float,
    canvasHeight: Float,
): ResizeHandle? {
    val unrotatedTarget = if (type == WorkbenchObjectType.Rectangle && rotationDegrees != 0f) {
        rotateNormalizedPoint(Offset(x, y), Offset(centerX, centerY), -rotationDegrees, canvasAspectRatio)
    } else {
        Offset(x, y)
    }
    val left = centerX - width / 2f
    val top = centerY - height / 2f
    val right = centerX + width / 2f
    val bottom = centerY + height / 2f
    val handles = when (type) {
        WorkbenchObjectType.Circle -> listOf(ResizeHandle.CircleRadius to Offset(right, centerY))
        WorkbenchObjectType.Line -> listOf(
            ResizeHandle.LineStart to lineStart(),
            ResizeHandle.LineEnd to lineEnd(),
        )

        WorkbenchObjectType.Text -> listOf(ResizeHandle.TextSize to Offset(right, bottom))
        WorkbenchObjectType.Photo -> listOf(
            ResizeHandle.TopLeft to Offset(left, top),
            ResizeHandle.TopRight to Offset(right, top),
            ResizeHandle.BottomRight to Offset(right, bottom),
            ResizeHandle.BottomLeft to Offset(left, bottom),
        )
        else -> listOf(
            ResizeHandle.TopLeft to Offset(left, top),
            ResizeHandle.Top to Offset(centerX, top),
            ResizeHandle.TopRight to Offset(right, top),
            ResizeHandle.Right to Offset(right, centerY),
            ResizeHandle.BottomRight to Offset(right, bottom),
            ResizeHandle.Bottom to Offset(centerX, bottom),
            ResizeHandle.BottomLeft to Offset(left, bottom),
            ResizeHandle.Left to Offset(left, centerY),
        ) + if (type == WorkbenchObjectType.Rectangle) {
            listOf(ResizeHandle.Rotate to Offset(centerX, top - rotationHandleOffsetPx / canvasHeight))
        } else {
            emptyList()
        }
    }
    val touchRadiusX = editHandleTouchRadiusPx / canvasWidth
    val touchRadiusY = editHandleTouchRadiusPx / canvasHeight
    return handles.minByOrNull { (_, handle) -> abs(unrotatedTarget.x - handle.x) + abs(unrotatedTarget.y - handle.y) }
        ?.takeIf { (_, handle) ->
            abs(unrotatedTarget.x - handle.x) < touchRadiusX && abs(unrotatedTarget.y - handle.y) < touchRadiusY
        }
        ?.first
}

private fun WorkbenchObject.handlePosition(
    handle: ResizeHandle,
    canvasAspectRatio: Float,
    canvasHeight: Float,
): Offset? {
    val left = centerX - width / 2f
    val top = centerY - height / 2f
    val right = centerX + width / 2f
    val bottom = centerY + height / 2f
    val localPosition = when (type) {
        WorkbenchObjectType.Circle -> if (handle == ResizeHandle.CircleRadius) Offset(right, centerY) else null
        WorkbenchObjectType.Line -> when (handle) {
            ResizeHandle.LineStart -> lineStart()
            ResizeHandle.LineEnd -> lineEnd()
            else -> null
        }

        WorkbenchObjectType.Text -> if (handle == ResizeHandle.TextSize) Offset(right, bottom) else null
        WorkbenchObjectType.Photo -> when (handle) {
            ResizeHandle.TopLeft -> Offset(left, top)
            ResizeHandle.TopRight -> Offset(right, top)
            ResizeHandle.BottomRight -> Offset(right, bottom)
            ResizeHandle.BottomLeft -> Offset(left, bottom)
            else -> null
        }

        else -> when (handle) {
            ResizeHandle.TopLeft -> Offset(left, top)
            ResizeHandle.Top -> Offset(centerX, top)
            ResizeHandle.TopRight -> Offset(right, top)
            ResizeHandle.Right -> Offset(right, centerY)
            ResizeHandle.BottomRight -> Offset(right, bottom)
            ResizeHandle.Bottom -> Offset(centerX, bottom)
            ResizeHandle.BottomLeft -> Offset(left, bottom)
            ResizeHandle.Left -> Offset(left, centerY)
            ResizeHandle.Rotate -> Offset(centerX, top - rotationHandleOffsetPx / canvasHeight)
            else -> null
        }
    } ?: return null
    return if (type == WorkbenchObjectType.Rectangle && rotationDegrees != 0f) {
        rotateNormalizedPoint(localPosition, Offset(centerX, centerY), rotationDegrees, canvasAspectRatio)
    } else {
        localPosition
    }
}

private fun WorkbenchObject.resize(
    handle: ResizeHandle,
    targetX: Float,
    targetY: Float,
    canvasAspectRatio: Float,
): WorkbenchObject {
    if (type == WorkbenchObjectType.Rectangle && handle == ResizeHandle.Rotate) {
        val deltaX = (targetX - centerX) * canvasAspectRatio
        val deltaY = targetY - centerY
        val rotation = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat() + 90f
        return copy(rotationDegrees = ((rotation % 360f) + 360f) % 360f)
    }
    val unrotatedTarget = if (type == WorkbenchObjectType.Rectangle && rotationDegrees != 0f) {
        rotateNormalizedPoint(Offset(targetX, targetY), Offset(centerX, centerY), -rotationDegrees, canvasAspectRatio)
    } else {
        Offset(targetX, targetY)
    }
    val localTargetX = unrotatedTarget.x
    val localTargetY = unrotatedTarget.y
    if (type == WorkbenchObjectType.Circle && handle == ResizeHandle.CircleRadius) {
        val radius = maxOf(abs(localTargetX - centerX), abs(localTargetY - centerY)).coerceIn(0.025f, 0.45f)
        return copy(width = radius * 2f, height = radius * 2f)
    }
    if (type == WorkbenchObjectType.Line) {
        return when (handle) {
            ResizeHandle.LineStart -> copy(lineStartX = localTargetX, lineStartY = localTargetY).withLineBounds()
            ResizeHandle.LineEnd -> copy(lineEndX = localTargetX, lineEndY = localTargetY).withLineBounds()
            else -> this
        }
    }
    if (type == WorkbenchObjectType.Text && handle == ResizeHandle.TextSize) {
        val left = centerX - width / 2f
        val newWidth = abs(localTargetX - left).coerceIn(0.08f, 0.75f)
        return copy(width = newWidth, textSize = (newWidth * 120f).coerceIn(14f, 96f))
    }
    if (type == WorkbenchObjectType.Photo) {
        val left = centerX - width / 2f
        val top = centerY - height / 2f
        val right = centerX + width / 2f
        val bottom = centerY + height / 2f
        val fixed = when (handle) {
            ResizeHandle.TopLeft -> Offset(right, bottom)
            ResizeHandle.TopRight -> Offset(left, bottom)
            ResizeHandle.BottomRight -> Offset(left, top)
            ResizeHandle.BottomLeft -> Offset(right, top)
            else -> return this
        }
        val normalizedAspectRatio = imageAspectRatio / canvasAspectRatio
        val widthFromPointer = abs(localTargetX - fixed.x)
        val heightFromPointer = abs(localTargetY - fixed.y)
        val newWidth = if (widthFromPointer / normalizedAspectRatio <= heightFromPointer) {
            heightFromPointer * normalizedAspectRatio
        } else {
            widthFromPointer
        }.coerceIn(0.08f, 0.9f)
        val newHeight = (newWidth / normalizedAspectRatio).coerceIn(0.08f, 0.9f)
        val newLeft = if (handle == ResizeHandle.TopLeft || handle == ResizeHandle.BottomLeft) fixed.x - newWidth else fixed.x
        val newTop = if (handle == ResizeHandle.TopLeft || handle == ResizeHandle.TopRight) fixed.y - newHeight else fixed.y
        return copy(
            centerX = (newLeft + newWidth / 2f).coerceIn(0f, 1f),
            centerY = (newTop + newHeight / 2f).coerceIn(0f, 1f),
            width = newWidth,
            height = newHeight,
        )
    }
    var left = centerX - width / 2f
    var top = centerY - height / 2f
    var right = centerX + width / 2f
    var bottom = centerY + height / 2f
    when (handle) {
        ResizeHandle.TopLeft -> {
            left = localTargetX
            top = localTargetY
        }

        ResizeHandle.Top -> top = localTargetY
        ResizeHandle.TopRight -> {
            right = localTargetX
            top = localTargetY
        }

        ResizeHandle.Right -> right = localTargetX
        ResizeHandle.BottomRight -> {
            right = localTargetX
            bottom = localTargetY
        }

        ResizeHandle.Bottom -> bottom = localTargetY
        ResizeHandle.BottomLeft -> {
            left = localTargetX
            bottom = localTargetY
        }

        ResizeHandle.Left -> left = localTargetX
        ResizeHandle.Rotate -> Unit
        else -> Unit
    }
    val minimumSize = 0.05f
    if (right - left < minimumSize) {
        if (handle == ResizeHandle.TopLeft || handle == ResizeHandle.Left || handle == ResizeHandle.BottomLeft) {
            left = right - minimumSize
        } else {
            right = left + minimumSize
        }
    }
    if (bottom - top < minimumSize) {
        if (handle == ResizeHandle.TopLeft || handle == ResizeHandle.Top || handle == ResizeHandle.TopRight) {
            top = bottom - minimumSize
        } else {
            bottom = top + minimumSize
        }
    }
    val localCenter = Offset((left + right) / 2f, (top + bottom) / 2f)
    val visualCenter = if (type == WorkbenchObjectType.Rectangle && rotationDegrees != 0f) {
        rotateNormalizedPoint(localCenter, Offset(centerX, centerY), rotationDegrees, canvasAspectRatio)
    } else {
        localCenter
    }
    return copy(
        centerX = visualCenter.x.coerceIn(0f, 1f),
        centerY = visualCenter.y.coerceIn(0f, 1f),
        width = (right - left).coerceAtLeast(minimumSize),
        height = (bottom - top).coerceAtLeast(minimumSize),
    )
}

private fun WorkbenchObject.moveBy(deltaX: Float, deltaY: Float): WorkbenchObject = copy(
    centerX = (centerX + deltaX).coerceIn(0f, 1f),
    centerY = (centerY + deltaY).coerceIn(0f, 1f),
    lineStartX = lineStartX?.plus(deltaX)?.coerceIn(0f, 1f),
    lineStartY = lineStartY?.plus(deltaY)?.coerceIn(0f, 1f),
    lineEndX = lineEndX?.plus(deltaX)?.coerceIn(0f, 1f),
    lineEndY = lineEndY?.plus(deltaY)?.coerceIn(0f, 1f),
)

private fun WorkbenchObject.lineStart(): Offset = Offset(
    lineStartX ?: centerX - width / 2f,
    lineStartY ?: centerY + height / 2f,
)

private fun WorkbenchObject.lineEnd(): Offset = Offset(
    lineEndX ?: centerX + width / 2f,
    lineEndY ?: centerY - height / 2f,
)

private fun WorkbenchObject.withLineBounds(): WorkbenchObject {
    val start = lineStart()
    val end = lineEnd()
    return copy(
        centerX = (start.x + end.x) / 2f,
        centerY = (start.y + end.y) / 2f,
        width = abs(end.x - start.x).coerceAtLeast(0.05f),
        height = abs(end.y - start.y).coerceAtLeast(0.05f),
    )
}

private fun rotateNormalizedPoint(
    point: Offset,
    center: Offset,
    angleDegrees: Float,
    canvasAspectRatio: Float,
): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val deltaX = (point.x - center.x) * canvasAspectRatio
    val deltaY = point.y - center.y
    val rotatedX = deltaX * cos(radians).toFloat() - deltaY * sin(radians).toFloat()
    val rotatedY = deltaX * sin(radians).toFloat() + deltaY * cos(radians).toFloat()
    return Offset(center.x + rotatedX / canvasAspectRatio, center.y + rotatedY)
}

private fun createWorkbenchObject(type: WorkbenchObjectType, existingObjects: List<WorkbenchObject>): WorkbenchObject {
    val offset = (existingObjects.size % 5) * 0.055f
    val measurement = type.isMeasurement
    val availableColors = objectColors.filterNot { color -> existingObjects.any { it.color == color } }
    val centerX = (0.5f + offset).coerceAtMost(0.82f)
    val centerY = (0.48f + offset).coerceAtMost(0.78f)
    val width = if (measurement) 0.56f else 0.24f
    val height = when (type) {
        WorkbenchObjectType.Circle -> 0.24f
        WorkbenchObjectType.Line -> 0.12f
        WorkbenchObjectType.Protractor -> 0.36f
        WorkbenchObjectType.MeasureRectangle -> 0.24f
        WorkbenchObjectType.Ruler -> 0.12f
        else -> 0.18f
    }
    return WorkbenchObject(
        id = System.nanoTime(),
        type = type,
        color = if (measurement) {
            AndroidColor.rgb(250, 204, 21)
        } else {
            (availableColors.ifEmpty { objectColors }).random(Random)
        },
        centerX = centerX,
        centerY = centerY,
        width = width,
        height = height,
        lineStartX = if (type == WorkbenchObjectType.Line) centerX - width / 2f else null,
        lineStartY = if (type == WorkbenchObjectType.Line) centerY + height / 2f else null,
        lineEndX = if (type == WorkbenchObjectType.Line) centerX + width / 2f else null,
        lineEndY = if (type == WorkbenchObjectType.Line) centerY - height / 2f else null,
    )
}

private fun createPhotoWorkbenchObject(
    uri: Uri,
    aspectRatio: Float,
    existingObjects: List<WorkbenchObject>,
    context: Context,
): WorkbenchObject {
    val offset = (existingObjects.size % 4) * 0.05f
    val normalizedAspectRatio = aspectRatio * context.resources.displayMetrics.heightPixels /
        context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    val height = 0.28f
    val width = (height * normalizedAspectRatio).coerceIn(0.16f, 0.72f)
    return WorkbenchObject(
        id = System.nanoTime(),
        type = WorkbenchObjectType.Photo,
        color = objectColors[Random.nextInt(objectColors.size)],
        centerX = (0.5f + offset).coerceAtMost(0.82f),
        centerY = (0.48f + offset).coerceAtMost(0.78f),
        width = width,
        height = height,
        imageUri = uri.toString(),
        imageAspectRatio = aspectRatio,
    )
}

private fun imageAspectRatio(context: Context, uri: Uri): Float {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            options.outWidth.toFloat() / options.outHeight.coerceAtLeast(1)
        } ?: 1f
    }.getOrDefault(1f)
}

private fun copyPhotoToWorkbench(context: Context, sourceUri: Uri): Uri? = runCatching {
    val directory = File(context.filesDir, "workbench_images").apply { mkdirs() }
    val destination = File(directory, "${UUID.randomUUID()}.image")
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        destination.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    Uri.fromFile(destination)
}.getOrNull()

private fun loadImageBitmap(context: Context, uri: Uri): ImageBitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
}.getOrNull()

private fun DrawScope.drawWorkbenchBackground(
    background: WorkbenchBackgroundState,
    pixelsPerMmX: Float,
    pixelsPerMmY: Float,
) {
    drawRect(colorToCompose(background.backgroundColor))
    if (background.type == WorkbenchBackground.Solid) return

    val color = colorToCompose(background.foregroundColor)
    val width = background.sizeMm * pixelsPerMmX
    val height = background.sizeMm * pixelsPerMmY
    when (background.type) {
        WorkbenchBackground.Checkerboard -> {
            val columns = ceil(size.width / width).toInt()
            val rows = ceil(size.height / height).toInt()
            for (row in 0 until rows) for (column in 0 until columns) {
                if ((row + column) % 2 == 0) drawRect(color, Offset(column * width, row * height), Size(width, height))
            }
        }

        WorkbenchBackground.Grid -> {
            val lineWidth = background.lineThicknessMm * pixelsPerMmX
            val lineHeight = background.lineThicknessMm * pixelsPerMmY
            for (column in 0..ceil(size.width / width).toInt()) {
                drawLine(color, Offset(column * width, 0f), Offset(column * width, size.height), lineWidth)
            }
            for (row in 0..ceil(size.height / height).toInt()) {
                drawLine(color, Offset(0f, row * height), Offset(size.width, row * height), lineHeight)
            }
        }

        WorkbenchBackground.VerticalStripes -> {
            for (column in 0..ceil(size.width / width).toInt()) {
                if (column % 2 == 0) drawRect(color, Offset(column * width, 0f), Size(width, size.height))
            }
        }

        WorkbenchBackground.HorizontalStripes -> {
            for (row in 0..ceil(size.height / height).toInt()) {
                if (row % 2 == 0) drawRect(color, Offset(0f, row * height), Size(size.width, height))
            }
        }

        WorkbenchBackground.Solid -> Unit
    }
}

private fun DrawScope.drawWorkbenchObject(
    item: WorkbenchObject,
    pixelsPerMmX: Float,
    pixelsPerMmY: Float,
    photo: ImageBitmap?,
) {
    val color = colorToCompose(item.color)
    val center = Offset(item.centerX * size.width, item.centerY * size.height)
    val objectWidth = item.width * size.width
    val objectHeight = item.height * size.height
    val topLeft = Offset(center.x - objectWidth / 2f, center.y - objectHeight / 2f)
    when (item.type) {
        WorkbenchObjectType.Rectangle -> rotate(item.rotationDegrees, center) {
            drawRect(color, topLeft, Size(objectWidth, objectHeight), style = Stroke(4f))
        }
        WorkbenchObjectType.Circle -> drawCircle(color, min(objectWidth, objectHeight) / 2f, center, style = Stroke(4f))
        WorkbenchObjectType.Line -> drawLine(
            color,
            Offset(item.lineStart().x * size.width, item.lineStart().y * size.height),
            Offset(item.lineEnd().x * size.width, item.lineEnd().y * size.height),
            5f,
        )

        WorkbenchObjectType.Text -> drawCanvasText(item.text, topLeft.x, center.y, color, item.textSize)
        WorkbenchObjectType.Photo -> {
            if (photo != null) {
                drawImage(
                    image = photo,
                    dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(objectWidth.toInt(), objectHeight.toInt()),
                )
            } else {
                drawRect(color, topLeft, Size(objectWidth, objectHeight), style = Stroke(4f))
                drawCanvasText("사진", center.x, center.y, color, 16f, Paint.Align.CENTER)
            }
        }
        WorkbenchObjectType.Ruler -> drawRuler(center, objectWidth, color, pixelsPerMmX)
        WorkbenchObjectType.Protractor -> drawProtractor(center, min(objectWidth, objectHeight), color)
        WorkbenchObjectType.MeasureRectangle -> drawMeasurementRectangle(
            topLeft,
            objectWidth,
            objectHeight,
            color,
            pixelsPerMmX,
            pixelsPerMmY,
        )
    }
}

private fun DrawScope.drawResizeHandles(item: WorkbenchObject) {
    val center = Offset(item.centerX * size.width, item.centerY * size.height)
    val width = item.width * size.width
    val height = item.height * size.height
    val left = center.x - width / 2f
    val top = center.y - height / 2f
    val right = center.x + width / 2f
    val bottom = center.y + height / 2f
    val accent = Color.White
    val shadow = Color.Gray.copy(alpha = 0.72f)
    when (item.type) {
        WorkbenchObjectType.Rectangle -> rotate(item.rotationDegrees, center) {
            drawRectangleResizeHandles(left, top, right, bottom, center, accent, shadow)
            val rotationCenter = Offset(center.x, top - rotationHandleOffsetPx)
            drawLine(shadow, Offset(center.x, top), Offset(center.x, rotationCenter.y + 18f), 2f)
            drawCircle(shadow, 20f, rotationCenter)
            drawCircle(accent, 16f, rotationCenter)
            drawArc(
                shadow,
                0f,
                270f,
                false,
                Offset(rotationCenter.x - 9f, rotationCenter.y - 9f),
                Size(18f, 18f),
                style = Stroke(2.5f),
            )
            drawLine(
                shadow,
                Offset(rotationCenter.x + 9f, rotationCenter.y),
                Offset(rotationCenter.x + 11f, rotationCenter.y + 6f),
                2.5f,
            )
            drawLine(
                shadow,
                Offset(rotationCenter.x + 9f, rotationCenter.y),
                Offset(rotationCenter.x + 3f, rotationCenter.y + 2f),
                2.5f,
            )
        }

        WorkbenchObjectType.Circle -> {
            val radius = min(width, height) / 2f
            drawCircle(shadow, radius + 4f, center, style = Stroke(2f))
            drawCircle(shadow, 12f, Offset(center.x + radius, center.y))
            drawCircle(accent, 8f, Offset(center.x + radius, center.y))
        }

        WorkbenchObjectType.Line -> {
            val start = Offset(item.lineStart().x * size.width, item.lineStart().y * size.height)
            val end = Offset(item.lineEnd().x * size.width, item.lineEnd().y * size.height)
            drawCircle(shadow, 13f, start)
            drawCircle(shadow, 13f, end)
            drawCircle(accent, 9f, start)
            drawCircle(accent, 9f, end)
        }

        WorkbenchObjectType.Text -> {
            drawRect(shadow, Offset(left - 3f, top - 3f), Size(width + 6f, height + 6f), style = Stroke(2f))
            drawCircle(shadow, 12f, Offset(right, bottom))
            drawCircle(accent, 8f, Offset(right, bottom))
        }

        WorkbenchObjectType.Photo -> {
            drawRect(shadow, Offset(left - 3f, top - 3f), Size(width + 6f, height + 6f), style = Stroke(2f))
            listOf(
                Offset(left, top),
                Offset(right, top),
                Offset(right, bottom),
                Offset(left, bottom),
            ).forEach { point ->
                drawCircle(shadow, 12f, point)
                drawCircle(accent, 8f, point)
            }
        }

        else -> {
            drawRectangleResizeHandles(left, top, right, bottom, center, accent, shadow)
        }
    }
}

private fun DrawScope.drawRectangleResizeHandles(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    center: Offset,
    accent: Color,
    shadow: Color,
) {
    drawRect(shadow, Offset(left - 3f, top - 3f), Size(right - left + 6f, bottom - top + 6f), style = Stroke(2f))
    listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(right, bottom),
        Offset(left, bottom),
    ).forEach { point ->
        drawCircle(shadow, 12f, point)
        drawCircle(accent, 8f, point)
    }
    drawHorizontalResizeHandle(Offset(left, center.y), accent, shadow)
    drawHorizontalResizeHandle(Offset(right, center.y), accent, shadow)
    drawVerticalResizeHandle(Offset(center.x, top), accent, shadow)
    drawVerticalResizeHandle(Offset(center.x, bottom), accent, shadow)
}

private fun DrawScope.drawHorizontalResizeHandle(center: Offset, color: Color, shadow: Color) {
    drawCircle(shadow, 12f, center)
    drawCircle(color, 8f, center)
    drawLine(shadow, Offset(center.x - 5f, center.y), Offset(center.x + 5f, center.y), 2f)
    drawLine(shadow, Offset(center.x - 5f, center.y), Offset(center.x - 1f, center.y - 4f), 2f)
    drawLine(shadow, Offset(center.x - 5f, center.y), Offset(center.x - 1f, center.y + 4f), 2f)
    drawLine(shadow, Offset(center.x + 5f, center.y), Offset(center.x + 1f, center.y - 4f), 2f)
    drawLine(shadow, Offset(center.x + 5f, center.y), Offset(center.x + 1f, center.y + 4f), 2f)
}

private fun DrawScope.drawVerticalResizeHandle(center: Offset, color: Color, shadow: Color) {
    drawCircle(shadow, 12f, center)
    drawCircle(color, 8f, center)
    drawLine(shadow, Offset(center.x, center.y - 5f), Offset(center.x, center.y + 5f), 2f)
    drawLine(shadow, Offset(center.x, center.y - 5f), Offset(center.x - 4f, center.y - 1f), 2f)
    drawLine(shadow, Offset(center.x, center.y - 5f), Offset(center.x + 4f, center.y - 1f), 2f)
    drawLine(shadow, Offset(center.x, center.y + 5f), Offset(center.x - 4f, center.y + 1f), 2f)
    drawLine(shadow, Offset(center.x, center.y + 5f), Offset(center.x + 4f, center.y + 1f), 2f)
}

private fun DrawScope.drawInteractiveMeasurement(
    tool: MeasurementTool,
    measurement: MeasurementState,
    pixelsPerMmX: Float,
    pixelsPerMmY: Float,
) {
    val start = Offset(measurement.startX * size.width, measurement.startY * size.height)
    val end = Offset(measurement.endX * size.width, measurement.endY * size.height)
    val color = Color(0xFFFFD54F)
    when (tool) {
        MeasurementTool.Ruler -> {
            drawLine(color, start, end, 5f)
            drawCircle(color, 8f, start)
            drawCircle(color, 8f, end)
            val lengthMm = sqrt(
                ((end.x - start.x) / pixelsPerMmX) * ((end.x - start.x) / pixelsPerMmX) +
                    ((end.y - start.y) / pixelsPerMmY) * ((end.y - start.y) / pixelsPerMmY),
            )
            drawCanvasText("${formatMm(lengthMm)} mm", (start.x + end.x) / 2f, (start.y + end.y) / 2f - 12f, color, 16f)
        }

        MeasurementTool.Protractor -> {
            val radius = maxOf(36f, sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)))
            drawArc(
                color,
                180f,
                180f,
                false,
                Offset(start.x - radius, start.y - radius),
                Size(radius * 2f, radius * 2f),
                style = Stroke(4f),
            )
            drawLine(color, Offset(start.x - radius, start.y), Offset(start.x + radius, start.y), 3f)
            drawLine(color, start, end, 4f)
            drawCircle(color, 8f, start)
            val angle = ((Math.toDegrees(atan2((start.y - end.y).toDouble(), (end.x - start.x).toDouble())) + 360.0) % 360.0)
            drawCanvasText("${formatMm(angle.toFloat())}°", start.x + 12f, start.y - 12f, color, 16f)
        }

        MeasurementTool.Rectangle -> {
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            val width = abs(end.x - start.x)
            val height = abs(end.y - start.y)
            drawRect(color, Offset(left, top), Size(width, height), style = Stroke(4f))
            val widthMm = width / pixelsPerMmX
            val heightMm = height / pixelsPerMmY
            drawCanvasText(
                "${formatMm(widthMm)} mm",
                left + width / 2f,
                top - 12f,
                color,
                14f,
                Paint.Align.CENTER,
            )
            drawCanvasText("${formatMm(heightMm)} mm", left + width + 10f, top + height / 2f, color, 14f)
            drawCanvasText(
                "${formatMm(widthMm * heightMm)} mm²",
                left + width / 2f,
                top + height / 2f,
                color,
                14f,
                Paint.Align.CENTER,
            )
        }
    }
}

private fun DrawScope.drawRuler(center: Offset, width: Float, color: Color, pixelsPerMm: Float) {
    val startX = center.x - width / 2f
    val baseline = center.y
    drawLine(color, Offset(startX, baseline), Offset(startX + width, baseline), 4f)
    val millimeters = (width / pixelsPerMm).toInt()
    for (millimeter in 0..millimeters) {
        val x = startX + millimeter * pixelsPerMm
        val tickHeight = if (millimeter % 10 == 0) 28f else if (millimeter % 5 == 0) 20f else 12f
        drawLine(color, Offset(x, baseline), Offset(x, baseline - tickHeight), 2f)
        if (millimeter % 10 == 0) drawCanvasText("$millimeter", x + 2f, baseline + 22f, color, 13f)
    }
}

private fun DrawScope.drawProtractor(center: Offset, diameter: Float, color: Color) {
    val radius = diameter / 2f
    val arcTopLeft = Offset(center.x - radius, center.y - radius)
    drawArc(color, 180f, 180f, false, arcTopLeft, Size(diameter, diameter), style = Stroke(4f))
    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 3f)
    for (angle in 0..180 step 10) {
        val radians = Math.toRadians((180 - angle).toDouble())
        val outer = Offset(center.x + radius * cos(radians).toFloat(), center.y - radius * sin(radians).toFloat())
        val inner = Offset(
            center.x + (radius - if (angle % 30 == 0) 18f else 10f) * cos(radians).toFloat(),
            center.y - (radius - if (angle % 30 == 0) 18f else 10f) * sin(radians).toFloat(),
        )
        drawLine(color, outer, inner, 2f)
        if (angle % 30 == 0) drawCanvasText(angle.toString(), inner.x - 7f, inner.y - 4f, color, 12f)
    }
}

private fun DrawScope.drawMeasurementRectangle(
    topLeft: Offset,
    width: Float,
    height: Float,
    color: Color,
    pixelsPerMmX: Float,
    pixelsPerMmY: Float,
) {
    drawRect(color, topLeft, Size(width, height), style = Stroke(4f))
    val widthMm = width / pixelsPerMmX
    val heightMm = height / pixelsPerMmY
    drawCanvasText(
        "${formatMm(widthMm)} mm",
        topLeft.x + width / 2f,
        topLeft.y - 12f,
        color,
        14f,
        Paint.Align.CENTER,
    )
    drawCanvasText("${formatMm(heightMm)} mm", topLeft.x + width + 10f, topLeft.y + height / 2f, color, 14f)
    drawCanvasText(
        "${formatMm(widthMm * heightMm)} mm²",
        topLeft.x + width / 2f,
        topLeft.y + height / 2f,
        color,
        14f,
        Paint.Align.CENTER,
    )
}

private fun DrawScope.drawCanvasText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    textSize: Float,
    textAlign: Paint.Align = Paint.Align.LEFT,
) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        x,
        y,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            this.textSize = textSize * density
            this.textAlign = textAlign
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        },
    )
}

@Composable
@Suppress("DEPRECATION")
private fun WorkbenchSystemBars(activity: Activity?) {
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose {}
        } else {
            val statusBarColor = window.statusBarColor
            val navigationBarColor = window.navigationBarColor
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val behavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                window.statusBarColor = statusBarColor
                window.navigationBarColor = navigationBarColor
                controller.systemBarsBehavior = behavior
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private object WorkbenchStore {
    private const val preferencesName = "workbench_store"
    private const val workbenchesKey = "saved_workbenches"

    fun load(context: Context): List<SavedWorkbench> = runCatching {
        val raw = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(workbenchesKey, "[]")
            .orEmpty()
        val array = JSONArray(raw)
        List(array.length()) { index -> array.getJSONObject(index).toSavedWorkbench() }
    }.getOrDefault(emptyList())

    fun save(context: Context, workbench: SavedWorkbench) {
        val saved = load(context).filterNot { it.name == workbench.name } + workbench
        val array = JSONArray()
        saved.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(workbenchesKey, array.toString())
            .apply()
    }
}

private fun SavedWorkbench.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("background", background.toJson())
    put("objects", JSONArray().apply { objects.forEach { put(it.toJson()) } })
}

private fun WorkbenchBackgroundState.toJson(): JSONObject = JSONObject().apply {
    put("type", type.name)
    put("foregroundColor", foregroundColor)
    put("backgroundColor", backgroundColor)
    put("sizeMm", sizeMm.toDouble())
    put("lineThicknessMm", lineThicknessMm.toDouble())
}

private fun WorkbenchObject.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("type", type.name)
    put("color", color)
    put("centerX", centerX.toDouble())
    put("centerY", centerY.toDouble())
    put("width", width.toDouble())
    put("height", height.toDouble())
    put("text", text)
    put("textSize", textSize.toDouble())
    lineStartX?.let { put("lineStartX", it.toDouble()) }
    lineStartY?.let { put("lineStartY", it.toDouble()) }
    lineEndX?.let { put("lineEndX", it.toDouble()) }
    lineEndY?.let { put("lineEndY", it.toDouble()) }
    imageUri?.let { put("imageUri", it) }
    put("imageAspectRatio", imageAspectRatio.toDouble())
    put("rotationDegrees", rotationDegrees.toDouble())
}

private fun JSONObject.toSavedWorkbench(): SavedWorkbench {
    val objectArray = getJSONArray("objects")
    return SavedWorkbench(
        id = getLong("id"),
        name = getString("name"),
        background = getJSONObject("background").toBackgroundState(),
        objects = List(objectArray.length()) { index -> objectArray.getJSONObject(index).toWorkbenchObject() },
    )
}

private fun JSONObject.toBackgroundState(): WorkbenchBackgroundState = WorkbenchBackgroundState(
    type = WorkbenchBackground.valueOf(getString("type")),
    foregroundColor = getInt("foregroundColor"),
    backgroundColor = getInt("backgroundColor"),
    sizeMm = getDouble("sizeMm").toFloat(),
    lineThicknessMm = getDouble("lineThicknessMm").toFloat(),
)

private fun JSONObject.toWorkbenchObject(): WorkbenchObject = WorkbenchObject(
    id = getLong("id"),
    type = WorkbenchObjectType.valueOf(getString("type")),
    color = getInt("color"),
    centerX = getDouble("centerX").toFloat(),
    centerY = getDouble("centerY").toFloat(),
    width = getDouble("width").toFloat(),
    height = getDouble("height").toFloat(),
    text = optString("text", "텍스트"),
    textSize = optDouble("textSize", 24.0).toFloat(),
    lineStartX = optDoubleOrNull("lineStartX"),
    lineStartY = optDoubleOrNull("lineStartY"),
    lineEndX = optDoubleOrNull("lineEndX"),
    lineEndY = optDoubleOrNull("lineEndY"),
    imageUri = optString("imageUri").takeIf { it.isNotBlank() },
    imageAspectRatio = optDouble("imageAspectRatio", 1.0).toFloat(),
    rotationDegrees = optDouble("rotationDegrees", 0.0).toFloat(),
)

private fun JSONObject.optDoubleOrNull(name: String): Float? = if (has(name)) optDouble(name).toFloat() else null

private fun String.toMmOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()

private fun String.toNumberOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()

private fun formatMm(value: Float): String = "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')

private fun colorToHex(color: Int): String = "#%02X%02X%02X".format(
    AndroidColor.red(color),
    AndroidColor.green(color),
    AndroidColor.blue(color),
)
