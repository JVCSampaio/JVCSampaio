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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.EmptyState
import io.github.jvcsampaio.sonar.ui.components.PlayShuffleRow
import io.github.jvcsampaio.sonar.ui.components.SectionHeader
import io.github.jvcsampaio.sonar.ui.groupByArtist
import io.github.jvcsampaio.sonar.ui.songCount
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

@Composable
fun HomeScreen(vm: SonarViewModel, actions: AppActions) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
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

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (tracks.isEmpty()) "Sua música, sem anúncios e offline" else "${songCount(tracks.size)} no aparelho",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions.openSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                when {
                    !settings.connected -> EmptyState(
                        icon = Icons.Rounded.CloudDownload,
                        title = "Traga suas curtidas",
                        body = "Conecte sua conta do YouTube. O Sonar encontra os vídeos que você curtiu e baixa as músicas em áudio.",
                        actionLabel = "Começar",
                        onAction = actions.openSync,
                    )
                    else -> EmptyState(
                        icon = Icons.Rounded.CloudDownload,
                        title = if (queued > 0) "Baixando suas músicas" else "Procurando suas curtidas",
                        body = progress?.let { "${it.position} de ${it.total}: ${it.title}" }
                            ?: if (queued > 0) "${songCount(queued)} na fila. Elas aparecem aqui assim que terminam."
                            else "Isso leva alguns segundos.",
                        actionLabel = "Ver progresso",
                        onAction = actions.openSync,
                    )
                }
            }
            return@LazyColumn
        }

        item { PlayShuffleRow(onPlay = { vm.play(tracks) }, onShuffle = { vm.shuffle(tracks) }) }

        progress?.let { p ->
            item {
                Surface(
                    onClick = actions.openSync,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Baixando ${p.position} de ${p.total} · ${p.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Curtidas recentes")
            CoverRow(recent, player.current?.mediaId) { index -> vm.play(recent, index) }
        }

        if (mostPlayed.isNotEmpty()) {
            item {
                SectionHeader("Mais tocadas")
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mostPlayed.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { track ->
                                QuickTile(track, Modifier.weight(1f)) { vm.play(mostPlayed, mostPlayed.indexOf(track)) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (artists.size >= 2) {
            item {
                SectionHeader("Seus artistas")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artists, key = { it.name }) { artist ->
                        Column(
                            Modifier.width(104.dp).clickable { actions.openArtist(artist.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Artwork(artist.tracks.first().artwork, Modifier.size(104.dp), shape = CircleShape, iconSize = 32.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (rediscover.size >= 4) {
            item {
                SectionHeader("Redescubra")
                CoverRow(rediscover, player.current?.mediaId) { index -> vm.play(rediscover, index) }
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                SectionHeader("Suas playlists")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Column(Modifier.width(148.dp).clickable { actions.openPlaylist(playlist.id) }) {
                            Artwork(playlist.artwork, Modifier.size(148.dp), shape = MaterialTheme.shapes.medium, iconSize = 40.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                songCount(playlist.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverRow(tracks: List<TrackEntity>, currentId: String?, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(tracks, key = { _, track -> track.videoId }) { index, track ->
            Column(Modifier.width(148.dp).clickable { onPlay(index) }) {
                Artwork(track.artwork, Modifier.size(148.dp), shape = MaterialTheme.shapes.medium, iconSize = 40.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (track.videoId == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickTile(track: TrackEntity, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artwork, Modifier.size(56.dp), shape = MaterialTheme.shapes.small)
            Spacer(Modifier.width(10.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}
