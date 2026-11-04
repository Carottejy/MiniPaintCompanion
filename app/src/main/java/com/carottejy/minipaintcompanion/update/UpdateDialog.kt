package com.carottejy.minipaintcompanion.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private sealed interface Phase {
    data object Offer : Phase
    data object NeedPermission : Phase
    data class Downloading(val percent: Int) : Phase
    data class Failed(val message: String) : Phase
}

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<Phase>(Phase.Offer) }

    fun start() {
        if (!Updater.canInstall(context)) {
            phase = Phase.NeedPermission
            return
        }
        phase = Phase.Downloading(0)
        scope.launch {
            try {
                val apk = Updater.download(context, info) { phase = Phase.Downloading(it) }
                Updater.install(context, apk)
                onDismiss()
            } catch (e: Exception) {
                phase = Phase.Failed(e.message ?: "erreur inconnue")
            }
        }
    }

    val downloading = phase is Phase.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = !downloading),
        title = { Text("Mise à jour ${info.version} disponible") },
        text = {
            Column {
                when (val p = phase) {
                    Phase.Offer -> {
                        val mo = if (info.size > 0) " (%.1f Mo)".format(info.size / 1_048_576.0) else ""
                        Text("Une nouvelle version de MiniPaintCompanion est prête$mo. Tes réalisations et ta collection sont conservées.")
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                info.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                    Phase.NeedPermission -> Text(
                        "Android doit autoriser MiniPaintCompanion à installer des applications. " +
                            "Active « Autoriser cette source » dans les réglages, reviens ici puis touche « Installer »."
                    )
                    is Phase.Downloading -> {
                        Text("Téléchargement… ${p.percent} %")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    is Phase.Failed -> Text("Le téléchargement a échoué : ${p.message}. Tu peux récupérer l'APK depuis la page GitHub.")
                }
            }
        },
        confirmButton = {
            when (phase) {
                Phase.Offer -> Button(onClick = ::start) { Text("Installer") }
                Phase.NeedPermission -> Column {
                    TextButton(onClick = { Updater.openInstallSettings(context) }) { Text("Ouvrir les réglages") }
                    Button(onClick = ::start) { Text("Installer") }
                }
                is Phase.Downloading -> {}
                is Phase.Failed -> Button(onClick = { uriHandler.openUri(info.url); onDismiss() }) { Text("Télécharger via le navigateur") }
            }
        },
        dismissButton = {
            when (phase) {
                Phase.Offer -> Column {
                    TextButton(onClick = onDismiss) { Text("Plus tard") }
                    TextButton(onClick = { Updater.ignore(context, info.version); onDismiss() }) { Text("Ignorer cette version") }
                }
                is Phase.Downloading -> {}
                else -> TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
    )
}
