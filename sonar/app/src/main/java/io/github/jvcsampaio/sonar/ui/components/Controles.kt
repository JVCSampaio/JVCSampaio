package io.github.jvcsampaio.sonar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import io.github.jvcsampaio.sonar.ui.theme.Traco

enum class TipoBotao { Principal, Padrao, Discreto }

/**
 * Botão: a verb in the infinitive, in Norma, on a chamfered piece. No icons. Principal is the
 * one main action of a screen; discreto is underlined text.
 */
@Composable
fun Botao(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tipo: TipoBotao = TipoBotao.Padrao,
    compacto: Boolean = false,
    enabled: Boolean = true,
) {
    val cores = M.cores
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (enchimento, borda, tinta) = when {
        !enabled && tipo != TipoBotao.Discreto -> Triple(cores.fundoPoco, cores.linha, cores.tintaSuave)
        tipo == TipoBotao.Principal -> {
            val c = if (pressed) cores.insigniaForte else cores.insignia
            Triple(c, c, cores.sobreInsignia)
        }
        tipo == TipoBotao.Padrao ->
            if (pressed) Triple(cores.relevo, cores.tintaSuave, cores.tinta) else Triple(cores.superficie, cores.linhaForte, cores.tinta)
        else -> Triple(Color.Transparent, Color.Transparent, if (enabled) cores.tinta else cores.tintaSuave)
    }
    val altura = if (compacto) Passo.p7 else Passo.p8
    Box(
        modifier
            .heightIn(min = altura)
            .then(if (tipo == TipoBotao.Discreto) Modifier else Modifier.peca(enchimento, borda, Chanfro.p, Traco.medio))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (tipo == TipoBotao.Discreto) Passo.p4 else if (compacto) Passo.p5 else Passo.p6),
        contentAlignment = Alignment.Center,
    ) {
        Rotulo(
            texto,
            color = tinta,
            style = if (tipo == TipoBotao.Discreto) {
                Tipo.rotulo.copy(textDecoration = TextDecoration.Underline)
            } else {
                Tipo.rotulo
            },
        )
    }
}

/** Geometric glyphs on a 24-unit grid: square ends, mitred corners, no rounding. */
enum class Glifo { Tocar, Pausar, Proxima, Anterior, Fila, Fechar, Voltar, Recolher, Mais, Adicionar }

private fun DrawScope.glifo(glifo: Glifo, color: Color) {
    val u = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun poly(vararg xy: Float, fill: Boolean) {
        val path = Path().apply {
            moveTo(xy[0] * u, xy[1] * u)
            var i = 2
            while (i < xy.size) {
                lineTo(xy[i] * u, xy[i + 1] * u)
                i += 2
            }
            if (fill) close()
        }
        if (fill) drawPath(path, color) else drawPath(path, color, style = Stroke(2f * u, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
    }
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), 2f * u)
    when (glifo) {
        Glifo.Tocar -> poly(7f, 4.5f, 19.5f, 12f, 7f, 19.5f, fill = true)
        Glifo.Pausar -> {
            drawRect(color, p(6f, 5f), Size(4f * u, 14f * u))
            drawRect(color, p(14f, 5f), Size(4f * u, 14f * u))
        }
        Glifo.Proxima -> {
            poly(5f, 5f, 15.5f, 12f, 5f, 19f, fill = true)
            drawRect(color, p(16.5f, 5f), Size(2.5f * u, 14f * u))
        }
        Glifo.Anterior -> {
            poly(19f, 5f, 8.5f, 12f, 19f, 19f, fill = true)
            drawRect(color, p(5f, 5f), Size(2.5f * u, 14f * u))
        }
        Glifo.Fila -> {
            line(4f, 7f, 20f, 7f)
            line(4f, 12f, 20f, 12f)
            line(4f, 17f, 13f, 17f)
            poly(16f, 14f, 20.5f, 17f, 16f, 20f, fill = true)
        }
        Glifo.Fechar -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }
        Glifo.Voltar -> {
            line(5.5f, 12f, 20f, 12f)
            poly(11.5f, 6f, 5.5f, 12f, 11.5f, 18f, fill = false)
        }
        Glifo.Recolher -> poly(5f, 9f, 12f, 16f, 19f, 9f, fill = false)
        Glifo.Mais -> listOf(4.5f, 10.5f, 16.5f).forEach { y -> drawRect(color, p(10.5f, y), Size(3f * u, 3f * u)) }
        Glifo.Adicionar -> {
            line(12f, 5f, 12f, 19f)
            line(5f, 12f, 19f, 12f)
        }
    }
}

@Composable
fun Glyph(glifo: Glifo, modifier: Modifier = Modifier, color: Color = M.cores.tinta) {
    Canvas(modifier) { glifo(glifo, color) }
}

/**
 * A glyph control. [principal] draws it on the bronze chamfered piece (the one main action),
 * [moldura] on a standard chamfered piece; otherwise it stands alone on a 45 dp target.
 */
@Composable
fun BotaoGlifo(
    glifo: Glifo,
    descricao: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tamanho: Dp = Passo.p8,
    glifoTamanho: Dp = 24.dp,
    principal: Boolean = false,
    moldura: Boolean = false,
    enabled: Boolean = true,
    color: Color = M.cores.tinta,
) {
    val cores = M.cores
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fundo = when {
        principal -> Modifier.peca(if (pressed) cores.insigniaForte else cores.insignia, if (pressed) cores.insigniaForte else cores.insignia, Chanfro.m, Traco.medio)
        moldura -> Modifier.peca(if (pressed) cores.relevo else cores.superficie, if (pressed) cores.tintaSuave else cores.linhaForte, Chanfro.p, Traco.medio)
        pressed -> Modifier.peca(cores.relevo, Color.Transparent, Chanfro.p, 0.dp)
        else -> Modifier
    }
    val tinta = when {
        !enabled -> cores.linhaForte
        principal -> cores.sobreInsignia
        else -> color
    }
    Box(
        modifier
            .size(tamanho)
            .then(fundo)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glifo, Modifier.size(glifoTamanho), tinta)
    }
}

/**
 * A two-position switch: the row is the target; the piece on the right shows the position as a
 * filled or empty lamp and a word. It is a position, not light, so it never glows.
 */
@Composable
fun Chave(
    titulo: String,
    descricao: String,
    ligada: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val cores = M.cores
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!ligada) }
            .padding(vertical = Passo.p4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Passo.p5),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = Tipo.corpoForte, color = if (enabled) cores.tinta else cores.tintaSuave)
            Text(descricao, style = Tipo.nota, color = cores.tintaSuave)
        }
        Row(
            Modifier
                .widthIn(min = 76.dp)
                .heightIn(min = Passo.p7)
                .peca(if (ligada) cores.relevo else cores.superficie, if (ligada) cores.tinta else cores.linhaForte, Chanfro.p, Traco.medio)
                .padding(horizontal = Passo.p4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Passo.p3),
        ) {
            Canvas(Modifier.size(Passo.p4)) {
                val w = Traco.medio.toPx()
                if (ligada) drawCircle(cores.tinta) else drawCircle(cores.linhaForte, size.minDimension / 2 - w / 2, style = Stroke(w))
            }
            Rotulo(if (ligada) "Sim" else "Não", color = if (ligada) cores.tinta else cores.tintaSuave)
        }
    }
}

/** A row of chamfered choices; the chosen one rises to `relevo` with an ink border. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Escolhas(
    opcoes: List<T>,
    selecionada: T,
    rotulo: (T) -> String,
    modifier: Modifier = Modifier,
    contagem: ((T) -> Int)? = null,
    onSelect: (T) -> Unit,
) {
    val cores = M.cores
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Passo.p3), verticalArrangement = Arrangement.spacedBy(Passo.p3)) {
        opcoes.forEach { opcao ->
            val ativa = opcao == selecionada
            Row(
                Modifier
                    .heightIn(min = Passo.p8)
                    .peca(
                        if (ativa) cores.relevo else cores.fundo,
                        if (ativa) cores.tinta else cores.linhaForte,
                        Chanfro.p,
                        if (ativa) Traco.medio else Traco.fio,
                    )
                    .clickable(role = Role.RadioButton) { onSelect(opcao) }
                    .padding(horizontal = Passo.p5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Passo.p3),
            ) {
                Rotulo(rotulo(opcao), color = if (ativa) cores.tinta else cores.tintaSuave)
                if (contagem != null) Text(contagem(opcao).toString(), style = Tipo.dado, color = if (ativa) cores.tinta else cores.tintaSuave)
            }
        }
    }
}

/** Text field: a chamfered well with a label above; the border turns to `foco` while typing. */
@Composable
fun CampoTexto(
    valor: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    rotulo: String? = null,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fim: (@Composable () -> Unit)? = null,
) {
    val cores = M.cores
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth()) {
        if (rotulo != null) Rotulo(rotulo, Modifier.padding(bottom = Passo.p2))
        BasicTextField(
            value = valor,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Tipo.corpo.copy(color = cores.tinta),
            cursorBrush = SolidColor(cores.tinta),
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = Passo.p8)
                        .peca(cores.fundoPoco, if (focused) cores.foco else cores.linhaForte, Chanfro.p, Traco.medio)
                        .padding(start = Passo.p5, end = Passo.p2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = Passo.p4)) {
                        if (valor.isEmpty()) Text(placeholder, style = Tipo.corpo, color = cores.tintaSuave, maxLines = 1)
                        inner()
                    }
                    fim?.invoke()
                }
            },
        )
    }
}

/** An overflow action: a command in the infinitive. */
data class MenuAction(val label: String, val onClick: () -> Unit)

/** The overflow control: three stacked squares opening a chamfered menu of commands. */
@Composable
fun MenuMais(acoes: List<MenuAction>, modifier: Modifier = Modifier, descricao: String = "Mais opções") {
    var aberto by remember { mutableStateOf(false) }
    val cores = M.cores
    Box(modifier) {
        BotaoGlifo(Glifo.Mais, descricao, onClick = { aberto = true }, color = cores.tintaSuave, glifoTamanho = 20.dp)
        DropdownMenu(
            expanded = aberto,
            onDismissRequest = { aberto = false },
            shape = ChanfroShape(Chanfro.p),
            containerColor = cores.relevo,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(Traco.fio, cores.linhaForte),
        ) {
            acoes.forEach { acao ->
                DropdownMenuItem(
                    text = { Rotulo(acao.label, color = cores.tinta) },
                    onClick = {
                        aberto = false
                        acao.onClick()
                    },
                )
            }
        }
    }
}
