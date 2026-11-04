package com.carottejy.minipaintcompanion

import android.os.Bundle
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
import com.carottejy.minipaintcompanion.ui.ShoppingScreen
import com.carottejy.minipaintcompanion.ui.UnknownBarcodeSheet
import com.carottejy.minipaintcompanion.ui.scanBarcode
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
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)
        setContent {
            MiniPaintTheme {
                App(repo)
            }
        }
    }
}

private fun NavHostController.back() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
fun App(repo: Repository) {
    val data by repo.data.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    // Vérification discrète au lancement (APK GitHub uniquement, au plus toutes les 6 h).
    LaunchedEffect(Unit) {
        if (Updater.enabled) update = runCatching { Updater.check(context, auto = true) }.getOrNull()
    }
    update?.let { UpdateDialog(it, onDismiss = { update = null }) }

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
                onOpenCatalog = { nav.navigate("catalog") },
                onUpdateFound = { update = it },
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
        composable("catalog") {
            CatalogScreen(data = data, repo = repo, onBack = { nav.back() })
        }
    }
}

private data class Tab(val title: String, val label: String)

private val TABS = listOf(
    Tab("Mes réalisations", "Réalisations"),
    Tab("Ma collection", "Collection"),
    Tab("Liste d'achat", "Achats"),
    Tab("Couleurs", "Couleurs"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    data: AppData,
    repo: Repository,
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onOpenCatalog: () -> Unit,
    onUpdateFound: (UpdateInfo) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showCustom by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var unknownCode by rememberSaveable { mutableStateOf<String?>(null) }
    var colorBasePaintId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // ---------------------------------------------------------------- scan

    var rescanRequests by remember { mutableIntStateOf(0) }

    fun added(p: Paint, code: String) {
        val wasToBuy = p.id in data.toBuyIds
        val wasOwned = p.id in data.ownedPaintIds
        repo.linkBarcode(code, p.id)
        repo.markBought(p.id)
        val msg = when {
            wasToBuy -> "${p.name} acheté : retiré de la liste, ajouté à ta collection"
            wasOwned -> "${p.name} est déjà dans ta collection"
            else -> "${p.name} (${p.brand.shortLabel()}) ajouté à ta collection"
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(msg, actionLabel = "Scanner un autre", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) rescanRequests++
        }
    }

    fun startScan() {
        scanBarcode(
            context,
            onCode = { code ->
                val p = repo.paintForBarcode(code)
                if (p != null) added(p, code) else unknownCode = code
            },
            onError = { toast(it) },
        )
    }
    LaunchedEffect(rescanRequests) { if (rescanRequests > 0) startScan() }

    // ---------------------------------------------------------------- sauvegarde

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching { repo.exportBackup(uri) }.fold(
                { n -> "Sauvegarde créée ($n réalisation${if (n > 1) "s" else ""})" },
                { e -> "Échec de la sauvegarde : ${e.message}" },
            )
            toast(msg)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching { repo.importBackup(uri) }.fold(
                { n -> "Sauvegarde restaurée ($n réalisation${if (n > 1) "s" else ""})" },
                { e -> "Échec de la restauration : ${e.message}" },
            )
            toast(msg)
        }
    }

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
                    if (tab == 1 || tab == 2) {
                        IconButton(onClick = { startScan() }) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scanner le code-barres d'un pot")
                        }
                    }
                    if (tab == 1) {
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
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            runCatching { Updater.check(context, auto = false) }.fold(
                                                { info -> if (info != null) onUpdateFound(info) else toast("Tu as déjà la dernière version") },
                                                { e -> toast("Vérification impossible : ${e.message}") },
                                            )
                                        }
                                    },
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
                                0 -> Icons.Outlined.AutoAwesome
                                1 -> Icons.Outlined.Inventory2
                                2 -> Icons.Outlined.ShoppingCart
                                else -> Icons.Outlined.Palette
                            }
                            if (i == 2 && data.toBuyIds.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${data.toBuyIds.size}") } }) {
                                    Icon(icon, contentDescription = null)
                                }
                            } else {
                                Icon(icon, contentDescription = null)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            when {
                tab == 0 -> ExtendedFloatingActionButton(
                    onClick = onNewProject,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nouvelle") },
                )
                (tab == 1 && data.ownedPaintIds.isNotEmpty()) || (tab == 2 && data.toBuyIds.isNotEmpty()) ->
                    ExtendedFloatingActionButton(
                        onClick = onOpenCatalog,
                        icon = { Icon(Icons.Outlined.MenuBook, contentDescription = null) },
                        text = { Text("Catalogue") },
                    )
            }
        },
    ) { padding ->
        val m = Modifier.fillMaxSize().padding(padding)
        when (tab) {
            0 -> ProjectsScreen(data, repo, onOpen = { onOpenProject(it.id) }, modifier = m)
            1 -> CollectionScreen(
                data, repo,
                onOpenCatalog = onOpenCatalog,
                onScan = { startScan() },
                onHarmonies = { p -> colorBasePaintId = p.id; tab = 3 },
                modifier = m,
            )
            2 -> ShoppingScreen(data, repo, onOpenCatalog = onOpenCatalog, onScan = { startScan() }, modifier = m)
            else -> ColorsScreen(
                data, repo,
                basePaintId = colorBasePaintId,
                onBasePaintChange = { colorBasePaintId = it },
                modifier = m,
            )
        }
    }

    unknownCode?.let { code ->
        UnknownBarcodeSheet(
            code = code,
            data = data,
            repo = repo,
            onPicked = { p -> unknownCode = null; added(p, code) },
            onDismiss = { unknownCode = null },
        )
    }

    if (showCustom) CustomPaintDialog(repo, onDismiss = { showCustom = false })

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restaurer une sauvegarde ?") },
            text = {
                Text(
                    "Tes réalisations, ta collection, ta liste d'achat et tes pots perso actuels seront remplacés " +
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
