package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.MenuAction
import io.github.jvcsampaio.sonar.ui.components.NameDialog
import io.github.jvcsampaio.sonar.ui.components.OverflowMenu
import io.github.jvcsampaio.sonar.ui.components.PlayShuffleRow
import io.github.jvcsampaio.sonar.ui.components.TrackRow
import io.github.jvcsampaio.sonar.ui.components.rememberArtworkColor
import io.github.jvcsampaio.sonar.ui.formatTotalDuration
import io.github.jvcsampaio.sonar.ui.songActions
import io.github.jvcsampaio.sonar.ui.songCount
import io.github.jvcsampaio.sonar.ui.theme.Ink

@Composable
fun ArtistScreen(name: String, vm: SonarViewModel, actions: AppActions) {
    val all by vm.tracks.collectAsStateWithLifecycle()
    val tracks = remember(all, name) { all.filter { it.artist == name } }
    CollectionScreen(
        title = name,
        tracks = tracks,
        cover = tracks.firstOrNull()?.artwork,
        coverShape = CircleShape,
        vm = vm,
        actions = actions,
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
        coverShape = MaterialTheme.shapes.medium,
        vm = vm,
        actions = actions,
        headerActions = listOf(
            MenuAction("Renomear", Icons.Rounded.Edit) { renaming = true },
            MenuAction("Apagar playlist", Icons.Rounded.Delete) { deleting = true },
        ),
        rowActions = { track ->
            songActions(
                track, vm, actions,
                extra = listOf(MenuAction("Tirar da playlist", Icons.Rounded.RemoveCircleOutline) { vm.removeFromPlaylist(id, track) }),
            )
        },
        emptyText = "Adicione músicas pelo menu ⋮ de qualquer faixa.",
    )

    if (renaming) {
        NameDialog(
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
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Apagar \"$name\"?") },
            text = { Text("As músicas continuam na biblioteca.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deletePlaylist(id)
                    actions.back()
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun CollectionScreen(
    title: String,
    tracks: List<TrackEntity>,
    cover: Any?,
    coverShape: Shape,
    vm: SonarViewModel,
    actions: AppActions,
    rowActions: (TrackEntity) -> List<MenuAction>,
    headerActions: List<MenuAction> = emptyList(),
    emptyText: String = "Nenhuma música baixada.",
) {
    val player by vm.player.state.collectAsStateWithLifecycle()
    val tint = rememberArtworkColor(cover)
    val totalMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(tint, Ink)))) {
                Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.back) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                        }
                        Spacer(Modifier.weight(1f))
                        if (headerActions.isNotEmpty()) OverflowMenu(headerActions)
                    }
                    Artwork(cover, Modifier.size(200.dp), shape = coverShape, iconSize = 56.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        if (tracks.isEmpty()) songCount(0) else "${songCount(tracks.size)} · ${formatTotalDuration(totalMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
        item {
            PlayShuffleRow(
                onPlay = { vm.play(tracks) },
                onShuffle = { vm.shuffle(tracks) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (tracks.isEmpty()) {
            item {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        itemsIndexed(tracks, key = { _, track -> track.videoId }) { index, track ->
            TrackRow(
                track = track,
                isCurrent = track.videoId == player.current?.mediaId,
                onClick = { vm.play(tracks, index) },
                actions = rowActions(track),
            )
        }
    }
}
