package com.carottejy.minipaintcompanion.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Folder
import com.carottejy.minipaintcompanion.data.Friends
import com.carottejy.minipaintcompanion.data.Project
import com.carottejy.minipaintcompanion.data.Repository
import kotlinx.coroutines.launch

/**
 * Mes schémas : dossiers puis schémas hors dossier ; dans un dossier, ses schémas.
 * Appui long sur un schéma : le déplacer vers un dossier.
 */
@Composable
fun ProjectsScreen(
    data: AppData,
    repo: Repository,
    onOpen: (Project) -> Unit,
    openFolder: String?,
    onOpenFolder: (String?) -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var newFolder by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Project?>(null) }
    val folder = data.folder(openFolder)
    BackHandler(enabled = folder != null) { onOpenFolder(null) }

    if (data.projects.isEmpty() && data.folders.isEmpty()) {
        EmptyState(
            Icons.Outlined.AutoAwesome,
            "Aucun schéma",
            "Photographie ta figurine terminée et note chaque étape : sous-couche, aplats, lavis, éclaircissements…",
            modifier,
        )
        return
    }
    val shown = data.projects.filter { it.folderId == folder?.id || (folder == null && data.folder(it.folderId) == null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (folder != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FolderHeader(folder, shown.size, data, repo, onBack = { onOpenFolder(null) }, onOpenFriends = onOpenFriends)
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val paintsUsed = data.projects.flatMap { p -> p.steps.flatMap { it.allPaintIds } }.distinct().size
                    Text(
                        "${data.projects.size} schéma${if (data.projects.size > 1) "s" else ""} · $paintsUsed pots utilisés",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { newFolder = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Dossier")
                    }
                }
            }
            items(data.folders, key = { "folder:" + it.id }) { f ->
                FolderCard(
                    f,
                    count = data.projects.count { it.folderId == f.id },
                    shared = f.id in data.friendSharedFolderIds && data.friendSecret != null,
                    cover = data.projects.firstOrNull { it.folderId == f.id && it.photo != null }?.let { repo.photoFile(it.photo) },
                    onClick = { onOpenFolder(f.id) },
                )
            }
        }
        if (shown.isEmpty() && folder != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Dossier vide. Crée un schéma ici avec « Nouveau schéma », ou fais un appui long sur un schéma pour l'y ranger.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        items(shown, key = { it.id }) { p ->
            ProjectCard(p, repo, shared = data.friendSecret != null && data.visibleToFriends(p), onClick = { onOpen(p) }, onLongClick = { moving = p })
        }
    }

    if (newFolder) {
        FolderNameDialog(
            title = "Nouveau dossier",
            initial = "",
            onDismiss = { newFolder = false },
            onConfirm = { name -> newFolder = false; repo.createFolder(name) },
        )
    }
    moving?.let { p ->
        MoveToFolderDialog(
            data = data,
            repo = repo,
            current = p.folderId,
            title = "Ranger « ${p.name.ifBlank { "Sans titre" }} »",
            onDismiss = { moving = null },
            onPick = { fid -> moving = null; repo.moveToFolder(listOf(p.id), fid) },
        )
    }
}

@Composable
private fun FolderHeader(
    folder: Folder,
    count: Int,
    data: AppData,
    repo: Repository,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val shared = folder.id in data.friendSharedFolderIds
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tous mes schémas") }
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$count schéma${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Modifier le dossier") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer le dossier") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Dossier visible par mes amis", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        data.friendSecret == null -> "Crée ton profil d'amis pour partager ce dossier"
                        busy -> "Envoi en cours…"
                        shared -> "Tes amis voient ce dossier et tous les schémas qui y sont rangés"
                        else -> "Seuls les schémas rendus visibles un par un sont partagés"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (data.friendSecret == null) {
                TextButton(onClick = onOpenFriends) { Text("Mes amis") }
            } else {
                Switch(
                    checked = shared,
                    enabled = !busy,
                    onCheckedChange = { on ->
                        scope.launch {
                            busy = true
                            runCatching { Friends.setFolderShared(repo, folder.id, on) }
                                .onFailure { Toast.makeText(context, it.message ?: "Erreur", Toast.LENGTH_LONG).show() }
                            busy = false
                        }
                    },
                )
            }
        }
    }
    if (renaming) {
        FolderNameDialog(
            title = "Renommer le dossier",
            initial = folder.name,
            onDismiss = { renaming = false },
            onConfirm = { name -> renaming = false; repo.renameFolder(folder.id, name) },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Supprimer « ${folder.name} » ?") },
            text = { Text("Les schémas qu'il contient ne sont pas supprimés : ils reviennent dans « Mes schémas ».") },
            confirmButton = {
                TextButton(onClick = { deleting = false; onBack(); repo.deleteFolder(folder.id) }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun FolderCard(folder: Folder, count: Int, shared: Boolean, cover: java.io.File?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(18.dp))) {
            if (cover != null) ProjectPhoto(cover, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = if (cover != null) 0.35f else 0f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$count schéma${if (count > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (shared) Icon(Icons.Outlined.Group, contentDescription = "Visible par mes amis", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(project: Project, repo: Repository, shared: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            ProjectPhoto(
                repo.photoFile(project.photo),
                Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                project.name.ifBlank { "Sans titre" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${project.steps.count { !it.group }} étape${if (project.steps.count { !it.group } > 1) "s" else ""} · ${formatDate(project.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (shared) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.Group, contentDescription = "Visible par mes amis", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                project.steps.filterNot { it.group }.map { it.technique }.distinct().forEach { t ->
                    Spacer(Modifier.width(4.dp))
                    Icon(t.icon, contentDescription = t.label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun FolderNameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = v,
                onValueChange = { v = it.take(60) },
                label = { Text("Nom du dossier") },
                placeholder = { Text("ex. Space Marines, Orks, Socles…") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(v) }, enabled = v.isNotBlank()) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Choix du dossier où ranger un schéma (ou création d'un nouveau dossier). */
@Composable
fun MoveToFolderDialog(
    data: AppData,
    repo: Repository,
    current: String?,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        FolderNameDialog("Nouveau dossier", "", onDismiss = { creating = false }) { name ->
            creating = false
            onPick(repo.createFolder(name).id)
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Aucun dossier") },
                        leadingContent = { Icon(Icons.Outlined.FolderOff, contentDescription = null) },
                        trailingContent = { if (current == null || data.folder(current) == null) Text("actuel") },
                        modifier = Modifier.clickable { onPick(null) },
                    )
                }
                items(data.folders, key = { it.id }) { f ->
                    ListItem(
                        headlineContent = { Text(f.name) },
                        supportingContent = if (f.id in data.friendSharedFolderIds && data.friendSecret != null) {
                            { Text("Visible par mes amis") }
                        } else null,
                        leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { if (current == f.id) Text("actuel") },
                        modifier = Modifier.clickable { onPick(f.id) },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Nouveau dossier…") },
                        leadingContent = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                        modifier = Modifier.clickable { creating = true },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
