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
import androidx.compose.material.icons.outlined.Share
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
import com.carottejy.minipaintcompanion.data.groupOwners
import com.carottejy.minipaintcompanion.data.groupSizes
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.carottejy.minipaintcompanion.data.Repository
import com.carottejy.minipaintcompanion.data.Friends
import com.carottejy.minipaintcompanion.data.Project
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    data: AppData,
    repo: Repository,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    /** Schéma d'un ami (lecture seule) : son code ami. */
    friendCode: String? = null,
    onOpenFriends: () -> Unit = {},
    onCopied: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val friendData = friendCode?.let { Friends.cache[it] }
    val friendEntry = friendData?.schemas?.firstOrNull { it.projectId == projectId }
    val isFriend = friendCode != null
    val project = if (isFriend) friendEntry?.schema?.project else data.projects.firstOrNull { it.id == projectId }
    var copying by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullPhoto by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    if (project != null && isFriend && friendEntry != null) {
                        TextButton(
                            enabled = !copying,
                            onClick = {
                                scope.launch {
                                    copying = true
                                    runCatching {
                                        val bytes = friendEntry.photoUrl?.let { runCatching { Friends.download(it) }.getOrNull() }
                                        repo.importFriendSchema(friendEntry.schema, bytes)
                                    }.onSuccess { p ->
                                        Toast.makeText(context, "Copié dans tes schémas", Toast.LENGTH_SHORT).show()
                                        onCopied(p.id)
                                    }.onFailure {
                                        Toast.makeText(context, it.message ?: "Copie impossible", Toast.LENGTH_LONG).show()
                                    }
                                    copying = false
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copying) "Copie…" else "Copier dans mes schémas")
                        }
                    }
                    if (project != null && !isFriend) {
                        IconButton(onClick = { sharing = true }) { Icon(Icons.Outlined.Share, contentDescription = "Partager") }
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
                Text(if (isFriend) "Ce schéma n'est plus disponible : reviens à la liste de ton ami." else "Ce schéma n'existe plus.")
            }
            return@Scaffold
        }
        val paintsById = remember(data, friendEntry) {
            (repo.allPaints(data) + friendEntry?.schema?.customPaints.orEmpty()).associateBy { it.id }
        }
        val photo: Any? = if (isFriend) friendEntry?.photoUrl else repo.photoFile(project.photo)

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
                        if (isFriend) "Schéma de ${friendData?.pseudo.orEmpty()} · mis à jour le ${formatDate(project.updatedAt)}"
                        else "Commencée le ${formatDate(project.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isFriend) {
                        Spacer(Modifier.height(8.dp))
                        FolderRow(project, data, repo)
                        Spacer(Modifier.height(8.dp))
                        FriendVisibilityRow(project, data, repo, onOpenFriends)
                    } else friendEntry?.schema?.folderName?.let { fn ->
                        Spacer(Modifier.height(6.dp))
                        InfoChip("Dossier · $fn")
                    }
                    if (project.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(project.notes, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // Récapitulatif des pots
            val used = project.steps.flatMap { it.allPaintIds }.distinct().mapNotNull { paintsById[it] }
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

            val owners = project.steps.groupOwners()
            val sizes = project.steps.groupSizes()
            itemsIndexed(project.steps, key = { _, s -> s.id }) { position, step ->
                if (step.group) {
                    // En-tête de groupe : touche pour replier / ouvrir
                    val open = step.id !in collapsed
                    val n = sizes[step.id] ?: 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                            .clickable { collapsed = if (open) collapsed + step.id else collapsed - step.id }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = if (open) "Replier" else "Ouvrir",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            step.title.ifBlank { "Groupe" }.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$n étape${if (n > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    return@itemsIndexed
                }
                if (owners[position] != null && owners[position] in collapsed) return@itemsIndexed
                val index = project.steps.take(position).count { !it.group }
                val isLast = project.steps.drop(position + 1).let { rest -> rest.isEmpty() || rest.first().group }
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
                        if (step.primer || step.dilution > 0) {
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (step.primer) InfoChip("Sous-couche")
                                if (step.dilution > 0) InfoChip("Dilution ${step.dilution} %")
                            }
                        }
                        step.paintIds.mapNotNull { paintsById[it] }.forEach { p ->
                            PaintLine(p, owned = p.id in data.ownedPaintIds)
                        }
                        step.mixes.forEach { mix ->
                            val a = paintsById[mix.paintA]
                            val b = paintsById[mix.paintB]
                            if (a != null && b != null) {
                                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PaintSwatch(com.carottejy.minipaintcompanion.data.ColorTheory.mixHex(a.hex, b.hex, mix.ratioA), 28.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Mélange ${ratioLabel(mix.ratioA)}", style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            "${mix.ratioA} % ${a.displayName} + ${mix.ratioB} % ${b.displayName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
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

    if (sharing && project != null) {
        ShareSchemaDialog(project, data, repo, onDismiss = { sharing = false })
    }

    if (confirmDelete && project != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce schéma ?") },
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

/** Interrupteur « Visible par mes amis » sur la fiche d'un de mes schémas. */
@Composable
private fun FriendVisibilityRow(project: Project, data: AppData, repo: Repository, onOpenFriends: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val shared = project.id in data.friendSharedIds
    val viaFolder = data.folder(project.folderId)?.takeIf { it.id in data.friendSharedFolderIds }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Visible par mes amis", style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    data.friendSecret == null -> "Crée ton profil d'amis pour partager ce schéma"
                    busy -> "Envoi en cours…"
                    viaFolder != null -> "Visible via le dossier « ${viaFolder.name} »"
                    shared -> "Tes amis voient ce schéma et ses mises à jour"
                    else -> "Seul toi vois ce schéma"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (data.friendSecret == null) {
            TextButton(onClick = onOpenFriends) { Text("Mes amis") }
        } else {
            Switch(
                checked = shared || viaFolder != null,
                enabled = !busy && viaFolder == null,
                onCheckedChange = { on ->
                    scope.launch {
                        busy = true
                        runCatching { Friends.setShared(repo, project, on) }
                            .onFailure { Toast.makeText(context, it.message ?: "Erreur", Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                },
            )
        }
    }
}

/** Ligne « Dossier » : touche pour ranger le schéma ailleurs. */
@Composable
private fun FolderRow(project: Project, data: AppData, repo: Repository) {
    var picking by remember { mutableStateOf(false) }
    val folder = data.folder(project.folderId)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { picking = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(androidx.compose.material.icons.Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Dossier", style = MaterialTheme.typography.bodyLarge)
            Text(
                folder?.name ?: "Aucun — touche pour ranger ce schéma",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Changer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    if (picking) {
        MoveToFolderDialog(
            data = data,
            repo = repo,
            current = project.folderId,
            title = "Ranger dans un dossier",
            onDismiss = { picking = false },
            onPick = { fid -> picking = false; repo.moveToFolder(listOf(project.id), fid) },
        )
    }
}
