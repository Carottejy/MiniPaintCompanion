package com.carottejy.minipaintcompanion.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.R
import com.carottejy.minipaintcompanion.update.Updater

data class ReleaseNote(val title: String, val items: List<String>)

/** Lit les notes de version embarquées (assets/CHANGELOG.md, numérotées par la CI). */
fun loadReleaseNotes(context: Context): List<ReleaseNote> {
    val text = runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() } }.getOrDefault("")
    val notes = mutableListOf<ReleaseNote>()
    var title: String? = null
    val items = mutableListOf<String>()
    fun flush() {
        title?.let { notes += ReleaseNote(it, items.toList()) }
        items.clear()
    }
    text.lines().forEach { line ->
        when {
            line.startsWith("## ") -> { flush(); title = line.removePrefix("## ").trim() }
            line.startsWith("- ") -> items += line.removePrefix("- ").trim()
            line.isNotBlank() && items.isNotEmpty() -> items[items.lastIndex] = items.last() + " " + line.trim()
        }
    }
    flush()
    // Version non numérotée (build local) : on l'affiche comme « en développement ».
    return notes.map { it.copy(title = it.title.replace("@VERSION@", "En développement").replace("@DATE@", "").trimEnd(' ', '—')) }
}

@Composable
fun InfoScreen(onCheckUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (versionName, build) = remember { Updater.installedVersion(context) }
    val notes = remember { loadReleaseNotes(context) }

    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(26.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Text("MiniPaintCompanion", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Version $versionName (build $build)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (Updater.enabled) "Édition GitHub · mises à jour intégrées" else "Édition Google Play",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (Updater.enabled) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = onCheckUpdate) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Rechercher une mise à jour")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Carnet de peinture de figurines. Les noms de peintures appartiennent à leurs marques respectives " +
                        "(Citadel, Vallejo, The Army Painter, Prince August) ; application non affiliée. " +
                        "Teintes affichées approximatives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Notes de version",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
        items(notes) { note ->
            val current = note.title.substringBefore(" ").trim() == versionName
            Column(
                Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (current) {
                        Text(
                            "INSTALLÉE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    note.items.forEach { item ->
                        Row {
                            Text("•  ", color = MaterialTheme.colorScheme.primary)
                            Text(item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
