package io.github.jvcsampaio.sonar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jvcsampaio.sonar.R

/**
 * MERIDIANO: monumental rationalism. Graphite is the base, ivory carries the text, bronze marks
 * hierarchy, mineral green is reserved for active systems and red only for alerts.
 */
object Meridiano {
    val Graphite = Color(0xFF121C24)
    val GraphiteRaised = Color(0xFF17232D)
    val GraphiteHigh = Color(0xFF1D2A35)
    val GraphiteHighest = Color(0xFF24333F)
    val Line = Color(0xFF2B3A46)
    val Ivory = Color(0xFFDAD6C9)
    val IvoryMuted = Color(0xFF949792)
    val Bronze = Color(0xFFB49363)
    val Mineral = Color(0xFF709B9D)
    val Critical = Color(0xFF963F3C)
    /** Critical red lifted enough to be read as text on graphite. */
    val CriticalText = Color(0xFFC9817B)
}

val SerifFamily = FontFamily(
    Font(R.font.plex_serif_regular, FontWeight.Normal),
    Font(R.font.plex_serif_medium, FontWeight.Medium),
    Font(R.font.plex_serif_semibold, FontWeight.SemiBold),
)

val MonoFamily = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

/** Technical readouts: counters, times, states. */
val DataStyle = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp)

private val Colors = darkColorScheme(
    primary = Meridiano.Bronze,
    onPrimary = Meridiano.Graphite,
    primaryContainer = Color(0xFF3A3226),
    onPrimaryContainer = Color(0xFFE6D3B3),
    secondary = Meridiano.Mineral,
    onSecondary = Meridiano.Graphite,
    tertiary = Meridiano.Mineral,
    onTertiary = Meridiano.Graphite,
    background = Meridiano.Graphite,
    onBackground = Meridiano.Ivory,
    surface = Meridiano.Graphite,
    onSurface = Meridiano.Ivory,
    surfaceVariant = Meridiano.GraphiteHigh,
    onSurfaceVariant = Meridiano.IvoryMuted,
    surfaceContainerLowest = Color(0xFF0E161D),
    surfaceContainerLow = Meridiano.GraphiteRaised,
    surfaceContainer = Meridiano.GraphiteRaised,
    surfaceContainerHigh = Meridiano.GraphiteHigh,
    surfaceContainerHighest = Meridiano.GraphiteHighest,
    inverseSurface = Meridiano.Ivory,
    inverseOnSurface = Meridiano.Graphite,
    outline = Color(0xFF3A4A57),
    outlineVariant = Meridiano.Line,
    error = Meridiano.Critical,
    onError = Meridiano.Ivory,
    scrim = Color(0xCC0B1218),
)

private val Base = Typography()

private val SonarTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontFamily = SerifFamily, fontWeight = FontWeight.Medium),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge.copy(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.2.sp),
    labelMedium = Base.labelMedium.copy(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.4.sp),
    labelSmall = Base.labelSmall.copy(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 1.4.sp),
)

/** Architectural corners: precise, barely softened. */
private val SonarShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun SonarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = SonarTypography, shapes = SonarShapes, content = content)
}
