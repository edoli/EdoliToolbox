package kr.co.edoli.edolitool.feature.physical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private enum class PhysicalTool(
    val id: String,
    val title: String,
    val description: String,
    val group: String,
    val icon: ImageVector,
) {
    Level("level", "수평계", "가로·세로 기울기와 영점 보정", "각도와 길이", Icons.Rounded.Tune),
    Protractor("protractor", "각도기", "화면에서 0°부터 180°까지 측정", "각도와 길이", Icons.Rounded.Architecture),
    Ruler("ruler", "화면 자", "전체 화면에서 가로·세로 길이 측정", "각도와 길이", Icons.Rounded.Straighten),
    Compass("compass", "나침반", "자북을 기준으로 현재 방향 확인", "방향과 움직임", Icons.Rounded.Explore),
    Magnetic("magnetic", "자력계", "자기장의 세기와 X·Y·Z 성분", "방향과 움직임", Icons.Rounded.Sensors),
    Acceleration("acceleration", "가속도계", "중력을 포함한 세 축의 가속도", "방향과 움직임", Icons.Rounded.Speed),
    Vibration("vibration", "진도측정기", "기기 진동의 실시간 파형과 최대 가속도", "방향과 움직임", Icons.Rounded.GraphicEq),
    Pressure("pressure", "기압계", "현재 기압과 기준점 대비 고도 변화", "주변 환경", Icons.Rounded.Compress),
    Light("light", "조도계", "주변 밝기를 lux 단위로 측정", "주변 환경", Icons.Rounded.LightMode),
    Sound("sound", "소음측정기", "마이크 입력 레벨 측정과 기준값 보정", "주변 환경", Icons.Rounded.Mic),
}

@Composable
fun PhysicalMeasurementsRoute(onBackClick: () -> Unit, onOpenTool: (String) -> Unit) {
    PhysicalScreen("물리 측정", onBackClick) {
        MeasurementBody {
            PhysicalTool.entries.groupBy { it.group }.forEach { (group, tools) ->
                MeasurementCard {
                    Text(group, style = MaterialTheme.typography.titleMedium)
                    tools.forEach { tool ->
                        ListItem(
                            headlineContent = { Text(tool.title) },
                            supportingContent = { Text(tool.description) },
                            leadingContent = {
                                Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(role = Role.Button) { onOpenTool(tool.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhysicalToolRoute(toolId: String, onBackClick: () -> Unit) {
    val tool = PhysicalTool.entries.firstOrNull { it.id == toolId }
    if (tool == PhysicalTool.Ruler) {
        ScreenRuler(onBackClick)
        return
    }
    PhysicalScreen(tool?.title ?: "물리 측정", onBackClick) {
        MeasurementBody {
            when (tool) {
                PhysicalTool.Level -> LevelMeter()
                PhysicalTool.Protractor -> ScreenProtractor()
                PhysicalTool.Compass -> CompassMeter()
                PhysicalTool.Magnetic -> VectorMeter(magnetic = true)
                PhysicalTool.Acceleration -> VectorMeter(magnetic = false)
                PhysicalTool.Vibration -> VibrationMeter()
                PhysicalTool.Pressure -> EnvironmentMeter(pressure = true)
                PhysicalTool.Light -> EnvironmentMeter(pressure = false)
                PhysicalTool.Sound -> SoundMeter()
                else -> MeasurementCard { Text("측정 도구를 찾을 수 없습니다.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhysicalScreen(title: String, onBackClick: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { content() }
    }
}

@Composable
private fun MeasurementBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun MeasurementCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun MeasurementValue(value: String, unit: String, caption: String) {
    Text("$value $unit", style = MaterialTheme.typography.displaySmall)
    Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
