package io.github.jvcsampaio.sonar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.jvcsampaio.sonar.BuildConfig
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.ui.formatDuration
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import kotlin.math.cos
import kotlin.math.sin

/** Materials of the cover art: patina and red lead appear only as illustration, never as text. */
private val Patina400 = Color(0xFF709B9D)
private val Zarcao500 = Color(0xFF963F3C)

/** Cover art, clipped to the machined outline. Without a cover, the sinal waits in the well. */
@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corte: Dp = Chanfro.p) {
    val cores = M.cores
    Box(modifier.clip(ChanfroShape(corte)).background(cores.fundoPoco), contentAlignment = Alignment.Center) {
        Sinal(Modifier.fillMaxSize(0.42f), cores.linha)
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/**
 * One song. The one playing rises to `relevo` with a lit lamp; [indicador] adds a state (lamp
 * and word) for tracks that are not on the device yet.
 */
@Composable
fun TrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    actions: List<MenuAction> = emptyList(),
    supporting: String? = null,
    supportingColor: Color = M.cores.tintaSuave,
    indicador: Pair<Estado, String>? = null,
) {
    val cores = M.cores
    Row(
        modifier
            .fillMaxWidth()
            .background(if (isCurrent) cores.relevo else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = Passo.p5, end = Passo.p1, top = Passo.p3, bottom = Passo.p3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artwork, Modifier.size(48.dp))
        Spacer(Modifier.width(Passo.p5))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Lampada(if (playing) Estado.Aceso else Estado.Repouso, Modifier.padding(end = Passo.p3), tamanho = 9.dp)
                }
                Text(track.title, style = Tipo.corpoForte, color = cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                supporting ?: track.artist.ifBlank { "—" },
                style = Tipo.nota,
                color = supportingColor,
                maxLines = if (supporting != null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (indicador != null) Indicador(indicador.first, indicador.second, Modifier.padding(top = Passo.p1), style = Tipo.cota)
        }
        if (indicador == null && supporting == null && track.durationMs > 0) {
            Text(formatDuration(track.durationMs), style = Tipo.dado, color = cores.tintaSuave, modifier = Modifier.padding(start = Passo.p3))
        }
        if (actions.isNotEmpty()) MenuMais(actions) else Spacer(Modifier.width(Passo.p4))
    }
}

/** Section: a name in Capitalis, a hairline to the edge and, when it helps, a count. */
@Composable
fun Secao(titulo: String, modifier: Modifier = Modifier, contagem: Int? = null) {
    val cores = M.cores
    Row(
        modifier.fillMaxWidth().padding(start = Passo.p5, end = Passo.p5, top = Passo.p7, bottom = Passo.p4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Passo.p4),
    ) {
        Text(titulo, style = Tipo.titulus3, color = cores.tinta, maxLines = 1)
        Fio(Modifier.weight(1f))
        if (contagem != null) Text(contagem.toString(), style = Tipo.dado, color = cores.tintaSuave)
    }
}

/**
 * Screen header: a Latin name in Capitalis (with V for U), the place in the building as a
 * label, and the FINALIDADE of the screen. Every piece declares what it is for.
 */
@Composable
fun Cabecalho(
    nome: String,
    finalidade: String,
    modifier: Modifier = Modifier,
    local: String? = null,
    acoes: @Composable () -> Unit = {},
) {
    val cores = M.cores
    Column(modifier.fillMaxWidth().padding(start = Passo.p5, end = Passo.p3, top = Passo.p6, bottom = Passo.p4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (local != null) Rotulo(local, style = Tipo.cota)
                Text(nome, style = Tipo.titulus1, color = cores.tinta, maxLines = 1)
            }
            acoes()
        }
        Row(Modifier.padding(top = Passo.p2, end = Passo.p3), horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
            Rotulo("Finalidade", Modifier.padding(top = 4.dp), style = Tipo.cota)
            Text(finalidade, style = Tipo.nota, color = cores.tinta)
        }
    }
}

/** Empty state: the emblem, what is missing and the one action that fixes it. */
@Composable
fun Vazio(
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    acao: String? = null,
    onAcao: () -> Unit = {},
    estado: Pair<Estado, String>? = null,
) {
    val cores = M.cores
    Column(
        modifier.fillMaxWidth().padding(horizontal = Passo.p6, vertical = Passo.p7),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emblema(Modifier.size(width = 120.dp, height = 96.dp), cores.tintaSuave)
        Spacer(Modifier.height(Passo.p6))
        Text(titulo, style = Tipo.titulus3, color = cores.tinta, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Passo.p3))
        Text(texto, style = Tipo.corpo, color = cores.tintaSuave, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (estado != null) {
            Spacer(Modifier.height(Passo.p5))
            Indicador(estado.first, estado.second)
        }
        if (acao != null) {
            Spacer(Modifier.height(Passo.p6))
            Botao(acao, onAcao, tipo = TipoBotao.Principal)
        }
    }
}

/**
 * The cover (Capa): a ceramic slab standing on a bronze horizon, two satellites of patina and
 * red lead, and the graduations of an arc of radius passo-11 around the foot of the slab.
 */
@Composable
fun Capa(modifier: Modifier = Modifier) {
    val cores = M.cores
    Canvas(modifier.fillMaxWidth().aspectRatio(480f / 288f)) {
        val s = size.width / 480f
        fun r(x: Float, y: Float, w: Float, h: Float, c: Color) = drawRect(c, Offset(x * s, y * s), Size(w * s, h * s))
        r(0f, 32f, 128f, 160f, Patina400)
        r(208f, -8f, 64f, 200f, cores.tinta)
        r(368f, 128f, 45f, 64f, Zarcao500)
        r(0f, 192f, 480f, 64f, cores.insignia)
        for (k in 1..17) {
            if (k == 9) continue
            val a = Math.toRadians(k * 10.0)
            val inner = if (k % 3 == 0) 105f else 112f
            drawLine(
                cores.tinta,
                Offset((240 + 128 * cos(a)).toFloat() * s, (192 - 128 * sin(a)).toFloat() * s),
                Offset((240 + inner * cos(a)).toFloat() * s, (192 - inner * sin(a)).toFloat() * s),
                3f * s,
            )
        }
    }
}

/** Colophon: the signature, the revision and the motto, at the foot of a screen. */
@Composable
fun Colofao(modifier: Modifier = Modifier) {
    val cores = M.cores
    Row(
        modifier.fillMaxWidth().padding(horizontal = Passo.p5, vertical = Passo.p7),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Passo.p4),
    ) {
        Sinal(Modifier.size(Passo.p6), cores.tintaSuave)
        Column(Modifier.weight(1f)) {
            Rotulo("Forma servit fini", style = Tipo.cota)
            Text("SONAR · REV ${BuildConfig.VERSION_NAME}", style = Tipo.dado, color = cores.tempo)
        }
    }
}

/** A critical note: square lamp and the word, then the explanation. Never a red surface. */
@Composable
fun Aviso(texto: String, modifier: Modifier = Modifier, palavra: String = "Falha") {
    val cores = M.cores
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Passo.p2)) {
        Indicador(Estado.Critico, palavra)
        Text(texto, style = Tipo.nota, color = cores.tinta)
    }
}
