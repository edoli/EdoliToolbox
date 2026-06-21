package kr.co.edoli.edoltool.util

import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PickedColor(
    val colorInt: Int,
    val source: String,
    val x: Int? = null,
    val y: Int? = null,
) {
    val alpha: Int = Color.alpha(colorInt)
    val red: Int = Color.red(colorInt)
    val green: Int = Color.green(colorInt)
    val blue: Int = Color.blue(colorInt)
    val hexRgb: String = "#%02X%02X%02X".format(red, green, blue)
    val hexArgb: String = "#%02X%02X%02X%02X".format(alpha, red, green, blue)
    val hsv: FloatArray = FloatArray(3).also { Color.colorToHSV(colorInt, it) }
    val hsl: HslColor = toHsl(red, green, blue)
    val cmyk: CmykColor = toCmyk(red, green, blue)
    val luminance: Double = relativeLuminance(red, green, blue)
}

data class HslColor(
    val hue: Int,
    val saturation: Int,
    val lightness: Int,
)

data class CmykColor(
    val cyan: Int,
    val magenta: Int,
    val yellow: Int,
    val black: Int,
)

data class NamedColor(
    val name: String,
    val aliases: List<String>,
    val colorInt: Int,
)

val commonNamedColors = listOf(
    NamedColor("검정", listOf("black", "블랙"), Color.BLACK),
    NamedColor("흰색", listOf("white", "화이트"), Color.WHITE),
    NamedColor("빨강", listOf("red", "레드"), Color.RED),
    NamedColor("초록", listOf("green", "녹색", "그린"), Color.GREEN),
    NamedColor("파랑", listOf("blue", "블루"), Color.BLUE),
    NamedColor("노랑", listOf("yellow", "옐로우"), Color.YELLOW),
    NamedColor("청록", listOf("cyan", "시안"), Color.CYAN),
    NamedColor("자홍", listOf("magenta", "마젠타"), Color.MAGENTA),
    NamedColor("회색", listOf("gray", "grey", "그레이"), Color.GRAY),
    NamedColor("주황", listOf("orange", "오렌지"), Color.rgb(255, 152, 0)),
    NamedColor("보라", listOf("purple", "퍼플"), Color.rgb(128, 0, 128)),
)

fun parseColorQuery(query: String): Int? {
    val value = query.trim()
    if (value.isBlank()) return null

    val normalized = when {
        value.startsWith("#") -> value
        value.matches(Regex("[0-9a-fA-F]{6}")) -> "#$value"
        value.matches(Regex("[0-9a-fA-F]{8}")) -> "#$value"
        else -> null
    }

    if (normalized != null) {
        return runCatching { Color.parseColor(normalized) }.getOrNull()
    }

    return commonNamedColors.firstOrNull { color ->
        color.name.equals(value, ignoreCase = true) ||
            color.aliases.any { it.equals(value, ignoreCase = true) }
    }?.colorInt
}

fun colorToCompose(colorInt: Int): androidx.compose.ui.graphics.Color {
    return androidx.compose.ui.graphics.Color(colorInt)
}

private fun toHsl(red: Int, green: Int, blue: Int): HslColor {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val lightness = (max + min) / 2f

    if (max == min) {
        return HslColor(0, 0, (lightness * 100).roundToInt())
    }

    val delta = max - min
    val saturation = if (lightness > 0.5f) {
        delta / (2f - max - min)
    } else {
        delta / (max + min)
    }
    val hue = when (max) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / delta + 2f) / 6f
        else -> ((r - g) / delta + 4f) / 6f
    }

    return HslColor(
        hue = (hue * 360).roundToInt(),
        saturation = (saturation * 100).roundToInt(),
        lightness = (lightness * 100).roundToInt(),
    )
}

private fun toCmyk(red: Int, green: Int, blue: Int): CmykColor {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val black = 1f - max(r, max(g, b))
    if (black >= 1f) {
        return CmykColor(0, 0, 0, 100)
    }

    return CmykColor(
        cyan = (((1f - r - black) / (1f - black)) * 100).roundToInt(),
        magenta = (((1f - g - black) / (1f - black)) * 100).roundToInt(),
        yellow = (((1f - b - black) / (1f - black)) * 100).roundToInt(),
        black = (black * 100).roundToInt(),
    )
}

private fun relativeLuminance(red: Int, green: Int, blue: Int): Double {
    fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
    }

    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}
