package io.github.jvcsampaio.sonar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The meridian: a graduated arc over the horizon, cut by a single transit stroke. Geometry is
 * the design system's own (meridiano-sinal / meridiano-emblema), drawn in SVG units around the
 * foot of the axis and scaled to fit. Never rotated, never filled, never inside a circle.
 */
private fun DrawScope.inViewBox(x: Float, y: Float, w: Float, h: Float, block: DrawScope.() -> Unit) {
    val s = min(size.width / w, size.height / h)
    val dx = (size.width - w * s) / 2 - x * s
    val dy = (size.height - h * s) / 2 - y * s
    translate(dx, dy) { scale(s, s, pivot = Offset.Zero) { block() } }
}

private fun DrawScope.arco(color: Color, width: Float) =
    drawArc(color, 180f, 180f, false, Offset(-100f, -100f), Size(200f, 200f), style = Stroke(width, cap = StrokeCap.Butt))

/** Sinal: arc, horizon and axis, without graduations. For 16 to 96 dp. */
@Composable
fun Sinal(modifier: Modifier = Modifier, color: Color = M.cores.tinta) {
    Canvas(modifier) {
        inViewBox(-111f, -178.21f, 222f, 222f) {
            arco(color, 14f)
            drawLine(color, Offset(-107f, 0f), Offset(107f, 0f), 14f)
            drawLine(color, Offset(0f, 0f), Offset(0f, -141.42f), 11f)
            drawCircle(color, 17f, Offset(0f, -100f))
        }
    }
}

/**
 * Emblema: graduations every 10° (longer every 30°), the horizon open at the axis, the noon
 * disc on the arc and the midnight ring below the horizon. From 96 dp.
 */
@Composable
fun Emblema(modifier: Modifier = Modifier, color: Color = M.cores.tinta) {
    Canvas(modifier) {
        inViewBox(-124.71f, -145.42f, 249.42f, 200.44f) {
            arco(color, 6f)
            drawLine(color, Offset(-120.71f, 0f), Offset(-10f, 0f), 6f)
            drawLine(color, Offset(10f, 0f), Offset(120.71f, 0f), 6f)
            for (k in 1..17) {
                if (k == 9) continue
                val a = Math.toRadians(k * 10.0)
                val inner = if (k % 3 == 0) 79f else 87f
                drawLine(
                    color,
                    Offset((97 * cos(a)).toFloat(), (-97 * sin(a)).toFloat()),
                    Offset((inner * cos(a)).toFloat(), (-inner * sin(a)).toFloat()),
                    2.6f,
                )
            }
            drawLine(color, Offset(0f, 33.42f), Offset(0f, -141.42f), 6f)
            drawCircle(color, 11f, Offset(0f, -100f))
            drawCircle(color, 8f, Offset(0f, 41.42f), style = Stroke(3.2f))
        }
    }
}

/** Assinatura: the sinal, the name in Capitalis and the motto in Norma. */
@Composable
fun Assinatura(
    modifier: Modifier = Modifier,
    nome: String = "Sonar",
    lema: String = "Forma servit fini",
    tamanho: Dp = Passo.p8,
    estiloNome: TextStyle = Tipo.titulus2,
    color: Color = M.cores.tinta,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Passo.p4)) {
        Sinal(Modifier.size(tamanho), color)
        Column {
            Text(nome, style = estiloNome, color = color)
            Text(
                lema.uppercase(),
                style = Tipo.cota.copy(letterSpacing = 0.3.em),
                color = M.cores.tintaSuave,
            )
        }
    }
}
