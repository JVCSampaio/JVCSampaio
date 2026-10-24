package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.domain.MetadataCleaner
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.Botao
import io.github.jvcsampaio.sonar.ui.components.BotaoGlifo
import io.github.jvcsampaio.sonar.ui.components.Cabecalho
import io.github.jvcsampaio.sonar.ui.components.CampoTexto
import io.github.jvcsampaio.sonar.ui.components.ChanfroShape
import io.github.jvcsampaio.sonar.ui.components.Escolhas
import io.github.jvcsampaio.sonar.ui.components.Glifo
import io.github.jvcsampaio.sonar.ui.components.Rotulo
import io.github.jvcsampaio.sonar.ui.components.TextInputDialog
import io.github.jvcsampaio.sonar.ui.components.TipoBotao
import io.github.jvcsampaio.sonar.ui.components.TrackRow
import io.github.jvcsampaio.sonar.ui.components.Vazio
import io.github.jvcsampaio.sonar.ui.formatTotalDuration
import io.github.jvcsampaio.sonar.ui.groupByArtist
import io.github.jvcsampaio.sonar.ui.songActions
import io.github.jvcsampaio.sonar.ui.songCount
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import io.github.jvcsampaio.sonar.ui.theme.Traco

private enum class LibraryTab(val label: String) { Songs("Músicas"), Artists("Artistas"), Playlists("Playlists") }

private enum class SongSort(val label: String) { Recent("Curtidas recentes"), Title("Título"), Artist("Artista"), Plays("Mais tocadas") }

@Composable
fun LibraryScreen(vm: SonarViewModel, actions: AppActions) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var sort by rememberSaveable { mutableStateOf(SongSort.Recent) }
    var query by rememberSaveable { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    val filtered = remember(tracks, query) { tracks.filterByQuery(query) }
    val songs = remember(filtered, sort) {
        when (sort) {
            SongSort.Recent -> filtered
            SongSort.Title -> filtered.sortedBy { it.title.lowercase() }
            SongSort.Artist -> filtered.sortedWith(compareBy<TrackEntity> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            SongSort.Plays -> filtered.sortedByDescending { it.playCount }
        }
    }
    val artists = remember(filtered) { filtered.groupByArtist() }
    val shownPlaylists = remember(playlists, query) {
        val key = MetadataCleaner.key(query)
        playlists.filter { key.isEmpty() || MetadataCleaner.key(it.name).contains(key) }
    }
    val cores = M.cores

    LazyColumn(contentPadding = PaddingValues(bottom = Passo.p6)) {
        item {
            Cabecalho(
                "Bibliotheca",
                finalidade = "Guardar as músicas no aparelho e encontrá-las depressa.",
                local = "Sonar · acervo",
            )
        }
        item {
            CampoTexto(
                valor = query,
                onValueChange = { query = it },
                rotulo = "Busca",
                placeholder = "Música, artista ou playlist",
                modifier = Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3),
                fim = {
                    if (query.isNotEmpty()) {
                        BotaoGlifo(Glifo.Fechar, "Limpar busca", { query = "" }, tamanho = 40.dp, glifoTamanho = 18.dp, color = cores.tintaSuave)
                    }
                },
            )
        }
        item {
            Escolhas(
                opcoes = LibraryTab.entries,
                selecionada = tab,
                rotulo = { it.label },
                contagem = {
                    when (it) {
                        LibraryTab.Songs -> filtered.size
                        LibraryTab.Artists -> artists.size
                        LibraryTab.Playlists -> shownPlaylists.size
                    }
                },
                modifier = Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3),
            ) { tab = it }
        }

        when (tab) {
            LibraryTab.Songs -> {
                if (tracks.isEmpty()) {
                    item {
                        Vazio(
                            titulo = "Acervo vazio",
                            texto = "As músicas aparecem aqui assim que a extração termina.",
                            acao = "Ver oficina",
                            onAcao = actions.openSync,
                        )
                    }
                } else {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = Passo.p2, end = Passo.p5, top = Passo.p3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SortMenu(sort) { sort = it }
                            Spacer(Modifier.weight(1f))
                            Text(formatTotalDuration(songs.sumOf { it.durationMs }), style = Tipo.dado, color = cores.tintaSuave)
                        }
                    }
                    item {
                        Row(
                            Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3),
                            horizontalArrangement = Arrangement.spacedBy(Passo.p3),
                        ) {
                            Botao("Tocar", { vm.play(songs) }, Modifier.weight(1f), tipo = TipoBotao.Principal, enabled = songs.isNotEmpty())
                            Botao("Aleatório", { vm.shuffle(songs) }, Modifier.weight(1f), enabled = songs.isNotEmpty())
                        }
                    }
                    itemsIndexed(songs, key = { _, track -> track.videoId }) { index, track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.videoId == player.current?.mediaId,
                            playing = player.isPlaying,
                            onClick = { vm.play(songs, index) },
                            actions = songActions(track, vm, actions),
                        )
                    }
                }
            }

            LibraryTab.Artists -> items(artists, key = { it.name }) { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.openArtist(artist.name) }
                        .padding(horizontal = Passo.p5, vertical = Passo.p3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Passo.p5),
                ) {
                    Artwork(artist.tracks.first().artwork, Modifier.size(56.dp), corte = Chanfro.m)
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, style = Tipo.corpoForte, color = cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(songCount(artist.tracks.size), style = Tipo.nota, color = cores.tintaSuave)
                    }
                }
            }

            LibraryTab.Playlists -> {
                item {
                    Botao(
                        "Criar playlist",
                        { creating = true },
                        Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3),
                    )
                }
                items(shownPlaylists, key = { it.id }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clickable { actions.openPlaylist(playlist.id) }
                            .padding(horizontal = Passo.p5, vertical = Passo.p3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Passo.p5),
                    ) {
                        Artwork(playlist.artwork, Modifier.size(56.dp), corte = Chanfro.m)
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = Tipo.corpoForte, color = cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(songCount(playlist.trackCount), style = Tipo.nota, color = cores.tintaSuave)
                        }
                    }
                }
                if (shownPlaylists.isEmpty()) {
                    item {
                        Text(
                            "Crie uma playlist aqui ou pelo menu de qualquer música.",
                            style = Tipo.corpo,
                            color = cores.tintaSuave,
                            modifier = Modifier.padding(horizontal = Passo.p5, vertical = Passo.p4),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Passo.p3)) }
    }

    if (creating) {
        TextInputDialog(
            title = "Nova playlist",
            confirmLabel = "Criar",
            onConfirm = { name ->
                creating = false
                vm.createPlaylist(name)
            },
            onDismiss = { creating = false },
        )
    }
}

/** "ORDEM: CURTIDAS RECENTES", underlined, opening the list of orders. */
@Composable
private fun SortMenu(current: SongSort, onSelect: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cores = M.cores
    Box {
        Botao("Ordem: ${current.label}", { open = true }, tipo = TipoBotao.Discreto)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = ChanfroShape(Chanfro.p),
            containerColor = cores.relevo,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(Traco.fio, cores.linhaForte),
        ) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Rotulo(option.label, color = if (option == current) cores.tinta else cores.tintaSuave) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Every word of the query must appear in the title, artist or album, ignoring accents. */
private fun List<TrackEntity>.filterByQuery(query: String): List<TrackEntity> {
    val words = query.split(' ').map(MetadataCleaner::key).filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    return filter { track ->
        val haystack = MetadataCleaner.key("${track.title} ${track.artist} ${track.album.orEmpty()}")
        words.all { it in haystack }
    }
}
