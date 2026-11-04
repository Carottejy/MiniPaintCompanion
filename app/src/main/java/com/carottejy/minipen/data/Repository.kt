package com.carottejy.minipen.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Stockage local : un fichier JSON + un dossier de photos dans l'espace privé de l'app. */
class Repository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "minipen.json")
    val photosDir: File = File(appContext.filesDir, "photos").apply { mkdirs() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    init {
        scope.launch { cleanOrphanPhotos() }
    }

    private fun load(): AppData = try {
        if (file.exists()) json.decodeFromString(AppData.serializer(), file.readText()) else AppData()
    } catch (e: Exception) {
        // Fichier illisible : on le garde de côté plutôt que de l'écraser.
        runCatching {
            file.copyTo(File(appContext.filesDir, "minipen-corrompu-${System.currentTimeMillis()}.json"), overwrite = true)
        }
        AppData()
    }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                val text = json.encodeToString(AppData.serializer(), _data.value)
                val tmp = File(file.parentFile, "minipen.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.writeText(text)
                    tmp.delete()
                }
            }
        }
    }

    private fun mutate(transform: (AppData) -> AppData) {
        _data.update(transform)
        persist()
    }

    // ------------------------------------------------------------ peintures

    fun allPaints(d: AppData = _data.value): List<Paint> = Catalog.all + d.customPaints

    fun setOwned(paintId: String, owned: Boolean) = mutate {
        it.copy(ownedPaintIds = if (owned) it.ownedPaintIds + paintId else it.ownedPaintIds - paintId)
    }

    fun addCustomPaint(name: String, range: String, hex: String): Paint {
        val paint = Paint(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            brand = Brand.CUSTOM,
            range = range.ifBlank { "Autre" },
            name = name.trim(),
            hex = hex,
        )
        mutate { it.copy(customPaints = it.customPaints + paint, ownedPaintIds = it.ownedPaintIds + paint.id) }
        return paint
    }

    fun deleteCustomPaint(id: String) = mutate { d ->
        d.copy(
            customPaints = d.customPaints.filterNot { it.id == id },
            ownedPaintIds = d.ownedPaintIds - id,
        )
    }

    // ------------------------------------------------------------ réalisations

    fun saveProject(project: Project) {
        val old = _data.value.projects.firstOrNull { it.id == project.id }
        if (old?.photo != null && old.photo != project.photo) File(photosDir, old.photo).delete()
        val saved = project.copy(updatedAt = System.currentTimeMillis())
        mutate { d ->
            val others = d.projects.filterNot { it.id == project.id }
            d.copy(projects = (others + saved).sortedByDescending { it.createdAt })
        }
    }

    fun deleteProject(id: String) {
        val p = _data.value.projects.firstOrNull { it.id == id } ?: return
        p.photo?.let { File(photosDir, it).delete() }
        mutate { d -> d.copy(projects = d.projects.filterNot { it.id == id }) }
    }

    // ------------------------------------------------------------ photos

    fun newPhotoFile(): File = File(photosDir, "photo_${UUID.randomUUID()}.jpg")

    fun photoFile(name: String?): File? = name?.let { File(photosDir, it) }?.takeIf { it.exists() }

    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val target = newPhotoFile()
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            target.name
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanOrphanPhotos() {
        // On laisse une marge d'une heure pour ne pas supprimer la photo d'un brouillon en cours.
        val used = _data.value.projects.mapNotNull { it.photo }.toSet()
        val limit = System.currentTimeMillis() - 60 * 60 * 1000
        photosDir.listFiles()?.forEach { f ->
            if (f.name !in used && f.lastModified() < limit) f.delete()
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
