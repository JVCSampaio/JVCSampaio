package io.github.jvcsampaio.sonar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.jvcsampaio.sonar.playback.PlayerState
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.BotaoGlifo
import io.github.jvcsampaio.sonar.ui.components.Estado
import io.github.jvcsampaio.sonar.ui.components.Folha
import io.github.jvcsampaio.sonar.ui.components.Glifo
import io.github.jvcsampaio.sonar.ui.components.Indicador
import io.github.jvcsampaio.sonar.ui.components.Lampada
import io.github.jvcsampaio.sonar.ui.components.Regua
import io.github.jvcsampaio.sonar.ui.components.Rotulo
import io.github.jvcsampaio.sonar.ui.components.peca
import io.github.jvcsampaio.sonar.ui.formatData
import io.github.jvcsampaio.sonar.ui.formatDuration
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import io.github.jvcsampaio.sonar.ui.theme.Traco
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The observatory: the cover on the axis, the name in Capitalis, the régua as the playhead and
 * the transport below. Shuffle and repeat are switch positions, read as lamp and word.
 */
@Composable
fun PlayerScreen(vm: SonarViewModel, onCollapse: () -> Unit, onAddToPlaylist: (String) -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val current = state.current ?: return
    val track = remember(tracks, current.mediaId) { tracks.firstOrNull { it.videoId == current.mediaId } }
    var position by remember { mutableLongStateOf(vm.player.positionMs) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val cores = M.cores

    LaunchedEffect(current.mediaId, state.isPlaying) {
        while (true) {
            position = vm.player.positionMs
            delay(if (state.isPlaying) 250L else 1000L)
        }
    }
    BackHandler(onBack = onCollapse)

    val duration = state.durationMs
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = seeking?.let { (it * duration).toLong() } ?: position

    // The player sits outside the Scaffold, so it sets its own content color.
    CompositionLocalProvider(LocalContentColor provides cores.tinta) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffset.roundToInt()) }
                .background(cores.fundo)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 280f) onCollapse()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { _, dy -> dragOffset = (dragOffset + dy).coerceAtLeast(0f) }
                },
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Passo.p5)) {
                Row(Modifier.fillMaxWidth().padding(top = Passo.p2), verticalAlignment = Alignment.CenterVertically) {
                    BotaoGlifo(Glifo.Recolher, "Minimizar", onCollapse)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Indicador(
                            when {
                                state.isBuffering -> Estado.Atencao
                                state.isPlaying -> Estado.Aceso
                                else -> Estado.Repouso
                            },
                            when {
                                state.isBuffering -> "Carregando"
                                state.isPlaying -> "Tocando"
                                else -> "Pausado"
                            },
                        )
                        if (state.queue.size > 1) {
                            Text(
                                String.format(Locale.ROOT, "%02d / %02d", state.currentIndex + 1, state.queue.size),
                                style = Tipo.dado,
                                color = cores.tintaSuave,
                            )
                        }
                    }
                    BotaoGlifo(Glifo.Adicionar, "Adicionar à playlist", { onAddToPlaylist(current.mediaId) })
                    BotaoGlifo(Glifo.Fila, "Fila", { showQueue = true })
                }

                Spacer(Modifier.weight(1f))
                Artwork(current.artwork, Modifier.fillMaxWidth().aspectRatio(1f), corte = Chanfro.g)
                Spacer(Modifier.weight(1f))

                Text(
                    current.title,
                    style = if (current.title.length > 28) Tipo.titulus3 else Tipo.titulus2,
                    color = cores.tinta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(current.artist, style = Tipo.corpo, color = cores.tintaSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track != null) {
                    val ficha = listOfNotNull(
                        track.filePath?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT),
                        track.downloadedAt?.let { "gravada ${formatData(it)}" },
                        "${track.playCount}×",
                    )
                    Text(ficha.joinToString("  ·  "), style = Tipo.dado, color = cores.tempo, modifier = Modifier.padding(top = Passo.p1))
                }

                Spacer(Modifier.height(Passo.p5))
                Regua(
                    valor = seeking ?: fraction,
                    leitura = formatDuration(shown),
                    descricao = "Posição da música",
                    onSeek = { seeking = it },
                    onSeekFinished = {
                        seeking?.let { vm.player.seekTo((it * duration).toLong()) }
                        seeking = null
                    },
                )
                Row(Modifier.fillMaxWidth().padding(top = Passo.p1)) {
                    Text("0:00", style = Tipo.dado, color = cores.tintaSuave)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(duration), style = Tipo.dado, color = cores.tintaSuave)
                }

                Spacer(Modifier.height(Passo.p5))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Passo.p5, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotaoGlifo(Glifo.Anterior, "Anterior", { vm.player.previous() }, tamanho = Passo.p9, moldura = true)
                    BotaoGlifo(
                        if (state.isPlaying) Glifo.Pausar else Glifo.Tocar,
                        if (state.isPlaying) "Pausar" else "Tocar",
                        { vm.player.togglePlay() },
                        Modifier.width(Passo.p10),
                        tamanho = Passo.p9,
                        glifoTamanho = 32.dp,
                        principal = true,
                    )
                    BotaoGlifo(
                        Glifo.Proxima,
                        "Próxima",
                        { vm.player.next() },
                        tamanho = Passo.p9,
                        moldura = true,
                        enabled = state.hasNext,
                    )
                }

                Spacer(Modifier.height(Passo.p5))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
                    Modo("Aleatório", if (state.shuffle) 2 else 0, Modifier.weight(1f)) { vm.player.toggleShuffle() }
                    Modo(
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repetir uma"
                            Player.REPEAT_MODE_ALL -> "Repetir tudo"
                            else -> "Repetir"
                        },
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> 1
                            Player.REPEAT_MODE_ALL -> 2
                            else -> 0
                        },
                        Modifier.weight(1f),
                    ) { vm.player.cycleRepeat() }
                }
                Spacer(Modifier.height(Passo.p6))
            }
        }
    }

    if (showQueue) QueueSheet(state, vm, onDismiss = { showQueue = false })
}

/** A mode switch: empty lamp off, half lamp for one, full lamp for all. */
@Composable
private fun Modo(texto: String, nivel: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cores = M.cores
    val ligado = nivel > 0
    Row(
        modifier
            .heightIn(min = Passo.p8)
            .peca(if (ligado) cores.relevo else cores.fundo, if (ligado) cores.tinta else cores.linhaForte, Chanfro.p, if (ligado) Traco.medio else Traco.fio)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = Passo.p5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Passo.p3, Alignment.CenterHorizontally),
    ) {
        Canvas(Modifier.size(Passo.p4)) {
            val w = Traco.medio.toPx()
            val r = size.minDimension / 2
            drawCircle(if (ligado) cores.tinta else cores.linhaForte, r - w / 2, style = Stroke(w))
            when (nivel) {
                2 -> drawCircle(cores.tinta, r)
                1 -> drawArc(cores.tinta, 90f, 180f, true)
            }
        }
        Rotulo(texto, color = if (ligado) cores.tinta else cores.tintaSuave)
    }
}

@Composable
private fun QueueSheet(state: PlayerState, vm: SonarViewModel, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
    val cores = M.cores
    Folha(onDismiss) {
        Row(Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3), verticalAlignment = Alignment.CenterVertically) {
            Text("Fila", style = Tipo.titulus3, color = cores.tinta, modifier = Modifier.weight(1f))
            Text("${state.queue.size}", style = Tipo.dado, color = cores.tintaSuave)
        }
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(state.queue) { index, entry ->
                val isCurrent = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) cores.relevo else Color.Transparent)
                        .clickable { vm.player.skipTo(index) }
                        .padding(start = Passo.p5, end = Passo.p1, top = Passo.p2, bottom = Passo.p2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        String.format(Locale.ROOT, "%02d", index + 1),
                        style = Tipo.dado,
                        color = if (isCurrent) cores.tinta else cores.tintaSuave,
                        modifier = Modifier.width(Passo.p7),
                    )
                    Artwork(entry.artwork, Modifier.size(42.dp))
                    Spacer(Modifier.width(Passo.p4))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = Tipo.corpoForte, color = cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.artist, style = Tipo.nota, color = cores.tintaSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (isCurrent) {
                        Lampada(if (state.isPlaying) Estado.Aceso else Estado.Repouso, Modifier.padding(horizontal = Passo.p5))
                    } else {
                        BotaoGlifo(Glifo.Fechar, "Tirar da fila", { vm.player.removeAt(index) }, glifoTamanho = 18.dp, color = cores.tintaSuave)
                    }
                }
            }
        }
    }
}
