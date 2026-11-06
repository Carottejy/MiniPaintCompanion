package com.carottejy.minipaintcompanion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.carottejy.minipaintcompanion.data.Brand
import com.carottejy.minipaintcompanion.data.Paint
import com.carottejy.minipaintcompanion.data.Technique
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun hexColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

val Technique.icon: ImageVector
    get() = when (this) {
        Technique.BRUSH -> Icons.Outlined.Brush
        Technique.AIRBRUSH -> Icons.Outlined.Air
        Technique.SPRAY -> Icons.Outlined.FormatColorFill
        Technique.SPONGE -> Icons.Outlined.BubbleChart
        Technique.PEN -> Icons.Outlined.Create
    }

/** Couleur d'accent de chaque technique (badges, puces). */
val Technique.accent: Color
    get() = when (this) {
        Technique.BRUSH -> Color(0xFFD9A441)
        Technique.AIRBRUSH -> Color(0xFF7FB3A6)
        Technique.SPRAY -> Color(0xFFC8553D)
        Technique.SPONGE -> Color(0xFFB59BD6)
        Technique.PEN -> Color(0xFF8FB0E0)
    }

fun Brand.shortLabel(): String = when (this) {
    Brand.CITADEL -> "Citadel"
    Brand.VALLEJO -> "Vallejo"
    Brand.ARMY_PAINTER -> "Army Painter"
    Brand.PRINCE_AUGUST -> "Prince August"
    Brand.AK -> "AK"
    Brand.SCALE75 -> "Scale75"
    Brand.CUSTOM -> "Perso"
}

fun formatDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(Date(ms))

/** Pastille de couleur façon « goutte dans un pot ». */
@Composable
fun PaintSwatch(hex: String, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val c = hexColor(hex)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                GBrush.radialGradient(
                    listOf(lerpWhite(c, 0.25f), c, lerpBlack(c, 0.35f)),
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
    )
}

private fun lerpWhite(c: Color, t: Float) =
    Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t)

private fun lerpBlack(c: Color, t: Float) =
    Color(c.red * (1 - t), c.green * (1 - t), c.blue * (1 - t))

@Composable
fun PaintLine(
    paint: Paint,
    modifier: Modifier = Modifier,
    owned: Boolean? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaintSwatch(paint.hex)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                paint.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${paint.brand.shortLabel()} · ${paint.range}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (owned == false) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "pas en stock",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun ProjectPhoto(file: File?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier.background(
                GBrush.linearGradient(listOf(BenchHigh, Bench))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
fun TechniqueBadge(technique: Technique, modifier: Modifier = Modifier) {
    val color = technique.accent
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(technique.icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(technique.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
        Spacer(Modifier.padding(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.padding(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Petite étiquette d'information (sous-couche, dilution…). */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
