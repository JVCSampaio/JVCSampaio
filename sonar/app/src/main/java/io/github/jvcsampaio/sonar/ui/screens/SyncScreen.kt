package io.github.jvcsampaio.sonar.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.data.Settings
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.data.youtube.GoogleAuth
import io.github.jvcsampaio.sonar.sync.DownloadProgress
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.artistAndDuration
import io.github.jvcsampaio.sonar.ui.components.AlertNote
import io.github.jvcsampaio.sonar.ui.components.Hairline
import io.github.jvcsampaio.sonar.ui.components.MenuAction
import io.github.jvcsampaio.sonar.ui.components.MonoLabel
import io.github.jvcsampaio.sonar.ui.components.PrimaryButton
import io.github.jvcsampaio.sonar.ui.components.ProgressLine
import io.github.jvcsampaio.sonar.ui.components.ScreenHeader
import io.github.jvcsampaio.sonar.ui.components.SecondaryButton
import io.github.jvcsampaio.sonar.ui.components.SelectChip
import io.github.jvcsampaio.sonar.ui.components.StatusDot
import io.github.jvcsampaio.sonar.ui.components.TextInputDialog
import io.github.jvcsampaio.sonar.ui.components.TrackRow
import io.github.jvcsampaio.sonar.ui.formatAgo
import io.github.jvcsampaio.sonar.ui.songCount
import io.github.jvcsampaio.sonar.ui.theme.Meridiano
import io.github.jvcsampaio.sonar.ui.theme.MonoFamily
import kotlinx.coroutines.launch

private enum class PendingFilter(val label: String) { Queue("Na fila"), Errors("Com erro"), Skipped("Ignoradas") }

/** One line of the operations panel: a stage of the pipeline and its live state. */
private data class Stage(
    val numeral: String,
    val name: String,
    val role: String,
    val detail: String,
    val color: Color,
    val active: Boolean = false,
    val progress: Float? = null,
)

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
    val signInError by vm.signInError.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
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
    val syncFault = settings.connected && settings.lastSyncError != null
    val current = progress

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenHeader("Sincronização", kicker = "Meridiano / Sonar · Núcleo", subtitle = "Curtidas do YouTube, em áudio") {
                IconButton(onClick = actions.openSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!settings.connected) {
            item {
                Panel {
                    MonoLabel("Acesso · conta Google", color = Meridiano.Bronze, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Conecte seu YouTube", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O Sonar só lê a lista de vídeos que você curtiu (permissão somente leitura). " +
                            "Os downloads são anônimos: sua conta nunca é usada para baixar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton("Conectar com o Google", connect, Modifier.fillMaxWidth())
                    signInError?.let { error ->
                        Spacer(Modifier.height(14.dp))
                        AlertNote(error)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Antes do primeiro login, o Google exige um projeto seu no Google Cloud (grátis, uns 10 minutos).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { uriHandler.openUri(SETUP_GUIDE_URL) }, contentPadding = PaddingValues(0.dp)) {
                        Text("VER O PASSO A PASSO")
                    }
                }
            }
        }

        item {
            val stages = listOf(
                Stage(
                    numeral = "I",
                    name = "Intellectus",
                    role = "Descoberta",
                    detail = when {
                        !settings.connected -> "Conta não conectada"
                        syncing -> "Lendo a lista de curtidas…"
                        syncFault -> "Falha na última leitura"
                        settings.lastSyncAt > 0 -> "${settings.lastSyncFound} curtidas · lidas ${formatAgo(settings.lastSyncAt)}"
                        else -> "Aguardando a primeira leitura"
                    },
                    color = when {
                        syncFault -> Meridiano.Critical
                        !settings.connected -> Meridiano.IvoryMuted
                        else -> Meridiano.Mineral
                    },
                    active = syncing,
                ),
                Stage(
                    numeral = "II",
                    name = "Ars",
                    role = "Extração do áudio",
                    detail = when {
                        current != null -> "${current.position} de ${current.total} · ${current.title}"
                        waiting && queued > 0 ->
                            "${songCount(queued)} na fila · " + if (settings.wifiOnly) "aguardando Wi-Fi" else "aguardando conexão"
                        queued > 0 -> "${songCount(queued)} na fila"
                        else -> "Ocioso"
                    },
                    color = when {
                        current != null -> Meridiano.Mineral
                        queued > 0 -> Meridiano.Bronze
                        else -> Meridiano.IvoryMuted
                    },
                    active = current != null,
                    progress = current?.fraction,
                ),
                Stage(
                    numeral = "III",
                    name = "Prudentia",
                    role = "Verificação",
                    detail = "$done no aparelho · $failed com erro · $skipped ignoradas",
                    color = if (failed > 0) Meridiano.Critical else Meridiano.Mineral,
                ),
            )
            Panel {
                OperationalState(settings, syncing, current, waiting, queued, syncFault)
                Spacer(Modifier.height(14.dp))
                Hairline(Modifier.fillMaxWidth())
                stages.forEach { StageRow(it) }
                Hairline(Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.connected) {
                        PrimaryButton(
                            if (syncing) "Lendo" else "Sincronizar",
                            onClick = vm::syncNow,
                            icon = Icons.Rounded.Sync,
                            enabled = !syncing,
                        )
                    }
                    when {
                        current != null -> SecondaryButton("Pausar", vm::pauseDownloads, icon = Icons.Rounded.Pause)
                        queued > 0 -> SecondaryButton("Baixar", vm::resumeDownloads, icon = Icons.Rounded.PlayArrow)
                    }
                }
                if (settings.connected) {
                    settings.lastSyncError?.let { error ->
                        Spacer(Modifier.height(14.dp))
                        AlertNote(error)
                        if (error.contains("conta", ignoreCase = true)) {
                            TextButton(onClick = connect, contentPadding = PaddingValues(0.dp)) { Text("CONECTAR DE NOVO") }
                        }
                    }
                    TextButton(onClick = vm::disconnect, contentPadding = PaddingValues(0.dp)) {
                        Text("DESCONECTAR CONTA", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            TextButton(onClick = { addingLink = true }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("BAIXAR POR LINK DO YOUTUBE")
            }
        }

        item {
            Telemetry(listOf("Baixadas" to done, "Na fila" to queued, "Erros" to failed, "Ignoradas" to skipped))
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PendingFilter.entries.forEach { option ->
                    val count = when (option) {
                        PendingFilter.Queue -> queued
                        PendingFilter.Errors -> failed
                        PendingFilter.Skipped -> skipped
                    }
                    SelectChip("${option.label} · $count", selected = filter == option, onClick = { filter = option })
                }
            }
        }

        if (filter == PendingFilter.Errors && failed > 0) {
            item {
                SecondaryButton(
                    "Tentar todas de novo",
                    vm::retryFailed,
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    icon = Icons.Rounded.Refresh,
                )
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    when (filter) {
                        PendingFilter.Queue -> if (done > 0) "Nada na fila. Tudo o que você curtiu e é música já está no aparelho." else "Nada na fila."
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

/** "ESTADO OPERACIONAL ........ ● NOMINAL" */
@Composable
private fun OperationalState(
    settings: Settings,
    syncing: Boolean,
    progress: DownloadProgress?,
    waiting: Boolean,
    queued: Int,
    fault: Boolean,
) {
    val (label, color, active) = when {
        fault -> Triple("Falha", Meridiano.Critical, false)
        syncing || progress != null -> Triple("Em operação", Meridiano.Mineral, true)
        waiting && queued > 0 -> Triple("Aguardando rede", Meridiano.Bronze, false)
        !settings.connected -> Triple("Sem conta", Meridiano.IvoryMuted, false)
        else -> Triple("Nominal", Meridiano.Mineral, false)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonoLabel("Estado operacional", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        StatusDot(color, pulse = active)
        Spacer(Modifier.width(8.dp))
        MonoLabel(label, color = if (color == Meridiano.Critical) Meridiano.CriticalText else color)
    }
}

@Composable
private fun StageRow(stage: Stage) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(
            stage.numeral,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFamily, fontWeight = FontWeight.Medium),
            color = Meridiano.Bronze,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(stage.name, color = Meridiano.Ivory)
                Spacer(Modifier.width(8.dp))
                MonoLabel(stage.role, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stage.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            stage.progress?.let {
                Spacer(Modifier.height(8.dp))
                ProgressLine(it)
            }
        }
        Spacer(Modifier.width(12.dp))
        StatusDot(stage.color, Modifier.padding(top = 6.dp), pulse = stage.active)
    }
}

/** Four readouts separated by hairlines, like an instrument panel. */
@Composable
private fun Telemetry(values: List<Pair<String, Int>>) {
    Surface(
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, Meridiano.Line),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            values.forEachIndexed { index, (label, value) ->
                if (index > 0) Hairline(Modifier.fillMaxHeight().width(1.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Text(
                        value.toString(),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFamily, fontSize = 22.sp),
                        color = Meridiano.Ivory,
                    )
                    Spacer(Modifier.height(2.dp))
                    MonoLabel(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PendingRow(track: TrackEntity, vm: SonarViewModel) {
    val (supporting, color) = when (track.status) {
        TrackStatus.DOWNLOADING -> "Extraindo agora" to Meridiano.Mineral
        TrackStatus.QUEUED -> artistAndDuration(track.artist, track.durationMs) to MaterialTheme.colorScheme.onSurfaceVariant
        TrackStatus.FAILED -> (track.statusNote ?: "Falhou") to Meridiano.CriticalText
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
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        color = Meridiano.GraphiteRaised,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Meridiano.Line),
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
        // A failed consent screen also returns RESULT_CANCELED; the real reason is in the intent.
        val data = result.data
        if (data == null) {
            Log.w(TAG, "Consent screen closed without data, resultCode=${result.resultCode}")
            vm.onSignInFailed(GoogleAuth.CANCELLED_MESSAGE)
            return@rememberLauncherForActivityResult
        }
        runCatching { vm.auth.resultFromIntent(data) }
            .onSuccess {
                if (it.accessToken != null) vm.onSignedIn()
                else vm.onSignInFailed("O Google não liberou o acesso às curtidas. Tente de novo e permita ver sua conta do YouTube.")
            }
            .onFailure {
                Log.w(TAG, "Consent failed, resultCode=${result.resultCode}", it)
                vm.onSignInFailed(GoogleAuth.describe(it))
            }
    }
    return {
        vm.onSignInStarted()
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
                .onFailure {
                    Log.w(TAG, "authorize() failed", it)
                    vm.onSignInFailed(GoogleAuth.describe(it))
                }
        }
    }
}

private const val TAG = "SyncScreen"
private const val SETUP_GUIDE_URL =
    "https://github.com/JVCSampaio/JVCSampaio/blob/claude/android-youtube-downloader-app-pxb9br/sonar/README.md" +
        "#configurar-o-acesso-ao-youtube-uma-vez-uns-10-minutos"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
