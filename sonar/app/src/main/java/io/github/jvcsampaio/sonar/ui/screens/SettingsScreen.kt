package io.github.jvcsampaio.sonar.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jvcsampaio.sonar.BuildConfig
import io.github.jvcsampaio.sonar.data.AudioFormat
import io.github.jvcsampaio.sonar.download.MediaStoreExporter
import io.github.jvcsampaio.sonar.ui.AppActions
import io.github.jvcsampaio.sonar.ui.SonarViewModel
import io.github.jvcsampaio.sonar.ui.components.Motto
import io.github.jvcsampaio.sonar.ui.components.ScreenHeader
import io.github.jvcsampaio.sonar.ui.components.SecondaryButton
import io.github.jvcsampaio.sonar.ui.components.SectionHeader
import io.github.jvcsampaio.sonar.ui.components.SelectChip
import io.github.jvcsampaio.sonar.ui.theme.DataStyle
import io.github.jvcsampaio.sonar.ui.theme.Meridiano
import io.github.jvcsampaio.sonar.ui.formatAgo
import io.github.jvcsampaio.sonar.ui.formatBytes
import io.github.jvcsampaio.sonar.ui.songCount

@Composable
fun SettingsScreen(vm: SonarViewModel, actions: AppActions) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val bytes by vm.storageBytes.collectAsStateWithLifecycle()
    val engineVersion by vm.engineVersion.collectAsStateWithLifecycle()
    val engineUpdating by vm.engineUpdating.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
            }
            ScreenHeader("Ajustes", kicker = "Meridiano / Sonar · Parâmetros")
        }

        item { SectionHeader("Formato do áudio", index = 1) }
        AudioFormat.entries.forEach { format ->
            item(key = format.name) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { vm.updateSettings { it.copy(audioFormat = format) } }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.audioFormat == format,
                        onClick = { vm.updateSettings { it.copy(audioFormat = format) } },
                        colors = RadioButtonDefaults.colors(selectedColor = Meridiano.Bronze, unselectedColor = Meridiano.IvoryMuted),
                    )
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(format.label, style = MaterialTheme.typography.titleSmall)
                        Text(format.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Note("Vale para os próximos downloads. As músicas já baixadas não mudam.") }

        item { SectionHeader("Downloads", index = 2) }
        item {
            SwitchRow("Baixar só no Wi-Fi", "Evita gastar o plano de dados.", settings.wifiOnly, onChange = vm::setWifiOnly)
        }
        item {
            SwitchRow(
                title = "Salvar cópia na pasta Música",
                subtitle = if (MediaStoreExporter.supported) "Em Música/Sonar, para outros players e para não perder ao desinstalar."
                else "Precisa do Android 10 ou mais novo.",
                checked = settings.exportToMusic && MediaStoreExporter.supported,
                enabled = MediaStoreExporter.supported,
            ) { value -> vm.updateSettings { it.copy(exportToMusic = value) } }
        }

        item { SectionHeader("O que baixar", index = 3) }
        item {
            SwitchRow(
                "Só a categoria Música do YouTube",
                "Desligue para baixar o áudio de qualquer vídeo que você curtir.",
                settings.musicOnly,
            ) { value -> vm.updateSettings { it.copy(musicOnly = value) } }
        }
        item {
            SwitchRow("Ignorar vídeos de até 1 minuto", "Shorts e trechos curtos.", settings.skipShort) { value ->
                vm.updateSettings { it.copy(skipShort = value) }
            }
        }
        item {
            Choices(
                title = "Duração máxima",
                options = listOf(10, 15, 20, 30, 60, 0),
                selected = settings.maxDurationMinutes,
                label = { if (it == 0) "Sem limite" else "$it min" },
            ) { value -> vm.updateSettings { it.copy(maxDurationMinutes = value) } }
        }

        item { SectionHeader("Sincronização", index = 4) }
        item {
            SwitchRow(
                "Buscar curtidas automaticamente",
                if (settings.lastSyncAt > 0) "Última busca ${formatAgo(settings.lastSyncAt)}." else "Em segundo plano, com bateria boa.",
                settings.autoSync,
            ) { value -> vm.updateSettings { it.copy(autoSync = value) } }
        }
        if (settings.autoSync) {
            item {
                Choices(
                    title = "Frequência",
                    options = listOf(3, 6, 12, 24),
                    selected = settings.syncIntervalHours,
                    label = { "a cada $it h" },
                ) { value -> vm.updateSettings { it.copy(syncIntervalHours = value) } }
            }
        }
        item {
            SwitchRow(
                "Apagar o que eu descurtir",
                "Quando você tira o gostei no YouTube, a música sai do aparelho.",
                settings.removeUnliked,
            ) { value -> vm.updateSettings { it.copy(removeUnliked = value) } }
        }

        item { SectionHeader("Motor de download", index = 5) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("yt-dlp", style = MaterialTheme.typography.titleSmall)
                    Text(
                        engineVersion?.let { "versão $it" } ?: "versão embutida (atualiza no primeiro download)",
                        style = DataStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (engineUpdating) {
                    CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp, color = Meridiano.Mineral)
                } else {
                    SecondaryButton("Atualizar", vm::updateEngine)
                }
            }
        }
        item { Note("O YouTube muda com frequência; o Sonar atualiza o yt-dlp sozinho uma vez por dia.") }

        item { SectionHeader("Armazenamento", index = 6) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("${songCount(tracks.size)} no aparelho", style = MaterialTheme.typography.titleSmall)
                Text(formatBytes(bytes), style = DataStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item { SectionHeader("Sobre", index = 7) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Sonar", style = MaterialTheme.typography.titleLarge)
                Text("versão ${BuildConfig.VERSION_NAME}", style = DataStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Identidade MERIDIANO: racionalismo monumental, humanismo tecnológico, engenharia de precisão. " +
                        "A forma serve à finalidade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "App pessoal. Baixe só para uso próprio e respeite os direitos dos artistas e os termos do YouTube. " +
                        "Tipografia IBM Plex (SIL Open Font License).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Motto() }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Meridiano.Bronze,
                checkedThumbColor = Meridiano.Graphite,
                uncheckedTrackColor = Meridiano.GraphiteHigh,
                uncheckedBorderColor = Meridiano.Line,
                uncheckedThumbColor = Meridiano.IvoryMuted,
            ),
        )
    }
}

@Composable
private fun <T> Choices(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                SelectChip(label(option), selected = option == selected, onClick = { onSelect(option) })
            }
        }
    }
}
