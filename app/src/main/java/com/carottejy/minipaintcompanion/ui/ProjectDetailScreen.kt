package com.carottejy.minipaintcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Repository

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    data: AppData,
    repo: Repository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val project = data.projects.firstOrNull { it.id == projectId }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    if (project != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Modifier") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Supprimer") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (project == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Ce projet n'existe plus.")
            }
            return@Scaffold
        }
        val paintsById = remember(data) { repo.allPaints(data).associateBy { it.id } }
        val photo = repo.photoFile(project.photo)

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 48.dp),
        ) {
            item {
                ProjectPhoto(
                    photo,
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .aspectRatio(0.85f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = photo != null) { fullPhoto = true },
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(project.name.ifBlank { "Sans titre" }, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Commencée le ${formatDate(project.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (project.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(project.notes, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // Récapitulatif des pots
            val used = project.steps.flatMap { it.paintIds }.distinct().mapNotNull { paintsById[it] }
            if (used.isNotEmpty()) {
                item {
                    val missing = used.count { it.id !in data.ownedPaintIds }
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp)
                    ) {
                        Text("PALETTE · ${used.size} POTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            used.forEach { PaintSwatch(it.hex, 28.dp) }
                        }
                        if (missing > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "$missing pot${if (missing > 1) "s" else ""} absent${if (missing > 1) "s" else ""} de ta collection",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            val missingIds = used.map { it.id }.filter { it !in data.ownedPaintIds }
                            val notListed = missingIds.filter { it !in data.toBuyIds }
                            if (notListed.isEmpty()) {
                                Text(
                                    "Ils sont tous dans ta liste d'achat.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                TextButton(onClick = { repo.addAllToBuy(notListed) }) {
                                    Text("Ajouter ${notListed.size} pot${if (notListed.size > 1) "s" else ""} à la liste d'achat")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Étapes de peinture",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
                )
                if (project.steps.isEmpty()) {
                    Text(
                        "Aucune étape renseignée.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            itemsIndexed(project.steps, key = { _, s -> s.id }) { index, step ->
                val isLast = index == project.steps.lastIndex
                Row(Modifier.padding(horizontal = 16.dp).height(IntrinsicSize.Min)) {
                    // Fil de la chronologie
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp).fillMaxHeight()) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        if (!isLast) {
                            Box(
                                Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(bottom = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                step.title.ifBlank { "Étape ${index + 1}" },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(top = 4.dp),
                            )
                            TechniqueBadge(step.technique)
                        }
                        step.paintIds.mapNotNull { paintsById[it] }.forEach { p ->
                            PaintLine(p, owned = p.id in data.ownedPaintIds)
                        }
                        if (step.notes.isNotBlank()) {
                            Text(
                                step.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        if (fullPhoto && photo != null) {
            Dialog(onDismissRequest = { fullPhoto = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(
                    Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable { fullPhoto = false },
                    contentAlignment = Alignment.Center,
                ) {
                    ProjectPhoto(photo, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }

    if (confirmDelete && project != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce projet ?") },
            text = { Text("La photo et toutes les étapes de « ${project.name.ifBlank { "Sans titre" }} » seront effacées.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repo.deleteProject(project.id)
                    onBack()
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.secondary) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}
