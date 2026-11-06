package com.carottejy.minipaintcompanion.data

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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Stockage local : un fichier JSON + un dossier de photos dans l'espace privé de l'app. */
class Repository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "minipaint.json")
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
            file.copyTo(File(appContext.filesDir, "minipaint-corrompu-${System.currentTimeMillis()}.json"), overwrite = true)
        }
        AppData()
    }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                val text = json.encodeToString(AppData.serializer(), _data.value)
                val tmp = File(file.parentFile, "minipaint.json.tmp")
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

    fun setToBuy(paintId: String, toBuy: Boolean) = mutate {
        it.copy(toBuyIds = if (toBuy) it.toBuyIds + paintId else it.toBuyIds - paintId)
    }

    fun addAllToBuy(ids: Collection<String>) = mutate { it.copy(toBuyIds = it.toBuyIds + ids) }

    /** Pot acheté : il quitte la liste d'achat et rejoint la collection. */
    fun markBought(paintId: String) = mutate {
        it.copy(toBuyIds = it.toBuyIds - paintId, ownedPaintIds = it.ownedPaintIds + paintId)
    }

    // ------------------------------------------------------------ codes-barres

    /**
     * Retrouve le pot d'un code-barres : d'abord les codes déjà appris,
     * puis la structure des EAN Vallejo (8429551 + référence à 5 chiffres + clé).
     */
    fun paintForBarcode(raw: String, d: AppData = _data.value): Paint? {
        val digits = raw.filter { it.isDigit() }
        val code = if (digits.length == 12) "0$digits" else digits
        val all = allPaints(d)
        d.barcodes[code]?.let { id -> all.firstOrNull { it.id == id }?.let { return it } }
        // Codes du catalogue (y compris la variante sans le 0 initial d'un UPC)
        (Catalog.barcodeIndex[code] ?: Catalog.barcodeIndex[code.removePrefix("0")])
            ?.let { id -> all.firstOrNull { it.id == id }?.let { return it } }
        if (code.length == 13 && code.startsWith("8429551")) {
            val ref = code.substring(7, 12) // ex. 70951
            val formatted = ref.substring(0, 2) + "." + ref.substring(2) // 70.951
            all.firstOrNull { it.brand == Brand.VALLEJO && it.code == formatted }?.let { return it }
        }
        return null
    }

    fun linkBarcode(code: String, paintId: String) = mutate { it.copy(barcodes = it.barcodes + (code to paintId)) }

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
            toBuyIds = d.toBuyIds - id,
            barcodes = d.barcodes.filterValues { it != id },
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

    // ------------------------------------------------------------ sauvegarde

    /** Écrit un zip contenant les données et toutes les photos. Retourne le nombre de réalisations. */
    suspend fun exportBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val snapshot = _data.value
        val out = appContext.contentResolver.openOutputStream(uri) ?: error("Impossible d'écrire le fichier")
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_JSON))
            zip.write(json.encodeToString(AppData.serializer(), snapshot).toByteArray())
            zip.closeEntry()
            snapshot.projects.mapNotNull { it.photo }.distinct().forEach { name ->
                val f = File(photosDir, name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        snapshot.projects.size
    }

    /** Remplace toutes les données par celles d'une sauvegarde. Retourne le nombre de réalisations. */
    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val staging = File(appContext.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        try {
            var imported: AppData? = null
            val input = appContext.contentResolver.openInputStream(uri) ?: error("Impossible de lire le fichier")
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == BACKUP_JSON ->
                            imported = json.decodeFromString(AppData.serializer(), zip.readBytes().decodeToString())
                        name.startsWith("photos/") && !entry.isDirectory -> {
                            // On ne garde que le nom de fichier : pas de chemin arbitraire dans l'archive.
                            val safe = File(name).name
                            if (safe.isNotBlank()) File(staging, safe).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val restored = imported ?: throw IllegalArgumentException("Ce fichier n'est pas une sauvegarde MiniPaintCompanion")

            staging.listFiles()?.forEach { f ->
                val target = File(photosDir, f.name)
                if (!f.renameTo(target)) { f.copyTo(target, overwrite = true); f.delete() }
            }
            val keep = restored.projects.mapNotNull { it.photo }.toSet()
            photosDir.listFiles()?.forEach { if (it.name !in keep) it.delete() }

            _data.value = restored
            persist()
            restored.projects.size
        } finally {
            staging.deleteRecursively()
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
        private const val BACKUP_JSON = "minipaint.json"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
