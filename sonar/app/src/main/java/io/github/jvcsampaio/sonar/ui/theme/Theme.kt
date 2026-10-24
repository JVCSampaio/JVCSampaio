package io.github.jvcsampaio.sonar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Coral = Color(0xFFFF6B4A)
val Ink = Color(0xFF0D0D11)

private val Colors = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF1B0B06),
    primaryContainer = Color(0xFF3A1A12),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFB8B3FF),
    onSecondary = Color(0xFF16132E),
    tertiary = Color(0xFF7FE0C3),
    background = Ink,
    onBackground = Color(0xFFF4F3F8),
    surface = Ink,
    onSurface = Color(0xFFF4F3F8),
    surfaceVariant = Color(0xFF1C1C24),
    onSurfaceVariant = Color(0xFF9D9BAA),
    surfaceContainerLowest = Color(0xFF09090C),
    surfaceContainerLow = Color(0xFF121217),
    surfaceContainer = Color(0xFF16161C),
    surfaceContainerHigh = Color(0xFF1D1D25),
    surfaceContainerHighest = Color(0xFF26262F),
    outline = Color(0xFF3A3A46),
    outlineVariant = Color(0xFF26262F),
    error = Color(0xFFFF6B6B),
)

private val Base = Typography()

private val SonarTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall,
)

private val SonarShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SonarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = SonarTypography, shapes = SonarShapes, content = content)
}
