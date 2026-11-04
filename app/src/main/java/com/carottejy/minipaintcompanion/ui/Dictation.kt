package com.carottejy.minipaintcompanion.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Dictée continue : contrairement à la fenêtre de reconnaissance de Google, qui s'arrête au premier
 * silence, on relance l'écoute à chaque pause jusqu'à ce que l'utilisateur touche « Terminé ».
 * Le texte reconnu reste modifiable avant d'être analysé.
 */
@Composable
fun DictationDialog(
    hint: String,
    biasing: List<String>,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    onFallback: () -> Unit,
) {
    val context = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    var text by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }       // l'utilisateur veut écouter
    var listening by remember { mutableStateOf(false) }   // le micro est ouvert
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    val handler = remember { Handler(Looper.getMainLooper()) }
    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Laisse plus de temps avant de considérer la phrase finie (pris en compte selon le moteur).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8000L)
            if (Build.VERSION.SDK_INT >= 33 && biasing.isNotEmpty()) {
                putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", ArrayList(biasing.take(300)))
            }
        }
    }

    fun append(chunk: String) {
        val c = chunk.trim()
        if (c.isEmpty()) return
        text = if (text.isBlank()) c else text.trimEnd() + " " + c
    }

    fun start() {
        if (recognizer == null || !active) return
        error = null
        runCatching { recognizer.startListening(intent) }.onFailure { error = it.message }
    }

    fun restartSoon(delay: Long = 250) {
        handler.removeCallbacksAndMessages(null)
        if (active) handler.postDelayed({ start() }, delay)
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onResults(results: Bundle?) {
                append(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                partial = ""
                listening = false
                restartSoon()
            }
            override fun onError(code: Int) {
                listening = false
                when (code) {
                    // Silence ou rien compris : on continue d'écouter.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        append(partial); partial = ""; restartSoon()
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartSoon(600)
                    SpeechRecognizer.ERROR_CLIENT -> Unit // annulation volontaire
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { active = false; error = "Autorise le micro pour dicter." }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                        active = false; error = "Connexion nécessaire pour la reconnaissance vocale."
                    }
                    else -> { active = false; error = "La reconnaissance s'est arrêtée (code $code). Touche le micro pour reprendre." }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            handler.removeCallbacksAndMessages(null)
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    LaunchedEffect(granted) {
        if (!granted) permission.launch(Manifest.permission.RECORD_AUDIO) else start()
    }

    fun finish() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        append(partial)
        partial = ""
        onDone(text)
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Dictée") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!available) {
                    Text("La reconnaissance vocale intégrée n'est pas disponible sur ce téléphone.")
                    TextButton(onClick = onFallback) { Text("Utiliser la dictée Google") }
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pulse by animateFloatAsState(if (listening) 1f + level * 0.5f else 1f, label = "pulse")
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .scale(pulse)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                        FilledTonalIconButton(onClick = {
                            active = !active
                            if (active) start() else {
                                handler.removeCallbacksAndMessages(null)
                                recognizer?.stopListening()
                            }
                        }) {
                            Icon(if (active) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = if (active) "Pause" else "Reprendre")
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when {
                            !granted -> "Autorise le micro pour dicter."
                            active -> "Je t'écoute… Fais des pauses si tu veux, touche « Terminé » quand tu as fini."
                            else -> "En pause. Touche le micro pour reprendre."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Texte dicté (modifiable)") },
                    placeholder = { Text(hint) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (partial.isNotBlank()) {
                    Text("… $partial", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = ::finish, enabled = text.isNotBlank() || partial.isNotBlank()) { Text("Terminé") }
        },
        dismissButton = {
            TextButton(onClick = {
                active = false
                recognizer?.cancel()
                onDismiss()
            }) { Text("Annuler") }
        },
    )
}
