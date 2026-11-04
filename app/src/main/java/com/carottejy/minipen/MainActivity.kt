package com.carottejy.minipen

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
import com.carottejy.minipen.data.AppData
import com.carottejy.minipen.data.Repository
import com.carottejy.minipen.ui.CatalogScreen
import com.carottejy.minipen.ui.CollectionScreen
import com.carottejy.minipen.ui.CustomPaintDialog
import com.carottejy.minipen.ui.MiniPenTheme
import com.carottejy.minipen.ui.ProjectDetailScreen
import com.carottejy.minipen.ui.ProjectEditorScreen
import com.carottejy.minipen.ui.ProjectsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = Repository.get(this)
        setContent {
            MiniPenTheme {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    data: AppData,
    repo: Repository,
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onOpenCatalog: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showCustom by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "MINIPEN COMPANION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            if (tab == 0) "Mes réalisations" else "Ma collection",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                actions = {
                    if (tab == 1) {
                        IconButton(onClick = { showCustom = true }) {
                            Icon(Icons.Outlined.LibraryAdd, contentDescription = "Ajouter un pot perso")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    label = { Text("Réalisations") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                    label = { Text("Collection") },
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onNewProject,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nouvelle") },
                )
            } else if (data.ownedPaintIds.isNotEmpty()) {
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
            else -> CollectionScreen(data, repo, onOpenCatalog = onOpenCatalog, modifier = m)
        }
    }

    if (showCustom) CustomPaintDialog(repo, onDismiss = { showCustom = false })
}
