package com.carottejy.minipaintcompanion.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.Brand
import com.carottejy.minipaintcompanion.data.Catalog
import com.carottejy.minipaintcompanion.data.Paint

private fun normalize(s: String) = Catalog.slug(s).replace("-", "")

/**
 * Liste filtrable de peintures (recherche, marque, gamme), regroupée par marque et gamme.
 * Sert au catalogue (cocher ce que je possède) et au choix des pots d'une étape.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaintBrowser(
    paints: List<Paint>,
    ownedIds: Set<String>,
    isChecked: (Paint) -> Boolean,
    onToggle: (Paint, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showOwnedFilter: Boolean = false,
    initialOnlyOwned: Boolean = false,
    showToBuy: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
    onRowClick: ((Paint) -> Unit)? = null,
    rowTrailing: (@Composable (Paint) -> Unit)? = null,
    emptyText: String? = null,
    initialQuery: String = "",
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var brandName by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyOwned by rememberSaveable { mutableStateOf(initialOnlyOwned) }

    val brand = brandName?.let { n -> Brand.entries.firstOrNull { it.name == n } }
    val brands = remember(paints) { paints.map { it.brand }.distinct() }
    val ranges = remember(paints, brand) {
        if (brand == null) emptyList() else paints.filter { it.brand == brand }.map { it.range }.distinct()
    }

    val filtered = remember(paints, query, brand, range, onlyOwned, ownedIds) {
        val q = normalize(query)
        paints.filter { p ->
            (brand == null || p.brand == brand) &&
                (range == null || p.range == range) &&
                (!onlyOwned || p.id in ownedIds) &&
                (q.isEmpty() || normalize(p.name + p.code + p.range + p.brand.label).contains(q) ||
                    (q.length >= 6 && q.all { it.isDigit() } && Catalog.barcodesFor(p).any { it.contains(q) }))
        }
    }
    val groups = remember(filtered) { filtered.groupBy { "${it.brand.shortLabel()} · ${it.range}" } }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Nom, référence ou code-barres…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                    Icon(Icons.Default.Close, contentDescription = "Effacer")
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = brand == null,
                    onClick = { brandName = null; range = null },
                    label = { Text("Toutes") },
                )
            }
            items(brands) { b ->
                FilterChip(
                    selected = brand == b,
                    onClick = {
                        brandName = if (brand == b) null else b.name
                        range = null
                    },
                    label = { Text(b.shortLabel()) },
                )
            }
        }
        if (ranges.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ranges) { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = { range = if (range == r) null else r },
                        label = { Text(r) },
                    )
                }
            }
        }
        if (showOwnedFilter) {
            Row(
                Modifier.fillMaxWidth().clickable { onlyOwned = !onlyOwned }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Seulement ma collection", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = onlyOwned, onCheckedChange = { onlyOwned = it })
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

        if (filtered.isEmpty()) {
            Text(
                emptyText ?: if (onlyOwned) "Aucun pot de ta collection ne correspond. Désactive le filtre pour voir tout le catalogue."
                else "Aucun pot ne correspond à ta recherche.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }

        LazyColumn(contentPadding = contentPadding) {
            groups.forEach { (title, list) ->
                stickyHeader(key = "h_$title") {
                    Text(
                        "${title.uppercase()}  ·  ${list.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(list, key = { it.id }) { p ->
                    val checked = isChecked(p)
                    PaintLine(
                        paint = p,
                        owned = if (showToBuy) p.id in ownedIds else null,
                        modifier = Modifier
                            .clickable { if (onRowClick != null) onRowClick(p) else onToggle(p, !checked) }
                            .padding(horizontal = 16.dp),
                        trailing = {
                            if (rowTrailing != null) rowTrailing(p)
                            else Checkbox(checked = checked, onCheckedChange = { onToggle(p, it) })
                        },
                    )
                }
            }
        }
    }
}
