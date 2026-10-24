package io.github.jvcsampaio.sonar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Traco
import kotlin.math.min

/** The machined outline: square corners, 45° cuts at the top-left and bottom-right. */
fun chanfroPath(rect: Rect, cut: Float): Path {
    val c = cut.coerceIn(0f, min(rect.width, rect.height) / 2)
    return Path().apply {
        moveTo(rect.left + c, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right, rect.bottom - c)
        lineTo(rect.right - c, rect.bottom)
        lineTo(rect.left, rect.bottom)
        lineTo(rect.left, rect.top + c)
        close()
    }
}

class ChanfroShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(chanfroPath(Rect(0f, 0f, size.width, size.height), with(density) { cut.toPx() }))
}

/**
 * Draws a chamfered piece behind the content, like `mer-chanfro`: a border layer and an inset
 * fill whose cut is reduced so the border keeps the same width along the diagonal.
 */
fun Modifier.peca(
    enchimento: Color,
    borda: Color,
    corte: Dp = Chanfro.p,
    espessura: Dp = Traco.medio,
): Modifier = drawBehind {
    val c = corte.toPx()
    val b = espessura.toPx()
    val outer = chanfroPath(Rect(0f, 0f, size.width, size.height), c)
    val inner = chanfroPath(Rect(b, b, size.width - b, size.height - b), c - b * 0.586f)
    if (enchimento.alpha > 0f) drawPath(inner, enchimento)
    if (b > 0f && borda.alpha > 0f) drawPath(Path().apply { op(outer, inner, PathOperation.Difference) }, borda)
}

/** A hairline divider (`traco-fio` in `linha`). */
@Composable
fun Fio(modifier: Modifier = Modifier, color: Color = M.cores.linha) {
    Box(modifier.height(Traco.fio).background(color))
}

@Composable
fun FioVertical(modifier: Modifier = Modifier, color: Color = M.cores.linha) {
    Box(modifier.width(Traco.fio).background(color))
}
