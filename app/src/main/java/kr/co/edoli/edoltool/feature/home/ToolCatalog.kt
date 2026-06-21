package kr.co.edoli.edoltool.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.Color
import kr.co.edoli.edoltool.model.ToolItem

val toolCatalog = listOf(
    ToolItem(
        id = "camera_color_picker",
        name = "카메라 컬러 피커",
        description = "카메라나 이미지에서 픽셀 색상 분석",
        icon = Icons.Rounded.PhotoCamera,
        tint = Color(0xFF2563EB),
    ),
    ToolItem(
        id = "solid_color_screen",
        name = "단색화면 배경",
        description = "시스템 UI까지 숨기는 전체 단색 화면",
        icon = Icons.Rounded.FormatColorFill,
        tint = Color(0xFF16A34A),
    ),
    ToolItem(
        id = "clipboard_analyzer",
        name = "클립보드 분석",
        description = "텍스트, URI, 이미지 클립보드 상세 분석",
        icon = Icons.Rounded.ContentPaste,
        tint = Color(0xFF9333EA),
    ),
)
