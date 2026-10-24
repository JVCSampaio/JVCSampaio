package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.domain.MetadataCleaner
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.EmptyState
import io.github.jvcsampaio.sonar.ui.components.NameDialog
import io.github.jvcsampaio.sonar.ui.components.PlayShuffleRow
import io.github.jvcsampaio.sonar.ui.components.ScreenHeader
import io.github.jvcsampaio.sonar.ui.components.TrackRow
import io.github.jvcsampaio.sonar.ui.groupByArtist
import io.github.jvcsampaio.sonar.ui.songActions
import io.github.jvcsampaio.sonar.ui.songCount

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

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Biblioteca", subtitle = songCount(tracks.size)) }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar músicas, artistas, playlists") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Limpar") }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryTab.entries.forEach { option ->
                    FilterChip(
                        selected = tab == option,
                        onClick = { tab = option },
                        label = { Text(option.label) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        when (tab) {
            LibraryTab.Songs -> {
                if (tracks.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "Nada por aqui ainda",
                            body = "As músicas aparecem assim que o download termina.",
                            actionLabel = "Ver sincronização",
                            onAction = actions.openSync,
                        )
                    }
                } else {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SortMenu(sort) { sort = it }
                            Spacer(Modifier.weight(1f))
                            Text(songCount(songs.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        PlayShuffleRow(
                            onPlay = { vm.play(songs) },
                            onShuffle = { vm.shuffle(songs) },
                            enabled = songs.isNotEmpty(),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    itemsIndexed(songs, key = { _, track -> track.videoId }) { index, track ->
                        TrackRow(
                            track = track,
                            isCurrent = track.videoId == player.current?.mediaId,
                            onClick = { vm.play(songs, index) },
                            actions = songActions(track, vm, actions),
                        )
                    }
                }
            }

            LibraryTab.Artists -> items(artists, key = { it.name }) { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.openArtist(artist.name) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(artist.tracks.first().artwork, Modifier.size(56.dp), shape = CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(songCount(artist.tracks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            LibraryTab.Playlists -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text("Nova playlist", style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(shownPlaylists, key = { it.id }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clickable { actions.openPlaylist(playlist.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(playlist.artwork, Modifier.size(56.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(songCount(playlist.trackCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (shownPlaylists.isEmpty()) {
                    item {
                        Text(
                            "Crie playlists pelo menu ⋮ de qualquer música ou aqui em cima.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (creating) {
        NameDialog(
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

@Composable
private fun SortMenu(current: SongSort, onSelect: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(current.label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
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
