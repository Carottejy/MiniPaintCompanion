package com.carottejy.minipaintcompanion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Paint
import com.carottejy.minipaintcompanion.data.Repository
import com.carottejy.minipaintcompanion.data.VoiceParser
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ScanMode(val label: String) { LABEL("Étiquette"), QR("QR code") }

/** Transforme le contenu d'un QR code (souvent une URL produit) en texte exploitable. */
fun qrToText(content: String): String =
    runCatching { URLDecoder.decode(content, "UTF-8") }.getOrDefault(content)
        .replace(Regex("""https?://"""), " ")
        .replace(Regex("""[/\-_.?=&#+]"""), " ")

/**
 * Identification d'un pot, plein écran :
 *  - Étiquette : la caméra lit le texte du pot (nom, référence) et propose les pots correspondants ;
 *  - QR code : lit un QR code (pot, fiche produit) ; le pot associé est ensuite mémorisé.
 */
@Composable
fun PaintScannerOverlay(
    paints: List<Paint>,
    ownedIds: Set<String>,
    onPaint: (Paint) -> Unit,
    onQr: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    BackHandler(onBack = onClose)

    var mode by remember { mutableStateOf(ScanMode.LABEL) }
    var candidates by remember { mutableStateOf<List<Paint>>(emptyList()) }
    var lastText by remember { mutableStateOf("") }
    var frozen by remember { mutableStateOf(false) } // résultat d'une photo de la galerie affiché

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(Unit) { onDispose { recognizer.close() } }

    fun analyzeText(text: String) {
        val found = VoiceParser.candidates(text, paints, ownedIds, 4).map { it.first }
        if (found.isNotEmpty()) {
            candidates = found
            lastText = text
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()?.let { img ->
                frozen = true
                candidates = emptyList()
                recognizer.process(img)
                    .addOnSuccessListener { result ->
                        analyzeText(result.text)
                        if (candidates.isEmpty()) lastText = result.text.lines().firstOrNull { it.length > 3 }.orEmpty()
                    }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            LiveCamera(
                mode = mode,
                recognizer = recognizer,
                onText = { if (!frozen) analyzeText(it) },
                onQr = onQr,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("L'appareil photo est nécessaire pour identifier un pot.", color = Color.White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
            }
        }

        // En-tête : fermer + choix du mode
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                ) { Icon(Icons.Default.Close, contentDescription = "Fermer") }
                Spacer(Modifier.width(8.dp))
                ScanMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m; candidates = emptyList(); frozen = false },
                        label = { Text(m.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            labelColor = Color.White,
                        ),
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Text(
                if (mode == ScanMode.LABEL) "Cadre le nom ou la référence du pot, bien éclairé."
                else "Vise le QR code.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // Résultats (mode étiquette)
        if (mode == ScanMode.LABEL) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f), RoundedCornerShape(18.dp))
                    .padding(12.dp),
            ) {
                Text(
                    if (candidates.isEmpty()) "Je cherche un nom de pot…" else "C'est lequel ? Touche le bon pot",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                candidates.take(3).forEach { p ->
                    PaintLine(p, owned = null, modifier = Modifier.clickable { onPaint(p) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Photo existante")
                    }
                    TextButton(onClick = { onSearch(lastText) }) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pas dans la liste", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (frozen) {
                    TextButton(onClick = { frozen = false; candidates = emptyList() }) { Text("Revenir à la caméra") }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun LiveCamera(
    mode: ScanMode,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    onText: (String) -> Unit,
    onQr: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val currentText by rememberUpdatedState(onText)
    val currentQr by rememberUpdatedState(onQr)
    val modeRef = remember { AtomicReference(mode) }
    SideEffect { modeRef.set(mode) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(0.15f) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy = remember { AtomicBoolean(false) }
    val qrDelivered = remember { AtomicBoolean(false) }
    val lastOcr = remember { longArrayOf(0L) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val qrScanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    val now = System.currentTimeMillis()
                    val m = modeRef.get()
                    // Lecture de texte : une image toutes les 0,7 s suffit et ménage la batterie.
                    val skip = media == null || busy.get() || (m == ScanMode.LABEL && now - lastOcr[0] < 700)
                    if (skip) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    busy.set(true)
                    lastOcr[0] = now
                    val input = InputImage.fromMediaImage(media!!, proxy.imageInfo.rotationDegrees)
                    val done = {
                        busy.set(false)
                        proxy.close()
                    }
                    if (m == ScanMode.QR) {
                        qrScanner.process(input)
                            .addOnSuccessListener { codes ->
                                val v = codes.firstNotNullOfOrNull { it.rawValue?.takeIf { s -> s.isNotBlank() } }
                                if (v != null && qrDelivered.compareAndSet(false, true)) currentQr(v)
                            }
                            .addOnCompleteListener { done() }
                    } else {
                        recognizer.process(input)
                            .addOnSuccessListener { result -> if (result.text.isNotBlank()) currentText(result.text) }
                            .addOnCompleteListener { done() }
                    }
                }
                p.unbindAll()
                val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                cam.cameraControl.setLinearZoom(zoom)
                camera = cam
            } catch (e: Exception) {
                error = "Caméra indisponible : ${e.message}"
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            provider?.unbindAll()
            qrScanner.close()
            executor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 280.dp, height = if (mode == ScanMode.QR) 240.dp else 170.dp)
                .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
        )
        error?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp)) }

        // Lampe + zoom, au-dessus du panneau de résultats
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    torch = !torch
                    camera?.cameraControl?.enableTorch(torch)
                }) {
                    Icon(if (torch) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, contentDescription = "Lampe", tint = Color.White)
                }
                Text("Zoom", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        camera?.cameraControl?.setLinearZoom(it)
                    },
                    modifier = Modifier.width(110.dp),
                )
            }
        }
    }
}

/** Choix d'un pot quand l'identification est incertaine (QR inconnu, étiquette illisible). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaintChoiceSheet(
    title: String,
    message: String,
    candidates: List<Paint>,
    initialQuery: String,
    data: AppData,
    repo: Repository,
    onPicked: (Paint) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = remember(data.customPaints) { repo.allPaints(data) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (candidates.isNotEmpty()) {
            Text(
                "SUGGESTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
            candidates.take(3).forEach { p ->
                PaintLine(p, modifier = Modifier.clickable { onPicked(p) }.padding(horizontal = 16.dp))
            }
        }
        PaintBrowser(
            paints = all,
            ownedIds = data.ownedPaintIds,
            isChecked = { false },
            onToggle = { _, _ -> },
            onRowClick = onPicked,
            rowTrailing = { },
            initialQuery = initialQuery,
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
