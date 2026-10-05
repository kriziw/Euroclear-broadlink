package io.github.kriziw.bl3372setup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9E6FF),
    onPrimaryContainer = Color(0xFF001E2F),
    secondary = Color(0xFF4F616E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF006A63),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9EF2E7),
    onTertiaryContainer = Color(0xFF00201D),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF171C20),
    surface = Color.White,
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF0F3F7),
    surfaceContainerHigh = Color(0xFFEAEEF2),
    surfaceContainerHighest = Color(0xFFE3E8ED),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BCEFF),
    onPrimary = Color(0xFF00344D),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFC9E6FF),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21323E),
    secondaryContainer = Color(0xFF384955),
    onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFF82D5CB),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF00504A),
    onTertiaryContainer = Color(0xFF9EF2E7),
    background = Color(0xFF0E1316),
    onBackground = Color(0xFFDEE3E8),
    surface = Color(0xFF181E22),
    onSurface = Color(0xFFDEE3E8),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF090E11),
    surfaceContainerLow = Color(0xFF161C20),
    surfaceContainer = Color(0xFF1B2125),
    surfaceContainerHigh = Color(0xFF252B30),
    surfaceContainerHighest = Color(0xFF30363B),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
)

/** Status colours Material 3 has no slot for. */
@Immutable
data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val LightStatus = StatusColors(
    success = Color(0xFF1B6D3B),
    successContainer = Color(0xFFC6EFCF),
    onSuccessContainer = Color(0xFF00210D),
    warning = Color(0xFF8A5100),
    warningContainer = Color(0xFFFFDDB9),
    onWarningContainer = Color(0xFF2C1600),
)

private val DarkStatus = StatusColors(
    success = Color(0xFF8DD89F),
    successContainer = Color(0xFF005227),
    onSuccessContainer = Color(0xFFA9F5BA),
    warning = Color(0xFFFFB961),
    warningContainer = Color(0xFF693C00),
    onWarningContainer = Color(0xFFFFDDB9),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Follows the system light/dark setting. */
@Composable
fun BL3372Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
