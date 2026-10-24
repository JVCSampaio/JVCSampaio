package io.github.jvcsampaio.sonar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.jvcsampaio.sonar.data.db.PlaylistSummary
import io.github.jvcsampaio.sonar.data.db.TrackEntity
import io.github.jvcsampaio.sonar.data.db.artwork
import io.github.jvcsampaio.sonar.ui.songCount
import io.github.jvcsampaio.sonar.ui.theme.Chanfro
import io.github.jvcsampaio.sonar.ui.theme.M
import io.github.jvcsampaio.sonar.ui.theme.Passo
import io.github.jvcsampaio.sonar.ui.theme.Tipo
import io.github.jvcsampaio.sonar.ui.theme.Traco

/** A dialog is a panel lifted over the screen: name, body, then the commands. */
@Composable
fun Dialogo(
    titulo: String,
    onDismiss: () -> Unit,
    confirmar: String,
    onConfirm: () -> Unit,
    confirmarAtivo: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Painel(nome = titulo, corpo = androidx.compose.foundation.layout.PaddingValues(Passo.p5)) {
            content()
            Spacer(Modifier.height(Passo.p6))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Botao("Cancelar", onDismiss, tipo = TipoBotao.Discreto)
                Spacer(Modifier.width(Passo.p3))
                Botao(confirmar, onConfirm, tipo = TipoBotao.Principal, enabled = confirmarAtivo)
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    label: String = "Nome",
    placeholder: String = "Nome da playlist",
    maxLength: Int = 80,
) {
    var name by remember { mutableStateOf(initial) }
    Dialogo(
        titulo = title,
        onDismiss = onDismiss,
        confirmar = confirmLabel,
        onConfirm = { onConfirm(name.trim()) },
        confirmarAtivo = name.isNotBlank(),
    ) {
        CampoTexto(
            valor = name,
            onValueChange = { name = it.take(maxLength) },
            rotulo = label,
            placeholder = placeholder,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) }),
        )
    }
}

/** Bottom sheet with square top corners cut like a plate and a plain grip line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Folha(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cores = M.cores
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = CutCornerShape(topStart = Chanfro.g),
        containerColor = cores.superficie,
        contentColor = cores.tinta,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(vertical = Passo.p4).size(Passo.p7, Traco.medio).background(cores.linhaForte))
        },
        content = content,
    )
}

@Composable
fun AddToPlaylistSheet(
    track: TrackEntity,
    playlists: List<PlaylistSummary>,
    onPick: (PlaylistSummary) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val cores = M.cores
    Folha(onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Passo.p4),
            ) {
                Artwork(track.artwork, Modifier.size(Passo.p8))
                Column(Modifier.weight(1f)) {
                    Rotulo("Adicionar à playlist", style = Tipo.cota)
                    Text(track.title, style = Tipo.corpoForte, color = cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Botao(
                "Criar playlist",
                onClick = { creating = true },
                modifier = Modifier.padding(horizontal = Passo.p5, vertical = Passo.p3),
            )
            Fio(Modifier.fillMaxWidth().padding(top = Passo.p3))
            LazyColumn(Modifier.padding(bottom = Passo.p5)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(playlist) }.padding(horizontal = Passo.p5, vertical = Passo.p3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Passo.p5),
                    ) {
                        Artwork(playlist.artwork, Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = Tipo.corpoForte, color = cores.tinta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(songCount(playlist.trackCount), style = Tipo.nota, color = cores.tintaSuave)
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        TextInputDialog(
            title = "Nova playlist",
            confirmLabel = "Criar",
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
            onDismiss = { creating = false },
        )
    }
}
