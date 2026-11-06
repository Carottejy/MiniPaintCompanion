package com.carottejy.minipaintcompanion

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.compose.material.icons.outlined.FileOpen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Repository
import com.carottejy.minipaintcompanion.ui.CatalogScreen
import com.carottejy.minipaintcompanion.ui.CollectionScreen
import com.carottejy.minipaintcompanion.ui.CustomPaintDialog
import com.carottejy.minipaintcompanion.ui.MiniPaintTheme
import com.carottejy.minipaintcompanion.ui.ProjectDetailScreen
import com.carottejy.minipaintcompanion.ui.ProjectEditorScreen
import com.carottejy.minipaintcompanion.ui.ProjectsScreen
import com.carottejy.minipaintcompanion.ui.ColorsScreen
import com.carottejy.minipaintcompanion.ui.InfoScreen
import com.carottejy.minipaintcompanion.ui.PipetteScreen
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Info
import com.carottejy.minipaintcompanion.ui.ShoppingScreen
import com.carottejy.minipaintcompanion.ui.PaintChoiceSheet
import com.carottejy.minipaintcompanion.ui.qrToText
import com.carottejy.minipaintcompanion.data.VoiceParser
import androidx.compose.material.icons.outlined.DocumentScanner
import com.carottejy.minipaintcompanion.ui.PaintScannerOverlay
import com.carottejy.minipaintcompanion.ui.shortLabel
import com.carottejy.minipaintcompanion.update.UpdateDialog
import com.carottejy.minipaintcompanion.update.UpdateInfo
import com.carottejy.minipaintcompanion.update.Updater
import com.carottejy.minipaintcompanion.data.Paint
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SystemUpdate

class MainActivity : ComponentActivity() {
    /** Schéma reçu d'une autre appli (fichier .minipaint.zip). */
    private val incoming = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)
        if (savedInstanceState == null) incoming.value = sharedUri(intent)
        setContent {
            MiniPaintTheme {
                App(repo, incoming = incoming.value, onIncomingHandled = { incoming.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedUri(intent)?.let { incoming.value = it }
    }

    @Suppress("DEPRECATION")
    private fun sharedUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND ->
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }
}

private fun NavHostController.back() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
fun App(repo: Repository, incoming: Uri? = null, onIncomingHandled: () -> Unit = {}) {
    val data by repo.data.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current

    // Import d'un schéma partagé, puis ouverture de sa fiche.
    fun importSchema(uri: Uri) {
        (context as? ComponentActivity)?.lifecycleScope?.launch {
            runCatching { repo.importSchema(uri) }
                .onSuccess { p ->
                    Toast.makeText(context, "Schéma « ${p.name.ifBlank { "Sans titre" }} » importé", Toast.LENGTH_LONG).show()
                    nav.navigate("project/${p.id}")
                }
                .onFailure { Toast.makeText(context, it.message ?: "Import impossible", Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(incoming) {
        if (incoming != null) {
            importSchema(incoming)
            onIncomingHandled()
        }
    }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    var installing by remember { mutableStateOf<String?>(null) }

    // Mise à jour automatique (APK GitHub) :
    //  1. une version déjà téléchargée est installée dès le lancement, avant toute saisie ;
    //  2. sinon on interroge GitHub (au plus toutes les heures) et on télécharge en arrière-plan,
    //     pour une installation au lancement suivant.
    LaunchedEffect(Unit) {
        if (!Updater.enabled) return@LaunchedEffect
        val auto = Updater.isAutoEnabled(context)
        val pending = Updater.pendingApk(context)
        if (auto && pending != null && Updater.canInstall(context)) {
            installing = "1.0.${pending.second}"
            delay(1500)
            runCatching { withContext(Dispatchers.IO) { Updater.installSilently(context, pending.first) } }
                .onFailure {
                    installing = null
                    Toast.makeText(context, "Mise à jour impossible : ${it.message}", Toast.LENGTH_LONG).show()
                }
            return@LaunchedEffect
        }
        val info = runCatching { Updater.check(context, auto = true) }.getOrNull() ?: return@LaunchedEffect
        if (auto && Updater.canInstall(context)) {
            runCatching { Updater.download(context, info) {} }
                .onSuccess {
                    Toast.makeText(
                        context,
                        "Mise à jour ${info.version} téléchargée : elle s'installera au prochain lancement.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onFailure { update = info }
        } else {
            // Autorisation d'installer pas encore donnée, ou mode automatique coupé : on demande.
            update = info
        }
    }
    update?.let { UpdateDialog(it, onDismiss = { update = null }) }
    installing?.let { v ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Mise à jour vers $v") },
            text = { Text("Installation en cours… L'appli va se fermer : rouvre-la dans quelques secondes.") },
            confirmButton = {},
        )
    }

    NavHost(
        navController = nav,
        startDestination = "home",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        composable("home") {
            HomeScreen(
                data = data,
                repo = repo,
                onOpenProject = { nav.navigate("project/$it") },
                onNewProject = { nav.navigate("edit/new") },
                onUpdateFound = { update = it },
                onImportSchema = { importSchema(it) },
            )
        }
        composable("project/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ProjectDetailScreen(
                projectId = id,
                data = data,
                repo = repo,
                onBack = { nav.back() },
                onEdit = { nav.navigate("edit/$id") },
            )
        }
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
            ProjectEditorScreen(
                projectId = id,
                data = data,
                repo = repo,
                onDone = { savedId ->
                    if (id == null && savedId != null) {
                        // Nouvelle réalisation : on l'affiche directement.
                        nav.navigate("project/$savedId") { popUpTo("home") }
                    } else {
                        nav.back()
                    }
                },
            )
        }
    }
}

private data class Tab(val title: String, val label: String)

private data class PaintChoice(
    val title: String,
    val message: String,
    val candidates: List<Paint>,
    val query: String,
    val code: String?,
)

private val TABS = listOf(
    Tab("Mes schémas", "Mes schémas"),
    Tab("Mes peintures", "Mes peintures"),
    Tab("Catalogue", "Catalogue"),
    Tab("Liste d'achat", "Achat"),
    Tab("Théorie des couleurs", "Théorie"),
    Tab("Pipette", "Pipette"),
    Tab("Informations", "Info"),
)

private const val T_PROJECTS = 0
private const val T_PAINTS = 1
private const val T_CATALOG = 2
private const val T_SHOPPING = 3
private const val T_THEORY = 4
private const val T_PIPETTE = 5
private const val T_INFO = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    data: AppData,
    repo: Repository,
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onUpdateFound: (UpdateInfo) -> Unit,
    onImportSchema: (Uri) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showCustom by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    // Identification incertaine : choix du pot (QR inconnu, étiquette non reconnue).
    var choice by remember { mutableStateOf<PaintChoice?>(null) }
    var colorBasePaintId by rememberSaveable { mutableStateOf<String?>(null) }
    var colorFreeHex by rememberSaveable { mutableStateOf("#C8553D") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val onOpenCatalog = { tab = T_CATALOG }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun checkUpdate() {
        scope.launch {
            runCatching { Updater.check(context, auto = false) }.fold(
                { info -> if (info != null) onUpdateFound(info) else toast("Tu as déjà la dernière version") },
                { e -> toast("Vérification impossible : ${e.message}") },
            )
        }
    }

    // ---------------------------------------------------------------- scan

    var rescanRequests by remember { mutableIntStateOf(0) }

    fun added(p: Paint, code: String?) {
        val wasToBuy = p.id in data.toBuyIds
        val wasOwned = p.id in data.ownedPaintIds
        if (code != null) repo.linkBarcode(code, p.id)
        repo.markBought(p.id)
        val msg = when {
            wasToBuy -> "${p.name} acheté : retiré de la liste, ajouté à ta collection"
            wasOwned -> "${p.name} est déjà dans ta collection"
            else -> "${p.name} (${p.brand.shortLabel()}) ajouté à ta collection"
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(msg, actionLabel = "Identifier un autre", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) rescanRequests++
        }
    }

    var scanning by rememberSaveable { mutableStateOf(false) }
    fun startScan() { scanning = true }

    LaunchedEffect(rescanRequests) { if (rescanRequests > 0) startScan() }

    // ---------------------------------------------------------------- sauvegarde

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching { repo.exportBackup(uri) }.fold(
                { n -> "Sauvegarde créée ($n schéma${if (n > 1) "s" else ""})" },
                { e -> "Échec de la sauvegarde : ${e.message}" },
            )
            toast(msg)
        }
    }
    val schemaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportSchema(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching { repo.importBackup(uri) }.fold(
                { n -> "Sauvegarde restaurée ($n schéma${if (n > 1) "s" else ""})" },
                { e -> "Échec de la restauration : ${e.message}" },
            )
            toast(msg)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MINIPAINT COMPANION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(TABS[tab].title, style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    if (tab == T_PAINTS || tab == T_CATALOG || tab == T_SHOPPING) {
                        IconButton(onClick = { startScan() }) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = "Identifier un pot (étiquette ou QR code)")
                        }
                    }
                    if (tab == T_PAINTS || tab == T_CATALOG) {
                        IconButton(onClick = { showCustom = true }) {
                            Icon(Icons.Outlined.LibraryAdd, contentDescription = "Ajouter un pot perso")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Plus d'options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sauvegarder mes données") },
                                leadingIcon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                                    exportLauncher.launch("MiniPaintCompanion-sauvegarde-$day.zip")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Importer un schéma") },
                                leadingIcon = { Icon(Icons.Outlined.FileOpen, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    schemaPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Restaurer une sauvegarde") },
                                leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                                onClick = { menuOpen = false; confirmRestore = true },
                            )
                            if (Updater.enabled) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Rechercher une mise à jour")
                                            Text(
                                                "Version ${Updater.installedVersion(context).first}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                                    onClick = { menuOpen = false; checkUpdate() },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            val icon = when (i) {
                                T_PROJECTS -> Icons.Outlined.AutoAwesome
                                T_PAINTS -> Icons.Outlined.Inventory2
                                T_CATALOG -> Icons.Outlined.MenuBook
                                T_SHOPPING -> Icons.Outlined.ShoppingCart
                                T_THEORY -> Icons.Outlined.Palette
                                T_PIPETTE -> Icons.Outlined.Colorize
                                else -> Icons.Outlined.Info
                            }
                            if (i == T_SHOPPING && data.toBuyIds.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${data.toBuyIds.size}") } }) {
                                    Icon(icon, contentDescription = null)
                                }
                            } else {
                                Icon(icon, contentDescription = null)
                            }
                        },
                        label = {
                            Text(
                                t.label,
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontSize = 10.sp),
                            )
                        },
                        // 7 onglets : icônes seules, libellé de l'onglet actif uniquement.
                        alwaysShowLabel = false,
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == T_PROJECTS) {
                ExtendedFloatingActionButton(
                    onClick = onNewProject,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nouveau schéma") },
                )
            }
        },
    ) { padding ->
        val m = Modifier.fillMaxSize().padding(padding)
        when (tab) {
            T_PROJECTS -> ProjectsScreen(data, repo, onOpen = { onOpenProject(it.id) }, modifier = m)
            T_PAINTS -> CollectionScreen(
                data, repo,
                onOpenCatalog = onOpenCatalog,
                onScan = { startScan() },
                onHarmonies = { p -> colorBasePaintId = p.id; tab = T_THEORY },
                modifier = m,
            )
            T_CATALOG -> CatalogScreen(data, repo, modifier = m)
            T_SHOPPING -> ShoppingScreen(data, repo, onOpenCatalog = onOpenCatalog, onScan = { startScan() }, modifier = m)
            T_INFO -> InfoScreen(onCheckUpdate = { checkUpdate() }, modifier = m)
            T_PIPETTE -> PipetteScreen(
                data, repo,
                onHarmonies = { hex -> colorFreeHex = hex; colorBasePaintId = null; tab = T_THEORY },
                modifier = m,
            )
            else -> ColorsScreen(
                data, repo,
                basePaintId = colorBasePaintId,
                onBasePaintChange = { colorBasePaintId = it },
                freeHex = colorFreeHex,
                onFreeHexChange = { colorFreeHex = it },
                modifier = m,
            )
        }
    }

    if (scanning) {
        val allPaints = repo.allPaints(data)
        PaintScannerOverlay(
            paints = allPaints,
            ownedIds = data.ownedPaintIds,
            onPaint = { p -> scanning = false; added(p, null) },
            onQr = { content ->
                scanning = false
                val digits = content.trim()
                if (digits.length in 8..14 && digits.all { it.isDigit() }) {
                    // Code-barres EAN / UPC
                    val code = if (digits.length == 12) "0$digits" else digits
                    val p = repo.paintForBarcode(code)
                    if (p != null) {
                        added(p, code)
                    } else {
                        choice = PaintChoice(
                            title = "Code-barres inconnu",
                            message = "Code $code. Choisis le pot correspondant : je retiendrai ce code pour les prochaines fois.",
                            candidates = emptyList(),
                            query = "",
                            code = code,
                        )
                    }
                    return@PaintScannerOverlay
                }
                val key = "qr:$content"
                val learned = data.barcodes[key]?.let { id -> allPaints.firstOrNull { it.id == id } }
                if (learned != null) {
                    added(learned, key)
                } else {
                    val cands = VoiceParser.candidates(qrToText(content), allPaints, data.ownedPaintIds, 3).map { it.first }
                    choice = PaintChoice(
                        title = "QR code lu",
                        message = "Choisis le pot correspondant : je retiendrai ce QR code pour les prochaines fois.",
                        candidates = cands,
                        query = "",
                        code = key,
                    )
                }
            },
            onSearch = { text ->
                scanning = false
                choice = PaintChoice(
                    title = "Choisir le pot",
                    message = "Je n'ai pas réussi à lire le nom avec certitude. Cherche le pot par son nom ou sa référence.",
                    candidates = emptyList(),
                    query = "",
                    code = null,
                )
            },
            onClose = { scanning = false },
        )
    }
    }

    choice?.let { c ->
        PaintChoiceSheet(
            title = c.title,
            message = c.message,
            candidates = c.candidates,
            initialQuery = c.query,
            data = data,
            repo = repo,
            onPicked = { p -> choice = null; added(p, c.code) },
            onDismiss = { choice = null },
        )
    }

    if (showCustom) CustomPaintDialog(repo, onDismiss = { showCustom = false })

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restaurer une sauvegarde ?") },
            text = {
                Text(
                    "Tes schémas, tes peintures, ta liste d'achat et tes pots perso actuels seront remplacés " +
                        "par le contenu du fichier .zip choisi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }) { Text("Choisir le fichier") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Annuler") } },
        )
    }
}
