package com.carottejy.minipaintcompanion.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.time.Instant

@Serializable
data class Friend(
    val code: String,
    val pseudo: String,
    /** friend, incoming (il m'a invité), outgoing (j'ai invité). */
    val status: String,
    val schemaCount: Int = 0,
)

@Serializable
data class FriendSchema(
    val projectId: String,
    val name: String = "",
    val schema: SharedSchema,
    val updatedAt: String = "",
    val photoUrl: String? = null,
)

@Serializable
data class FriendSchemas(val pseudo: String = "", val schemas: List<FriendSchema> = emptyList())

@Serializable
private data class FriendList(val friends: List<Friend> = emptyList())

@Serializable
private data class Published(val projectId: String, val updatedAt: String)

@Serializable
private data class PublishedList(val schemas: List<Published> = emptyList())

/**
 * Amis et schémas partagés, via la fonction Supabase « minipaint-friends ».
 * Pas de compte : un secret aléatoire propre à l'appareil (inclus dans la sauvegarde) identifie l'utilisateur,
 * et un code ami public permet de s'ajouter.
 */
object Friends {
    private const val URL_FN = "https://qqrmozqxzeicbxwauiqt.supabase.co/functions/v1/minipaint-friends"
    private const val KEY = "sb_publishable_doGPithS7QQzmp0up3bRLw_zeg2jnDw"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    /** Schémas d'amis déjà chargés (par code ami), pour ouvrir leur fiche sans recharger. */
    val cache = mutableMapOf<String, FriendSchemas>()

    class ApiException(message: String) : Exception(message)

    private fun newSecret(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private suspend fun call(secret: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val conn = (URL(URL_FN).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-app", "MiniPaintCompanion")
            setRequestProperty("x-secret", secret)
            setRequestProperty("apikey", KEY)
            setRequestProperty("Authorization", "Bearer $KEY")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val obj = runCatching { Repository.json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (code !in 200..299) {
                val msg = obj?.get("error")?.jsonPrimitive?.content
                throw ApiException(msg ?: "Serveur injoignable ($code).")
            }
            obj ?: JsonObject(emptyMap())
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Pas de connexion au serveur.")
        } finally {
            conn.disconnect()
        }
    }

    private fun secretOf(repo: Repository): String =
        repo.data.value.friendSecret ?: throw ApiException("Choisis d'abord un pseudo.")

    private suspend fun act(repo: Repository, action: String, extra: Map<String, JsonElement> = emptyMap()): JsonObject =
        call(secretOf(repo), JsonObject(mapOf("action" to JsonPrimitive(action)) + extra))

    // ------------------------------------------------------------ profil

    /** Crée le profil (ou change le pseudo). */
    suspend fun register(repo: Repository, pseudo: String) {
        val secret = repo.data.value.friendSecret ?: newSecret()
        val r = call(secret, buildJsonObject { put("action", "hello"); put("pseudo", pseudo.trim()) })
        repo.setFriendIdentity(secret, r["pseudo"]!!.jsonPrimitive.content, r["code"]!!.jsonPrimitive.content)
    }

    /** Supprime le profil, les liens d'amitié et les schémas publiés sur le serveur. */
    suspend fun deleteProfile(repo: Repository) {
        act(repo, "delete_me")
        cache.clear()
        repo.clearFriendIdentity()
    }

    // ------------------------------------------------------------ amis

    suspend fun friends(repo: Repository): List<Friend> =
        Repository.json.decodeFromJsonElement(FriendList.serializer(), act(repo, "friends")).friends

    /** Envoie une invitation ; retourne le statut (« friend » si l'autre m'avait déjà invité) et son pseudo. */
    suspend fun add(repo: Repository, code: String): Pair<String, String> {
        val r = act(repo, "add", mapOf("code" to JsonPrimitive(code)))
        return r["status"]!!.jsonPrimitive.content to (r["pseudo"]?.jsonPrimitive?.content ?: "")
    }

    suspend fun accept(repo: Repository, code: String) { act(repo, "accept", mapOf("code" to JsonPrimitive(code))) }

    suspend fun remove(repo: Repository, code: String) {
        act(repo, "remove", mapOf("code" to JsonPrimitive(code)))
        cache.remove(code)
    }

    suspend fun schemasOf(repo: Repository, code: String): FriendSchemas {
        val r = act(repo, "schemas", mapOf("code" to JsonPrimitive(code)))
        return Repository.json.decodeFromJsonElement(FriendSchemas.serializer(), r).also { cache[code] = it }
    }

    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000; readTimeout = 30_000
            try { inputStream.use { it.readBytes() } } finally { disconnect() }
        }
    }

    // ------------------------------------------------------------ publication

    suspend fun publish(repo: Repository, project: Project) {
        val photo = repo.photoFile(project.photo)?.let { compress(it) }
        act(
            repo, "publish",
            mapOf(
                "projectId" to JsonPrimitive(project.id),
                "name" to JsonPrimitive(project.name),
                "schema" to Repository.json.encodeToJsonElement(SharedSchema.serializer(), repo.sharedPayload(project)),
                "photo" to (photo?.let { JsonPrimitive(it) } ?: JsonNull),
            ),
        )
    }

    suspend fun unpublish(repo: Repository, projectId: String) {
        act(repo, "unpublish", mapOf("projectId" to JsonPrimitive(projectId)))
    }

    /** Publie ou retire un schéma selon sa visibilité actuelle (lui-même ou son dossier). */
    private suspend fun apply(repo: Repository, project: Project) {
        val d = repo.data.value
        val p = d.projects.firstOrNull { it.id == project.id } ?: project
        if (d.visibleToFriends(p)) publish(repo, p) else unpublish(repo, p.id)
    }

    /** Rend un schéma visible ou non par mes amis (publication immédiate). */
    suspend fun setShared(repo: Repository, project: Project, shared: Boolean) {
        repo.setSharedWithFriends(project.id, shared)
        try {
            apply(repo, project)
        } catch (e: Exception) {
            repo.setSharedWithFriends(project.id, !shared)
            throw e
        }
    }

    /** Rend un dossier (et tous ses schémas) visible ou non par mes amis. */
    suspend fun setFolderShared(repo: Repository, folderId: String, shared: Boolean) {
        secretOf(repo)
        repo.setFolderShared(folderId, shared)
        try {
            repo.data.value.projects.filter { it.folderId == folderId }.forEach { apply(repo, it) }
        } catch (e: Exception) {
            repo.setFolderShared(folderId, !shared)
            syncInBackground(repo)
            throw e
        }
    }

    /** Republie en arrière-plan (ex. dossier partagé renommé). */
    internal fun republish(repo: Repository, projects: List<Project>) {
        val d = repo.data.value
        if (d.friendSecret == null) return
        projects.filter { d.visibleToFriends(it) }.forEach { p -> scope.launch { runCatching { publish(repo, p) } } }
    }

    internal fun onSaved(repo: Repository, project: Project) {
        val d = repo.data.value
        if (d.friendSecret == null || !d.visibleToFriends(project)) return
        scope.launch { runCatching { publish(repo, project) } }
    }

    internal fun onDeleted(repo: Repository, projectId: String) {
        if (repo.data.value.friendSecret == null) return
        scope.launch { runCatching { unpublish(repo, projectId) } }
    }

    /**
     * Rattrapage (au lancement) : republie ce qui a changé hors connexion et retire du serveur
     * ce qui n'est plus partagé.
     */
    fun syncInBackground(repo: Repository) {
        if (repo.data.value.friendSecret == null) return
        scope.launch {
            syncMutex.withLock {
                runCatching {
                    val online = Repository.json.decodeFromJsonElement(PublishedList.serializer(), act(repo, "mine")).schemas
                        .associate { it.projectId to (runCatching { Instant.parse(it.updatedAt).toEpochMilli() }.getOrNull() ?: 0L) }
                    val d = repo.data.value
                    d.projects.filter { d.visibleToFriends(it) }.forEach { p ->
                        val at = online[p.id]
                        if (at == null || at < p.updatedAt) runCatching { publish(repo, p) }
                    }
                    val visible = d.friendVisibleIds
                    online.keys.filter { it !in visible }.forEach { runCatching { unpublish(repo, it) } }
                }
            }
        }
    }

    /** Photo réduite (1280 px max, JPEG 80) et redressée, encodée en base64. */
    private fun compress(file: File): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = 1280f / maxOf(bmp.width, bmp.height)
        val rotation = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (scale < 1f || rotation != 0f) {
            val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}
