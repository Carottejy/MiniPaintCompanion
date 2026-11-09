package com.carottejy.minipaintcompanion.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_MARKS = 26
/** En dessous de cet écart de L*, deux pots ont pratiquement la même valeur. */
private const val SAME_VALUE = 3f

/** Repère posé sur un pot : position et rayon relatifs à l'image, couleur moyenne relevée. */
private data class ValueMark(val label: String, val fx: Float, val fy: Float, val fr: Float, val hex: String) {
    val value: Float get() = valueOf(hex)
}

private fun encodeMarks(m: List<ValueMark>) = m.joinToString("|") { "${it.label};${it.fx};${it.fy};${it.fr};${it.hex}" }
private fun decodeMarks(s: String) = s.split("|").filter { it.isNotBlank() }.mapNotNull {
    val p = it.split(";")
    if (p.size == 5) ValueMark(p[0], p[1].toFloat(), p[2].toFloat(), p[3].toFloat(), p[4]) else null
}

private fun valueFile(context: Context) = File(File(context.cacheDir, "pipette").apply { mkdirs() }, "value.jpg")

/** Niveau de valeur sur 10 (0 = noir, 10 = blanc), comme une échelle de gris de peintre. */
fun valueLevel(l: Float): Int = (l / 10f).roundToInt().coerceIn(0, 10)

private fun contrastLabel(range: Float) = when {
    range < 15f -> "contraste très faible : les pots se confondront en noir et blanc"
    range < 30f -> "contraste faible"
    range < 50f -> "contraste moyen"
    else -> "contraste fort"
}

/**
 * Valeur : photo de pots en noir et blanc. On touche chaque pot, l'appli mesure sa valeur (clarté)
 * et les classe du plus foncé au plus clair.
 */
@Composable
fun ValueContent(data: AppData, repo: Repository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageVersion by rememberSaveable { mutableStateOf(if (valueFile(context).exists()) 1 else 0) }
    var marksText by rememberSaveable { mutableStateOf("") }
    var showColor by rememberSaveable { mutableStateOf(false) }
    var lightFirst by rememberSaveable { mutableStateOf(false) }
    val marks = remember(marksText) { decodeMarks(marksText) }
    val zoom = remember { ZoomState() }
    var dragging by remember { mutableStateOf<String?>(null) }

    val bitmap by produceState<Bitmap?>(null, imageVersion) {
        value = if (imageVersion == 0) null else loadBitmap(valueFile(context))
    }

    fun newImage() {
        imageVersion++
        marksText = ""
        zoom.reset()
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        valueFile(context).outputStream().use { input.copyTo(it) }
                    }
                }.isSuccess
            }
            if (ok) newImage() else Toast.makeText(context, "Impossible d'ouvrir cette image", Toast.LENGTH_SHORT).show()
        }
    }
    val captureFile = remember { File(valueFile(context).parentFile, "value_capture.jpg") }
    val takePicture = rememberTakePicture { ok ->
        if (ok && captureFile.exists() && captureFile.length() > 0) {
            val target = valueFile(context)
            target.delete()
            if (!captureFile.renameTo(target)) captureFile.copyTo(target, overwrite = true)
            newImage()
        } else captureFile.delete()
    }

    // Classement : rang 1 = le plus foncé (ou le plus clair si inversé).
    val ranked = remember(marks, lightFirst) {
        marks.sortedBy { if (lightFirst) -it.value else it.value }
    }
    val rankOf = remember(ranked) { ranked.mapIndexed { i, m -> m.label to i + 1 }.toMap() }

    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Galerie")
                }
                FilledTonalButton(
                    onClick = { takePicture(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Photo")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        val bmp = bitmap
        if (bmp == null) {
            item {
                EmptyState(
                    Icons.Outlined.Contrast,
                    "Étude des valeurs",
                    "Photographie tes pots côte à côte, sous une lumière uniforme. La photo passe en noir et blanc : " +
                        "touche chaque pot pour mesurer sa valeur, et je les classe du plus foncé au plus clair.",
                    Modifier.height(360.dp),
                )
            }
            return@LazyColumn
        }

        item {
            val density = LocalDensity.current
            val markRadiusPx = with(density) { 16.dp.toPx() }
            val grabPx = with(density) { 28.dp.toPx() }
            fun resample(m: ValueMark, fx: Float, fy: Float) =
                m.copy(fx = fx, fy = fy, hex = sampleAverageHex(bmp, fx, fy, max(1, (m.fr * bmp.width).roundToInt())))

            ZoomableImage(
                bitmap = bmp,
                zoom = zoom,
                grayscale = !showColor,
                height = 420.dp,
                onTouchStart = { mapper, p ->
                    dragging = marks.minByOrNull { (mapper.toScreen(it.fx, it.fy) - p).getDistance() }
                        ?.takeIf { m ->
                            val r = max(grabPx, m.fr * bmp.width * mapper.screenPerBitmapPx)
                            (mapper.toScreen(m.fx, m.fy) - p).getDistance() <= r
                        }?.label
                },
                onTouchMove = { mapper, p ->
                    val id = dragging ?: return@ZoomableImage
                    val (x, y) = mapper.toFraction(p)
                    marksText = encodeMarks(marks.map { if (it.label == id) resample(it, x, y) else it })
                },
                onTouchEnd = { mapper, p, moved ->
                    if (!moved && dragging == null) {
                        if (marks.size >= MAX_MARKS) {
                            Toast.makeText(context, "$MAX_MARKS pots au maximum", Toast.LENGTH_SHORT).show()
                        } else {
                            val (x, y) = mapper.toFraction(p)
                            val label = ('A'..'Z').map { it.toString() }.first { l -> marks.none { it.label == l } }
                            // Rayon : 16 dp à l'écran, converti en fraction de la largeur de la photo.
                            val fr = markRadiusPx / mapper.screenPerBitmapPx / bmp.width
                            val r = max(1, (fr * bmp.width).roundToInt())
                            marksText = encodeMarks(marks + ValueMark(label, x, y, fr, sampleAverageHex(bmp, x, y, r)))
                        }
                    }
                    dragging = null
                },
                onTouchCancel = { dragging = null },
                overlay = { mapper -> drawValueMarks(mapper, marks, rankOf, bmp.width, dragging) },
                extraButtons = {
                    FilledIconToggleButton(checked = showColor, onCheckedChange = { showColor = it }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Palette, contentDescription = if (showColor) "Revenir au noir et blanc" else "Voir en couleur")
                    }
                },
            )
            Text(
                "Touche un pot pour le mesurer · fais glisser un repère pour l'ajuster · pince pour zoomer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (marks.isEmpty()) {
            item {
                Text(
                    "Aucun pot mesuré pour l'instant. Vise de préférence le bouchon ou une pastille de peinture, " +
                        "plutôt que l'étiquette ou un reflet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            return@LazyColumn
        }

        item {
            val darkest = ranked.minByOrNull { it.value }!!
            val lightest = ranked.maxByOrNull { it.value }!!
            val range = lightest.value - darkest.value
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Classement des valeurs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { marksText = "" }) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text("Effacer")
                }
            }
            if (marks.size > 1) {
                Text(
                    "Écart entre le plus foncé (${darkest.label}) et le plus clair (${lightest.label}) : ${range.roundToInt()} — ${contrastLabel(range)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable { lightFirst = !lightFirst }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (lightFirst) "N° 1 = le plus clair" else "N° 1 = le plus foncé",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = lightFirst, onCheckedChange = { lightFirst = it })
            }
            // Échelle de valeurs : chaque pot placé sur un dégradé du noir au blanc.
            ValueScale(ranked, rankOf)
            Spacer(Modifier.height(8.dp))
        }

        itemsIndexed(ranked, key = { _, m -> m.label }) { i, m ->
            val prev = ranked.getOrNull(i - 1)
            val same = prev != null && abs(prev.value - m.value) < SAME_VALUE
            val tag = when {
                ranked.size < 2 -> null
                m === ranked.minByOrNull { it.value } -> "le plus foncé"
                m === ranked.maxByOrNull { it.value } -> "le plus clair"
                else -> null
            }
            ValueRow(
                rank = i + 1,
                mark = m,
                tag = tag,
                sameAs = if (same) i else null,
                onRemove = { marksText = encodeMarks(marks.filterNot { it.label == m.label }) },
            )
        }
        item {
            Text(
                "La valeur est la clarté perçue (L*, de 0 = noir à 100 = blanc), mesurée sur la zone entourée. " +
                    "L'éclairage et l'appareil photo influent : photographie tous les pots ensemble, sous la même lumière, sans flash.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Repères sur la photo : cercle de la zone mesurée et pastille numérotée (rang). */
private fun DrawScope.drawValueMarks(
    mapper: ImageMapper,
    marks: List<ValueMark>,
    rankOf: Map<String, Int>,
    bitmapWidth: Int,
    dragging: String?,
) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 13.dp.toPx()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    marks.forEach { m ->
        val c = mapper.toScreen(m.fx, m.fy)
        val r = (m.fr * bitmapWidth * mapper.screenPerBitmapPx).coerceAtLeast(4.dp.toPx())
        val active = m.label == dragging
        drawCircle(Color.Black, radius = r + 2f, center = c, style = Stroke(4f))
        drawCircle(if (active) Color(0xFFFFC857) else Color.White, radius = r, center = c, style = Stroke(if (active) 4f else 2.5f))
        // Pastille : rang + lettre du pot
        val badge = 12.dp.toPx()
        val bc = Offset(c.x + r * 0.75f, c.y - r * 0.75f)
        val g = grayOf(m.hex)
        drawCircle(Color.Black, radius = badge + 2f, center = bc)
        drawCircle(g, radius = badge, center = bc)
        paint.color = if (valueOf(m.hex) > 55f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        drawContext.canvas.nativeCanvas.drawText(
            "${rankOf[m.label] ?: "?"}",
            bc.x, bc.y + paint.textSize * 0.35f, paint,
        )
    }
}

/** Dégradé du noir au blanc avec la position de chaque pot. */
@Composable
private fun ValueScale(ranked: List<ValueMark>, rankOf: Map<String, Int>) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(20.dp).align(Alignment.Center).clip(RoundedCornerShape(6.dp))) {
            (0..10).forEach { lvl ->
                Box(Modifier.weight(1f).fillMaxHeight().background(grayOf(levelHex(lvl))))
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight()) {
            ranked.forEach { m ->
                val x = maxWidth * (m.value / 100f).coerceIn(0f, 1f) - 10.dp
                Box(
                    Modifier
                        .padding(start = x.coerceAtLeast(0.dp))
                        .size(20.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.5.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${rankOf[m.label]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** Gris neutre du niveau donné (0–10). */
private fun levelHex(level: Int): String {
    val l = level * 10.0
    val y = if (l > 8) Math.pow((l + 16) / 116, 3.0) else l / 903.3
    val s = if (y <= 0.0031308) 12.92 * y else 1.055 * Math.pow(y, 1 / 2.4) - 0.055
    val v = (s * 255).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(v, v, v)
}

@Composable
private fun ValueRow(rank: Int, mark: ValueMark, tag: String?, sameAs: Int?, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp),
        )
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(grayOf(mark.hex)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(width = 14.dp, height = 44.dp).clip(RoundedCornerShape(5.dp)).background(hexColor(mark.hex)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append("Pot ${mark.label}")
                    tag?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Valeur ${mark.value.roundToInt()}/100 · niveau ${valueLevel(mark.value)}/10",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sameAs != null) {
                Text(
                    "≈ même valeur que le n° $sameAs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            // Barre de valeur
            Box(
                Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((mark.value / 100f).coerceIn(0.02f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, contentDescription = "Retirer le pot ${mark.label}") }
    }
}
