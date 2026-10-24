package io.github.jvcsampaio.sonar.ui.components

import android.graphics.Paint
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import io.github.jvcsampaio.sonar.R
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Movimento
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import io.github.jvcsampaio.sonar.ui.theme.Traco
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val PT = Locale.forLanguageTag("pt-BR")

/** Norma is always set in capitals. */
@Composable
fun Rotulo(
    texto: String,
    modifier: Modifier = Modifier,
    color: Color = M.cores.tintaSuave,
    style: TextStyle = Tipo.rotulo,
    maxLines: Int = 1,
) {
    Text(texto.uppercase(PT), modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Motion only shows real processes, and stops entirely when the system asks for less motion. */
@Composable
fun movimentoReduzido(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

enum class Estado { Aceso, Repouso, Atencao, Critico, Verificado }

/** The lamp: its shape changes with the state, so color is never the only signal. */
@Composable
fun Lampada(estado: Estado, modifier: Modifier = Modifier, tamanho: Dp = Passo.p4) {
    val cores = M.cores
    val pulso = if (estado == Estado.Atencao && !movimentoReduzido()) {
        rememberInfiniteTransition(label = "pulso").animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(Movimento.PULSO / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulso",
        ).value
    } else {
        1f
    }
    Canvas(modifier.size(tamanho).alpha(pulso)) {
        val w = Traco.medio.toPx()
        val r = size.minDimension / 2
        val c = center
        when (estado) {
            Estado.Aceso -> {
                if (cores.brilho) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to cores.aceso.copy(alpha = 0.45f),
                            1f to Color.Transparent,
                            center = c,
                            radius = r + 11.dp.toPx(),
                        ),
                        radius = r + 11.dp.toPx(),
                        center = c,
                    )
                    drawCircle(cores.aceso, r + 1.dp.toPx(), c)
                }
                drawCircle(cores.aceso, r, c)
            }
            Estado.Repouso -> drawCircle(cores.linhaForte, r - w / 2, c, style = Stroke(w))
            Estado.Atencao -> {
                clipRect(right = c.x) { drawCircle(cores.aceso, r, c) }
                drawCircle(cores.aceso, r - w / 2, c, style = Stroke(w))
            }
            Estado.Critico -> drawRect(cores.alerta)
            Estado.Verificado -> {
                drawCircle(cores.insignia, r - w / 2, c, style = Stroke(w))
                drawLine(cores.insignia, Offset(c.x, 0f), Offset(c.x, size.height), w)
            }
        }
    }
}

fun corDoEstado(estado: Estado, cores: io.github.jvcsampaio.sonar.ui.theme.Cores): Color = when (estado) {
    Estado.Repouso -> cores.tintaSuave
    Estado.Critico -> cores.alerta
    Estado.Verificado -> cores.insignia
    else -> cores.tinta
}

/** Indicador: lamp plus word. */
@Composable
fun Indicador(estado: Estado, texto: String, modifier: Modifier = Modifier, style: TextStyle = Tipo.rotulo) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
        Lampada(estado)
        Rotulo(texto, color = corDoEstado(estado, M.cores), style = style)
    }
}

/**
 * Régua: a graduated measure instead of a progress bar. Minor ticks every 10 %, major every
 * 50 %; the measured part is lit, the transit stroke marks the value and carries its reading.
 * With [onSeek] it becomes the playhead: tap or drag along the scale.
 */
@Composable
fun Regua(
    valor: Float,
    leitura: String,
    modifier: Modifier = Modifier,
    descricao: String? = null,
    onSeek: ((Float) -> Unit)? = null,
    onSeekFinished: () -> Unit = {},
) {
    val cores = M.cores
    val v = valor.coerceIn(0f, 1f)
    var largura by remember { mutableIntStateOf(1) }
    var arrastando by remember { mutableStateOf(false) }
    val seek by rememberUpdatedState(onSeek)
    val finish by rememberUpdatedState(onSeekFinished)
    val medidor = rememberTextMeasurer()
    val gestos = if (onSeek == null) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures { o ->
                seek?.invoke((o.x / largura).coerceIn(0f, 1f))
                finish()
            }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { arrastando = true },
                onDragEnd = {
                    arrastando = false
                    finish()
                },
                onDragCancel = {
                    arrastando = false
                    finish()
                },
            ) { change, _ -> seek?.invoke((change.position.x / largura).coerceIn(0f, 1f)) }
        }
    Box(
        modifier
            .fillMaxWidth()
            .height(Passo.p8)
            .onSizeChanged { largura = it.width.coerceAtLeast(1) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(v, 0f..1f)
                if (descricao != null) contentDescription = descricao
            }
            .then(gestos),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val fio = Traco.fio.toPx()
            val eixo = Traco.eixo.toPx()
            val escala = Passo.p4.toPx()
            val base = size.height - fio / 2
            drawLine(cores.linhaForte, Offset(0f, base), Offset(size.width, base), fio)
            for (i in 0..10) {
                val x = (size.width - fio) * i / 10f + fio / 2
                val alto = if (i % 5 == 0) escala else escala / 2
                drawLine(cores.linhaForte, Offset(x, size.height), Offset(x, size.height - alto), fio)
            }
            drawRect(cores.aceso, Offset(0f, size.height - eixo), Size(size.width * v, eixo))
            val x = (size.width * v).coerceIn(eixo / 2, size.width - eixo / 2)
            val transito = if (arrastando) eixo * 1.5f else eixo
            drawRect(cores.insignia, Offset(x - transito / 2, 0f), Size(transito, size.height))
            // The reading sits right of the transit stroke, or left of it near the end.
            val texto = medidor.measure(leitura, Tipo.dado.copy(color = cores.tinta))
            val folga = Passo.p3.toPx()
            val tx = if (x + folga + texto.size.width <= size.width) x + folga else x - folga - texto.size.width
            drawText(texto, topLeft = Offset(tx.coerceAtLeast(0f), 0f))
        }
    }
}

/** A slim régua for tight places: the lit measure over a hairline scale. */
@Composable
fun ReguaFina(valor: Float, modifier: Modifier = Modifier) {
    val cores = M.cores
    Canvas(modifier.fillMaxWidth().height(Traco.eixo)) {
        drawRect(cores.linha, Offset(0f, size.height - Traco.fio.toPx()), Size(size.width, Traco.fio.toPx()))
        drawRect(cores.aceso, Offset.Zero, Size(size.width * valor.coerceIn(0f, 1f), size.height))
    }
}

/** Leitura: label, value, unit and a reference. Every number has a reference. */
@Composable
fun Leitura(
    rotulo: String,
    valor: String,
    referencia: String,
    modifier: Modifier = Modifier,
    unidade: String? = null,
) {
    val cores = M.cores
    Column(
        modifier
            .peca(cores.superficie, cores.linhaForte, Chanfro.m, Traco.fio)
            .padding(horizontal = Passo.p5, vertical = Passo.p4),
        verticalArrangement = Arrangement.spacedBy(Passo.p1),
    ) {
        Rotulo(rotulo)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Passo.p2)) {
            Text(valor, style = Tipo.leitura, color = cores.tinta, maxLines = 1)
            // Units keep their SI case ("ms", "h"), so they are not set in capitals.
            if (unidade != null) Text(unidade, style = Tipo.rotulo, color = cores.tintaSuave, modifier = Modifier.padding(bottom = 7.dp))
        }
        Text(referencia, style = Tipo.nota, color = cores.tempo, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Delta de revisão: the technical-drawing triangle with the revision number. */
@Composable
fun Delta(numero: Int, modifier: Modifier = Modifier, antigo: Boolean = false) {
    val color = if (antigo) M.cores.tempo else M.cores.tinta
    Box(modifier.size(Passo.p7, 28.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = size.width / 24f
            val dy = (size.height - 21f * s) / 2
            val p = Path().apply {
                moveTo(12f * s, 1.6f * s + dy)
                lineTo(22.6f * s, 19.6f * s + dy)
                lineTo(1.4f * s, 19.6f * s + dy)
                close()
            }
            drawPath(p, color, style = Stroke(1.6f * s, join = StrokeJoin.Miter))
        }
        Text(
            numero.toString(),
            style = Tipo.cota.copy(fontSize = 10.5.sp),
            color = color,
            modifier = Modifier.padding(top = 9.dp),
        )
    }
}

/** Selo Probatvm, compact: PROBATVM | REV | count | date. Issued only when a check passed. */
@Composable
fun SeloCompacto(revisao: String, contagem: String, data: String, modifier: Modifier = Modifier, antigo: Boolean = false) {
    val cores = M.cores
    val tinta = if (antigo) cores.tempo else cores.insignia
    Row(
        modifier
            .peca(cores.fundo, tinta, Chanfro.p, Traco.fio)
            .height(IntrinsicSize.Min)
            .padding(horizontal = Passo.p5, vertical = Passo.p2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Passo.p3),
    ) {
        Rotulo("Probatvm", color = tinta)
        Box(Modifier.width(Traco.fio).fillMaxHeight().background(tinta.copy(alpha = 0.6f)))
        Text("REV $revisao", style = Tipo.dadoForte, color = tinta, maxLines = 1)
        Text(contagem, style = Tipo.dado, color = tinta, maxLines = 1)
        Text(data, style = Tipo.dado, color = cores.tintaSuave, maxLines = 1)
    }
}

/**
 * The full Probatvm seal (from 120 dp): PROBATVM on the upper arc, the revision on the lower
 * arc, the count in the centre, a graduated ring every 6°.
 */
@Composable
fun Selo(
    revisao: String,
    contagem: String,
    unidade: String,
    data: String,
    modifier: Modifier = Modifier,
    tamanho: Dp = 148.dp,
) {
    val context = LocalContext.current
    val cores = M.cores
    val fontes = remember(context) {
        Triple(
            ResourcesCompat.getFont(context, R.font.marcellus_sc),
            ResourcesCompat.getFont(context, R.font.osifont),
            ResourcesCompat.getFont(context, R.font.b612_regular),
        )
    }
    Canvas(
        modifier.size(tamanho).semantics {
            contentDescription = "Probatvm: revisão $revisao, $contagem $unidade, $data"
        },
    ) {
        val s = size.minDimension / 248f
        val c = center
        val ink = cores.insignia
        drawCircle(ink, 120f * s - 2.5f * s, c, style = Stroke(5f * s))
        drawCircle(ink, 90f * s, c, style = Stroke(2f * s))
        for (k in 0 until 60) {
            val a = Math.toRadians(k * 6.0)
            val inner = if (k % 5 == 0) 80f else 84.5f
            drawLine(
                ink,
                Offset(c.x + (89f * cos(a)).toFloat() * s, c.y + (89f * sin(a)).toFloat() * s),
                Offset(c.x + (inner * cos(a)).toFloat() * s, c.y + (inner * sin(a)).toFloat() * s),
                1.6f * s,
            )
        }
        drawCircle(ink, 3f * s, Offset(c.x - 105f * s, c.y))
        drawCircle(ink, 3f * s, Offset(c.x + 105f * s, c.y))
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            fun paint(face: android.graphics.Typeface?, px: Float, spacing: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                textSize = px * s
                color = ink.toArgb()
                textAlign = Paint.Align.CENTER
                letterSpacing = spacing / px
            }
            val topo = android.graphics.Path().apply {
                addArc(c.x - 96f * s, c.y - 96f * s, c.x + 96f * s, c.y + 96f * s, 180f, 180f)
            }
            val base = android.graphics.Path().apply {
                addArc(c.x - 109f * s, c.y - 109f * s, c.x + 109f * s, c.y + 109f * s, 180f, -180f)
            }
            val capitalis = paint(fontes.first, 25f, 5.5f)
            native.drawTextOnPath("PROBATVM", topo, 0f, 0f, capitalis)
            val norma = paint(fontes.second, 17f, 3f)
            native.drawTextOnPath("REV $revisao", base, 0f, 0f, norma)
            native.drawText(data, c.x, c.y - 30f * s, paint(fontes.second, 12f, 1.4f))
            native.drawText(contagem, c.x, c.y + 11f * s, paint(fontes.third, 34f, 0f))
            native.drawText(unidade.uppercase(PT), c.x, c.y + 31f * s, paint(fontes.second, 12f, 3.6f))
        }
    }
}

/** Painel: an instrument plate with a header (name and id) and an optional footer. */
@Composable
fun Painel(
    modifier: Modifier = Modifier,
    nome: String? = null,
    id: String? = null,
    cabecalho: (@Composable RowScope.() -> Unit)? = null,
    pe: (@Composable RowScope.() -> Unit)? = null,
    corpo: PaddingValues = PaddingValues(Passo.p5),
    content: @Composable ColumnScope.() -> Unit,
) {
    val cores = M.cores
    Column(modifier.fillMaxWidth().peca(cores.superficie, cores.linhaForte, Chanfro.m, Traco.fio)) {
        if (nome != null || cabecalho != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Passo.p5, vertical = Passo.p4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Passo.p5),
            ) {
                if (cabecalho != null) {
                    cabecalho()
                } else {
                    Text(nome.orEmpty(), style = Tipo.titulus3, color = cores.tinta, modifier = Modifier.weight(1f))
                    if (id != null) Text(id, style = Tipo.dado, color = cores.tintaSuave)
                }
            }
            Fio(Modifier.fillMaxWidth().padding(horizontal = Traco.fio))
        }
        Column(Modifier.fillMaxWidth().padding(corpo)) { content() }
        if (pe != null) {
            Fio(Modifier.fillMaxWidth().padding(horizontal = Traco.fio))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Passo.p5, vertical = Passo.p3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Passo.p3),
                content = pe,
            )
        }
    }
}

/** Placa: identification plate with model, series, operator, manufacture, purpose and seals. */
@Composable
fun Placa(
    titulo: String,
    revisao: Int,
    campos: List<Pair<String, String>>,
    finalidade: String,
    modifier: Modifier = Modifier,
    selos: @Composable ColumnScope.() -> Unit = {},
) {
    val cores = M.cores
    Column(
        modifier
            .fillMaxWidth()
            .peca(cores.superficie, cores.insignia, Chanfro.g, Traco.medio)
            .padding(Passo.p6),
        verticalArrangement = Arrangement.spacedBy(Passo.p5),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Passo.p4)) {
                Sinal(Modifier.size(Passo.p7), cores.insignia)
                Rotulo(titulo, Modifier.weight(1f), color = cores.tinta, style = Tipo.placa)
                Delta(revisao)
            }
            Spacer(Modifier.height(Passo.p4))
            Fio(Modifier.fillMaxWidth(), cores.linhaForte)
        }
        campos.chunked(2).forEach { par ->
            Row(horizontalArrangement = Arrangement.spacedBy(Passo.p6)) {
                par.forEach { (rotulo, valor) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Passo.p1)) {
                        Rotulo(rotulo, style = Tipo.cota)
                        Text(valor, style = Tipo.dado, color = cores.tinta)
                    }
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Passo.p1)) {
            Rotulo("Finalidade", style = Tipo.cota)
            Text(finalidade, style = Tipo.nota, color = cores.tinta)
        }
        selos()
    }
}

/** One line of a log well. */
data class LinhaPoco(val hora: String, val origem: String, val mensagem: String, val critica: Boolean = false)

/** Poço: the recessed well for logs, in Instrumentum. */
@Composable
fun Poco(linhas: List<LinhaPoco>, modifier: Modifier = Modifier) {
    val cores = M.cores
    Column(
        modifier
            .fillMaxWidth()
            .background(cores.fundoPoco)
            .border(Traco.fio, cores.linha)
            .padding(horizontal = Passo.p5, vertical = Passo.p4),
        verticalArrangement = Arrangement.spacedBy(Passo.p1),
    ) {
        linhas.forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(Passo.p4)) {
                Text(linha.hora, style = Tipo.dado, color = cores.tintaSuave, maxLines = 1)
                Text(linha.origem, style = Tipo.dado, color = cores.tinta, maxLines = 1, modifier = Modifier.width(88.dp))
                Text(
                    linha.mensagem,
                    style = Tipo.dado,
                    color = if (linha.critica) cores.alerta else cores.tinta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
