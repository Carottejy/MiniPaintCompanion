package com.carottejy.minipaintcompanion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Brand
import com.carottejy.minipaintcompanion.data.Repository

/**
 * Gestion de ma liste de sous-couches : on coche les bombes / primers utilisés parmi le catalogue
 * (Citadel, Vallejo, Army Painter, AK) et on peut créer ses propres sous-couches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimerManagerSheet(data: AppData, repo: Repository, onDismiss: () -> Unit) {
    var addingCustom by remember { mutableStateOf(false) }
    val inList = repo.primerIds(data)
    val primers = remember(data.customPaints) { repo.allPrimers(data) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mes sous-couches", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${inList.size} dans ma liste · coche celles que tu utilises",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = { addingCustom = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Perso")
            }
        }
        PaintBrowser(
            paints = primers.sortedByDescending { it.id in inList },
            ownedIds = data.ownedPaintIds,
            isChecked = { it.id in inList },
            onToggle = { p, checked -> repo.setInPrimerList(p.id, checked) },
            rowTrailing = { p ->
                if (p.brand == Brand.CUSTOM) {
                    androidx.compose.material3.TextButton(onClick = { repo.deleteCustomPaint(p.id) }) { Text("Supprimer") }
                }
                androidx.compose.material3.Checkbox(checked = p.id in inList, onCheckedChange = { repo.setInPrimerList(p.id, it) })
            },
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (addingCustom) CustomPaintDialog(repo, onDismiss = { addingCustom = false }, primer = true)
}
