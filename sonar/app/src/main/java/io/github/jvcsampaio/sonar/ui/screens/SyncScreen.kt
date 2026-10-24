package io.github.jvcsampaio.sonar.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.data.youtube.GoogleAuth
import io.github.jvcsampaio.sonar.sync.DownloadProgress
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.MenuAction
import io.github.jvcsampaio.sonar.ui.components.ScreenHeader
import io.github.jvcsampaio.sonar.ui.components.TextInputDialog
import io.github.jvcsampaio.sonar.ui.components.TrackRow
import io.github.jvcsampaio.sonar.ui.formatAgo
import io.github.jvcsampaio.sonar.ui.formatDuration
import io.github.jvcsampaio.sonar.ui.songCount
import kotlinx.coroutines.launch

private enum class PendingFilter(val label: String) { Queue("Na fila"), Errors("Com erro"), Skipped("Ignoradas") }

@Composable
fun SyncScreen(vm: SonarViewModel, actions: AppActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val syncing by vm.syncRunning.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val waiting by vm.downloadsWaiting.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(PendingFilter.Queue) }
    var addingLink by remember { mutableStateOf(false) }

    val connect = rememberGoogleConnect(vm)
    val done = counts[TrackStatus.DONE] ?: 0
    val queued = (counts[TrackStatus.QUEUED] ?: 0) + (counts[TrackStatus.DOWNLOADING] ?: 0)
    val failed = counts[TrackStatus.FAILED] ?: 0
    val skipped = counts[TrackStatus.SKIPPED] ?: 0
    val shown = remember(pending, filter) {
        pending.filter {
            when (filter) {
                PendingFilter.Queue -> it.status == TrackStatus.QUEUED || it.status == TrackStatus.DOWNLOADING
                PendingFilter.Errors -> it.status == TrackStatus.FAILED
                PendingFilter.Skipped -> it.status == TrackStatus.SKIPPED
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader("Sincronizar", subtitle = "Suas curtidas do YouTube, em áudio") {
                IconButton(onClick = actions.openSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card {
                if (!settings.connected) {
                    Text("Conecte seu YouTube", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "O Sonar só lê a lista de vídeos que você curtiu (permissão somente leitura). " +
                            "Os downloads são anônimos: sua conta nunca é usada para baixar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = connect, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Conectar com o Google") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Conta conectada", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (settings.lastSyncAt > 0) {
                                    "Última busca ${formatAgo(settings.lastSyncAt)} · ${settings.lastSyncFound} curtidas"
                                } else {
                                    "Ainda não sincronizou"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = vm::syncNow, enabled = !syncing, modifier = Modifier.height(44.dp)) {
                            if (syncing) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (syncing) "Buscando curtidas" else "Sincronizar agora")
                        }
                        TextButton(onClick = vm::disconnect) { Text("Desconectar") }
                    }
                }
                settings.lastSyncError?.let { error ->
                    Spacer(Modifier.height(14.dp))
                    Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                    if (settings.connected && error.contains("conta", ignoreCase = true)) {
                        TextButton(onClick = connect) { Text("Conectar de novo") }
                    }
                }
            }
        }

        item {
            TextButton(onClick = { addingLink = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Baixar por link do YouTube")
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Stat("Baixadas", done, Icons.Rounded.Download, Modifier.weight(1f))
                Stat("Na fila", queued, Icons.Rounded.CloudDownload, Modifier.weight(1f))
                Stat("Erros", failed, Icons.Rounded.ErrorOutline, Modifier.weight(1f))
                Stat("Ignoradas", skipped, Icons.Rounded.Block, Modifier.weight(1f))
            }
        }

        item {
            DownloadCard(
                progress = progress,
                waiting = waiting,
                queued = queued,
                wifiOnly = settings.wifiOnly,
                onPause = vm::pauseDownloads,
                onResume = vm::resumeDownloads,
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PendingFilter.entries.forEach { option ->
                    val count = when (option) {
                        PendingFilter.Queue -> queued
                        PendingFilter.Errors -> failed
                        PendingFilter.Skipped -> skipped
                    }
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text("${option.label} ($count)") },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        if (filter == PendingFilter.Errors && failed > 0) {
            item {
                OutlinedButton(onClick = vm::retryFailed, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tentar todas de novo")
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    when (filter) {
                        PendingFilter.Queue -> "Nada na fila. Tudo o que você curtiu e é música já está no aparelho."
                        PendingFilter.Errors -> "Nenhum erro."
                        PendingFilter.Skipped -> "Nada ignorado."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        items(shown, key = { it.videoId }) { track -> PendingRow(track, vm) }
    }

    if (addingLink) {
        TextInputDialog(
            title = "Baixar por link",
            confirmLabel = "Baixar",
            placeholder = "https://youtu.be/…",
            maxLength = 500,
            onConfirm = { link ->
                addingLink = false
                vm.addFromLink(link)
            },
            onDismiss = { addingLink = false },
        )
    }
}

@Composable
private fun PendingRow(track: TrackEntity, vm: SonarViewModel) {
    val (supporting, color) = when (track.status) {
        TrackStatus.DOWNLOADING -> "Baixando agora · ${track.artist}" to MaterialTheme.colorScheme.primary
        TrackStatus.QUEUED -> "${track.artist} · ${formatDuration(track.durationMs)}" to MaterialTheme.colorScheme.onSurfaceVariant
        TrackStatus.FAILED -> (track.statusNote ?: "Falhou") to MaterialTheme.colorScheme.error
        else -> (track.statusNote ?: "Ignorada") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val actions = when (track.status) {
        TrackStatus.QUEUED -> listOf(MenuAction("Não baixar", Icons.Rounded.Block) { vm.skip(track) })
        TrackStatus.FAILED -> listOf(
            MenuAction("Tentar de novo", Icons.Rounded.Refresh) { vm.downloadAnyway(track) },
            MenuAction("Não baixar", Icons.Rounded.Block) { vm.skip(track) },
        )
        TrackStatus.SKIPPED -> listOf(MenuAction("Baixar mesmo assim", Icons.Rounded.Download) { vm.downloadAnyway(track) })
        else -> emptyList()
    }
    TrackRow(
        track = track,
        isCurrent = false,
        onClick = {},
        supporting = supporting,
        supportingColor = color,
        actions = actions,
    )
}

@Composable
private fun DownloadCard(
    progress: DownloadProgress?,
    waiting: Boolean,
    queued: Int,
    wifiOnly: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    when {
        progress != null -> Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Baixando ${progress.position} de ${progress.total}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(progress.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(progress.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = onPause) { Icon(Icons.Rounded.Pause, contentDescription = "Pausar downloads") }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }
        waiting && queued > 0 -> Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (wifiOnly) "${songCount(queued)} esperando uma rede Wi-Fi. Dá para liberar os dados móveis nos Ajustes."
                    else "${songCount(queued)} esperando conexão.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        queued > 0 -> Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${songCount(queued)} na fila", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Button(onClick = onResume) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Baixar agora")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, icon: ImageVector, modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            Text(value.toString(), style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

/** Starts Google's consent flow for read-only YouTube access. */
@Composable
private fun rememberGoogleConnect(vm: SonarViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            vm.say("Login cancelado")
            return@rememberLauncherForActivityResult
        }
        runCatching { vm.auth.resultFromIntent(data) }
            .onSuccess { if (it.accessToken != null) vm.onSignedIn() else vm.say("O Google não liberou o acesso às curtidas") }
            .onFailure { vm.say(GoogleAuth.describe(it)) }
    }
    return {
        scope.launch {
            val activity = context.findActivity() ?: return@launch
            runCatching { vm.auth.authorize(activity) }
                .onSuccess { result ->
                    val intent = result.pendingIntent
                    if (result.hasResolution() && intent != null) {
                        launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    } else {
                        vm.onSignedIn()
                    }
                }
                .onFailure { vm.say(GoogleAuth.describe(it)) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
