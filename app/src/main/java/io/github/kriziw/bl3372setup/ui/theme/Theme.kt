package io.github.kriziw.bl3372setup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SuccessGreen = Color(0xFF1B7F3B)
val WarningAmber = Color(0xFFA15C00)

private val WaterColors = lightColorScheme(
    primary = Color(0xFF0B63C5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E7FF),
    onPrimaryContainer = Color(0xFF00315F),
    secondary = Color(0xFF1C86CF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F0FD),
    onSecondaryContainer = Color(0xFF003A5C),
    background = Color(0xFFE6F5FD),
    surface = Color.White,
    onSurface = Color(0xFF17212B),
    onSurfaceVariant = Color(0xFF44505C),
    outline = Color(0xFF7A8A99),
)

/**
 * Always light: the water artwork is a light image, and the app is used briefly during setup.
 */
@Composable
fun BL3372Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WaterColors, content = content)
}
