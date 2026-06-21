package kr.co.edoli.edoltool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    secondary = Color(0xFF0F766E),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7E5E4),
    onSurface = Color(0xFF171717),
    onSurfaceVariant = Color(0xFF57534E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF172554),
    secondary = Color(0xFF5EEAD4),
    background = Color(0xFF111111),
    onBackground = Color(0xFFF5F5F4),
    surface = Color(0xFF1C1917),
    surfaceContainer = Color(0xFF292524),
    surfaceVariant = Color(0xFF44403C),
    onSurface = Color(0xFFF5F5F4),
    onSurfaceVariant = Color(0xFFD6D3D1)
)

@Composable
fun EdolAndroidToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content
    )
}
