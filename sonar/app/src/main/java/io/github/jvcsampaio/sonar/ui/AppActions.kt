package io.github.jvcsampaio.sonar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Person
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.ui.components.MenuAction

/** Navigation and app-wide actions handed to every screen. */
class AppActions(
    val openArtist: (String) -> Unit,
    val openPlaylist: (Long) -> Unit,
    val openSettings: () -> Unit,
    val openSync: () -> Unit,
    val back: () -> Unit,
    val addToPlaylist: (TrackEntity) -> Unit,
)

/** The overflow menu of a downloaded song. */
fun songActions(
    track: TrackEntity,
    vm: SonarViewModel,
    actions: AppActions,
    showArtist: Boolean = true,
    extra: List<MenuAction> = emptyList(),
): List<MenuAction> = buildList {
    add(MenuAction("Tocar em seguida", Icons.AutoMirrored.Rounded.PlaylistPlay) { vm.playNext(track) })
    add(MenuAction("Adicionar à fila", Icons.AutoMirrored.Rounded.QueueMusic) { vm.addToQueue(track) })
    add(MenuAction("Adicionar à playlist", Icons.AutoMirrored.Rounded.PlaylistAdd) { actions.addToPlaylist(track) })
    if (showArtist) add(MenuAction("Ver artista", Icons.Rounded.Person) { actions.openArtist(track.artist) })
    addAll(extra)
    add(MenuAction("Remover do aparelho", Icons.Rounded.Delete) { vm.removeFromDevice(track) })
}

data class ArtistSummary(val name: String, val tracks: List<TrackEntity>)

fun List<TrackEntity>.groupByArtist(): List<ArtistSummary> =
    groupBy { it.artist }
        .map { (name, tracks) -> ArtistSummary(name, tracks) }
        .sortedWith(compareByDescending<ArtistSummary> { it.tracks.size }.thenBy { it.name.lowercase() })
