package com.carottejy.minipaintcompanion.update

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.carottejy.minipaintcompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val build: Long,
    val url: String,
    val size: Long,
    val notes: String,
)

/**
 * Mise à jour depuis les Releases GitHub (APK « github » uniquement).
 * Les releases sont nommées v1.0.<build> : le dernier nombre est comparé au versionCode installé.
 */
object Updater {
    private const val API = "https://api.github.com/repos/Carottejy/MiniPaintCompanion/releases/latest"
    const val RELEASES_PAGE = "https://github.com/Carottejy/MiniPaintCompanion/releases/latest"
    private const val PREFS = "updater"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_IGNORED = "ignored"
    private const val KEY_AUTO = "auto_install"
    private const val KEY_SILENT_BLOCKED = "silent_blocked"
    private const val AUTO_INTERVAL_MS = 60 * 60 * 1000L

    val enabled: Boolean get() = BuildConfig.SELF_UPDATE

    fun installedVersion(context: Context): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return (info.versionName ?: "?") to PackageInfoCompat.getLongVersionCode(info)
    }

    /**
     * Interroge GitHub. Retourne la mise à jour si une version plus récente existe.
     * [auto] : vérification discrète au lancement (au plus toutes les 6 h, versions ignorées exclues).
     */
    suspend fun check(context: Context, auto: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (auto && System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECK, 0) < AUTO_INTERVAL_MS) {
            return@withContext null
        }
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MiniPaintCompanion")
        }
        val body = try {
            val code = conn.responseCode
            if (code == 404) throw IllegalStateException("aucune release publique trouvée (dépôt privé ?)")
            if (code != 200) throw IllegalStateException("GitHub a répondu $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

        val root = Json.parseToJsonElement(body).jsonObject
        val version = root["tag_name"]?.jsonPrimitive?.content.orEmpty().removePrefix("v")
        val build = version.substringAfterLast('.').toLongOrNull() ?: return@withContext null
        val asset = root["assets"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content.orEmpty().endsWith(".apk") }
            ?: return@withContext null
        val (_, installed) = installedVersion(context)
        if (build <= installed) return@withContext null
        if (auto && prefs.getString(KEY_IGNORED, null) == version) return@withContext null

        UpdateInfo(
            version = version,
            build = build,
            url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return@withContext null,
            size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            notes = root["body"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    /** Mise à jour automatique (téléchargement en arrière-plan + installation au lancement suivant). */
    fun isAutoEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    private fun updatesDir(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

    /** APK déjà téléchargé et plus récent que la version installée, prêt à installer. */
    fun pendingApk(context: Context): Pair<File, Long>? {
        val installed = installedVersion(context).second
        var best: Pair<File, Long>? = null
        updatesDir(context).listFiles()?.forEach { f ->
            val build = Regex("""MiniPaintCompanion-(\d+)\.apk""").matchEntire(f.name)?.groupValues?.get(1)?.toLongOrNull()
            when {
                build == null || build <= installed -> f.delete()
                best == null || build > best!!.second -> best = f to build
            }
        }
        return best
    }

    /**
     * Installe l'APK via PackageInstaller. Sur Android 12+, une appli qui se met à jour elle-même
     * peut le faire sans confirmation (USER_ACTION_NOT_REQUIRED) ; sinon Android affiche sa fenêtre.
     */
    fun installSilently(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                flags,
            )
            session.commit(pending.intentSender)
        }
    }

    /**
     * Certains téléphones (MIUI/HyperOS de Xiaomi notamment) refusent l'installation silencieuse
     * (« INSTALL_FAILED_INTERNAL_ERROR: Permission Denied »). On le mémorise pour passer
     * ensuite par l'installateur système, avec confirmation.
     */
    fun isSilentBlocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SILENT_BLOCKED, false)

    fun setSilentBlocked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SILENT_BLOCKED, true).apply()
    }

    /** Installation silencieuse si le téléphone l'accepte, sinon installateur système. */
    fun installBest(context: Context, apk: File) {
        if (isSilentBlocked(context)) {
            install(context, apk)
            return
        }
        try {
            installSilently(context, apk)
        } catch (e: Exception) {
            setSilentBlocked(context)
            install(context, apk)
        }
    }

    fun ignore(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_IGNORED, version).apply()
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Télécharge l'APK (en suivant les redirections GitHub) en signalant la progression en %. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = updatesDir(context)
            val apk = File(dir, "MiniPaintCompanion-${info.build}.apk")
            if (apk.exists() && info.size > 0 && apk.length() == info.size) return@withContext apk
            val part = File(dir, "download.part")

            var current = info.url
            var conn: HttpURLConnection? = null
            try {
                for (attempt in 0 until 6) {
                    val c = (URL(current).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 20000
                        readTimeout = 60000
                        setRequestProperty("User-Agent", "MiniPaintCompanion")
                    }
                    val code = c.responseCode
                    if (code in 300..399) {
                        val loc = c.getHeaderField("Location")
                        c.disconnect()
                        if (loc == null) throw IllegalStateException("redirection sans adresse")
                        current = URL(URL(current), loc).toString()
                        continue
                    }
                    if (code != 200) {
                        c.disconnect()
                        throw IllegalStateException("HTTP $code")
                    }
                    conn = c
                    break
                }
                val c = conn ?: throw IllegalStateException("trop de redirections")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
                var received = 0L
                var last = -1
                c.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            received += n
                            if (total > 0) {
                                val pct = (received * 100 / total).toInt()
                                if (pct != last) {
                                    last = pct
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                        }
                    }
                }
            } finally {
                conn?.disconnect()
            }
            apk.delete()
            if (!part.renameTo(apk)) throw IllegalStateException("enregistrement impossible")
            apk
        }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
