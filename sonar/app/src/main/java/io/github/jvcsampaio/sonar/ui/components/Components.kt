package io.github.jvcsampaio.sonar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.ui.formatDuration
import io.github.jvcsampaio.sonar.ui.theme.DataStyle
import io.github.jvcsampaio.sonar.ui.theme.Meridiano
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The MERIDIANO emblem: a guiding axis (the purpose), an open frame and an inner structure that
 * converge on it (the means), and a core where knowledge meets action. The frame stays open at
 * the top: the system can still be improved. Drawn on the same 108-unit grid as the app icon.
 */
@Composable
fun Emblem(modifier: Modifier = Modifier, pulse: Boolean = false) {
    val coreAlpha = if (pulse) {
        rememberInfiniteTransition(label = "emblem").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "core",
        ).value
    } else {
        1f
    }
    Canvas(modifier) {
        val u = size.minDimension / 108f
        val origin = Offset(size.width / 2 - 54f * u, size.height / 2 - 54f * u)
        fun p(x: Float, y: Float) = Offset(origin.x + x * u, origin.y + y * u)
        fun polyline(points: List<Offset>) = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val stroke = (3f * u).coerceAtLeast(1.2f)
        drawLine(Meridiano.Bronze, p(54f, 6f), p(54f, 102f), stroke)
        drawPath(
            polyline(listOf(p(44f, 30f), p(26f, 54f), p(54f, 92f), p(82f, 54f), p(64f, 30f))),
            Meridiano.Ivory,
            style = Stroke(stroke, join = StrokeJoin.Miter),
        )
        // The inner structure only reads at larger sizes.
        if (size.minDimension >= 40.dp.toPx()) {
            drawPath(
                polyline(listOf(p(48f, 38f), p(36f, 54f), p(54f, 74f), p(72f, 54f), p(60f, 38f))),
                Meridiano.Ivory.copy(alpha = 0.55f),
                style = Stroke(stroke * 0.66f, join = StrokeJoin.Miter),
            )
        }
        drawPath(
            polyline(listOf(p(54f, 45f), p(63f, 54f), p(54f, 63f), p(45f, 54f))).apply { close() },
            Meridiano.Bronze.copy(alpha = coreAlpha),
        )
    }
}

/** Small uppercase technical label. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Text(text.uppercase(Locale.ROOT), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Meridiano.Line) {
    Box(modifier.height(1.dp).background(color))
}

/** State indicator: mineral for active systems, bronze for waiting, red for faults. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val alpha = if (pulse) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dotAlpha",
        ).value
    } else {
        1f
    }
    Box(modifier.size(8.dp).alpha(alpha).background(color, CircleShape))
}

/** Registration marks at the four corners, like a technical drawing. */
fun Modifier.cornerMarks(color: Color = Meridiano.Bronze, length: Dp = 12.dp, gap: Dp = 6.dp): Modifier =
    drawWithContent {
        drawContent()
        val l = length.toPx()
        val g = gap.toPx()
        val w = 1.dp.toPx()
        val (x0, y0, x1, y1) = listOf(-g, -g, size.width + g, size.height + g)
        drawLine(color, Offset(x0, y0), Offset(x0 + l, y0), w)
        drawLine(color, Offset(x0, y0), Offset(x0, y0 + l), w)
        drawLine(color, Offset(x1, y0), Offset(x1 - l, y0), w)
        drawLine(color, Offset(x1, y0), Offset(x1, y0 + l), w)
        drawLine(color, Offset(x0, y1), Offset(x0 + l, y1), w)
        drawLine(color, Offset(x0, y1), Offset(x0, y1 - l), w)
        drawLine(color, Offset(x1, y1), Offset(x1 - l, y1), w)
        drawLine(color, Offset(x1, y1), Offset(x1, y1 - l), w)
    }

@Composable
fun Artwork(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier.clip(shape).background(Meridiano.GraphiteHigh),
        contentAlignment = Alignment.Center,
    ) {
        Emblem(Modifier.size(iconSize).alpha(0.35f))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** A restrained tint taken from the artwork, sunk into graphite. */
@Composable
fun rememberArtworkColor(model: Any?, fallback: Color = Meridiano.GraphiteHigh): Color {
    val context = LocalContext.current
    var color by remember { mutableStateOf(fallback) }
    LaunchedEffect(model) {
        if (model == null) {
            color = fallback
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context).data(model).size(128).allowHardware(false).build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val swatch = palette.mutedSwatch ?: palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.dominantSwatch
        if (swatch != null) color = lerp(Color(swatch.rgb), Meridiano.Graphite, 0.62f)
    }
    return animateColorAsState(color, tween(700), label = "artworkColor").value
}

data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun OverflowMenu(actions: List<MenuAction>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Mais opções", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    track: TrackEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<MenuAction> = emptyList(),
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track.artwork, Modifier.size(48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) Meridiano.Mineral else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                supporting ?: track.artist.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = supportingColor,
                maxLines = if (supporting != null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) StatusDot(Meridiano.Mineral, Modifier.padding(horizontal = 8.dp), pulse = true)
        if (supporting == null && track.durationMs > 0) {
            Text(
                formatDuration(track.durationMs),
                style = DataStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (actions.isNotEmpty()) OverflowMenu(actions) else Spacer(Modifier.width(12.dp))
    }
}

/** "02 — CURTIDAS RECENTES" followed by a hairline that runs to the edge. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    index: Int? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = if (action != null) 8.dp else 20.dp, top = 30.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (index != null) String.format(Locale.ROOT, "%02d — %s", index, title) else title
        MonoLabel(label, color = Meridiano.Bronze)
        Spacer(Modifier.width(12.dp))
        Hairline(Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action.uppercase(Locale.ROOT)) }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String = "Meridiano · Sonar",
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            MonoLabel(kicker, color = Meridiano.Bronze, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                MonoLabel(subtitle)
            }
        }
        actions()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = Meridiano.Bronze, contentColor = Meridiano.Graphite),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(Locale.ROOT))
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Meridiano.Ivory),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(Locale.ROOT))
    }
}

@Composable
fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("Tocar", onPlay, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, enabled = enabled)
        SecondaryButton("Aleatório", onShuffle, Modifier.weight(1f), icon = Icons.Rounded.Shuffle, enabled = enabled)
    }
}

/** Rectangular selector chip: bronze when selected, outlined otherwise. */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) Meridiano.Bronze else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Meridiano.Graphite else Meridiano.IvoryMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/** Alerts are the only place red appears: a thin bar, never a large surface. */
@Composable
fun AlertNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Meridiano.Critical.copy(alpha = 0.14f))) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(Meridiano.Critical))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Meridiano.Ivory,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    pulse: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Emblem(Modifier.size(96.dp), pulse = pulse)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(actionLabel, onAction)
        }
    }
}

/** The closing line of the identity. */
@Composable
fun Motto(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Emblem(Modifier.size(28.dp).alpha(0.7f))
        Spacer(Modifier.height(10.dp))
        MonoLabel("Forma servit fini", color = Meridiano.IvoryMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/** A thin bar for progress, mineral when a system is working. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = Meridiano.Mineral) {
    Box(modifier.fillMaxWidth().height(2.dp).background(Meridiano.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}
