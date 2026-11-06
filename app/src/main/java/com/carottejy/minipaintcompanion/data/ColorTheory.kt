package com.carottejy.minipaintcompanion.data

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Couleur en HSV : teinte 0–360 (au sens RVB), saturation et valeur 0–1. */
data class Hsv(val h: Float, val s: Float, val v: Float)

enum class Wheel(val label: String) {
    /** Roue traditionnelle du peintre : rouge ↔ vert, bleu ↔ orange, jaune ↔ violet. */
    RYB("Peintre (RJB)"),
    /** Roue de la lumière (écrans) : rouge ↔ cyan, bleu ↔ jaune. */
    RGB("Lumière (RVB)"),
}

enum class Harmony(val label: String, val description: String, val offsets: List<Float>, val names: List<String>) {
    COMPLEMENTARY(
        "Complémentaire",
        "La couleur opposée sur la roue : contraste maximal, idéal pour faire ressortir un point focal (gemme, œil, arme).",
        listOf(0f, 180f), listOf("Base", "Complémentaire"),
    ),
    SPLIT(
        "Compl. adjacentes",
        "Les deux voisines de la complémentaire : contraste fort mais plus doux et plus facile à équilibrer.",
        listOf(0f, 150f, 210f), listOf("Base", "Adjacente 1", "Adjacente 2"),
    ),
    ANALOGOUS(
        "Analogue",
        "Trois teintes voisines : schéma harmonieux et naturel, parfait pour les armures, capes et peaux.",
        listOf(0f, -30f, 30f), listOf("Base", "Voisine froide", "Voisine chaude"),
    ),
    TRIADIC(
        "Triade",
        "Trois teintes à 120° : palette vive et équilibrée. Garde une couleur dominante, les deux autres en accent.",
        listOf(0f, 120f, 240f), listOf("Base", "Triade 2", "Triade 3"),
    ),
    TETRADIC(
        "Rectangle",
        "Deux paires de complémentaires : très riche, à réserver aux figurines avec beaucoup de zones distinctes.",
        listOf(0f, 60f, 180f, 240f), listOf("Base", "Accent", "Complémentaire", "Compl. de l'accent"),
    ),
    SQUARE(
        "Carré",
        "Quatre teintes à 90° : palette très colorée, à doser avec des neutres (métal, cuir, noir).",
        listOf(0f, 90f, 180f, 270f), listOf("Base", "Carré 2", "Carré 3", "Carré 4"),
    ),
    MONOCHROME(
        "Camaïeu",
        "Une seule teinte déclinée du sombre au clair : la base de tout dégradé ombre → éclaircissement.",
        listOf(0f), listOf("Base"),
    ),
}

data class SwatchColor(val name: String, val hex: String, val wheelAngle: Float)

object ColorTheory {

    // Correspondance angle sur la roue du peintre (RJB) <-> teinte RVB, par interpolation linéaire.
    private val RYB_ANGLES = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
    private val RGB_HUES = floatArrayOf(0f, 30f, 60f, 120f, 225f, 280f, 360f)

    private fun interp(x: Float, from: FloatArray, to: FloatArray): Float {
        val v = ((x % 360f) + 360f) % 360f
        for (i in 0 until from.size - 1) {
            if (v >= from[i] && v <= from[i + 1]) {
                val t = (v - from[i]) / (from[i + 1] - from[i])
                return to[i] + t * (to[i + 1] - to[i])
            }
        }
        return v
    }

    fun hueToAngle(rgbHue: Float, wheel: Wheel): Float =
        if (wheel == Wheel.RYB) interp(rgbHue, RGB_HUES, RYB_ANGLES) else norm(rgbHue)

    fun angleToHue(angle: Float, wheel: Wheel): Float =
        if (wheel == Wheel.RYB) interp(angle, RYB_ANGLES, RGB_HUES) else norm(angle)

    fun norm(a: Float) = ((a % 360f) + 360f) % 360f

    // ---------------------------------------------------------------- conversions

    fun hexToRgb(hex: String): Triple<Int, Int, Int> {
        val c = hex.removePrefix("#").takeLast(6).padStart(6, '0').toInt(16)
        return Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    }

    fun rgbToHex(r: Int, g: Int, b: Int) = "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    fun hexToHsv(hex: String): Hsv {
        val (r8, g8, b8) = hexToRgb(hex)
        val r = r8 / 255f; val g = g8 / 255f; val b = b8 / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return Hsv(norm(h), if (mx == 0f) 0f else d / mx, mx)
    }

    fun hsvToHex(c: Hsv): String {
        val h = norm(c.h); val s = c.s.coerceIn(0f, 1f); val v = c.v.coerceIn(0f, 1f)
        val k = v * s
        val x = k * (1 - abs((h / 60f) % 2f - 1))
        val m = v - k
        val (r, g, b) = when {
            h < 60 -> Triple(k, x, 0f)
            h < 120 -> Triple(x, k, 0f)
            h < 180 -> Triple(0f, k, x)
            h < 240 -> Triple(0f, x, k)
            h < 300 -> Triple(x, 0f, k)
            else -> Triple(k, 0f, x)
        }
        return rgbToHex(((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }

    /** sRGB -> CIE L*a*b* (D65), pour mesurer la ressemblance visuelle entre deux teintes. */
    fun hexToLab(hex: String): FloatArray {
        val (r8, g8, b8) = hexToRgb(hex)
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        val r = lin(r8); val g = lin(g8); val b = lin(b8)
        val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
        val y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.0
        val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else (7.787 * t + 16.0 / 116.0)
        val fx = f(x); val fy = f(y); val fz = f(z)
        return floatArrayOf((116 * fy - 16).toFloat(), (500 * (fx - fy)).toFloat(), (200 * (fy - fz)).toFloat())
    }

    fun deltaE(a: FloatArray, b: FloatArray): Float =
        sqrt((a[0] - b[0]).pow(2) + (a[1] - b[1]).pow(2) + (a[2] - b[2]).pow(2))

    /** Teinte approximative d'un mélange (moyenne pondérée en lumière linéaire). */
    fun mixHex(hexA: String, hexB: String, ratioA: Int): String {
        val (r1, g1, b1) = hexToRgb(hexA)
        val (r2, g2, b2) = hexToRgb(hexB)
        val t = ratioA.coerceIn(0, 100) / 100.0
        fun lin(c: Int) = (c / 255.0).pow(2.2)
        fun mix(a: Int, b: Int) = ((lin(a) * t + lin(b) * (1 - t)).pow(1 / 2.2) * 255).toInt()
        return rgbToHex(mix(r1, r2), mix(g1, g2), mix(b1, b2))
    }

    // ---------------------------------------------------------------- harmonies

    fun harmony(baseHex: String, harmony: Harmony, wheel: Wheel): List<SwatchColor> {
        val base = hexToHsv(baseHex)
        val baseAngle = hueToAngle(base.h, wheel)
        if (harmony == Harmony.MONOCHROME) {
            return listOf(
                SwatchColor("Ombre profonde", hsvToHex(base.copy(v = base.v * 0.35f)), baseAngle),
                SwatchColor("Ombre", hsvToHex(base.copy(v = base.v * 0.65f)), baseAngle),
                SwatchColor("Base", baseHex, baseAngle),
                SwatchColor("Éclaircissement", hsvToHex(Hsv(base.h, base.s * 0.75f, min(1f, base.v * 1.15f + 0.1f))), baseAngle),
                SwatchColor("Rehaut", hsvToHex(Hsv(base.h, base.s * 0.4f, min(1f, base.v * 1.3f + 0.25f))), baseAngle),
            )
        }
        return harmony.offsets.mapIndexed { i, off ->
            val angle = norm(baseAngle + off)
            val hex = if (off == 0f) baseHex else hsvToHex(Hsv(angleToHue(angle, wheel), base.s, base.v))
            SwatchColor(harmony.names[i], hex, angle)
        }
    }

    /**
     * Ombre et lumière « à la peintre » : les ombres glissent vers le bleu/violet (froides),
     * les lumières vers le jaune (chaudes), plutôt que de simplement ajouter du noir ou du blanc.
     */
    fun shadeAndLight(baseHex: String): List<SwatchColor> {
        val base = hexToHsv(baseHex)
        val angle = hueToAngle(base.h, Wheel.RYB)
        fun towards(target: Float, amount: Float): Float {
            var diff = norm(target - angle)
            if (diff > 180f) diff -= 360f
            return norm(angle + diff.coerceIn(-amount, amount))
        }
        val shadowAngle = towards(260f, 20f)  // vers le bleu-violet
        val lightAngle = towards(120f, 20f)   // vers le jaune
        val shadow = Hsv(angleToHue(shadowAngle, Wheel.RYB), min(1f, base.s * 1.05f + 0.05f), base.v * 0.55f)
        val light = Hsv(angleToHue(lightAngle, Wheel.RYB), base.s * 0.7f, min(1f, base.v * 1.2f + 0.15f))
        return listOf(
            SwatchColor("Ombre (froide)", hsvToHex(shadow), shadowAngle),
            SwatchColor("Base", baseHex, angle),
            SwatchColor("Lumière (chaude)", hsvToHex(light), lightAngle),
        )
    }

    /** Gammes qui ne sont pas des peintures couvrantes : exclues des correspondances. */
    private val NON_OPAQUE = listOf("shade", "wash", "lavis", "technical", "contrast", "speedpaint", "primer", "bombe", "vernis")

    fun isOpaque(p: Paint) = NON_OPAQUE.none { p.range.lowercase().contains(it) }

    /** Les pots dont la teinte est la plus proche de [hex]. */
    fun closestPaints(hex: String, paints: List<Paint>, count: Int = 3): List<Pair<Paint, Float>> {
        val target = hexToLab(hex)
        return paints.asSequence()
            .filter(::isOpaque)
            .map { it to deltaE(target, labCache.getOrPut(it.hex) { hexToLab(it.hex) }) }
            .sortedBy { it.second }
            .take(count)
            .toList()
    }

    private val labCache = HashMap<String, FloatArray>()
}
