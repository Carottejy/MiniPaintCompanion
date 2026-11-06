package com.carottejy.minipaintcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Brand
import com.carottejy.minipaintcompanion.data.Paint
import com.carottejy.minipaintcompanion.data.Repository

/** Onglet « Mes peintures » : les pots que je possède. */
@Composable
fun CollectionScreen(
    data: AppData,
    repo: Repository,
    onOpenCatalog: () -> Unit,
    onScan: () -> Unit,
    onHarmonies: (Paint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = remember(data) { repo.allPaints(data) }
    val owned = remember(all, data.ownedPaintIds) { all.filter { it.id in data.ownedPaintIds } }
    val usage = remember(data.projects) {
        data.projects.flatMap { p -> p.steps.flatMap { it.paintIds }.distinct().map { it to p } }
            .groupBy({ it.first }, { it.second })
    }
    var selected by remember { mutableStateOf<Paint?>(null) }
    var managingPrimers by remember { mutableStateOf(false) }

    if (owned.isEmpty()) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                Icons.Outlined.Inventory2,
                "Aucune peinture pour l'instant",
                "Parcours les catalogues Citadel, Vallejo, Army Painter et Prince August et coche les pots que tu as sur ton étagère.",
                Modifier.weight(1f),
            )
            Row(Modifier.padding(bottom = 120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenCatalog) { Text("Ouvrir le catalogue") }
                OutlinedButton(onClick = onScan) {
                    Icon(Icons.Outlined.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Identifier un pot")
                }
            }
        }
        return
    }

    Column(modifier) {
        val primerCount = repo.primerIds(data).size
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { managingPrimers = true }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.FormatColorFill, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Mes sous-couches ($primerCount)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("Gérer", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val byBrand = owned.groupingBy { it.brand }.eachCount()
            Brand.entries.filter { (byBrand[it] ?: 0) > 0 }.forEach { b ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("${byBrand[b]}", style = MaterialTheme.typography.titleLarge)
                    Text(b.shortLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PaintBrowser(
            paints = owned,
            ownedIds = data.ownedPaintIds,
            isChecked = { true },
            onToggle = { _, _ -> },
            onRowClick = { selected = it },
            rowTrailing = { p ->
                val n = usage[p.id]?.size ?: 0
                if (n > 0) {
                    Text(
                        "$n sch.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (p.id in data.toBuyIds) {
                    Icon(
                        Icons.Filled.ShoppingCart,
                        contentDescription = "À racheter",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 8.dp).size(18.dp),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (managingPrimers) PrimerManagerSheet(data, repo, onDismiss = { managingPrimers = false })

    selected?.let { p ->
        val used = usage[p.id].orEmpty()
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = { PaintSwatch(p.hex, 48.dp) },
            title = { Text(p.displayName) },
            text = {
                Column {
                    Text("${p.brand.label} · ${p.range}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    if (used.isEmpty()) Text("Pas encore utilisé dans un schéma.")
                    else {
                        Text("Utilisé dans :")
                        used.forEach { Text("• ${it.name.ifBlank { "Sans titre" }}") }
                    }
                    Spacer(Modifier.height(16.dp))
                    val toBuy = p.id in data.toBuyIds
                    FilledTonalButton(
                        onClick = { repo.setToBuy(p.id, !toBuy) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (toBuy) Icons.Filled.ShoppingCart else Icons.Outlined.AddShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (toBuy) "Dans la liste d'achat (retirer)" else "Pot presque vide : à racheter")
                    }
                    OutlinedButton(
                        onClick = { selected = null; onHarmonies(p) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Harmonies de couleurs")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (p.brand == Brand.CUSTOM) repo.deleteCustomPaint(p.id) else repo.setOwned(p.id, false)
                    selected = null
                }) {
                    Text(
                        if (p.brand == Brand.CUSTOM) "Supprimer ce pot" else "Retirer de ma collection",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Fermer") } },
        )
    }
}

/** Onglet « Catalogue » : je coche les pots que je possède, 🛒 pour la liste d'achat. */
@Composable
fun CatalogScreen(data: AppData, repo: Repository, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "${data.ownedPaintIds.size} pots possédés · ${data.toBuyIds.size} à acheter — coche ce que tu as, 🛒 pour la liste d'achat",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PaintBrowser(
            paints = remember(data.customPaints) { repo.allPaints(data) },
            ownedIds = data.ownedPaintIds,
            isChecked = { it.id in data.ownedPaintIds },
            onToggle = { p, owned -> repo.setOwned(p.id, owned) },
            rowTrailing = { p ->
                val inList = p.id in data.toBuyIds
                IconButton(onClick = { repo.setToBuy(p.id, !inList) }) {
                    Icon(
                        if (inList) Icons.Filled.ShoppingCart else Icons.Outlined.AddShoppingCart,
                        contentDescription = if (inList) "Retirer de la liste d'achat" else "Ajouter à la liste d'achat",
                        tint = if (inList) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(checked = p.id in data.ownedPaintIds, onCheckedChange = { repo.setOwned(p.id, it) })
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private val PRESETS = listOf(
    "#F4F4F2", "#A8A8A0", "#5A5E62", "#1B1B1B", "#E8DFC4", "#C8A97A", "#D9A07A", "#9A5E3E",
    "#7A4A2A", "#4A3022", "#F5D21E", "#E8641E", "#B81E1E", "#6E1A1C", "#A02A5A", "#5A3A7A",
    "#1E2A4A", "#2A4A8A", "#6AB4DE", "#1E8A8A", "#2E7A3A", "#8DB43A", "#4E4A2E", "#C8A040",
)

/** Ajout d'un pot hors catalogue (autre marque, gamme manquante…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomPaintDialog(repo: Repository, onDismiss: () -> Unit, primer: Boolean = false) {
    var name by remember { mutableStateOf("") }
    var range by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf("#C8553D") }
    val validHex = Regex("^#[0-9A-Fa-f]{6}$").matches(hex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (primer) "Ajouter une sous-couche perso" else "Ajouter un pot perso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(
                    range, { range = it },
                    label = { Text(if (primer) "Marque" else "Marque / gamme") },
                    placeholder = { Text(if (primer) "ex. Tamiya, Mr. Surfacer, Stynylrez" else "ex. Scale75, Two Thin Coats") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PaintSwatch(if (validHex) hex else "#808080", 40.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        hex, { hex = it.take(7) },
                        label = { Text("Teinte") },
                        isError = !validHex,
                        singleLine = true,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESETS.forEach { c ->
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(hexColor(c))
                                .border(
                                    if (c.equals(hex, ignoreCase = true)) 2.dp else 1.dp,
                                    if (c.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                    CircleShape,
                                )
                                .clickable { hex = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && validHex,
                onClick = {
                    if (primer) repo.addCustomPrimer(name, range, hex.uppercase())
                    else repo.addCustomPaint(name, range, hex.uppercase())
                    onDismiss()
                },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
