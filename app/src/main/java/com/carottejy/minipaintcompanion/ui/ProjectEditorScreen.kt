package com.carottejy.minipaintcompanion.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.compose.material.icons.filled.Mic
import com.carottejy.minipaintcompanion.data.VoiceParser
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Project
import com.carottejy.minipaintcompanion.data.Repository
import com.carottejy.minipaintcompanion.data.Step
import com.carottejy.minipaintcompanion.data.Technique
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val STEP_SUGGESTIONS = listOf(
    "Sous-couche", "Aplats", "Lavis", "Brossage à sec", "Éclaircissements",
    "Glacis", "Détails", "Usure / effets", "Socle", "Vernis",
)

private fun encode(p: Project) = Repository.json.encodeToString(Project.serializer(), p)
private fun decode(s: String) = Repository.json.decodeFromString(Project.serializer(), s)
private const val NEW_STEPS = "__new__"
private fun newStep() = Step(id = UUID.randomUUID().toString())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectEditorScreen(
    projectId: String?,
    data: AppData,
    repo: Repository,
    onDone: (savedId: String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Le brouillon est sérialisé en JSON pour survivre à la rotation et au retour de l'appareil photo.
    val originalJson = rememberSaveable {
        val existing = data.projects.firstOrNull { it.id == projectId }
        encode(existing ?: Project(id = UUID.randomUUID().toString(), steps = listOf(newStep().copy(title = "Sous-couche"))))
    }
    var draftJson by rememberSaveable { mutableStateOf(originalJson) }
    val draft = remember(draftJson) { decode(draftJson) }
    val original = remember(originalJson) { decode(originalJson) }
    fun update(f: (Project) -> Project) { draftJson = encode(f(decode(draftJson))) }
    fun updateStep(stepId: String, f: (Step) -> Step) =
        update { p -> p.copy(steps = p.steps.map { if (it.id == stepId) f(it) else it }) }

    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingStepId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // ---------------------------------------------------------------- dictée vocale
    // Cible : identifiant d'une étape à compléter, ou NEW_STEPS pour créer de nouvelles étapes.
    var voiceTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = voiceTarget
        voiceTarget = null
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode != Activity.RESULT_OK || text.isNullOrBlank() || target == null) return@rememberLauncherForActivityResult
        val parsed = VoiceParser.parse(text, repo.allPaints(data), data.ownedPaintIds)
        if (parsed.isEmpty()) {
            Toast.makeText(context, "Je n'ai rien compris d'utile dans « $text »", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        val nbPaints = parsed.sumOf { it.paintIds.size }
        if (target == NEW_STEPS) {
            update { p ->
                var last = p.steps.lastOrNull()?.technique ?: Technique.BRUSH
                val added = parsed.map { ps ->
                    val t = ps.technique ?: last
                    last = t
                    newStep().copy(title = ps.title, technique = t, paintIds = ps.paintIds, notes = ps.notes)
                }
                // Une étape vide en fin de liste (ex. « Sous-couche » par défaut, sans pot) est remplacée.
                val base = p.steps.toMutableList()
                val tail = base.lastOrNull()
                if (tail != null && tail.paintIds.isEmpty() && tail.notes.isBlank() && base.size == 1 && p.steps.size == 1 && added.first().title.isNotBlank()) {
                    base.removeAt(base.lastIndex)
                }
                p.copy(steps = base + added)
            }
            Toast.makeText(
                context,
                "« $text »\n${parsed.size} étape${if (parsed.size > 1) "s" else ""} ajoutée${if (parsed.size > 1) "s" else ""}, $nbPaints pot${if (nbPaints > 1) "s" else ""} reconnu${if (nbPaints > 1) "s" else ""}. Vérifie avant d'enregistrer.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            updateStep(target) { st -> parsed.fold(st) { acc, ps -> VoiceParser.merge(acc, ps) } }
            Toast.makeText(context, "« $text »\n$nbPaints pot${if (nbPaints > 1) "s" else ""} reconnu${if (nbPaints > 1) "s" else ""}", Toast.LENGTH_LONG).show()
        }
    }

    fun dictate(target: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (target == NEW_STEPS) "Ex. : « Aplats au pinceau avec Macragge Blue, dilué 1 pour 1, ensuite lavis avec Nuln Oil »"
                else "Dis la technique, les pots et tes notes",
            )
            // Aide la reconnaissance à comprendre les noms (anglais) des pots de ta collection.
            if (Build.VERSION.SDK_INT >= 33) {
                val names = repo.allPaints(data).filter { it.id in data.ownedPaintIds }.map { it.name }.distinct().take(300)
                putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", ArrayList(names))
            }
        }
        voiceTarget = target
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            voiceTarget = null
            Toast.makeText(context, "Aucune reconnaissance vocale disponible sur ce téléphone (installe ou active l'appli Google).", Toast.LENGTH_LONG).show()
        }
    }
    val dirty = draftJson != originalJson

    fun replacePhoto(name: String) {
        val current = decode(draftJson).photo
        // Une photo prise pendant cette édition et remplacée aussitôt peut partir à la corbeille.
        if (current != null && current != original.photo) File(repo.photosDir, current).delete()
        update { it.copy(photo = name) }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pendingPhoto
        if (ok && name != null) replacePhoto(name) else name?.let { File(repo.photosDir, it).delete() }
        pendingPhoto = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = repo.importPhoto(uri)
            if (name != null) replacePhoto(name)
            else Toast.makeText(context, "Impossible d'importer cette image", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        val file = repo.newPhotoFile()
        pendingPhoto = file.name
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingPhoto = null
            Toast.makeText(context, "Aucune application appareil photo trouvée", Toast.LENGTH_SHORT).show()
        }
    }

    fun discard() {
        val p = decode(draftJson)
        if (p.photo != null && p.photo != original.photo) File(repo.photosDir, p.photo).delete()
        onDone(null)
    }

    fun save() {
        val p = decode(draftJson)
        repo.saveProject(p.copy(name = p.name.trim(), steps = p.steps.map { it.copy(title = it.title.trim()) }))
        onDone(p.id)
    }

    BackHandler { if (dirty) confirmDiscard = true else onDone(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (projectId == null) "Nouveau projet" else "Modifier") },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmDiscard = true else onDone(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                },
                actions = {
                    Button(onClick = ::save, modifier = Modifier.padding(end = 8.dp)) { Text("Enregistrer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val photo = repo.photoFile(draft.photo)
                Box {
                    ProjectPhoto(
                        photo,
                        Modifier.fillMaxWidth().aspectRatio(if (photo == null) 1.6f else 0.9f).clip(RoundedCornerShape(24.dp)),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = ::launchCamera, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Photo")
                    }
                    FilledTonalButton(
                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Galerie")
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { v -> update { it.copy(name = v) } },
                    label = { Text("Nom de la figurine") },
                    placeholder = { Text("ex. Capitaine Space Marine") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { v -> update { it.copy(notes = v) } },
                    label = { Text("Notes (optionnel)") },
                    placeholder = { Text("Schéma de couleurs, temps passé, figurine…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    "Étapes de peinture",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            itemsIndexed(draft.steps, key = { _, s -> s.id }) { index, step ->
                StepEditorCard(
                    index = index,
                    count = draft.steps.size,
                    step = step,
                    data = data,
                    repo = repo,
                    onChange = { f -> updateStep(step.id, f) },
                    onMove = { delta ->
                        update { p ->
                            val list = p.steps.toMutableList()
                            val target = index + delta
                            if (target in list.indices) {
                                val tmp = list[index]; list[index] = list[target]; list[target] = tmp
                            }
                            p.copy(steps = list)
                        }
                    },
                    onDelete = { update { p -> p.copy(steps = p.steps.filterNot { it.id == step.id }) } },
                    onPickPaints = { pickingStepId = step.id },
                    onDictate = { dictate(step.id) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            // La nouvelle étape reprend la technique de la précédente : on enchaîne souvent pareil.
                            update { p -> p.copy(steps = p.steps + newStep().copy(technique = p.steps.lastOrNull()?.technique ?: Technique.BRUSH)) }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Étape")
                    }
                    Button(onClick = { dictate(NEW_STEPS) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Dicter")
                    }
                }
                Text(
                    "Dicte une ou plusieurs étapes : « Sous-couche à l'aérographe avec Wraithbone, ensuite lavis avec Agrax Earthshade, note : bien laisser sécher ».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    val pickingStep = draft.steps.firstOrNull { it.id == pickingStepId }
    if (pickingStep != null) {
        ModalBottomSheet(
            onDismissRequest = { pickingStepId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Pots de l'étape", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${pickingStep.paintIds.size} sélectionné${if (pickingStep.paintIds.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { pickingStepId = null }) { Text("OK") }
            }
            PaintBrowser(
                paints = repo.allPaints(data),
                ownedIds = data.ownedPaintIds,
                isChecked = { it.id in pickingStep.paintIds },
                onToggle = { paint, checked ->
                    updateStep(pickingStep.id) { s ->
                        s.copy(paintIds = if (checked) (s.paintIds + paint.id).distinct() else s.paintIds - paint.id)
                    }
                },
                showOwnedFilter = true,
                initialOnlyOwned = data.ownedPaintIds.isNotEmpty(),
                showToBuy = true,
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Abandonner les modifications ?") },
            text = { Text("Ce que tu as saisi depuis l'ouverture sera perdu.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; discard() }) {
                    Text("Abandonner", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Continuer") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StepEditorCard(
    index: Int,
    count: Int,
    step: Step,
    data: AppData,
    repo: Repository,
    onChange: ((Step) -> Step) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onPickPaints: () -> Unit,
    onDictate: () -> Unit,
) {
    val paintsById = remember(data) { repo.allPaints(data).associateBy { it.id } }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ÉTAPE ${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Monter")
                }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Descendre")
                }
                IconButton(onClick = onDictate) {
                    Icon(Icons.Default.Mic, contentDescription = "Dicter pour cette étape", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Supprimer l'étape")
                }
            }
            Column(Modifier.padding(end = 8.dp)) {
                OutlinedTextField(
                    value = step.title,
                    onValueChange = { v -> onChange { it.copy(title = v) } },
                    label = { Text("Étape") },
                    placeholder = { Text("ex. Aplats, lavis, éclaircissements…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (step.title.isBlank()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        STEP_SUGGESTIONS.forEach { s ->
                            SuggestionChip(onClick = { onChange { it.copy(title = s) } }, label = { Text(s) })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Technique.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = step.technique == t,
                            onClick = { onChange { it.copy(technique = t) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = Technique.entries.size),
                            icon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        ) { Text(t.label) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                step.paintIds.forEach { id ->
                    val p = paintsById[id] ?: return@forEach
                    PaintLine(p, owned = p.id in data.ownedPaintIds, trailing = {
                        IconButton(onClick = { onChange { s -> s.copy(paintIds = s.paintIds - id) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Retirer", modifier = Modifier.size(18.dp))
                        }
                    })
                }
                TextButton(onClick = onPickPaints) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (step.paintIds.isEmpty()) "Choisir les pots" else "Modifier les pots")
                }
                OutlinedTextField(
                    value = step.notes,
                    onValueChange = { v -> onChange { it.copy(notes = v) } },
                    label = { Text("Notes") },
                    placeholder = {
                        Text(
                            if (step.technique == Technique.AIRBRUSH) "Dilution, pression, buse…"
                            else "Dilution, pinceau, astuces…"
                        )
                    },
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
