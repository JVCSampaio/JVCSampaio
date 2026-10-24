package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.Settings
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.sync.DownloadProgress
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.Assinatura
import io.github.jvcsampaio.sonar.ui.components.Botao
import io.github.jvcsampaio.sonar.ui.components.Capa
import io.github.jvcsampaio.sonar.ui.components.Colofao
import io.github.jvcsampaio.sonar.ui.components.Estado
import io.github.jvcsampaio.sonar.ui.components.Indicador
import io.github.jvcsampaio.sonar.ui.components.Lampada
import io.github.jvcsampaio.sonar.ui.components.Leitura
import io.github.jvcsampaio.sonar.ui.components.Painel
import io.github.jvcsampaio.sonar.ui.components.Regua
import io.github.jvcsampaio.sonar.ui.components.Rotulo
import io.github.jvcsampaio.sonar.ui.components.Secao
import io.github.jvcsampaio.sonar.ui.components.TipoBotao
import io.github.jvcsampaio.sonar.ui.components.Vazio
import io.github.jvcsampaio.sonar.ui.components.peca
import io.github.jvcsampaio.sonar.ui.formatBytes
import io.github.jvcsampaio.sonar.ui.formatData
import io.github.jvcsampaio.sonar.ui.groupByArtist
import io.github.jvcsampaio.sonar.ui.songCount
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import io.github.jvcsampaio.sonar.ui.theme.Traco
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
fun HomeScreen(vm: SonarViewModel, actions: AppActions) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val waiting by vm.downloadsWaiting.collectAsStateWithLifecycle()
    val syncing by vm.syncRunning.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bytes by vm.storageBytes.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()

    val recent = remember(tracks) { tracks.take(15) }
    val mostPlayed = remember(tracks) { tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(6) }
    val artists = remember(tracks) { tracks.groupByArtist().take(12) }
    val rediscover = remember(tracks) {
        // Songs not played for a month, reshuffled once a day.
        val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        tracks.filter { (it.lastPlayedAt ?: 0) < monthAgo }
            .shuffled(Random(LocalDate.now().toEpochDay()))
            .take(12)
    }
    val queued = (counts[TrackStatus.QUEUED] ?: 0) + (counts[TrackStatus.DOWNLOADING] ?: 0)
    val currentId = player.current?.mediaId

    LazyColumn(contentPadding = PaddingValues(bottom = Passo.p5)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = Passo.p5, end = Passo.p2, top = Passo.p6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Assinatura(Modifier.weight(1f))
                Botao("Ajustes", actions.openSettings, tipo = TipoBotao.Discreto)
            }
        }

        if (tracks.isEmpty()) {
            item {
                if (!settings.connected) {
                    Abertura(onStart = actions.openSync)
                } else {
                    Vazio(
                        titulo = if (queued > 0) "Construindo o acervo" else "Lendo as curtidas",
                        texto = if (queued > 0) "${songCount(queued)} na fila. Cada uma aparece aqui quando a extração termina."
                        else "O Sonar lê a lista de curtidas e separa o que é música.",
                        estado = estadoArs(progress, waiting, queued, syncing, settings),
                        acao = "Ver oficina",
                        onAcao = actions.openSync,
                    )
                }
            }
            return@LazyColumn
        }

        item {
            Column(Modifier.padding(horizontal = Passo.p5)) {
                Spacer(Modifier.height(Passo.p7))
                Text(saudacao(), style = Tipo.lead, color = M.cores.tinta)
                Spacer(Modifier.height(Passo.p5))
                Row(horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
                    Leitura(
                        rotulo = "Acervo",
                        valor = tracks.size.toString(),
                        unidade = if (tracks.size == 1) "música" else "músicas",
                        referencia = "${formatBytes(bytes)} no aparelho",
                        modifier = Modifier.weight(1f),
                    )
                    val (valor, unidade) = decorrido(settings.lastSyncAt)
                    Leitura(
                        rotulo = "Última leitura",
                        valor = valor,
                        unidade = unidade,
                        referencia = if (settings.lastSyncAt > 0) "em ${formatData(settings.lastSyncAt)}" else "nenhuma ainda",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Passo.p5))
                Row(horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
                    Botao("Tocar", { vm.play(tracks) }, Modifier.weight(1f), tipo = TipoBotao.Principal)
                    Botao("Aleatório", { vm.shuffle(tracks) }, Modifier.weight(1f))
                }
            }
        }

        val ars = estadoArs(progress, waiting, queued, syncing, settings)
        if (progress != null || (queued > 0 && waiting)) {
            item {
                Painel(
                    Modifier.padding(start = Passo.p5, end = Passo.p5, top = Passo.p6).clickable(onClick = actions.openSync),
                    cabecalho = {
                        Text("Ars", style = Tipo.titulus3, color = M.cores.tinta, modifier = Modifier.weight(1f))
                        if (ars != null) Indicador(ars.first, ars.second)
                    },
                ) {
                    val p = progress
                    if (p != null) {
                        Text(p.title, style = Tipo.corpoForte, color = M.cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.position} / ${p.total}", style = Tipo.dado, color = M.cores.tintaSuave)
                        Spacer(Modifier.height(Passo.p3))
                        Regua(p.fraction, "${(p.fraction * 100).roundToInt()} %", descricao = "Extração de ${p.title}")
                    } else {
                        Text("${songCount(queued)} na fila.", style = Tipo.corpo, color = M.cores.tinta)
                    }
                }
            }
        }

        item {
            Secao("Curtidas recentes", contagem = tracks.size)
            CoverRow(recent, currentId, player.isPlaying) { index -> vm.play(recent, index) }
        }

        if (mostPlayed.isNotEmpty()) {
            item {
                Secao("Mais tocadas")
                Column(Modifier.padding(horizontal = Passo.p5), verticalArrangement = Arrangement.spacedBy(Passo.p3)) {
                    mostPlayed.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
                            pair.forEach { track ->
                                QuickTile(track, track.videoId == currentId, Modifier.weight(1f)) {
                                    vm.play(mostPlayed, mostPlayed.indexOf(track))
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (artists.size >= 2) {
            item {
                Secao("Artistas")
                LazyRow(contentPadding = PaddingValues(horizontal = Passo.p5), horizontalArrangement = Arrangement.spacedBy(Passo.p5)) {
                    items(artists, key = { it.name }) { artist ->
                        Column(Modifier.width(96.dp).clickable { actions.openArtist(artist.name) }) {
                            Artwork(artist.tracks.first().artwork, Modifier.size(96.dp), corte = Chanfro.m)
                            Spacer(Modifier.height(Passo.p3))
                            Text(artist.name, style = Tipo.nota, color = M.cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(artist.tracks.size.toString(), style = Tipo.dado, color = M.cores.tintaSuave)
                        }
                    }
                }
            }
        }

        if (rediscover.size >= 4) {
            item {
                Secao("Redescubra")
                Text(
                    "Músicas que você não ouve há mais de 30 dias. A seleção muda a cada dia.",
                    style = Tipo.nota,
                    color = M.cores.tintaSuave,
                    modifier = Modifier.padding(start = Passo.p5, end = Passo.p5, bottom = Passo.p4),
                )
                CoverRow(rediscover, currentId, player.isPlaying) { index -> vm.play(rediscover, index) }
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                Secao("Playlists", contagem = playlists.size)
                LazyRow(contentPadding = PaddingValues(horizontal = Passo.p5), horizontalArrangement = Arrangement.spacedBy(Passo.p5)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Column(Modifier.width(140.dp).clickable { actions.openPlaylist(playlist.id) }) {
                            Artwork(playlist.artwork, Modifier.size(140.dp), corte = Chanfro.m)
                            Spacer(Modifier.height(Passo.p3))
                            Text(playlist.name, style = Tipo.corpoForte, color = M.cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(songCount(playlist.trackCount), style = Tipo.nota, color = M.cores.tintaSuave)
                        }
                    }
                }
            }
        }

        item { Colofao() }
    }
}

/** First run: the cover, the name, the motto and what the app is for. */
@Composable
private fun Abertura(onStart: () -> Unit) {
    val cores = M.cores
    Column {
        Spacer(Modifier.height(Passo.p7))
        Capa()
        Column(Modifier.padding(horizontal = Passo.p5)) {
            Spacer(Modifier.height(Passo.p6))
            Text("Sonar", style = Tipo.monumentum, color = cores.tinta)
            Text("Forma servit fini: a forma serve à finalidade.", style = Tipo.nota, color = cores.tintaSuave)
            Spacer(Modifier.height(Passo.p6))
            Rotulo("Finalidade", style = Tipo.cota)
            Spacer(Modifier.height(Passo.p1))
            Text(
                "Guardar no aparelho, em áudio, as músicas que você curtiu no YouTube, e tocá-las sem anúncios, mesmo offline.",
                style = Tipo.corpo,
                color = cores.tinta,
            )
            Spacer(Modifier.height(Passo.p6))
            Botao("Começar", onStart, Modifier.fillMaxWidth(), tipo = TipoBotao.Principal)
            Spacer(Modifier.height(Passo.p7))
        }
    }
}

@Composable
private fun CoverRow(tracks: List<TrackEntity>, currentId: String?, playing: Boolean, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = Passo.p5), horizontalArrangement = Arrangement.spacedBy(Passo.p5)) {
        itemsIndexed(tracks, key = { _, track -> track.videoId }) { index, track ->
            val current = track.videoId == currentId
            Column(Modifier.width(140.dp).clickable { onPlay(index) }) {
                Artwork(track.artwork, Modifier.size(140.dp), corte = Chanfro.m)
                Spacer(Modifier.height(Passo.p3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current) {
                        Lampada(if (playing) Estado.Aceso else Estado.Repouso, Modifier.padding(end = Passo.p2), tamanho = 9.dp)
                    }
                    Text(track.title, style = Tipo.corpoForte, color = M.cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(track.artist, style = Tipo.nota, color = M.cores.tintaSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A most-played song: cover, title and how many times it was played. */
@Composable
private fun QuickTile(track: TrackEntity, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cores = M.cores
    Row(
        modifier
            .peca(if (current) cores.relevo else cores.superficie, cores.linhaForte, Chanfro.p, Traco.fio)
            .clickable(onClick = onClick)
            .padding(Traco.fio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artwork, Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(horizontal = Passo.p3)) {
            Text(track.title, style = Tipo.nota, color = cores.tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${track.playCount}×", style = Tipo.dado, color = cores.tintaSuave)
        }
    }
}

/** The state of the extraction stage as lamp and word, or null when it rests. */
internal fun estadoArs(
    progress: DownloadProgress?,
    waiting: Boolean,
    queued: Int,
    syncing: Boolean,
    settings: Settings,
): Pair<Estado, String>? = when {
    progress != null -> Estado.Aceso to "Extraindo ${progress.position}/${progress.total}"
    waiting && queued > 0 -> Estado.Atencao to if (settings.wifiOnly) "Aguardando Wi-Fi" else "Aguardando rede"
    syncing -> Estado.Aceso to "Lendo curtidas"
    queued > 0 -> Estado.Atencao to "Na fila"
    else -> null
}

/** Time since the last reading, as a number and its unit. */
private fun decorrido(time: Long, now: Long = System.currentTimeMillis()): Pair<String, String?> {
    if (time <= 0) return "—" to null
    val minutes = (now - time).coerceAtLeast(0) / 60_000
    return when {
        minutes < 60 -> minutes.toString() to "min"
        minutes < 24 * 60 -> (minutes / 60).toString() to "h"
        else -> (minutes / (24 * 60)).toString() to if (minutes < 48 * 60) "dia" else "dias"
    }
}

private fun saudacao(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Bom dia."
    in 12..17 -> "Boa tarde."
    else -> "Boa noite."
}
