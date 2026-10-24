package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.BuildConfig
import io.github.jvcsampaio.sonar.data.AudioFormat
import io.github.jvcsampaio.sonar.data.db.TrackStatus
import io.github.jvcsampaio.sonar.download.MediaStoreExporter
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Botao
import io.github.jvcsampaio.sonar.ui.components.BotaoGlifo
import io.github.jvcsampaio.sonar.ui.components.Cabecalho
import io.github.jvcsampaio.sonar.ui.components.Chave
import io.github.jvcsampaio.sonar.ui.components.Colofao
import io.github.jvcsampaio.sonar.ui.components.Escolhas
import io.github.jvcsampaio.sonar.ui.components.Estado
import io.github.jvcsampaio.sonar.ui.components.Fio
import io.github.jvcsampaio.sonar.ui.components.Glifo
import io.github.jvcsampaio.sonar.ui.components.Indicador
import io.github.jvcsampaio.sonar.ui.components.Leitura
import io.github.jvcsampaio.sonar.ui.components.Painel
import io.github.jvcsampaio.sonar.ui.components.Placa
import io.github.jvcsampaio.sonar.ui.components.Rotulo
import io.github.jvcsampaio.sonar.ui.components.SeloCompacto
import io.github.jvcsampaio.sonar.ui.formatAgo
import io.github.jvcsampaio.sonar.ui.formatBytes
import io.github.jvcsampaio.sonar.ui.formatData
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import java.util.Locale

@Composable
fun SettingsScreen(vm: SonarViewModel, actions: AppActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val bytes by vm.storageBytes.collectAsStateWithLifecycle()
    val engineVersion by vm.engineVersion.collectAsStateWithLifecycle()
    val engineUpdating by vm.engineUpdating.collectAsStateWithLifecycle()
    val cores = M.cores
    val done = counts[TrackStatus.DONE] ?: 0
    val verified = done > 0 && (counts[TrackStatus.FAILED] ?: 0) == 0 &&
        (counts[TrackStatus.QUEUED] ?: 0) + (counts[TrackStatus.DOWNLOADING] ?: 0) == 0

    LazyColumn(contentPadding = PaddingValues(bottom = Passo.p5), verticalArrangement = Arrangement.spacedBy(Passo.p5)) {
        item {
            Column {
                BotaoGlifo(Glifo.Voltar, "Voltar", actions.back, Modifier.padding(start = Passo.p1, top = Passo.p1))
                Cabecalho(
                    "Ordinatio",
                    finalidade = "Ajustar como o Sonar escolhe, extrai e guarda as músicas.",
                    local = "Sonar · ajustes",
                )
            }
        }

        item {
            Parametros(nome = "Formato do áudio", id = "P-01") {
                Escolhas(AudioFormat.entries, settings.audioFormat, rotulo = { it.label }) { format ->
                    vm.updateSettings { it.copy(audioFormat = format) }
                }
                Spacer(Modifier.height(Passo.p4))
                Text(settings.audioFormat.description, style = Tipo.corpo, color = cores.tinta)
                Text("Vale para as próximas extrações; as músicas já gravadas não mudam.", style = Tipo.nota, color = cores.tintaSuave)
            }
        }

        item {
            Parametros(nome = "Downloads", id = "P-02") {
                Chave("Baixar só no Wi-Fi", "Não gasta o plano de dados.", settings.wifiOnly, onChange = vm::setWifiOnly)
                Fio(Modifier.fillMaxWidth())
                Chave(
                    titulo = "Guardar cópia na pasta Música",
                    descricao = if (MediaStoreExporter.supported) "Em Música/Sonar: outros players leem, e a cópia fica se o app sair."
                    else "Precisa do Android 10 ou mais novo.",
                    ligada = settings.exportToMusic && MediaStoreExporter.supported,
                    enabled = MediaStoreExporter.supported,
                ) { value -> vm.updateSettings { it.copy(exportToMusic = value) } }
            }
        }

        item {
            Parametros(nome = "Seleção", id = "P-03") {
                Chave(
                    "Só a categoria Música",
                    "Desligada, extrai o áudio de qualquer vídeo curtido.",
                    settings.musicOnly,
                ) { value -> vm.updateSettings { it.copy(musicOnly = value) } }
                Fio(Modifier.fillMaxWidth())
                Chave("Ignorar vídeos de até 1 min", "Shorts e trechos curtos.", settings.skipShort) { value ->
                    vm.updateSettings { it.copy(skipShort = value) }
                }
                Fio(Modifier.fillMaxWidth())
                Spacer(Modifier.height(Passo.p4))
                Rotulo("Duração máxima", Modifier.padding(bottom = Passo.p3))
                Escolhas(
                    opcoes = listOf(10, 15, 20, 30, 60, 0),
                    selecionada = settings.maxDurationMinutes,
                    rotulo = { if (it == 0) "Sem limite" else "$it min" },
                ) { value -> vm.updateSettings { it.copy(maxDurationMinutes = value) } }
            }
        }

        item {
            Parametros(nome = "Sincronização", id = "P-04") {
                Chave(
                    "Ler curtidas sozinho",
                    if (settings.lastSyncAt > 0) "Última leitura ${formatAgo(settings.lastSyncAt)}, em ${formatData(settings.lastSyncAt)}."
                    else "Em segundo plano, com a bateria em bom nível.",
                    settings.autoSync,
                ) { value -> vm.updateSettings { it.copy(autoSync = value) } }
                if (settings.autoSync) {
                    Rotulo("Intervalo", Modifier.padding(top = Passo.p2, bottom = Passo.p3))
                    Escolhas(listOf(3, 6, 12, 24), settings.syncIntervalHours, rotulo = { "$it h" }) { value ->
                        vm.updateSettings { it.copy(syncIntervalHours = value) }
                    }
                    Spacer(Modifier.height(Passo.p4))
                }
                Fio(Modifier.fillMaxWidth())
                Chave(
                    "Apagar o que eu descurtir",
                    "Ao tirar o gostei no YouTube, a música sai do aparelho.",
                    settings.removeUnliked,
                ) { value -> vm.updateSettings { it.copy(removeUnliked = value) } }
            }
        }

        item {
            Parametros(nome = "Aparência", id = "P-05") {
                Escolhas(listOf(false, true), settings.meioDia, rotulo = { if (it) "Meio-dia" else "Meia-noite" }) { value ->
                    vm.updateSettings { it.copy(meioDia = value) }
                }
                Spacer(Modifier.height(Passo.p4))
                Text(
                    "Meia-noite é o padrão das telas. Meio-dia serve à leitura sob luz forte.",
                    style = Tipo.nota,
                    color = cores.tintaSuave,
                )
            }
        }

        item {
            Parametros(nome = "Motor de extração", id = "P-06") {
                Leitura(
                    rotulo = "yt-dlp",
                    valor = engineVersion ?: "—",
                    referencia = if (engineVersion != null) "verificado uma vez por dia" else "versão embutida; atualiza na primeira extração",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Passo.p4))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Passo.p5)) {
                    if (engineUpdating) {
                        Indicador(Estado.Atencao, "Atualizando")
                    } else {
                        Botao("Atualizar", vm::updateEngine)
                    }
                }
                Spacer(Modifier.height(Passo.p3))
                Text("O YouTube muda com frequência; o motor acompanha.", style = Tipo.nota, color = cores.tintaSuave)
            }
        }

        item {
            Parametros(nome = "Armazenamento", id = "P-07") {
                Leitura(
                    rotulo = "Acervo",
                    valor = tracks.size.toString(),
                    unidade = if (tracks.size == 1) "música" else "músicas",
                    referencia = "${formatBytes(bytes)} no aparelho",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Placa(
                titulo = "Sonar · Meridiano",
                revisao = BuildConfig.VERSION_CODE,
                campos = listOf(
                    "Modelo" to "SONAR ${BuildConfig.VERSION_NAME}",
                    "Série" to String.format(Locale.ROOT, "MRD-%04d", BuildConfig.VERSION_CODE),
                    "Operador" to "USO PESSOAL",
                    "Fabricação" to BuildConfig.BUILD_DATE,
                ),
                finalidade = "Guardar no aparelho, em áudio, as músicas curtidas no YouTube, e tocá-las sem anúncios.",
                modifier = Modifier.padding(horizontal = Passo.p5),
            ) {
                if (verified) {
                    SeloCompacto(BuildConfig.VERSION_NAME, "$done/$done", formatData(settings.lastSyncAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
                }
            }
        }

        item {
            Text(
                "App pessoal. Baixe só para uso próprio e respeite os artistas e os termos do YouTube. " +
                    "Tipografia: Marcellus SC, Spectral e B612 (SIL Open Font License); osifont (LGPL 3 com exceção de fonte).",
                style = Tipo.nota,
                color = cores.tintaSuave,
                modifier = Modifier.padding(horizontal = Passo.p5),
            )
        }
        item { Colofao() }
    }
}

/** A settings panel, padded off the screen edges. */
@Composable
private fun Parametros(nome: String, id: String, content: @Composable ColumnScope.() -> Unit) {
    Painel(Modifier.padding(horizontal = Passo.p5), nome = nome, id = id, content = content)
}
