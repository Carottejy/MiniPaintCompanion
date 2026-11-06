package com.carottejy.minipaintcompanion.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Project
import com.carottejy.minipaintcompanion.data.Repository
import kotlinx.coroutines.launch

/** Texte lisible d'un schéma : étapes, technique, dilution, pots. */
fun schemaAsText(project: Project, data: AppData, repo: Repository): String {
    val byId = repo.allPaints(data).associateBy { it.id }
    return buildString {
        appendLine("🎨 ${project.name.ifBlank { "Schéma sans titre" }}")
        if (project.notes.isNotBlank()) appendLine(project.notes)
        project.steps.forEachIndexed { i, s ->
            appendLine()
            val title = s.title.ifBlank { "Étape ${i + 1}" }
            val extra = buildList {
                add(s.technique.label.lowercase())
                if (s.dilution > 0) add("dilution ${s.dilution} %")
            }.joinToString(", ")
            appendLine("${i + 1}. $title ($extra)")
            s.paintIds.mapNotNull { byId[it] }.forEach { p ->
                appendLine("   • ${p.brand.shortLabel()} ${p.displayName}")
            }
            if (s.notes.isNotBlank()) appendLine("   ${s.notes}")
        }
        appendLine()
        append("Partagé avec MiniPaintCompanion")
    }
}

private fun shareFile(context: Context, file: java.io.File, mime: String, text: String?) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
        clipData = ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Partager le schéma"))
}

/** Choix du format de partage d'un schéma. */
@Composable
fun ShareSchemaDialog(project: Project, data: AppData, repo: Repository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    @Composable
    fun Option(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager le schéma") },
        text = {
            Column {
                Option(
                    Icons.Outlined.Inventory2,
                    "Pour un autre utilisateur de l'appli",
                    "Fichier MiniPaint : il l'ouvre et le schéma complet (photo, étapes, pots) s'ajoute à ses schémas.",
                ) {
                    scope.launch {
                        runCatching { repo.exportSchema(project.id) }
                            .onSuccess { f ->
                                shareFile(context, f, "application/zip", "Schéma « ${project.name} » — à ouvrir avec MiniPaintCompanion")
                                onDismiss()
                            }
                            .onFailure { Toast.makeText(context, "Partage impossible : ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                }
                Option(
                    Icons.Outlined.Description,
                    "En texte (avec la photo)",
                    "Pour un message, un forum ou un réseau social : lisible par tous.",
                ) {
                    val text = schemaAsText(project, data, repo)
                    val photo = repo.photoFile(project.photo)
                    if (photo != null) {
                        shareFile(context, photo, "image/jpeg", text)
                    } else {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        context.startActivity(Intent.createChooser(send, "Partager le schéma"))
                    }
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
