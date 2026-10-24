package io.github.jvcsampaio.sonar.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.playback.PlayerState
import io.github.jvcsampaio.sonar.ui.components.AddToPlaylistSheet
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.BotaoGlifo
import io.github.jvcsampaio.sonar.ui.components.Estado
import io.github.jvcsampaio.sonar.ui.components.Fio
import io.github.jvcsampaio.sonar.ui.components.Glifo
import io.github.jvcsampaio.sonar.ui.components.Lampada
import io.github.jvcsampaio.sonar.ui.components.ReguaFina
import io.github.jvcsampaio.sonar.ui.components.Rotulo
import io.github.jvcsampaio.sonar.ui.components.peca
import io.github.jvcsampaio.sonar.ui.screens.ArtistScreen
import io.github.jvcsampaio.sonar.ui.screens.HomeScreen
import io.github.jvcsampaio.sonar.ui.screens.LibraryScreen
import io.github.jvcsampaio.sonar.ui.screens.PlayerScreen
import io.github.jvcsampaio.sonar.ui.screens.PlaylistScreen
import io.github.jvcsampaio.sonar.ui.screens.SettingsScreen
import io.github.jvcsampaio.sonar.ui.screens.SyncScreen
import io.github.jvcsampaio.sonar.ui.screens.estadoArs
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Movimento
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import io.github.jvcsampaio.sonar.ui.theme.Traco
import kotlinx.coroutines.delay

/** The three rooms of the building: the entrance, the library and the workshop. */
private enum class Tab(val route: String, val label: String) {
    Home("home", "Início"),
    Library("library", "Biblioteca"),
    Sync("sync", "Oficina"),
}

@Composable
fun SonarRoot(
    vm: SonarViewModel,
    openPlayer: Boolean,
    onPlayerOpened: () -> Unit,
    sharedText: String?,
    onSharedHandled: () -> Unit,
) {
    val nav = rememberNavController()
    val player by vm.player.state.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val waiting by vm.downloadsWaiting.collectAsStateWithLifecycle()
    val syncing by vm.syncRunning.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<TrackEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTab = Tab.entries.any { it.route == route }
    val cores = M.cores

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            expanded = true
            onPlayerOpened()
        }
    }

    // Wait for the nav graph (route != null) before navigating on a cold start from "Share".
    LaunchedEffect(sharedText, route != null) {
        if (sharedText != null && route != null) {
            vm.addFromLink(sharedText)
            nav.navigateToTab(Tab.Sync)
            onSharedHandled()
        }
    }

    val actions = remember(nav) {
        AppActions(
            openArtist = { name -> nav.navigate("artist/${Uri.encode(name)}") },
            openPlaylist = { id -> nav.navigate("playlist/$id") },
            openSettings = { nav.navigate("settings") { launchSingleTop = true } },
            openSync = { nav.navigateToTab(Tab.Sync) },
            back = { nav.popBackStack() },
            addToPlaylist = { playlistTarget = it },
        )
    }

    // The workshop tab carries a lamp while something operates there.
    val queued = (counts[TrackStatus.QUEUED] ?: 0) + (counts[TrackStatus.DOWNLOADING] ?: 0)
    val oficina = when {
        settings.connected && settings.lastSyncError != null -> Estado.Critico
        else -> estadoArs(progress, waiting, queued, syncing, settings)?.first
    }

    Box(Modifier.fillMaxSize().background(cores.fundo)) {
        Scaffold(
            containerColor = cores.fundo,
            contentColor = cores.tinta,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Text(
                        data.visuals.message,
                        style = Tipo.nota,
                        color = cores.tinta,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Passo.p5, vertical = Passo.p3)
                            .peca(cores.relevo, cores.linhaForte, Chanfro.p, Traco.fio)
                            .padding(horizontal = Passo.p5, vertical = Passo.p4),
                    )
                }
            },
            bottomBar = {
                Column(Modifier.background(cores.fundo)) {
                    if (player.current != null) {
                        MiniPlayer(player, vm, onOpen = { expanded = true })
                    }
                    if (onTab) {
                        BottomBar(route, oficina) { nav.navigateToTab(it) }
                    } else {
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Tab.Home.route,
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(Movimento.mecanico(Movimento.D2)) },
                exitTransition = { fadeOut(Movimento.mecanico(Movimento.D2)) },
            ) {
                composable(Tab.Home.route) { HomeScreen(vm, actions) }
                composable(Tab.Library.route) { LibraryScreen(vm, actions) }
                composable(Tab.Sync.route) { SyncScreen(vm, actions) }
                composable("settings") { SettingsScreen(vm, actions) }
                composable("artist/{name}") { entry ->
                    ArtistScreen(entry.arguments?.getString("name").orEmpty(), vm, actions)
                }
                composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    PlaylistScreen(entry.arguments?.getLong("id") ?: 0L, vm, actions)
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && player.current != null,
            enter = slideInVertically(Movimento.mecanico(Movimento.D4)) { it } + fadeIn(Movimento.mecanico(Movimento.D3)),
            exit = slideOutVertically(Movimento.mecanico(Movimento.D3)) { it } + fadeOut(Movimento.mecanico(Movimento.D2)),
        ) {
            PlayerScreen(
                vm = vm,
                onCollapse = { expanded = false },
                onAddToPlaylist = { mediaId -> playlistTarget = tracks.firstOrNull { it.videoId == mediaId } },
            )
        }

        playlistTarget?.let { track ->
            AddToPlaylistSheet(
                track = track,
                playlists = playlists,
                onPick = {
                    vm.addToPlaylist(it, track)
                    playlistTarget = null
                },
                onCreate = {
                    vm.createPlaylist(it, track)
                    playlistTarget = null
                },
                onDismiss = { playlistTarget = null },
            )
        }
    }
}

private fun NavHostController.navigateToTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The bottom bar reads like a scale: a hairline horizon with the three rooms on it, and the
 * bronze transit stroke standing on the one you are in.
 */
@Composable
private fun BottomBar(route: String?, oficina: Estado?, onSelect: (Tab) -> Unit) {
    val cores = M.cores
    Column(Modifier.navigationBarsPadding()) {
        Fio(Modifier.fillMaxWidth(), cores.linhaForte)
        Row(Modifier.fillMaxWidth().height(56.dp)) {
            Tab.entries.forEach { tab ->
                val selected = route == tab.route
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .semantics { this.selected = selected }
                        .clickable(role = Role.Tab) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Canvas(Modifier.align(Alignment.TopCenter).size(Traco.eixo, Passo.p5)) {
                            drawRect(cores.insignia, Offset.Zero, Size(size.width, size.height))
                        }
                    }
                    Row(
                        Modifier.padding(top = Passo.p3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Passo.p2),
                    ) {
                        if (tab == Tab.Sync && oficina != null) Lampada(oficina, tamanho = 9.dp)
                        Rotulo(tab.label, color = if (selected) cores.tinta else cores.tintaSuave)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(state: PlayerState, vm: SonarViewModel, onOpen: () -> Unit) {
    val current = state.current ?: return
    val cores = M.cores
    var position by remember { mutableLongStateOf(vm.player.positionMs) }
    LaunchedEffect(current.mediaId, state.isPlaying) {
        while (true) {
            position = vm.player.positionMs
            delay(if (state.isPlaying) 500L else 2000L)
        }
    }
    val fraction = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Passo.p3, vertical = Passo.p2)
            .peca(cores.superficie, cores.linhaForte, Chanfro.p, Traco.fio)
            .clickable(onClick = onOpen)
            .padding(Traco.fio),
    ) {
        Row(Modifier.padding(start = Passo.p3, top = Passo.p3, bottom = Passo.p3), verticalAlignment = Alignment.CenterVertically) {
            Artwork(current.artwork, Modifier.size(42.dp))
            Spacer(Modifier.width(Passo.p4))
            Column(Modifier.weight(1f)) {
                Text(current.title, style = Tipo.corpoForte, color = cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current.artist, style = Tipo.nota, color = cores.tintaSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BotaoGlifo(
                if (state.isPlaying) Glifo.Pausar else Glifo.Tocar,
                if (state.isPlaying) "Pausar" else "Tocar",
                { vm.player.togglePlay() },
            )
            BotaoGlifo(Glifo.Proxima, "Próxima", { vm.player.next() }, enabled = state.hasNext)
        }
        ReguaFina(fraction, Modifier.padding(horizontal = Passo.p3))
    }
}
