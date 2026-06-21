package kr.co.edoli.edoltool.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.Color
import kr.co.edoli.edoltool.model.ToolItem

val toolCatalog = listOf(
    ToolItem(
        id = "calculator",
        name = "계산기",
        description = "자주 쓰는 계산을 빠르게 처리",
        icon = Icons.Rounded.Calculate,
        tint = Color(0xFF2563EB),
    ),
    ToolItem(
        id = "clipboard",
        name = "클립보드",
        description = "복사한 텍스트와 스니펫 관리",
        icon = Icons.Rounded.ContentCopy,
        tint = Color(0xFF059669),
    ),
    ToolItem(
        id = "text",
        name = "텍스트",
        description = "대소문자, 줄 정리, 문자열 변환",
        icon = Icons.Rounded.TextFields,
        tint = Color(0xFF7C3AED),
    ),
    ToolItem(
        id = "timer",
        name = "타이머",
        description = "간단한 시간 측정과 알림",
        icon = Icons.Rounded.Timer,
        tint = Color(0xFFDC2626),
    ),
    ToolItem(
        id = "storage",
        name = "저장공간",
        description = "파일과 앱 데이터 확인",
        icon = Icons.Rounded.Storage,
        tint = Color(0xFF0891B2),
    ),
    ToolItem(
        id = "settings",
        name = "설정",
        description = "도구별 기본값과 앱 옵션",
        icon = Icons.Rounded.Settings,
        tint = Color(0xFF475569),
    ),
)
