package com.carottejy.minipaintcompanion.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.ColorTheory
import com.carottejy.minipaintcompanion.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_PINS = 8

/** Couleur épinglée : position relative dans l'image (0–1) + teinte relevée. */
private data class Pin(val fx: Float, val fy: Float, val hex: String)

private fun encodePins(p: List<Pin>) = p.joinToString("|") { "${it.fx};${it.fy};${it.hex}" }
private fun decodePins(s: String) = s.split("|").filter { it.isNotBlank() }.mapNotNull {
    val parts = it.split(";")
    if (parts.size == 3) Pin(parts[0].toFloat(), parts[1].toFloat(), parts[2]) else null
}

private fun pipetteFile(context: Context) = File(File(context.cacheDir, "pipette").apply { mkdirs() }, "image.jpg")

/** Charge l'image réduite (≤ 1600 px) et redressée selon l'EXIF. */
private suspend fun loadBitmap(file: File, maxSide: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return@withContext null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@withContext null
    val degrees = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) bmp
    else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
}

/** Moyenne des pixels autour du point : évite de tomber sur un reflet ou un grain isolé. */
private fun sampleHex(bmp: Bitmap, fx: Float, fy: Float): String {
    val cx = (fx * (bmp.width - 1)).roundToInt()
    val cy = (fy * (bmp.height - 1)).roundToInt()
    val r = max(2, min(bmp.width, bmp.height) / 150)
    var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
    for (y in max(0, cy - r)..min(bmp.height - 1, cy + r)) {
        for (x in max(0, cx - r)..min(bmp.width - 1, cx + r)) {
            val c = bmp.getPixel(x, y)
            rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
        }
    }
    if (n == 0) return "#808080"
    return ColorTheory.rgbToHex((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
}

private fun closeness(delta: Float) = when {
    delta < 3f -> "quasi identique"
    delta < 7f -> "très proche"
    delta < 13f -> "proche"
    else -> "approchant"
}

/**
 * Onglet « Pipette » : on relève une couleur sur une photo avec un curseur,
 * et l'appli propose les pots les plus proches, à ajouter à la liste d'achat.
 */
@Composable
fun PipetteScreen(
    data: AppData,
    repo: Repository,
    onHarmonies: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageVersion by rememberSaveable { mutableStateOf(if (pipetteFile(context).exists()) 1 else 0) }
    var fx by rememberSaveable { mutableStateOf(0.5f) }
    var fy by rememberSaveable { mutableStateOf(0.5f) }
    var pinsText by rememberSaveable { mutableStateOf("") }
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    val pins = remember(pinsText) { decodePins(pinsText) }

    val bitmap by produceState<Bitmap?>(null, imageVersion) {
        value = if (imageVersion == 0) null else loadBitmap(pipetteFile(context))
    }
    val hex = remember(bitmap, fx, fy) { bitmap?.let { sampleHex(it, fx, fy) } }

    fun newImage() {
        imageVersion++
        fx = 0.5f; fy = 0.5f
        pinsText = ""
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        pipetteFile(context).outputStream().use { input.copyTo(it) }
                    }
                }.isSuccess
            }
            if (ok) newImage() else Toast.makeText(context, "Impossible d'ouvrir cette image", Toast.LENGTH_SHORT).show()
        }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) newImage()
    }
    fun launchCamera() {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pipetteFile(context))
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune application appareil photo trouvée", Toast.LENGTH_SHORT).show()
        }
    }

    val allPaints = remember(data.customPaints) { repo.allPaints(data) }
    val owned = remember(allPaints, data.ownedPaintIds) { allPaints.filter { it.id in data.ownedPaintIds } }
    val pool = if (onlyMine && owned.isNotEmpty()) owned else allPaints
    val matches = remember(hex, pool) { hex?.let { ColorTheory.closestPaints(it, pool, 5) }.orEmpty() }

    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Galerie")
                }
                FilledTonalButton(onClick = ::launchCamera, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Photo")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val bmp = bitmap
        if (bmp == null) {
            item {
                EmptyState(
                    Icons.Outlined.Colorize,
                    "Pipette à couleurs",
                    "Ouvre une photo (figurine peinte, illustration, schéma de couleurs…), place le curseur sur une couleur : " +
                        "je te propose les pots les plus proches du catalogue.",
                    Modifier.height(360.dp),
                )
            }
            return@LazyColumn
        }

        item {
            PickerImage(
                bitmap = bmp,
                fx = fx, fy = fy, hex = hex ?: "#808080",
                pins = pins,
                onMove = { x, y -> fx = x; fy = y },
            )
            Spacer(Modifier.height(12.dp))

            // Couleur relevée
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 56.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(hexColor(hex ?: "#808080")))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("COULEUR RELEVÉE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(hex ?: "", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(
                    onClick = {
                        if (pins.size >= MAX_PINS) {
                            Toast.makeText(context, "$MAX_PINS couleurs épinglées au maximum", Toast.LENGTH_SHORT).show()
                        } else if (hex != null) {
                            pinsText = encodePins(pins + Pin(fx, fy, hex))
                        }
                    },
                ) { Icon(Icons.Outlined.PushPin, contentDescription = "Épingler cette couleur") }
                IconButton(onClick = { hex?.let(onHarmonies) }) {
                    Icon(Icons.Outlined.Palette, contentDescription = "Harmonies à partir de cette couleur")
                }
            }

            if (pins.isNotEmpty()) {
                Text(
                    "Palette épinglée — touche une couleur pour y revenir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(pins) { i, pin ->
                        Box {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(hexColor(pin.hex))
                                    .border(
                                        if (pin.fx == fx && pin.fy == fy) 3.dp else 1.dp,
                                        if (pin.fx == fx && pin.fy == fy) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                        CircleShape,
                                    )
                                    .clickable { fx = pin.fx; fy = pin.fy }
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { pinsText = encodePins(pins.filterIndexed { j, _ -> j != i }) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, contentDescription = "Retirer", modifier = Modifier.size(12.dp)) }
                        }
                    }
                }
                // Le meilleur pot de chaque couleur épinglée, s'il n'est pas déjà possédé
                val bestMissing = remember(pins, pool, data.ownedPaintIds, data.toBuyIds) {
                    pins.mapNotNull { ColorTheory.closestPaints(it.hex, pool, 1).firstOrNull()?.first }
                        .distinctBy { it.id }
                        .filter { it.id !in data.ownedPaintIds && it.id !in data.toBuyIds }
                }
                if (bestMissing.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            repo.addAllToBuy(bestMissing.map { it.id })
                            Toast.makeText(context, "${bestMissing.size} pot${if (bestMissing.size > 1) "s" else ""} ajouté${if (bestMissing.size > 1) "s" else ""} à la liste d'achat", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Acheter la palette (${bestMissing.size} pot${if (bestMissing.size > 1) "s" else ""} manquant${if (bestMissing.size > 1) "s" else ""})")
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clickable(enabled = owned.isNotEmpty()) { onlyMine = !onlyMine }.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (owned.isEmpty()) "Pots proposés : tout le catalogue" else "Seulement les pots de ma collection",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = onlyMine, onCheckedChange = { onlyMine = it }, enabled = owned.isNotEmpty())
            }
            Text(
                "Pots les plus proches",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                "La lumière et l'écran faussent les couleurs d'une photo : vérifie avec le pot en main.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        matches.forEach { (p, delta) ->
            item(key = "m_" + p.id) {
                PaintLine(p, trailing = {
                    Text(
                        closeness(delta),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    if (p.id in data.ownedPaintIds) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Dans ma collection",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(12.dp).size(22.dp),
                        )
                    } else {
                        val inList = p.id in data.toBuyIds
                        IconButton(onClick = { repo.setToBuy(p.id, !inList) }) {
                            Icon(
                                if (inList) Icons.Filled.ShoppingCart else Icons.Outlined.AddShoppingCart,
                                contentDescription = if (inList) "Retirer de la liste d'achat" else "Ajouter à la liste d'achat",
                                tint = if (inList) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                })
            }
        }
    }
}

/** Image affichée en entier, avec un curseur à déplacer au doigt et une loupe de la couleur relevée. */
@Composable
private fun PickerImage(
    bitmap: Bitmap,
    fx: Float,
    fy: Float,
    hex: String,
    pins: List<Pin>,
    onMove: (Float, Float) -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val scale = min(w / bitmap.width, h / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f

        fun toFraction(p: Offset) = Pair(((p.x - left) / dw).coerceIn(0f, 1f), ((p.y - top) / dh).coerceIn(0f, 1f))

        Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap, w, h) {
                    detectTapGestures { p -> val (x, y) = toFraction(p); onMove(x, y) }
                }
                .pointerInput(bitmap, w, h) {
                    var cur = Offset.Zero
                    detectDragGestures(
                        onDragStart = { p -> cur = p; val (x, y) = toFraction(p); onMove(x, y) },
                    ) { change, drag ->
                        change.consume()
                        cur += drag
                        val (x, y) = toFraction(cur)
                        onMove(x, y)
                    }
                }
        ) {
            // Couleurs épinglées
            pins.forEach { pin ->
                val c = Offset(left + pin.fx * dw, top + pin.fy * dh)
                drawCircle(Color.Black, radius = 9.dp.toPx(), center = c)
                drawCircle(Color.White, radius = 7.dp.toPx(), center = c)
                drawCircle(hexColor(pin.hex), radius = 5.dp.toPx(), center = c)
            }
            // Curseur
            val c = Offset(left + fx * dw, top + fy * dh)
            val r = 14.dp.toPx()
            drawCircle(Color.Black, radius = r + 2f, center = c, style = Stroke(5f))
            drawCircle(Color.White, radius = r, center = c, style = Stroke(3f))
            drawLine(Color.White, Offset(c.x - r - 8f, c.y), Offset(c.x - r / 2, c.y), strokeWidth = 3f)
            drawLine(Color.White, Offset(c.x + r / 2, c.y), Offset(c.x + r + 8f, c.y), strokeWidth = 3f)
            drawLine(Color.White, Offset(c.x, c.y - r - 8f), Offset(c.x, c.y - r / 2), strokeWidth = 3f)
            drawLine(Color.White, Offset(c.x, c.y + r / 2), Offset(c.x, c.y + r + 8f), strokeWidth = 3f)
            // Loupe de couleur au-dessus du doigt (en dessous si on est trop haut)
            val bubbleR = 24.dp.toPx()
            val above = c.y - r - bubbleR * 2.2f > 0
            val bc = Offset(c.x.coerceIn(bubbleR + 4f, size.width - bubbleR - 4f), if (above) c.y - r - bubbleR * 1.4f else c.y + r + bubbleR * 1.4f)
            drawCircle(Color.Black, radius = bubbleR + 4f, center = bc)
            drawCircle(Color.White, radius = bubbleR + 2f, center = bc)
            drawCircle(hexColor(hex), radius = bubbleR, center = bc)
        }
    }
}
