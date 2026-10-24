package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import io.github.jvcsampaio.sonar.ui.components.Emblem
import io.github.jvcsampaio.sonar.ui.components.EmptyState
import io.github.jvcsampaio.sonar.ui.components.MonoLabel
import io.github.jvcsampaio.sonar.ui.components.Motto
import io.github.jvcsampaio.sonar.ui.components.PlayShuffleRow
import io.github.jvcsampaio.sonar.ui.components.ProgressLine
import io.github.jvcsampaio.sonar.ui.components.SectionHeader
import io.github.jvcsampaio.sonar.ui.components.StatusDot
import io.github.jvcsampaio.sonar.ui.formatAgo
import io.github.jvcsampaio.sonar.ui.formatBytes
import io.github.jvcsampaio.sonar.ui.groupByArtist
import io.github.jvcsampaio.sonar.ui.songCount
import io.github.jvcsampaio.sonar.ui.theme.Meridiano
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

    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Emblem(Modifier.size(26.dp), pulse = progress != null)
                    Spacer(Modifier.width(10.dp))
                    MonoLabel("Meridiano · Sonar", color = Meridiano.Bronze, modifier = Modifier.weight(1f))
                    IconButton(onClick = actions.openSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(greeting(), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                MonoLabel(
                    if (tracks.isEmpty()) "Acervo vazio"
                    else listOfNotNull(
                        songCount(tracks.size),
                        formatBytes(bytes).takeIf { bytes > 0 },
                        settings.lastSyncAt.takeIf { it > 0 }?.let { "sincronizado ${formatAgo(it)}" },
                    ).joinToString(" · "),
                )
            }
        }

        if (tracks.isEmpty()) {
            item {
                if (!settings.connected) {
                    EmptyState(
                        title = "Traga suas curtidas",
                        body = "Conecte sua conta do YouTube. O Sonar encontra os vídeos que você curtiu e guarda as músicas no aparelho.",
                        actionLabel = "Começar",
                        onAction = actions.openSync,
                    )
                } else {
                    EmptyState(
                        title = if (queued > 0) "Construindo o acervo" else "Lendo suas curtidas",
                        body = progress?.let { "${it.position} de ${it.total}: ${it.title}" }
                            ?: if (queued > 0) "${songCount(queued)} na fila. Elas aparecem aqui assim que terminam."
                            else "Isso leva alguns segundos.",
                        actionLabel = "Ver operação",
                        onAction = actions.openSync,
                        pulse = true,
                    )
                }
            }
            item { Motto() }
            return@LazyColumn
        }

        item { PlayShuffleRow(onPlay = { vm.play(tracks) }, onShuffle = { vm.shuffle(tracks) }) }

        progress?.let { p ->
            item {
                Surface(
                    onClick = actions.openSync,
                    color = Meridiano.GraphiteRaised,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, Meridiano.Line),
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp),
                ) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(Meridiano.Mineral, pulse = true)
                            Spacer(Modifier.width(10.dp))
                            MonoLabel("Ars · extraindo ${p.position}/${p.total}", color = Meridiano.Mineral)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        ProgressLine(p.fraction)
                    }
                }
            }
        }

        // Section numbers are assigned while building the list, not inside the item lambdas.
        var section = 0
        val recentNumber = ++section
        item {
            SectionHeader("Curtidas recentes", index = recentNumber)
            CoverRow(recent, player.current?.mediaId) { index -> vm.play(recent, index) }
        }

        if (mostPlayed.isNotEmpty()) {
            val number = ++section
            item {
                SectionHeader("Mais tocadas", index = number)
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mostPlayed.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { track ->
                                QuickTile(track, track.videoId == player.current?.mediaId, Modifier.weight(1f)) {
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
            val number = ++section
            item {
                SectionHeader("Artistas", index = number)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(artists, key = { it.name }) { artist ->
                        Column(
                            Modifier.width(96.dp).clickable { actions.openArtist(artist.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Artwork(artist.tracks.first().artwork, Modifier.size(96.dp), shape = CircleShape, iconSize = 30.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(artist.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MonoLabel(songCount(artist.tracks.size), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (rediscover.size >= 4) {
            val number = ++section
            item {
                SectionHeader("Redescubra", index = number)
                CoverRow(rediscover, player.current?.mediaId) { index -> vm.play(rediscover, index) }
            }
        }

        if (playlists.isNotEmpty()) {
            val number = ++section
            item {
                SectionHeader("Playlists", index = number)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Column(Modifier.width(140.dp).clickable { actions.openPlaylist(playlist.id) }) {
                            Artwork(playlist.artwork, Modifier.size(140.dp), shape = MaterialTheme.shapes.medium, iconSize = 40.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            MonoLabel(songCount(playlist.trackCount), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item { Motto() }
    }
}

@Composable
private fun CoverRow(tracks: List<TrackEntity>, currentId: String?, onPlay: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(tracks, key = { _, track -> track.videoId }) { index, track ->
            val current = track.videoId == currentId
            Column(Modifier.width(140.dp).clickable { onPlay(index) }) {
                Artwork(track.artwork, Modifier.size(140.dp), shape = MaterialTheme.shapes.medium, iconSize = 40.dp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current) {
                        StatusDot(Meridiano.Mineral, pulse = true)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (current) Meridiano.Mineral else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
private fun QuickTile(track: TrackEntity, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Meridiano.GraphiteRaised,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (current) Meridiano.Mineral else Meridiano.Line),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artwork, Modifier.size(54.dp), shape = MaterialTheme.shapes.extraSmall)
            Spacer(Modifier.width(10.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.bodySmall,
                color = if (current) Meridiano.Mineral else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Bom dia."
    in 12..17 -> "Boa tarde."
    else -> "Boa noite."
}
