package io.github.jvcsampaio.sonar.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.jvcsampaio.sonar.R

/**
 * MERIDIANO tokens, taken from the design system (tokens.json). Colors are semantic: `aceso` is
 * light and only marks operation, `insignia` (bronze) marks the one main action and registry,
 * `tempo` (patina) marks what is past, `alerta` only a critical state.
 */
@Immutable
data class Cores(
    val fundo: Color,
    val fundoPoco: Color,
    val superficie: Color,
    val relevo: Color,
    val linha: Color,
    val linhaForte: Color,
    val tinta: Color,
    val tintaSuave: Color,
    val aceso: Color,
    val insignia: Color,
    val insigniaForte: Color,
    val sobreInsignia: Color,
    val tempo: Color,
    val alerta: Color,
    val sobreAlerta: Color,
    val foco: Color,
    /** brilho-aceso exists only at midnight. */
    val brilho: Boolean,
    val escuro: Boolean,
)

val MeiaNoite = Cores(
    fundo = Color(0xFF121C24),
    fundoPoco = Color(0xFF0B1217),
    superficie = Color(0xFF1A2731),
    relevo = Color(0xFF22303B),
    linha = Color(0xFF2C3B47),
    linhaForte = Color(0xFF6A7A87),
    tinta = Color(0xFFDAD6C9),
    tintaSuave = Color(0xFF9AA6AE),
    aceso = Color(0xFFF6F3EC),
    insignia = Color(0xFFB49363),
    insigniaForte = Color(0xFFC9AB7C),
    sobreInsignia = Color(0xFF121C24),
    tempo = Color(0xFF7AA5A7),
    alerta = Color(0xFFF0705B),
    sobreAlerta = Color(0xFF121C24),
    foco = Color(0xFFF6F3EC),
    brilho = true,
    escuro = true,
)

val MeioDia = Cores(
    fundo = Color(0xFFECE8DE),
    fundoPoco = Color(0xFFE2DDD1),
    superficie = Color(0xFFF6F3EC),
    relevo = Color(0xFFFAF8F3),
    linha = Color(0xFFD4CEC0),
    linhaForte = Color(0xFF7E786B),
    tinta = Color(0xFF121C24),
    tintaSuave = Color(0xFF4F5B64),
    aceso = Color(0xFF121C24),
    insignia = Color(0xFF7A5C30),
    insigniaForte = Color(0xFF5E4624),
    sobreInsignia = Color(0xFFFAF8F3),
    tempo = Color(0xFF3E6365),
    alerta = Color(0xFFA93526),
    sobreAlerta = Color(0xFFFAF8F3),
    foco = Color(0xFF121C24),
    brilho = false,
    escuro = false,
)

val LocalCores = staticCompositionLocalOf { MeiaNoite }

/** Short access to the current palette: `M.cores.tinta`. */
object M {
    val cores: Cores
        @Composable @ReadOnlyComposable get() = LocalCores.current
}

/** Spacing in √2 steps (passo-1 … passo-11). Panel padding passo-6, control height passo-8. */
object Passo {
    val p1 = 4.dp
    val p2 = 6.dp
    val p3 = 8.dp
    val p4 = 11.dp
    val p5 = 16.dp
    val p6 = 23.dp
    val p7 = 32.dp
    val p8 = 45.dp
    val p9 = 64.dp
    val p10 = 91.dp
    val p11 = 128.dp
}

/** 45° chamfers (top-left and bottom-right) and stroke weights. */
object Chanfro {
    val p = 6.dp
    val m = 11.dp
    val g = 16.dp
}

object Traco {
    val fio = 1.dp
    val medio = 2.dp
    val eixo = 3.dp
}

/** Durations on the √2 scale and the mechanical curve: nothing bounces or stretches. */
object Movimento {
    const val D1 = 128
    const val D2 = 181
    const val D3 = 256
    const val D4 = 362
    const val PULSO = 1414
    val curva = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> mecanico(duration: Int): TweenSpec<T> = androidx.compose.animation.core.tween(duration, easing = curva)
}

private val Capitalis = FontFamily(Font(R.font.marcellus_sc))

private val Humanistica = FontFamily(
    Font(R.font.spectral_regular, FontWeight.Normal),
    Font(R.font.spectral_semibold, FontWeight.SemiBold),
    Font(R.font.spectral_italic, FontWeight.Normal, FontStyle.Italic),
)

private val Norma = FontFamily(Font(R.font.osifont))

private val Instrumentum = FontFamily(
    Font(R.font.b612_regular, FontWeight.Normal),
    Font(R.font.b612_bold, FontWeight.Bold),
)

private val InstrumentumMono = FontFamily(Font(R.font.b612_mono_regular))

/**
 * The four voices. Capitalis (Marcellus SC) for names and titles, written in normal case;
 * Humanistica (Spectral) for running text; Norma (osifont, ISO 3098) for labels and buttons,
 * always in capitals; Instrumentum (B612) for every number that is compared.
 */
object Tipo {
    val monumentum = TextStyle(fontFamily = Capitalis, fontSize = 64.sp, lineHeight = 61.sp, letterSpacing = 0.04.em)
    val titulus1 = TextStyle(fontFamily = Capitalis, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.08.em)
    val titulus2 = TextStyle(fontFamily = Capitalis, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.1.em)
    val titulus3 = TextStyle(fontFamily = Capitalis, fontSize = 22.5.sp, lineHeight = 28.sp, letterSpacing = 0.12.em)

    val lead = TextStyle(fontFamily = Humanistica, fontSize = 22.5.sp, lineHeight = 32.sp)
    val corpo = TextStyle(fontFamily = Humanistica, fontSize = 16.sp, lineHeight = 24.sp)
    val corpoForte = corpo.copy(fontWeight = FontWeight.SemiBold)
    val citacao = TextStyle(fontFamily = Humanistica, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 28.sp)
    val nota = TextStyle(fontFamily = Humanistica, fontSize = 13.5.sp, lineHeight = 20.sp)

    // Norma text must be upper-cased by the caller (see `Rotulo`); the styles only set the face.
    val placa = TextStyle(fontFamily = Norma, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = 0.08.em)
    val rotulo = TextStyle(fontFamily = Norma, fontSize = 13.5.sp, lineHeight = 16.sp, letterSpacing = 0.06.em)
    val cota = TextStyle(fontFamily = Norma, fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = 0.04.em)

    val leitura = TextStyle(fontFamily = Instrumentum, fontSize = 32.sp, lineHeight = 40.sp)
    val dado = TextStyle(fontFamily = Instrumentum, fontSize = 13.5.sp, lineHeight = 20.sp)
    val dadoForte = dado.copy(fontWeight = FontWeight.Bold)
    val codigo = TextStyle(fontFamily = InstrumentumMono, fontSize = 13.5.sp, lineHeight = 20.sp)
}

/** Material components that remain (menus, sheets, text selection) read these. */
private fun Cores.materialScheme() = if (escuro) {
    darkColorScheme(
        primary = insignia, onPrimary = sobreInsignia, secondary = tinta, onSecondary = fundo,
        background = fundo, onBackground = tinta, surface = superficie, onSurface = tinta,
        surfaceVariant = relevo, onSurfaceVariant = tintaSuave, surfaceContainer = superficie,
        surfaceContainerLow = superficie, surfaceContainerHigh = relevo, surfaceContainerHighest = relevo,
        surfaceContainerLowest = fundoPoco, outline = linhaForte, outlineVariant = linha,
        error = alerta, onError = sobreAlerta, scrim = Color(0xCC0B1217),
    )
} else {
    lightColorScheme(
        primary = insignia, onPrimary = sobreInsignia, secondary = tinta, onSecondary = fundo,
        background = fundo, onBackground = tinta, surface = superficie, onSurface = tinta,
        surfaceVariant = relevo, onSurfaceVariant = tintaSuave, surfaceContainer = superficie,
        surfaceContainerLow = superficie, surfaceContainerHigh = relevo, surfaceContainerHighest = relevo,
        surfaceContainerLowest = fundoPoco, outline = linhaForte, outlineVariant = linha,
        error = alerta, onError = sobreAlerta, scrim = Color(0x99121C24),
    )
}

private val MaterialTypography = Typography().run {
    copy(
        bodyLarge = Tipo.corpo,
        bodyMedium = Tipo.corpo,
        bodySmall = Tipo.nota,
        titleMedium = Tipo.corpoForte,
        labelLarge = Tipo.rotulo,
    )
}

@Composable
fun SonarTheme(meioDia: Boolean = false, content: @Composable () -> Unit) {
    val cores = if (meioDia) MeioDia else MeiaNoite
    MaterialTheme(colorScheme = cores.materialScheme(), typography = MaterialTypography) {
        CompositionLocalProvider(
            LocalCores provides cores,
            LocalContentColor provides cores.tinta,
            LocalTextSelectionColors provides TextSelectionColors(cores.tinta, cores.linhaForte.copy(alpha = 0.5f)),
            content = content,
        )
    }
}
