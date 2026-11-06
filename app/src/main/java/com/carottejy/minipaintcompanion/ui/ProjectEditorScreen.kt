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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import kotlin.math.roundToInt
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
    "Glacis", "Lining", "Détails", "Usure / effets", "Socle", "Vernis",
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
        encode(existing ?: Project(id = UUID.randomUUID().toString(), steps = listOf(newStep().copy(title = "Sous-couche", primer = true))))
    }
    var draftJson by rememberSaveable { mutableStateOf(originalJson) }
    val draft = remember(draftJson) { decode(draftJson) }
    val original = remember(originalJson) { decode(originalJson) }
    fun update(f: (Project) -> Project) { draftJson = encode(f(decode(draftJson))) }
    fun updateStep(stepId: String, f: (Step) -> Step) =
        update { p -> p.copy(steps = p.steps.map { if (it.id == stepId) f(it) else it }) }

    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingStepId by rememberSaveable { mutableStateOf<String?>(null) }
    var managingPrimers by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // ---------------------------------------------------------------- dictée vocale
    // Cible : identifiant d'une étape à compléter, ou NEW_STEPS pour créer de nouvelles étapes.
    var voiceTarget by rememberSaveable { mutableStateOf<String?>(null) }   // dictée intégrée ouverte
    var fallbackTarget by rememberSaveable { mutableStateOf<String?>(null) } // dictée Google (secours)

    fun applyVoice(target: String, text: String) {
        if (text.isBlank()) return
        val parsed = VoiceParser.parse(text, repo.allPaints(data), data.ownedPaintIds)
        if (parsed.isEmpty()) {
            Toast.makeText(context, "Je n'ai rien compris d'utile dans « $text »", Toast.LENGTH_LONG).show()
            return
        }
        val nbPaints = parsed.sumOf { it.paintIds.size }
        if (target == NEW_STEPS) {
            update { p ->
                var last = p.steps.lastOrNull()?.technique ?: Technique.BRUSH
                val added = parsed.map { ps ->
                    val t = ps.technique ?: last
                    last = t
                    newStep().copy(
                        title = ps.title, technique = t, paintIds = ps.paintIds, notes = ps.notes,
                        dilution = ps.dilution ?: 0, primer = ps.primer,
                    )
                }
                // L'étape « Sous-couche » vide créée par défaut est remplacée par la dictée.
                val base = p.steps.toMutableList()
                val tail = base.lastOrNull()
                if (base.size == 1 && tail != null && tail.paintIds.isEmpty() && tail.notes.isBlank() && added.first().title.isNotBlank()) {
                    base.removeAt(base.lastIndex)
                }
                p.copy(steps = base + added)
            }
            Toast.makeText(
                context,
                "${parsed.size} étape${if (parsed.size > 1) "s" else ""} ajoutée${if (parsed.size > 1) "s" else ""}, $nbPaints pot${if (nbPaints > 1) "s" else ""} reconnu${if (nbPaints > 1) "s" else ""}. Vérifie avant d'enregistrer.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            updateStep(target) { st -> parsed.fold(st) { acc, ps -> VoiceParser.merge(acc, ps) } }
            Toast.makeText(context, "$nbPaints pot${if (nbPaints > 1) "s" else ""} reconnu${if (nbPaints > 1) "s" else ""}", Toast.LENGTH_LONG).show()
        }
    }

    val ownedNames = remember(data) { repo.allPaints(data).filter { it.id in data.ownedPaintIds }.map { it.name }.distinct() }

    // Secours : fenêtre de dictée de Google, si la reconnaissance intégrée n'est pas disponible.
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = fallbackTarget
        fallbackTarget = null
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank() && target != null) applyVoice(target, text)
    }

    fun googleDictation(target: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            if (Build.VERSION.SDK_INT >= 33) putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", ArrayList(ownedNames.take(300)))
        }
        fallbackTarget = target
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            fallbackTarget = null
            Toast.makeText(context, "Aucune reconnaissance vocale disponible sur ce téléphone (installe ou active l'appli Google).", Toast.LENGTH_LONG).show()
        }
    }

    fun dictate(target: String) { voiceTarget = target }

    val dirty = draftJson != originalJson

    fun replacePhoto(name: String) {
        val current = decode(draftJson).photo
        // Une photo prise pendant cette édition et remplacée aussitôt peut partir à la corbeille.
        if (current != null && current != original.photo) File(repo.photosDir, current).delete()
        update { it.copy(photo = name) }
    }

    val takePicture = rememberTakePicture { ok ->
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
        takePicture(uri)
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
                title = { Text(if (projectId == null) "Nouveau schéma" else "Modifier") },
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
            val onTogglePaint: (com.carottejy.minipaintcompanion.data.Paint, Boolean) -> Unit = { paint, checked ->
                updateStep(pickingStep.id) { s ->
                    s.copy(paintIds = if (checked) (s.paintIds + paint.id).distinct() else s.paintIds - paint.id)
                }
            }
            if (pickingStep.primer) {
                // Étape de sous-couche : ma liste dédiée, modifiable.
                val primerIds = repo.primerIds(data)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Mes sous-couches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { managingPrimers = true }) { Text("Gérer ma liste") }
                }
                PaintBrowser(
                    paints = repo.allPrimers(data).filter { it.id in primerIds || it.id in pickingStep.paintIds },
                    ownedIds = data.ownedPaintIds,
                    isChecked = { it.id in pickingStep.paintIds },
                    onToggle = onTogglePaint,
                    emptyText = "Ta liste de sous-couches est vide : touche « Gérer ma liste » pour en ajouter.",
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PaintBrowser(
                    paints = repo.allPaints(data),
                    ownedIds = data.ownedPaintIds,
                    isChecked = { it.id in pickingStep.paintIds },
                    onToggle = onTogglePaint,
                    showOwnedFilter = true,
                    initialOnlyOwned = data.ownedPaintIds.isNotEmpty(),
                    showToBuy = true,
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (managingPrimers) PrimerManagerSheet(data, repo, onDismiss = { managingPrimers = false })

    voiceTarget?.let { target ->
        DictationDialog(
            hint = if (target == NEW_STEPS) "Ex. : aplats au pinceau avec Macragge Blue, dilué 1 pour 1, ensuite lavis avec Nuln Oil"
            else "Dis la technique, les pots et tes notes",
            biasing = ownedNames,
            onDone = { text -> voiceTarget = null; applyVoice(target, text) },
            onDismiss = { voiceTarget = null },
            onFallback = { voiceTarget = null; googleDictation(target) },
        )
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
                            SuggestionChip(
                                onClick = { onChange { it.copy(title = s, primer = it.primer || s == "Sous-couche") } },
                                label = { Text(s) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Technique.entries.forEach { t ->
                        FilterChip(
                            selected = step.technique == t,
                            onClick = { onChange { it.copy(technique = t) } },
                            label = { Text(t.label) },
                            leadingIcon = {
                                Icon(t.icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = t.accent)
                            },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Étape de sous-couche", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Les pots se choisissent dans ta liste de sous-couches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = step.primer,
                        onCheckedChange = { on -> onChange { it.copy(primer = on, title = if (on && it.title.isBlank()) "Sous-couche" else it.title) } },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (step.dilution == 0) "Dilution : pure (0 %)" else "Dilution : ${step.dilution} %",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(150.dp),
                    )
                    Slider(
                        value = step.dilution.toFloat(),
                        onValueChange = { v -> onChange { it.copy(dilution = (v / 5).roundToInt() * 5) } },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(2.dp))
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
                    Text(
                        when {
                            step.primer && step.paintIds.isEmpty() -> "Choisir la sous-couche"
                            step.primer -> "Modifier la sous-couche"
                            step.paintIds.isEmpty() -> "Choisir les pots"
                            else -> "Modifier les pots"
                        }
                    )
                }
                OutlinedTextField(
                    value = step.notes,
                    onValueChange = { v -> onChange { it.copy(notes = v) } },
                    label = { Text("Notes") },
                    placeholder = {
                        Text(
                            when (step.technique) {
                                Technique.AIRBRUSH -> "Dilution, pression, buse…"
                                Technique.SPRAY -> "Distance, nombre de passes, temps de séchage…"
                                Technique.SPONGE -> "Type d'éponge, zones, intensité…"
                                Technique.PEN -> "Pointe, zones, finitions…"
                                Technique.BRUSH -> "Dilution, pinceau, astuces…"
                            }
                        )
                    },
                    minLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
