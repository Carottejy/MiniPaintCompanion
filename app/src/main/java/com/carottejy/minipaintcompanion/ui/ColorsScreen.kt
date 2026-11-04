package com.carottejy.minipaintcompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carottejy.minipaintcompanion.data.AppData
import com.carottejy.minipaintcompanion.data.ColorTheory
import com.carottejy.minipaintcompanion.data.Harmony
import com.carottejy.minipaintcompanion.data.Hsv
import com.carottejy.minipaintcompanion.data.Paint
import com.carottejy.minipaintcompanion.data.Repository
import com.carottejy.minipaintcompanion.data.SwatchColor
import com.carottejy.minipaintcompanion.data.Wheel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val SEGMENTS = 12

/** Les trois anneaux de la roue : teinte claire (extérieur), pure, sombre (intérieur). */
private val RINGS = listOf(0.45f to 1f, 0.85f to 0.9f, 0.85f to 0.5f)

/**
 * Onglet « Théorie » : roue chromatique, harmonies, et pour chaque couleur
 * les pots du catalogue (ou de ta collection) qui s'en rapprochent le plus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorsScreen(
    data: AppData,
    repo: Repository,
    basePaintId: String?,
    onBasePaintChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allPaints = remember(data.customPaints) { repo.allPaints(data) }
    val basePaint = allPaints.firstOrNull { it.id == basePaintId }

    var freeHex by rememberSaveable { mutableStateOf("#C8553D") }
    var wheelName by rememberSaveable { mutableStateOf(Wheel.RYB.name) }
    var harmonyName by rememberSaveable { mutableStateOf(Harmony.COMPLEMENTARY.name) }
    var onlyMine by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    val wheel = Wheel.valueOf(wheelName)
    val harmony = Harmony.valueOf(harmonyName)
    val baseHex = basePaint?.hex ?: freeHex
    val baseHsv = remember(baseHex) { ColorTheory.hexToHsv(baseHex) }
    val swatches = remember(baseHex, harmony, wheel) { ColorTheory.harmony(baseHex, harmony, wheel) }
    val shading = remember(baseHex) { ColorTheory.shadeAndLight(baseHex) }

    val owned = allPaints.filter { it.id in data.ownedPaintIds }
    val pool = if (onlyMine && owned.isNotEmpty()) owned else allPaints

    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Wheel.entries.forEachIndexed { i, w ->
                    SegmentedButton(
                        selected = wheel == w,
                        onClick = { wheelName = w.name },
                        shape = SegmentedButtonDefaults.itemShape(i, Wheel.entries.size),
                    ) { Text(w.label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ColorWheel(
                    wheel = wheel,
                    baseHex = baseHex,
                    swatches = swatches,
                    onPick = { hex -> freeHex = hex; onBasePaintChange(null) },
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f),
                )
            }
            Text(
                "Touche la roue pour choisir une teinte, ou pars d'un pot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            // Couleur de base
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaintSwatch(baseHex, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("COULEUR DE BASE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(basePaint?.displayName ?: "Teinte libre $baseHex", style = MaterialTheme.typography.titleMedium)
                    if (basePaint != null) {
                        Text(
                            "${basePaint.brand.shortLabel()} · ${basePaint.range}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(onClick = { picking = true }) {
                    Icon(Icons.Outlined.Colorize, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Un pot")
                }
            }
            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Harmony.entries) { h ->
                    FilterChip(selected = harmony == h, onClick = { harmonyName = h.name }, label = { Text(h.label) })
                }
            }
            Text(
                harmony.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (baseHsv.s < 0.12f && harmony != Harmony.MONOCHROME) {
                Text(
                    "Ta base est presque neutre (gris, noir, blanc) : les harmonies de teinte ont peu de sens. Essaie le camaïeu ou une base plus colorée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().clickable { onlyMine = !onlyMine }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (owned.isEmpty()) "Pots suggérés : tout le catalogue (ta collection est vide)"
                    else "Suggérer seulement des pots de ma collection",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = onlyMine, onCheckedChange = { onlyMine = it }, enabled = owned.isNotEmpty())
            }
        }

        items(swatches, key = { "h_" + it.name }) { sw ->
            SwatchMatches(sw, pool, data, repo)
        }

        item {
            Text(
                "Ombre et lumière",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                "Astuce de peintre : des ombres légèrement plus froides et des lumières plus chaudes rendent la figurine plus vivante qu'un simple ajout de noir ou de blanc.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(shading, key = { "s_" + it.name }) { sw ->
            SwatchMatches(sw, pool, data, repo)
        }
    }

    if (picking) {
        ModalBottomSheet(
            onDismissRequest = { picking = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text("Partir d'un pot", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            PaintBrowser(
                paints = allPaints,
                ownedIds = data.ownedPaintIds,
                isChecked = { it.id == basePaintId },
                onToggle = { _, _ -> },
                onRowClick = { p -> onBasePaintChange(p.id); picking = false },
                rowTrailing = { p -> if (p.id in data.ownedPaintIds) OwnedMark() },
                showOwnedFilter = true,
                initialOnlyOwned = data.ownedPaintIds.isNotEmpty(),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OwnedMark() {
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = "Dans ma collection",
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 8.dp).size(20.dp),
    )
}

/** Une couleur théorique + les pots qui s'en approchent. */
@Composable
private fun SwatchMatches(sw: SwatchColor, pool: List<Paint>, data: AppData, repo: Repository) {
    val matches = remember(sw.hex, pool) { ColorTheory.closestPaints(sw.hex, pool) }
    Column(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(10.dp)).background(hexColor(sw.hex))
            )
            Spacer(Modifier.width(12.dp))
            Text(sw.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(sw.hex, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
        matches.forEach { (p, _) ->
            PaintLine(p, trailing = {
                when {
                    p.id in data.ownedPaintIds -> OwnedMark()
                    else -> {
                        val inList = p.id in data.toBuyIds
                        IconButton(onClick = { repo.setToBuy(p.id, !inList) }) {
                            Icon(
                                if (inList) Icons.Filled.ShoppingCart else Icons.Outlined.AddShoppingCart,
                                contentDescription = if (inList) "Retirer de la liste d'achat" else "Ajouter à la liste d'achat",
                                tint = if (inList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            })
        }
    }
}

/** Roue chromatique en 12 secteurs × 3 anneaux, avec la figure de l'harmonie par-dessus. */
@Composable
private fun ColorWheel(
    wheel: Wheel,
    baseHex: String,
    swatches: List<SwatchColor>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.background
    Canvas(
        modifier.pointerInput(wheel) {
            detectTapGestures { pos ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f * 0.96f
                val ringWidth = radius * 0.24f
                val dx = pos.x - c.x
                val dy = pos.y - c.y
                val dist = hypot(dx, dy)
                val ringIndex = ((radius - dist) / ringWidth).toInt()
                if (ringIndex !in RINGS.indices || dist > radius) return@detectTapGestures
                // Angle de la roue : 0° en haut, sens horaire.
                val angle = ColorTheory.norm(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f)
                val segment = ((angle + 360f / SEGMENTS / 2) / (360f / SEGMENTS)).toInt() % SEGMENTS
                val (s, v) = RINGS[ringIndex]
                val hue = ColorTheory.angleToHue(segment * 360f / SEGMENTS, wheel)
                onPick(ColorTheory.hsvToHex(Hsv(hue, s, v)))
            }
        }
    ) {
        val c = center
        val radius = size.minDimension / 2f * 0.96f
        val ringWidth = radius * 0.24f
        val step = 360f / SEGMENTS

        RINGS.forEachIndexed { ring, (s, v) ->
            val r = radius - ringWidth * ring - ringWidth / 2f
            for (i in 0 until SEGMENTS) {
                val angle = i * step
                val hue = ColorTheory.angleToHue(angle, wheel)
                drawArc(
                    color = hexColor(ColorTheory.hsvToHex(Hsv(hue, s, v))),
                    startAngle = angle - 90f - step / 2f + 1f,
                    sweepAngle = step - 2f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = ringWidth - 3f),
                )
            }
        }

        // Disque central = couleur de base
        val innerR = radius - ringWidth * RINGS.size - 6f
        drawCircle(hexColor(baseHex), radius = innerR * 0.8f, center = c)
        drawCircle(Color.White.copy(alpha = 0.25f), radius = innerR * 0.8f, center = c, style = Stroke(2f))

        // Figure de l'harmonie sur l'anneau central
        val markR = radius - ringWidth * 1.5f
        fun pos(angle: Float): Offset {
            val rad = Math.toRadians((angle - 90f).toDouble())
            return Offset(c.x + markR * cos(rad).toFloat(), c.y + markR * sin(rad).toFloat())
        }
        val points = swatches.map { it.wheelAngle }.distinct().map(::pos)
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                if (points.size > 2) close()
            }
            drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 4f))
        }
        swatches.distinctBy { it.wheelAngle }.forEachIndexed { i, sw ->
            val p = pos(sw.wheelAngle)
            val r = if (i == 0) ringWidth * 0.42f else ringWidth * 0.32f
            drawCircle(outline, radius = r + 5f, center = p)
            drawCircle(Color.White, radius = r + 3f, center = p)
            drawCircle(hexColor(sw.hex), radius = r, center = p)
        }
    }
}
