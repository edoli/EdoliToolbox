package kr.co.edoli.edolitool.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolItem(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color,
    val status: ToolStatus = ToolStatus.Planned,
)

enum class ToolStatus {
    Ready,
    Planned
}
