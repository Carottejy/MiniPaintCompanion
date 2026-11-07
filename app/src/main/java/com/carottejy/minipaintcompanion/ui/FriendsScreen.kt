package com.carottejy.minipaintcompanion.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Friend
import com.carottejy.minipaintcompanion.data.FriendSchemas
import com.carottejy.minipaintcompanion.data.Friends
import com.carottejy.minipaintcompanion.data.Repository
import kotlinx.coroutines.launch

/** Mes amis : profil (pseudo + code ami), invitations, liste d'amis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    data: AppData,
    repo: Repository,
    onBack: () -> Unit,
    onOpenFriend: (Friend) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<Friend>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var editPseudo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Friend?>(null) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun refresh() {
        if (data.friendSecret == null) return
        scope.launch {
            loading = true
            runCatching { Friends.friends(repo) }
                .onSuccess { friends = it; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }
    fun run(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            runCatching { block() }.onFailure { toast(it.message ?: "Erreur") }
            loading = false
            refresh()
        }
    }
    LaunchedEffect(data.friendSecret) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes amis") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    if (data.friendSecret != null) {
                        IconButton(onClick = ::refresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Plus d'options") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Changer de pseudo") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = { menuOpen = false; editPseudo = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer mon profil") },
                                    leadingIcon = { Icon(Icons.Outlined.PersonRemove, contentDescription = null) },
                                    onClick = { menuOpen = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (data.friendSecret == null || data.friendCode == null) {
            ProfileSetup(repo, Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val list = friends.orEmpty()
        val incoming = list.filter { it.status == "incoming" }
        val accepted = list.filter { it.status == "friend" }
        val outgoing = list.filter { it.status == "outgoing" }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MyCodeCard(data.friendPseudo.orEmpty(), data.friendCode) }
            item { AddFriendCard(onAdd = { code ->
                run {
                    val (status, pseudo) = Friends.add(repo, code)
                    toast(if (status == "friend") "Vous êtes maintenant amis avec $pseudo !" else "Invitation envoyée à $pseudo")
                }
            }) }
            if (loading && friends == null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            error?.let { e ->
                item { Text(e, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp)) }
            }
            if (incoming.isNotEmpty()) {
                item { SectionTitle("Invitations reçues") }
                items(incoming, key = { "in" + it.code }) { f ->
                    FriendRow(f, trailing = {
                        IconButton(onClick = { run { Friends.accept(repo, f.code); toast("Vous êtes maintenant amis avec ${f.pseudo} !") } }) {
                            Icon(Icons.Outlined.Check, contentDescription = "Accepter", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { run { Friends.remove(repo, f.code) } }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Refuser")
                        }
                    })
                }
            }
            if (friends != null) {
                item { SectionTitle("Amis · ${accepted.size}") }
                if (accepted.isEmpty()) {
                    item {
                        Text(
                            "Donne ton code à tes amis ou saisis le leur : une fois l'invitation acceptée, vous verrez les schémas que chacun rend visibles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                items(accepted, key = { "f" + it.code }) { f ->
                    FriendRow(
                        f,
                        subtitle = "${f.schemaCount} schéma${if (f.schemaCount > 1) "s" else ""} partagé${if (f.schemaCount > 1) "s" else ""}",
                        onClick = { onOpenFriend(f) },
                        trailing = {
                            IconButton(onClick = { confirmRemove = f }) {
                                Icon(Icons.Outlined.PersonRemove, contentDescription = "Retirer de mes amis")
                            }
                        },
                    )
                }
            }
            if (outgoing.isNotEmpty()) {
                item { SectionTitle("Invitations envoyées") }
                items(outgoing, key = { "out" + it.code }) { f ->
                    FriendRow(f, subtitle = "En attente de réponse", trailing = {
                        TextButton(onClick = { run { Friends.remove(repo, f.code) } }) { Text("Annuler") }
                    })
                }
            }
        }
    }

    if (editPseudo) {
        PseudoDialog(
            initial = data.friendPseudo.orEmpty(),
            onDismiss = { editPseudo = false },
            onConfirm = { p -> editPseudo = false; run { Friends.register(repo, p) } },
        )
    }
    confirmRemove?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Retirer ${f.pseudo} ?") },
            text = { Text("Vous ne verrez plus les schémas l'un de l'autre.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; run { Friends.remove(repo, f.code) } }) { Text("Retirer") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Annuler") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer mon profil ?") },
            text = { Text("Ton pseudo, ton code ami, tes amitiés et les schémas visibles par tes amis seront effacés du serveur. Tes schémas restent sur ton téléphone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; run { Friends.deleteProfile(repo); friends = null } }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProfileSetup(repo: Repository, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pseudo by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(modifier.padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text("Partage tes schémas avec tes amis", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pas de compte à créer : choisis un pseudo et tu recevras un code ami à donner à tes amis. " +
                "Seuls les schémas que tu rends visibles (depuis leur fiche) sont partagés, et uniquement avec tes amis.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = pseudo,
            onValueChange = { pseudo = it.take(40) },
            label = { Text("Pseudo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    busy = true
                    runCatching { Friends.register(repo, pseudo) }
                        .onFailure { Toast.makeText(context, it.message ?: "Erreur", Toast.LENGTH_LONG).show() }
                    busy = false
                }
            },
            enabled = pseudo.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Création…" else "Créer mon profil") }
        Text(
            "Ton identité est incluse dans « Sauvegarder mes données » : restaure la sauvegarde pour la retrouver sur un autre téléphone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MyCodeCard(pseudo: String, code: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("MON CODE AMI · $pseudo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(code, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    Toast.makeText(context, "Code copié", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Copier")
                }
                FilledTonalButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Ajoute-moi comme ami sur MiniPaintCompanion avec mon code : $code")
                    }
                    context.startActivity(Intent.createChooser(send, "Envoyer mon code ami"))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Envoyer")
                }
            }
        }
    }
}

@Composable
private fun AddFriendCard(onAdd: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(12) },
            label = { Text("Code ami à ajouter") },
            placeholder = { Text("MPC-…") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = { onAdd(code.trim()); code = "" }, enabled = code.trim().length >= 6) {
            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text("Inviter")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp),
    )
}

@Composable
private fun FriendRow(
    f: Friend,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(f.pseudo.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            },
            headlineContent = { Text(f.pseudo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(subtitle ?: f.code) },
            trailingContent = { Row { trailing() } },
        )
    }
}

@Composable
private fun PseudoDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mon pseudo") },
        text = { OutlinedTextField(value = v, onValueChange = { v = it.take(40) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(v) }, enabled = v.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Galerie des schémas qu'un ami a rendus visibles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendSchemasScreen(
    code: String,
    pseudo: String,
    repo: Repository,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var result by remember { mutableStateOf(Friends.cache[code]) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() {
        scope.launch {
            loading = true
            runCatching { Friends.schemasOf(repo, code) }
                .onSuccess { result = it; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(code) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schémas de ${result?.pseudo?.ifBlank { null } ?: pseudo}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = { IconButton(onClick = ::load) { Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val m = Modifier.fillMaxSize().padding(padding)
        val r: FriendSchemas? = result
        when {
            r == null && loading -> Box(m, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            r == null -> Box(m.padding(24.dp), contentAlignment = Alignment.Center) { Text(error ?: "Impossible de charger les schémas.") }
            r.schemas.isEmpty() -> EmptyState(
                Icons.Outlined.Group,
                "Aucun schéma partagé",
                "${r.pseudo} n'a encore rendu aucun schéma visible.",
                m,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = m,
            ) {
                error?.let { e ->
                    item(span = { GridItemSpan(maxLineSpan) }) { Text(e, color = MaterialTheme.colorScheme.secondary) }
                }
                items(r.schemas, key = { it.projectId }) { s ->
                    SchemaCard(s.schema.project, s.photoUrl, onClick = { onOpen(s.projectId) })
                }
            }
        }
    }
}

@Composable
fun SchemaCard(project: com.carottejy.minipaintcompanion.data.Project, photo: Any?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ProjectPhoto(photo, Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                project.name.ifBlank { "Sans titre" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val n = project.steps.count { !it.group }
            Text(
                "$n étape${if (n > 1) "s" else ""} · ${formatDate(project.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(20.dp),
            )
        }
    }
}
