package io.github.jvcsampaio.sonar.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.jvcsampaio.sonar.playback.PlayerState
import io.github.jvcsampaio.sonar.ui.components.AddToPlaylistSheet
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.screens.ArtistScreen
import io.github.jvcsampaio.sonar.ui.screens.HomeScreen
import io.github.jvcsampaio.sonar.ui.screens.LibraryScreen
import io.github.jvcsampaio.sonar.ui.screens.PlayerScreen
import io.github.jvcsampaio.sonar.ui.screens.PlaylistScreen
import io.github.jvcsampaio.sonar.ui.screens.SettingsScreen
import io.github.jvcsampaio.sonar.ui.screens.SyncScreen
import io.github.jvcsampaio.sonar.ui.components.Hairline
import io.github.jvcsampaio.sonar.ui.components.ProgressLine
import io.github.jvcsampaio.sonar.ui.theme.Meridiano
import kotlinx.coroutines.delay

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Início", Icons.Rounded.Home),
    Library("library", "Biblioteca", Icons.Rounded.LibraryMusic),
    Sync("sync", "Sincronizar", Icons.Rounded.Sync),
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<TrackEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTab = Tab.entries.any { it.route == route }

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

    Box(Modifier.fillMaxSize().background(Meridiano.Graphite)) {
        Scaffold(
            containerColor = Meridiano.Graphite,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = MaterialTheme.shapes.small,
                        containerColor = Meridiano.GraphiteHighest,
                        contentColor = Meridiano.Ivory,
                    )
                }
            },
            bottomBar = {
                Column(Modifier.background(Meridiano.Graphite)) {
                    if (player.current != null) {
                        MiniPlayer(player, vm, onOpen = { expanded = true })
                    }
                    if (onTab) {
                        BottomBar(route) { nav.navigateToTab(it) }
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
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) },
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
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
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

@Composable
private fun BottomBar(route: String?, onSelect: (Tab) -> Unit) {
    Column {
        Hairline(Modifier.fillMaxWidth())
        NavigationBar(containerColor = Meridiano.Graphite, tonalElevation = 0.dp) {
            Tab.entries.forEach { tab ->
                val selected = route == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label.uppercase(), style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Meridiano.Bronze,
                        selectedTextColor = Meridiano.Bronze,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Meridiano.IvoryMuted,
                        unselectedTextColor = Meridiano.IvoryMuted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(state: PlayerState, vm: SonarViewModel, onOpen: () -> Unit) {
    val current = state.current ?: return
    var position by remember { mutableLongStateOf(vm.player.positionMs) }
    LaunchedEffect(current.mediaId, state.isPlaying) {
        while (true) {
            position = vm.player.positionMs
            delay(if (state.isPlaying) 500L else 2000L)
        }
    }
    val fraction = if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        onClick = onOpen,
        color = Meridiano.GraphiteRaised,
        contentColor = Meridiano.Ivory,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, Meridiano.Line),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            ProgressLine(fraction)
            Row(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(current.artwork, Modifier.size(42.dp), shape = MaterialTheme.shapes.extraSmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        current.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Meridiano.IvoryMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { vm.player.togglePlay() }) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Tocar",
                    )
                }
                IconButton(onClick = { vm.player.next() }, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima")
                }
            }
        }
    }
}
