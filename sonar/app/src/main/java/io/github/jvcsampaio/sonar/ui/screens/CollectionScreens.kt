package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.Botao
import io.github.jvcsampaio.sonar.ui.components.BotaoGlifo
import io.github.jvcsampaio.sonar.ui.components.Dialogo
import io.github.jvcsampaio.sonar.ui.components.Fio
import io.github.jvcsampaio.sonar.ui.components.Glifo
import io.github.jvcsampaio.sonar.ui.components.MenuAction
import io.github.jvcsampaio.sonar.ui.components.MenuMais
import io.github.jvcsampaio.sonar.ui.components.Rotulo
import io.github.jvcsampaio.sonar.ui.components.TextInputDialog
import io.github.jvcsampaio.sonar.ui.components.TipoBotao
import io.github.jvcsampaio.sonar.ui.components.TrackRow
import io.github.jvcsampaio.sonar.ui.formatTotalDuration
import io.github.jvcsampaio.sonar.ui.songActions
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo

@Composable
fun ArtistScreen(name: String, vm: SonarViewModel, actions: AppActions) {
    val all by vm.tracks.collectAsStateWithLifecycle()
    val tracks = remember(all, name) { all.filter { it.artist == name } }
    CollectionScreen(
        title = name,
        tracks = tracks,
        cover = tracks.firstOrNull()?.artwork,
        vm = vm,
        actions = actions,
        kind = "Artista",
        rowActions = { songActions(it, vm, actions, showArtist = false) },
    )
}

@Composable
fun PlaylistScreen(id: Long, vm: SonarViewModel, actions: AppActions) {
    val playlist by remember(id) { vm.playlist(id) }.collectAsStateWithLifecycle(null)
    val tracks by remember(id) { vm.playlistTracks(id) }.collectAsStateWithLifecycle(emptyList())
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val name = playlist?.name ?: ""

    CollectionScreen(
        title = name,
        tracks = tracks,
        cover = tracks.firstOrNull()?.artwork,
        vm = vm,
        actions = actions,
        kind = "Playlist",
        headerActions = listOf(
            MenuAction("Renomear") { renaming = true },
            MenuAction("Apagar playlist") { deleting = true },
        ),
        rowActions = { track ->
            songActions(track, vm, actions, extra = listOf(MenuAction("Tirar da playlist") { vm.removeFromPlaylist(id, track) }))
        },
        emptyText = "Adicione músicas pelo menu de qualquer faixa.",
    )

    if (renaming) {
        TextInputDialog(
            title = "Renomear playlist",
            confirmLabel = "Salvar",
            initial = name,
            onConfirm = {
                renaming = false
                vm.renamePlaylist(id, it)
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        Dialogo(
            titulo = "Apagar playlist",
            onDismiss = { deleting = false },
            confirmar = "Apagar",
            onConfirm = {
                deleting = false
                vm.deletePlaylist(id)
                actions.back()
            },
        ) {
            Text("\"$name\" deixa de existir. As músicas continuam na biblioteca.", style = Tipo.corpo, color = M.cores.tinta)
        }
    }
}

/** Artist or playlist: the cover on the axis, the name in Capitalis, readings, then the songs. */
@Composable
private fun CollectionScreen(
    title: String,
    tracks: List<TrackEntity>,
    cover: Any?,
    vm: SonarViewModel,
    actions: AppActions,
    rowActions: (TrackEntity) -> List<MenuAction>,
    kind: String,
    headerActions: List<MenuAction> = emptyList(),
    emptyText: String = "Nenhuma música no aparelho.",
) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val totalMs = remember(tracks) { tracks.sumOf { it.durationMs } }
    val cores = M.cores

    LazyColumn(contentPadding = PaddingValues(bottom = Passo.p6)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Passo.p1, vertical = Passo.p1), verticalAlignment = Alignment.CenterVertically) {
                BotaoGlifo(Glifo.Voltar, "Voltar", actions.back)
                Spacer(Modifier.weight(1f))
                if (headerActions.isNotEmpty()) MenuMais(headerActions)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = Passo.p5), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(cover, Modifier.size(208.dp), corte = Chanfro.g)
                Spacer(Modifier.height(Passo.p6))
                Rotulo(kind, style = Tipo.cota)
                Spacer(Modifier.height(Passo.p1))
                Text(title, style = Tipo.titulus2, color = cores.tinta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Passo.p2))
                Row(horizontalArrangement = Arrangement.spacedBy(Passo.p4), verticalAlignment = Alignment.CenterVertically) {
                    Text("${tracks.size} ${if (tracks.size == 1) "música" else "músicas"}", style = Tipo.dado, color = cores.tintaSuave)
                    if (totalMs > 0) Text(formatTotalDuration(totalMs), style = Tipo.dado, color = cores.tintaSuave)
                }
            }
        }
        item {
            Row(
                Modifier.padding(start = Passo.p5, end = Passo.p5, top = Passo.p6, bottom = Passo.p4),
                horizontalArrangement = Arrangement.spacedBy(Passo.p3),
            ) {
                Botao("Tocar", { vm.play(tracks) }, Modifier.weight(1f), tipo = TipoBotao.Principal, enabled = tracks.isNotEmpty())
                Botao("Aleatório", { vm.shuffle(tracks) }, Modifier.weight(1f), enabled = tracks.isNotEmpty())
            }
            Fio(Modifier.fillMaxWidth())
        }
        if (tracks.isEmpty()) {
            item {
                Text(emptyText, style = Tipo.corpo, color = cores.tintaSuave, modifier = Modifier.padding(Passo.p5))
            }
        }
        itemsIndexed(tracks, key = { _, track -> track.videoId }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.videoId == player.current?.mediaId,
                playing = player.isPlaying,
                onClick = { vm.play(tracks, index) },
                actions = rowActions(track),
            )
        }
    }
}
