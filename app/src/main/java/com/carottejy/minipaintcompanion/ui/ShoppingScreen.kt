package com.carottejy.minipaintcompanion.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Brand
import com.carottejy.minipaintcompanion.data.Repository

/** Onglet « Achats » : les pots à acheter ou à racheter. */
@Composable
fun ShoppingScreen(
    data: AppData,
    repo: Repository,
    onOpenCatalog: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val all = remember(data.customPaints) { repo.allPaints(data) }
    val list = remember(all, data.toBuyIds) {
        all.filter { it.id in data.toBuyIds }.sortedWith(compareBy({ it.brand.ordinal }, { it.range }, { it.name }))
    }

    if (list.isEmpty()) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                Icons.Outlined.ShoppingCart,
                "Rien à acheter",
                "Ajoute des pots depuis le catalogue, ta collection (pot vide à racheter), une réalisation ou l'onglet Couleurs.",
                Modifier.weight(1f),
            )
            Button(onClick = onOpenCatalog, modifier = Modifier.padding(bottom = 120.dp)) { Text("Ouvrir le catalogue") }
        }
        return
    }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Text(
                "${list.size} pot${if (list.size > 1) "s" else ""} à acheter. Coche un pot acheté : il rejoint ta collection. " +
                    "En boutique, tu peux aussi scanner les pots achetés.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilledTonalButton(onClick = {
                    val text = buildString {
                        appendLine("Pots à acheter (MiniPaintCompanion)")
                        list.groupBy { it.brand }.forEach { (brand, paints) ->
                            appendLine()
                            appendLine(if (brand == Brand.CUSTOM) "Autres" else brand.label)
                            paints.forEach { appendLine("☐ ${it.displayName} — ${it.range}") }
                        }
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Partager la liste d'achat"))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Partager")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onScan) { Text("Scanner un achat") }
            }
        }
        list.groupBy { "${it.brand.shortLabel()} · ${it.range}" }.forEach { (title, paints) ->
            item(key = "h_$title") {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(paints, key = { it.id }) { p ->
                val rebuy = p.id in data.ownedPaintIds
                Row(Modifier.padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = false, onCheckedChange = { repo.markBought(p.id) })
                    Column(Modifier.weight(1f)) {
                        PaintLine(p)
                    }
                    if (rebuy) {
                        Text(
                            "à racheter",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    IconButton(onClick = { repo.setToBuy(p.id, false) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Retirer de la liste", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
