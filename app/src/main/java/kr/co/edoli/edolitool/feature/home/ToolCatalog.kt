package kr.co.edoli.edolitool.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.Color
import kr.co.edoli.edolitool.model.ToolItem

val toolCatalog = listOf(
    ToolItem(
        id = "codex_pets",
        name = "Codex 펫",
        description = "Codex 펫 ZIP 가져오기 및 설치된 펫 관리",
        icon = Icons.Rounded.Pets,
        tint = Color(0xFFDB2777),
    ),
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
    ToolItem(
        id = "storage_inspector",
        name = "저장장치 정보",
        description = "외장 저장소 상태와 파일시스템 정보 확인",
        icon = Icons.Rounded.Storage,
        tint = Color(0xFF0F766E),
    ),
    ToolItem(
        id = "recent_apps_widget",
        name = "최근 앱 위젯",
        description = "홈 화면에서 최근 사용 앱을 바로 실행",
        icon = Icons.Rounded.Apps,
        tint = Color(0xFFDC2626),
    ),
)
