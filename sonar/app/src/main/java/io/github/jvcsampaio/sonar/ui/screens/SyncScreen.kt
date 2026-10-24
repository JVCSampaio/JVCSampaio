package io.github.jvcsampaio.sonar.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.BuildConfig
import io.github.jvcsampaio.sonar.data.Settings
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.data.youtube.GoogleAuth
import io.github.jvcsampaio.sonar.sync.DownloadProgress
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.artistAndDuration
import io.github.jvcsampaio.sonar.ui.components.Aviso
import io.github.jvcsampaio.sonar.ui.components.Botao
import io.github.jvcsampaio.sonar.ui.components.Cabecalho
import io.github.jvcsampaio.sonar.ui.components.Delta
import io.github.jvcsampaio.sonar.ui.components.Escolhas
import io.github.jvcsampaio.sonar.ui.components.Estado
import io.github.jvcsampaio.sonar.ui.components.Fio
import io.github.jvcsampaio.sonar.ui.components.Indicador
import io.github.jvcsampaio.sonar.ui.components.Leitura
import io.github.jvcsampaio.sonar.ui.components.LinhaPoco
import io.github.jvcsampaio.sonar.ui.components.MenuAction
import io.github.jvcsampaio.sonar.ui.components.Painel
import io.github.jvcsampaio.sonar.ui.components.Poco
import io.github.jvcsampaio.sonar.ui.components.Regua
import io.github.jvcsampaio.sonar.ui.components.Rotulo
import io.github.jvcsampaio.sonar.ui.components.Secao
import io.github.jvcsampaio.sonar.ui.components.Selo
import io.github.jvcsampaio.sonar.ui.components.Sinal
import io.github.jvcsampaio.sonar.ui.components.TextInputDialog
import io.github.jvcsampaio.sonar.ui.components.TipoBotao
import io.github.jvcsampaio.sonar.ui.components.TrackRow
import io.github.jvcsampaio.sonar.ui.formatAgo
import io.github.jvcsampaio.sonar.ui.formatBytes
import io.github.jvcsampaio.sonar.ui.formatCarimbo
import io.github.jvcsampaio.sonar.ui.formatData
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class PendingFilter(val label: String) { Queue("Na fila"), Errors("Com erro"), Skipped("Ignoradas") }

/** A faculty of the console: a Latin name, its definition, a reading and its state. */
private data class Faculdade(
    val nome: String,
    val definicao: String,
    val leitura: String,
    val estado: Estado,
    val palavra: String,
    val progresso: DownloadProgress? = null,
)

@Composable
fun SyncScreen(vm: SonarViewModel, actions: AppActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val bytes by vm.storageBytes.collectAsStateWithLifecycle()
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
    // The seal is issued only when the check passed: every accepted song is on the device.
    val verified = done > 0 && failed == 0 && queued == 0 && current == null && !syncing && !syncFault
    val sealDate = settings.lastSyncAt.takeIf { it > 0L } ?: tracks.maxOfOrNull { it.downloadedAt ?: 0L } ?: 0L
    val cores = M.cores

    LazyColumn(contentPadding = PaddingValues(bottom = Passo.p6)) {
        item {
            Cabecalho(
                "Officina",
                finalidade = "Ler as curtidas do YouTube, julgar o que é música e extrair o áudio de cada uma.",
                local = "Sonar · oficina",
            ) { Botao("Ajustes", actions.openSettings, tipo = TipoBotao.Discreto) }
        }

        if (!settings.connected) {
            item {
                Painel(Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3), nome = "Accessvs", id = "A-01") {
                    Text(
                        "O Sonar só lê a lista de vídeos que você curtiu, com permissão somente leitura. " +
                            "Os downloads são anônimos: a conta nunca é usada para baixar.",
                        style = Tipo.corpo,
                        color = cores.tinta,
                    )
                    Spacer(Modifier.height(Passo.p5))
                    Botao("Conectar com o Google", connect, Modifier.fillMaxWidth(), tipo = TipoBotao.Principal)
                    signInError?.let { error ->
                        Spacer(Modifier.height(Passo.p5))
                        Aviso(error, palavra = "Acesso negado")
                    }
                    Spacer(Modifier.height(Passo.p5))
                    Text(
                        "Antes do primeiro acesso, o Google exige um projeto seu no Google Cloud: grátis, cerca de 10 min.",
                        style = Tipo.nota,
                        color = cores.tintaSuave,
                    )
                    Botao("Ver o passo a passo", { uriHandler.openUri(SETUP_GUIDE_URL) }, Modifier.padding(top = Passo.p2), tipo = TipoBotao.Discreto)
                }
            }
        }

        item {
            val faculdades = listOf(
                Faculdade(
                    nome = "Intellectvs",
                    definicao = "Apreender o fim: ler as curtidas no YouTube.",
                    leitura = when {
                        !settings.connected -> "conta não conectada"
                        syncing -> "lendo a lista de curtidas"
                        settings.lastSyncAt > 0 -> "${settings.lastSyncFound} curtidas · ${formatAgo(settings.lastSyncAt)}"
                        else -> "nenhuma leitura ainda"
                    },
                    estado = when {
                        syncing -> Estado.Aceso
                        syncFault -> Estado.Critico
                        settings.connected && settings.lastSyncAt > 0 -> Estado.Verificado
                        else -> Estado.Repouso
                    },
                    palavra = when {
                        syncing -> "Lendo"
                        syncFault -> "Falha"
                        !settings.connected -> "Sem conta"
                        settings.lastSyncAt > 0 -> "Lido"
                        else -> "Aguardando"
                    },
                ),
                Faculdade(
                    nome = "Prvdentia",
                    definicao = "Julgar antes de agir: separar o que é música.",
                    leitura = "${done + queued + failed} aceitas · $skipped ignoradas",
                    estado = if (syncing) Estado.Atencao else Estado.Repouso,
                    palavra = if (syncing) "Deliberando" else "Em repouso",
                ),
                Faculdade(
                    nome = "Ars",
                    definicao = "Reta razão do fazer: extrair e gravar o áudio.",
                    leitura = when {
                        current != null -> "${current.position} de ${current.total} · ${current.title}"
                        queued > 0 -> "$queued na fila"
                        else -> "$done no aparelho"
                    },
                    estado = when {
                        current != null -> Estado.Aceso
                        queued > 0 -> Estado.Atencao
                        failed > 0 -> Estado.Critico
                        else -> Estado.Repouso
                    },
                    palavra = when {
                        current != null -> "Extraindo"
                        waiting && queued > 0 -> if (settings.wifiOnly) "Aguardando Wi-Fi" else "Aguardando rede"
                        queued > 0 -> "Pausada"
                        failed > 0 -> "$failed com erro"
                        else -> "Ociosa"
                    },
                    progresso = current,
                ),
            )
            val (geral, palavra) = when {
                syncFault -> Estado.Critico to "Falha"
                syncing || current != null -> Estado.Aceso to "Operando"
                waiting && queued > 0 -> Estado.Atencao to "Aguardando"
                verified -> Estado.Verificado to "Verificado"
                else -> Estado.Repouso to "Em repouso"
            }
            Painel(
                Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3),
                cabecalho = {
                    Sinal(Modifier.size(Passo.p7), cores.insignia)
                    Column(Modifier.weight(1f)) {
                        Text("Sonar", style = Tipo.titulus3, color = cores.tinta)
                        Rotulo("Systema extractionis", style = Tipo.cota)
                    }
                    Indicador(geral, palavra)
                },
                pe = {
                    Delta(BuildConfig.VERSION_CODE)
                    Text("REV ${BuildConfig.VERSION_NAME}", style = Tipo.dado, color = cores.tintaSuave, modifier = Modifier.weight(1f))
                    if (sealDate > 0) Text(formatData(sealDate), style = Tipo.dado, color = cores.tempo)
                },
            ) {
                faculdades.forEachIndexed { index, f ->
                    if (index > 0) Fio(Modifier.fillMaxWidth())
                    FaculdadeRow(f)
                }
                Spacer(Modifier.height(Passo.p4))
                Poco(registro(settings, syncing, current, tracks))
                if (settings.connected) {
                    settings.lastSyncError?.let { error ->
                        Spacer(Modifier.height(Passo.p5))
                        Aviso(error)
                        if (error.contains("conta", ignoreCase = true)) {
                            Botao("Conectar de novo", connect, tipo = TipoBotao.Discreto)
                        }
                    }
                }
                Spacer(Modifier.height(Passo.p5))
                Row(horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
                    if (settings.connected) {
                        Botao(
                            if (syncing) "Lendo" else "Sincronizar",
                            vm::syncNow,
                            Modifier.weight(1f),
                            tipo = TipoBotao.Principal,
                            enabled = !syncing,
                        )
                    }
                    when {
                        current != null -> Botao("Pausar", vm::pauseDownloads, Modifier.weight(1f))
                        queued > 0 -> Botao("Retomar", vm::resumeDownloads, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3), verticalArrangement = Arrangement.spacedBy(Passo.p3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
                    Leitura("No aparelho", done.toString(), formatBytes(bytes), Modifier.weight(1f))
                    Leitura(
                        "Na fila",
                        queued.toString(),
                        when {
                            current != null -> "extraindo agora"
                            waiting && queued > 0 -> if (settings.wifiOnly) "aguardando Wi-Fi" else "aguardando rede"
                            queued > 0 -> "pausada"
                            else -> "nada a extrair"
                        },
                        Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Passo.p3)) {
                    Leitura("Com erro", failed.toString(), if (failed > 0) "ver pendências" else "nenhum", Modifier.weight(1f))
                    Leitura("Ignoradas", skipped.toString(), "pelos filtros", Modifier.weight(1f))
                }
            }
        }

        if (verified) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Passo.p5, vertical = Passo.p5),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Passo.p5),
                ) {
                    Selo(BuildConfig.VERSION_NAME, "$done/$done", "músicas", formatData(sealDate))
                    Text(
                        "Todas as músicas aceitas estão no aparelho. O selo cai sozinho se uma extração falhar.",
                        style = Tipo.nota,
                        color = cores.tintaSuave,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(Modifier.padding(horizontal = Passo.p2), verticalAlignment = Alignment.CenterVertically) {
                Botao("Baixar por link", { addingLink = true }, tipo = TipoBotao.Discreto)
                if (settings.connected) Botao("Desconectar conta", vm::disconnect, tipo = TipoBotao.Discreto)
            }
        }

        item {
            Secao("Pendências", contagem = queued + failed + skipped)
            Escolhas(
                opcoes = PendingFilter.entries,
                selecionada = filter,
                rotulo = { it.label },
                contagem = {
                    when (it) {
                        PendingFilter.Queue -> queued
                        PendingFilter.Errors -> failed
                        PendingFilter.Skipped -> skipped
                    }
                },
                modifier = Modifier.padding(horizontal = Passo.p5),
            ) { filter = it }
        }

        if (filter == PendingFilter.Errors && failed > 0) {
            item {
                Botao("Tentar todas de novo", vm::retryFailed, Modifier.padding(horizontal = Passo.p5, vertical = Passo.p4))
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    when (filter) {
                        PendingFilter.Queue -> if (done > 0) "Nada na fila. Tudo o que foi aceito já está no aparelho." else "Nada na fila."
                        PendingFilter.Errors -> "Nenhum erro."
                        PendingFilter.Skipped -> "Nada ignorado."
                    },
                    style = Tipo.corpo,
                    color = cores.tintaSuave,
                    modifier = Modifier.padding(horizontal = Passo.p5, vertical = Passo.p4),
                )
            }
        } else {
            item { Spacer(Modifier.height(Passo.p3)) }
        }

        items(shown, key = { it.videoId }) { track -> PendingRow(track, vm) }
    }

    if (addingLink) {
        TextInputDialog(
            title = "Baixar por link",
            confirmLabel = "Baixar",
            label = "Link do YouTube",
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
private fun ColumnScope.FaculdadeRow(f: Faculdade) {
    val cores = M.cores
    Row(Modifier.fillMaxWidth().padding(vertical = Passo.p4), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = Passo.p4)) {
            Text(f.nome, style = Tipo.titulus3, color = cores.tinta)
            Text(f.definicao, style = Tipo.nota, color = cores.tintaSuave)
            Text(f.leitura, style = Tipo.dado, color = cores.tinta, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Passo.p1))
        }
        Indicador(f.estado, f.palavra, Modifier.padding(top = 6.dp))
    }
    f.progresso?.let { p ->
        Regua(p.fraction, "${(p.fraction * 100).roundToInt()} %", Modifier.padding(bottom = Passo.p4), descricao = "Extração de ${p.title}")
    }
}

/** The log well, newest first, composed from what the database and the workers record. */
private fun registro(settings: Settings, syncing: Boolean, progress: DownloadProgress?, tracks: List<TrackEntity>): List<LinhaPoco> {
    val now = System.currentTimeMillis()
    val lines = buildList {
        if (progress != null) add(now to LinhaPoco(formatCarimbo(now), "ARS", "extraindo ${progress.title}"))
        if (syncing) add(now to LinhaPoco(formatCarimbo(now), "INTELLECTVS", "lendo a lista de curtidas"))
        if (settings.lastSyncAt > 0) {
            val error = settings.lastSyncError
            add(
                settings.lastSyncAt to LinhaPoco(
                    formatCarimbo(settings.lastSyncAt),
                    "INTELLECTVS",
                    if (error != null) "falha: $error" else "${settings.lastSyncFound} curtidas lidas",
                    critica = error != null,
                ),
            )
        }
        tracks.mapNotNull { track -> track.downloadedAt?.let { it to track } }.sortedByDescending { it.first }.take(4)
            .forEach { (at, track) -> add(at to LinhaPoco(formatCarimbo(at), "ARS", "gravada: ${track.title}")) }
    }
    return lines.sortedByDescending { it.first }.take(6).map { it.second }
        .ifEmpty { listOf(LinhaPoco("--:--", "SONAR", "nenhuma operação registrada")) }
}

@Composable
private fun PendingRow(track: TrackEntity, vm: SonarViewModel) {
    val (supporting, indicador) = when (track.status) {
        TrackStatus.DOWNLOADING -> artistAndDuration(track.artist, track.durationMs) to (Estado.Aceso to "Extraindo")
        TrackStatus.QUEUED -> artistAndDuration(track.artist, track.durationMs) to (Estado.Repouso to "Na fila")
        TrackStatus.FAILED -> (track.statusNote ?: "Falhou") to (Estado.Critico to "Erro")
        else -> (track.statusNote ?: "Ignorada") to (Estado.Repouso to "Ignorada")
    }
    val actions = when (track.status) {
        TrackStatus.QUEUED -> listOf(MenuAction("Não baixar") { vm.skip(track) })
        TrackStatus.FAILED -> listOf(
            MenuAction("Tentar de novo") { vm.downloadAnyway(track) },
            MenuAction("Não baixar") { vm.skip(track) },
        )
        TrackStatus.SKIPPED -> listOf(MenuAction("Baixar mesmo assim") { vm.downloadAnyway(track) })
        else -> emptyList()
    }
    TrackRow(
        track = track,
        isCurrent = false,
        onClick = {},
        supporting = supporting,
        indicador = indicador,
        actions = actions,
    )
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
