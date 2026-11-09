package com.carottejy.minipaintcompanion.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

const val MAX_ZOOM = 16f

/** Zoom et déplacement de l'image : écran = position « ajustée » × scale + offset. */
class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    fun reset() { scale = 1f; offset = Offset.Zero }
}

/** Conversion entre l'écran et les coordonnées relatives (0–1) de l'image. */
class ImageMapper(
    private val left: Float,
    private val top: Float,
    private val dw: Float,
    private val dh: Float,
    val scale: Float,
    private val offset: Offset,
    /** Pixels écran par pixel de la photo. */
    val screenPerBitmapPx: Float,
) {
    fun toScreen(fx: Float, fy: Float) = Offset((left + fx * dw) * scale + offset.x, (top + fy * dh) * scale + offset.y)
    fun toFraction(p: Offset): Pair<Float, Float> {
        val bx = (p.x - offset.x) / scale
        val by = (p.y - offset.y) / scale
        return ((bx - left) / dw).coerceIn(0f, 1f) to ((by - top) / dh).coerceIn(0f, 1f)
    }
}

/** Filtre noir et blanc (luminance perçue). */
val GrayscaleFilter: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Image zoomable : pincer à deux doigts pour zoomer (jusqu'à ×16) et se déplacer,
 * un doigt pour agir sur l'image (curseur, repères…).
 */
@Composable
fun ZoomableImage(
    bitmap: Bitmap,
    zoom: ZoomState,
    grayscale: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 380.dp,
    onTouchStart: (ImageMapper, Offset) -> Unit = { _, _ -> },
    onTouchMove: (ImageMapper, Offset) -> Unit = { _, _ -> },
    /** Fin du geste à un doigt : [moved] faux pour un simple appui. */
    onTouchEnd: (ImageMapper, Offset, moved: Boolean) -> Unit = { _, _, _ -> },
    /** Un deuxième doigt est arrivé : le geste à un doigt est annulé. */
    onTouchCancel: () -> Unit = {},
    overlay: DrawScope.(ImageMapper) -> Unit = {},
    extraButtons: @Composable () -> Unit = {},
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val fit = min(w / bitmap.width, h / bitmap.height)
        val dw = bitmap.width * fit
        val dh = bitmap.height * fit
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f

        fun clampOffset(o: Offset, s: Float): Offset {
            // L'image zoomée doit rester dans le cadre (centrée si elle est plus petite).
            fun axis(v: Float, start: Float, len: Float, view: Float): Float {
                val a = start * s; val size = len * s
                return if (size <= view) (view - size) / 2f - a else v.coerceIn(view - a - size, -a)
            }
            return Offset(axis(o.x, left, dw, w), axis(o.y, top, dh, h))
        }
        fun zoomAround(center: Offset, factor: Float) {
            val ns = (zoom.scale * factor).coerceIn(1f, MAX_ZOOM)
            val o = center - (center - zoom.offset) * (ns / zoom.scale)
            zoom.scale = ns
            zoom.offset = clampOffset(o, ns)
        }
        val mapper = ImageMapper(left, top, dw, dh, zoom.scale, zoom.offset, fit * zoom.scale)
        val currentMapper by rememberUpdatedState(mapper)
        val start by rememberUpdatedState(onTouchStart)
        val move by rememberUpdatedState(onTouchMove)
        val end by rememberUpdatedState(onTouchEnd)
        val cancel by rememberUpdatedState(onTouchCancel)

        Image(
            image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = if (grayscale) GrayscaleFilter else null,
            filterQuality = if (zoom.scale > 4f) FilterQuality.None else FilterQuality.Low,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom.scale; scaleY = zoom.scale
                    translationX = zoom.offset.x; translationY = zoom.offset.y
                },
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap, w, h) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var single = true
                        var moved = false
                        var last = down.position
                        start(currentMapper, down.position)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                if (single) { single = false; cancel() }
                                val z = event.calculateZoom()
                                val pan = event.calculatePan()
                                val c = event.calculateCentroid(useCurrent = true)
                                if (z != 1f) zoomAround(c, z)
                                zoom.offset = clampOffset(zoom.offset + pan, zoom.scale)
                                event.changes.forEach { it.consume() }
                            } else if (single && pressed.size == 1) {
                                val ch = pressed[0]
                                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (ch.positionChange() != Offset.Zero) {
                                    last = ch.position
                                    move(currentMapper, ch.position)
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (single) end(currentMapper, last, moved)
                    }
                }
        ) { overlay(mapper) }

        // Commandes de zoom
        Column(
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
            )
            val center = Offset(w / 2f, h / 2f)
            FilledTonalIconButton(onClick = { zoomAround(center, 2f) }, colors = colors, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "Zoomer")
            }
            FilledTonalIconButton(onClick = { zoomAround(center, 0.5f) }, enabled = zoom.scale > 1f, colors = colors, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Remove, contentDescription = "Dézoomer")
            }
            if (zoom.scale > 1f) {
                FilledTonalIconButton(onClick = { zoom.reset() }, colors = colors, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Image entière")
                }
            }
            extraButtons()
        }
        if (zoom.scale > 1.05f) {
            Text(
                String.format(java.util.Locale.US, "×%.1f", zoom.scale).removeSuffix(".0"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Moyenne des pixels dans un disque (rayon en pixels de la photo), en hex. */
fun sampleAverageHex(bmp: Bitmap, fx: Float, fy: Float, radiusPx: Int): String {
    val cx = (fx * (bmp.width - 1)).toInt()
    val cy = (fy * (bmp.height - 1)).toInt()
    val r = max(0, radiusPx)
    var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
    val r2 = r * r
    for (y in max(0, cy - r)..min(bmp.height - 1, cy + r)) {
        for (x in max(0, cx - r)..min(bmp.width - 1, cx + r)) {
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy > r2) continue
            val c = bmp.getPixel(x, y)
            rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
        }
    }
    if (n == 0) return "#808080"
    return com.carottejy.minipaintcompanion.data.ColorTheory.rgbToHex((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
}

/** Valeur (clarté perçue L*, 0 = noir, 100 = blanc). */
fun valueOf(hex: String): Float = com.carottejy.minipaintcompanion.data.ColorTheory.hexToLab(hex)[0]

/** Gris de même valeur que la couleur, pour l'afficher en noir et blanc. */
fun grayOf(hex: String): Color {
    val l = valueOf(hex) / 100.0
    // L* -> luminance relative -> sRGB
    val y = if (l > 0.08) Math.pow((l * 100 + 16) / 116, 3.0) else l * 100 / 903.3
    val s = if (y <= 0.0031308) 12.92 * y else 1.055 * Math.pow(y, 1 / 2.4) - 0.055
    val v = (s * 255).toInt().coerceIn(0, 255)
    return Color(v, v, v)
}
