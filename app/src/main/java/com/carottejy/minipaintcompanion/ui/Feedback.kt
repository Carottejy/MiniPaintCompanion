package com.carottejy.minipaintcompanion.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.update.Updater
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

enum class FeedbackType(val label: String, val template: String, val titlePrefix: String) {
    BUG("Signaler un bug", "bug.yml", "[Bug] "),
    IDEA("Proposer une idée", "idea.yml", "[Idée] "),
}

/** Relais Supabase qui crée le ticket GitHub avec un jeton gardé côté serveur. */
private const val FEEDBACK_URL = "https://qqrmozqxzeicbxwauiqt.supabase.co/functions/v1/minipaint-feedback"
private const val FEEDBACK_KEY = "sb_publishable_doGPithS7QQzmp0up3bRLw_zeg2jnDw"
const val ISSUES_PAGE = "https://github.com/Carottejy/MiniPaintCompanion/issues"

fun deviceDescription() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

/** Envoie le retour ; renvoie le numéro du ticket créé. Aucun compte GitHub nécessaire. */
suspend fun sendFeedback(
    context: Context,
    type: FeedbackType,
    title: String,
    description: String,
    steps: String,
    author: String,
): Int = withContext(Dispatchers.IO) {
    val (version, build) = Updater.installedVersion(context)
    val payload = buildJsonObject {
        put("type", if (type == FeedbackType.BUG) "bug" else "idea")
        put("title", title.trim())
        put("description", description.trim())
        put("steps", steps.trim())
        put("version", "$version (build $build)")
        if (type == FeedbackType.BUG) put("device", deviceDescription())
        put("author", author.trim())
    }.toString()
    val conn = (URL(FEEDBACK_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 15000
        readTimeout = 20000
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("x-app", "MiniPaintCompanion")
        setRequestProperty("apikey", FEEDBACK_KEY)
    }
    try {
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (code !in 200..299) {
            throw IllegalStateException(obj?.get("error")?.jsonPrimitive?.content ?: "erreur $code")
        }
        obj?.get("number")?.jsonPrimitive?.intOrNull ?: 0
    } finally {
        conn.disconnect()
    }
}

@Composable
fun FeedbackDialog(type: FeedbackType, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val valid = title.trim().length >= 3 && description.trim().length >= 5

    fun copy() {
        val (version, build) = Updater.installedVersion(context)
        val text = buildString {
            appendLine(type.titlePrefix + title.trim())
            appendLine()
            appendLine(description.trim())
            if (steps.isNotBlank()) { appendLine(); appendLine("Pour reproduire : ${steps.trim()}") }
            appendLine()
            appendLine("Version $version (build $build) · ${deviceDescription()}")
        }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Retour MiniPaintCompanion", text))
        Toast.makeText(context, "Texte copié : colle-le où tu veux (message, mail…)", Toast.LENGTH_LONG).show()
    }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(type.label) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    title, { title = it },
                    label = { Text("Titre") },
                    placeholder = {
                        Text(if (type == FeedbackType.BUG) "ex. Le scan ne trouve pas mes pots Citadel" else "ex. Pouvoir noter le temps passé")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    description, { description = it },
                    label = { Text(if (type == FeedbackType.BUG) "Que se passe-t-il ?" else "Ton idée") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == FeedbackType.BUG) {
                    OutlinedTextField(
                        steps, { steps = it },
                        label = { Text("Comment le reproduire ? (optionnel)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    author, { author = it },
                    label = { Text("Pseudo (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Pas besoin de compte : ton message devient un ticket public sur la page GitHub du projet. " +
                        "La version de l'appli" + (if (type == FeedbackType.BUG) " et le modèle du téléphone sont ajoutés" else " est ajoutée") +
                        ". N'y mets pas d'informations personnelles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let {
                    Text(
                        "Envoi impossible : $it Tu peux copier le texte et l'envoyer autrement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !sending, onClick = {
                sending = true
                error = null
                scope.launch {
                    runCatching { sendFeedback(context, type, title, description, steps, author) }
                        .onSuccess { n ->
                            Toast.makeText(context, "Merci ! Ton message est enregistré (ticket n° $n).", Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                        .onFailure { error = it.message ?: "erreur réseau." }
                    sending = false
                }
            }) { Text(if (sending) "Envoi…" else "Envoyer") }
        },
        dismissButton = {
            TextButton(enabled = valid && !sending, onClick = ::copy) { Text("Copier") }
        },
    )
}
