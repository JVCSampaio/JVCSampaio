package io.github.jvcsampaio.sonar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.jvcsampaio.sonar.playback.PlayerState
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Artwork
import io.github.jvcsampaio.sonar.ui.components.MonoLabel
import io.github.jvcsampaio.sonar.ui.components.StatusDot
import io.github.jvcsampaio.sonar.ui.components.cornerMarks
import io.github.jvcsampaio.sonar.ui.components.rememberArtworkColor
import io.github.jvcsampaio.sonar.ui.formatDuration
import io.github.jvcsampaio.sonar.ui.theme.DataStyle
import io.github.jvcsampaio.sonar.ui.theme.Meridiano
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(vm: SonarViewModel, onCollapse: () -> Unit, onAddToPlaylist: (String) -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val current = state.current ?: return
    val tint = rememberArtworkColor(current.artwork)
    var position by remember { mutableLongStateOf(vm.player.positionMs) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(current.mediaId, state.isPlaying) {
        while (true) {
            position = vm.player.positionMs
            delay(if (state.isPlaying) 250L else 1000L)
        }
    }
    BackHandler(onBack = onCollapse)

    val duration = state.durationMs
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val artScale by animateFloatAsState(if (state.isPlaying) 1f else 0.9f, spring(dampingRatio = 0.75f), label = "artScale")

    // The player sits outside the Scaffold, so it sets its own content color.
    CompositionLocalProvider(LocalContentColor provides Meridiano.Ivory) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffset.roundToInt()) }
                .background(Meridiano.Graphite)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 280f) onCollapse()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                    ) { _, dy -> dragOffset = (dragOffset + dy).coerceAtLeast(0f) }
                },
        ) {
            Blueprint(tint)
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimizar", modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        MonoLabel("Em reprodução", color = Meridiano.Bronze, style = MaterialTheme.typography.labelSmall)
                        if (state.queue.size > 1) {
                            Text(
                                String.format(Locale.ROOT, "%02d / %02d", state.currentIndex + 1, state.queue.size),
                                style = DataStyle,
                                color = Meridiano.IvoryMuted,
                            )
                        }
                    }
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Fila")
                    }
                }

                Spacer(Modifier.weight(1f))
                Artwork(
                    current.artwork,
                    Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = artScale
                            scaleY = artScale
                        }
                        .cornerMarks(color = Meridiano.Bronze, length = 16.dp, gap = 10.dp),
                    shape = MaterialTheme.shapes.small,
                    iconSize = 96.dp,
                )
                Spacer(Modifier.weight(1f))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            current.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Meridiano.IvoryMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onAddToPlaylist(current.mediaId) }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Adicionar à playlist")
                    }
                }

                Spacer(Modifier.height(14.dp))
                SeekBar(
                    fraction = seeking ?: fraction,
                    onSeek = { seeking = it },
                    onSeekFinished = {
                        seeking?.let { vm.player.seekTo((it * duration).toLong()) }
                        seeking = null
                    },
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = seeking?.let { (it * duration).toLong() } ?: position
                    Text(formatDuration(shown), style = DataStyle, color = Meridiano.IvoryMuted)
                    Spacer(Modifier.weight(1f))
                    Text("−" + formatDuration(duration - shown), style = DataStyle, color = Meridiano.IvoryMuted)
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeButton(
                        active = state.shuffle,
                        onClick = { vm.player.toggleShuffle() },
                    ) { Icon(Icons.Rounded.Shuffle, contentDescription = "Aleatório") }
                    IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(34.dp))
                    }
                    FilledIconButton(
                        onClick = { vm.player.togglePlay() },
                        modifier = Modifier.size(74.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Meridiano.Bronze,
                            contentColor = Meridiano.Graphite,
                        ),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Tocar",
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(onClick = { vm.player.next() }, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Próxima", modifier = Modifier.size(34.dp))
                    }
                    ModeButton(
                        active = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = { vm.player.cycleRepeat() },
                    ) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repetir",
                        )
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showQueue) QueueSheet(state, vm, onDismiss = { showQueue = false })
}

/**
 * A drafting-table background: graphite, a faint grid, the meridian axis through the centre
 * and a trace of the artwork's colour at the top.
 */
@Composable
private fun Blueprint(tint: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(0f to tint.copy(alpha = 0.55f), 0.6f to Color.Transparent))
        val step = 28.dp.toPx()
        val grid = Meridiano.Ivory.copy(alpha = 0.035f)
        var x = (size.width / 2) % step
        while (x < size.width) {
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawLine(Meridiano.Bronze.copy(alpha = 0.16f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
    }
}

/** Shuffle and repeat: mineral with a mark underneath when the mode is on. */
@Composable
private fun ModeButton(active: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CompositionLocalProvider(LocalContentColor provides if (active) Meridiano.Mineral else Meridiano.IvoryMuted) {
            IconButton(onClick = onClick) { icon() }
        }
        Box(Modifier.size(4.dp).background(if (active) Meridiano.Mineral else Color.Transparent, CircleShape))
    }
}

/** A thin progress line with a diamond for the playhead: tap or drag to seek. */
@Composable
private fun SeekBar(fraction: Float, onSeek: (Float) -> Unit, onSeekFinished: () -> Unit) {
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val seek by rememberUpdatedState(onSeek)
    val finish by rememberUpdatedState(onSeekFinished)
    val thumb by animateDpAsState(if (dragging) 14.dp else 10.dp, label = "thumb")
    Box(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    seek((offset.x / width).coerceIn(0f, 1f))
                    finish()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        finish()
                    },
                    onDragCancel = {
                        dragging = false
                        finish()
                    },
                ) { change, _ -> seek((change.position.x / width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Meridiano.Ivory.copy(alpha = 0.18f)))
        Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Meridiano.Mineral))
        Box(
            Modifier
                .offset { IntOffset((fraction * width).roundToInt() - thumb.roundToPx() / 2, 0) }
                .size(thumb)
                .rotate(45f)
                .background(Meridiano.Ivory),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerState, vm: SonarViewModel, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Meridiano.GraphiteRaised,
        contentColor = Meridiano.Ivory,
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Fila", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            MonoLabel("${state.queue.size} faixas")
        }
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            itemsIndexed(state.queue) { index, entry ->
                val isCurrent = index == state.currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.player.skipTo(index) }
                        .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        String.format(Locale.ROOT, "%02d", index + 1),
                        style = DataStyle,
                        color = if (isCurrent) Meridiano.Mineral else Meridiano.IvoryMuted,
                        modifier = Modifier.width(28.dp),
                    )
                    Artwork(entry.artwork, Modifier.size(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isCurrent) Meridiano.Mineral else Meridiano.Ivory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(entry.artist, style = MaterialTheme.typography.bodySmall, color = Meridiano.IvoryMuted, maxLines = 1)
                    }
                    if (isCurrent) {
                        StatusDot(Meridiano.Mineral, Modifier.padding(horizontal = 18.dp), pulse = state.isPlaying)
                    } else {
                        IconButton(onClick = { vm.player.removeAt(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Tirar da fila", tint = Meridiano.IvoryMuted)
                        }
                    }
                }
            }
        }
    }
}
