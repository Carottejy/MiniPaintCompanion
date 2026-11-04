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
import java.net.URLEncoder

enum class FeedbackType(val label: String, val template: String, val titlePrefix: String) {
    BUG("Signaler un bug", "bug.yml", "[Bug] "),
    IDEA("Proposer une idée", "idea.yml", "[Idée] "),
}

private const val ISSUES_URL = "https://github.com/Carottejy/MiniPaintCompanion/issues/new"

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/**
 * Ouvre le formulaire de ticket GitHub prérempli (modèles .github/ISSUE_TEMPLATE/*.yml) :
 * le ticket arrive dans l'onglet Issues du dépôt, avec l'étiquette bug ou enhancement.
 */
fun feedbackUrl(context: Context, type: FeedbackType, title: String, description: String, steps: String): String {
    val (version, build) = Updater.installedVersion(context)
    val device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val params = buildList {
        add("template=${type.template}")
        add("title=" + enc(type.titlePrefix + title.trim()))
        add("description=" + enc(description.trim()))
        if (type == FeedbackType.BUG && steps.isNotBlank()) add("steps=" + enc(steps.trim()))
        add("version=" + enc("$version (build $build)"))
        if (type == FeedbackType.BUG) add("device=" + enc(device))
    }
    return "$ISSUES_URL?" + params.joinToString("&")
}

@Composable
fun FeedbackDialog(type: FeedbackType, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("") }
    val valid = title.isNotBlank() && description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
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
                Text(
                    "La version de l'appli" + (if (type == FeedbackType.BUG) " et le modèle du téléphone sont ajoutés" else " est ajoutée") +
                        " automatiquement. Le formulaire GitHub s'ouvre prérempli : il ne reste qu'à valider " +
                        "(un compte GitHub gratuit est nécessaire).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val url = feedbackUrl(context, type, title, description, steps)
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    onDismiss()
                } catch (e: Exception) {
                    Toast.makeText(context, "Aucun navigateur trouvé", Toast.LENGTH_LONG).show()
                }
            }) { Text("Envoyer sur GitHub") }
        },
        dismissButton = {
            TextButton(enabled = valid, onClick = {
                val (version, build) = Updater.installedVersion(context)
                val text = buildString {
                    appendLine(type.titlePrefix + title.trim())
                    appendLine()
                    appendLine(description.trim())
                    if (steps.isNotBlank()) { appendLine(); appendLine("Pour reproduire : ${steps.trim()}") }
                    appendLine()
                    appendLine("Version $version (build $build) · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                }
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Retour MiniPaintCompanion", text))
                Toast.makeText(context, "Texte copié : colle-le où tu veux (message, mail…)", Toast.LENGTH_LONG).show()
            }) { Text("Copier") }
        },
    )
}
