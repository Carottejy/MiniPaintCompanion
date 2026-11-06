package com.carottejy.minipaintcompanion.data

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Transforme une phrase dictée en étapes de peinture.
 *
 * Exemple : « Sous-couche à l'aérographe avec Wraithbone, ensuite lavis au pinceau
 * avec Agrax Earthshade, note : bien laisser sécher » donne deux étapes.
 */
object VoiceParser {

    data class ParsedStep(
        val title: String,
        /** null si la technique n'a pas été dite. */
        val technique: Technique?,
        val paintIds: List<String>,
        val notes: String,
    )

    private val STEP_SEPARATORS = Regex("""\b(?:étape suivante|etape suivante|nouvelle étape|nouvelle etape|ensuite|puis|et après|et apres)\b""", RegexOption.IGNORE_CASE)

    private val TITLES = listOf(
        Regex("""sous ?couche|primer|appret""") to "Sous-couche",
        Regex("""aplat""") to "Aplats",
        Regex("""lavis|wash""") to "Lavis",
        Regex("""brossage a sec|dry ?brush""") to "Brossage à sec",
        Regex("""eclairciss|highlight|rehaut|lumiere""") to "Éclaircissements",
        Regex("""glacis|glaze""") to "Glacis",
        Regex("""ombre""") to "Ombres",
        Regex("""detail""") to "Détails",
        Regex("""usure|effet|rouille|salissure""") to "Usure / effets",
        Regex("""socle""") to "Socle",
        Regex("""vernis""") to "Vernis",
        Regex("""contrast""") to "Contrast",
        Regex("""couche de base|basecoat|\bbase\b""") to "Couche de base",
    )

    private val BRAND_WORDS = mapOf(
        Brand.CITADEL to listOf("citadel", "citadelle", "games workshop"),
        Brand.VALLEJO to listOf("vallejo", "valejo", "valleyo", "valeo"),
        Brand.ARMY_PAINTER to listOf("army painter", "army"),
        Brand.PRINCE_AUGUST to listOf("prince august", "prince auguste"),
    )

    private val NOTE_KEYWORDS = Regex("""\b(?:note|notes|remarque|astuce|attention)\b\s*:?\s*""", RegexOption.IGNORE_CASE)
    /** Début d'une indication technique dans la phrase d'origine (dilution, pression…). */
    private val NOTE_HINTS = Regex("""dilu|pression|\d+\s?(?:bars?|psi|%)|(?:\d+|deux|trois|quatre|plusieurs)\s?couches|couches? fines?|mélang|melang|séch|sech|buse|\bmix""", RegexOption.IGNORE_CASE)
    private val VALLEJO_CODE = Regex("""\b(7[0-3])\s?[.,]?\s?(\d{3})\b""")

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("'s ", "s ")
            .replace(Regex("[^a-z0-9%]+"), " ")
            .trim()

    fun parse(text: String, paints: List<Paint>, ownedIds: Set<String>): List<ParsedStep> =
        text.split(STEP_SEPARATORS)
            .map { it.trim(' ', ',', '.', ';') }
            .filter { it.isNotBlank() }
            .map { parseSegment(it, paints, ownedIds) }
            .filter { it.title.isNotBlank() || it.paintIds.isNotEmpty() || it.notes.isNotBlank() || it.technique != null }

    private fun parseSegment(original: String, paints: List<Paint>, ownedIds: Set<String>): ParsedStep {
        // Notes explicites (« note : … ») : tout ce qui suit le mot-clé.
        val noteMatch = NOTE_KEYWORDS.find(original)
        val body = if (noteMatch != null) original.substring(0, noteMatch.range.first) else original
        val explicitNote = noteMatch?.let { original.substring(it.range.last + 1).trim() }.orEmpty()

        val norm = normalize(body)
        val technique = when {
            Regex("""\bbombe|aerosol|spray|rattle ?can""").containsMatchIn(norm) -> Technique.SPRAY
            Regex("""eponge|sponge""").containsMatchIn(norm) -> Technique.SPONGE
            Regex("""\bstylo|feutre|marqueur|posca|\bmarker|\bpen\b""").containsMatchIn(norm) -> Technique.PEN
            Regex("""aerograph|\baero\b|airbrush""").containsMatchIn(norm) -> Technique.AIRBRUSH
            Regex("""pinceau|brush""").containsMatchIn(norm) && !norm.contains("dry brush") -> Technique.BRUSH
            else -> null
        }
        val title = TITLES.firstOrNull { (re, _) -> re.containsMatchIn(norm) }?.second.orEmpty()

        // Indications de dilution / pression : de l'indice jusqu'à la fin du fragment.
        val hints = body.split(',', '.', ';')
            .mapNotNull { frag -> NOTE_HINTS.find(frag)?.let { frag.substring(it.range.first).trim() } }
        val notes = (hints + explicitNote).filter { it.isNotBlank() }.joinToString(". ")
            .replaceFirstChar { it.uppercase() }

        return ParsedStep(title, technique, matchPaints(norm, paints, ownedIds), notes)
    }

    private data class Hit(val paint: Paint, val start: Int, val end: Int, val score: Double)

    /** Repère les noms de pots dans la phrase (tolérant aux fautes de transcription). */
    fun matchPaints(norm: String, paints: List<Paint>, ownedIds: Set<String>): List<String> {
        val tokens = norm.split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val brandsSaid = BRAND_WORDS.filter { (_, words) -> words.any { norm.contains(it) } }.keys

        val hits = mutableListOf<Hit>()
        for (p in paints) {
            val nameTokens = normalize(p.name).split(" ").filter { it.isNotEmpty() }
            if (nameTokens.isEmpty()) continue
            val name = nameTokens.joinToString(" ")
            val compactName = name.replace(" ", "")
            val n = nameTokens.size
            // Les noms courts (« Red », « Gold ») doivent être dits exactement.
            val threshold = when {
                compactName.length <= 6 -> 1.0
                compactName.length >= 7 -> 0.75
                else -> 0.8
            }
            // Fenêtres de n-1 à n+1 mots : « earth shade » pour Earthshade, « nuln oil » dicté en un mot…
            for (size in max(1, n - 1)..n + 1) {
                for (start in 0..tokens.size - size) {
                    val window = tokens.subList(start, start + size).joinToString("")
                    val score = if (window == compactName) 1.0 else similarity(window, compactName)
                    if (score >= threshold) hits += Hit(p, start, start + size, score)
                }
            }
        }
        // Références Vallejo dictées : « 70 951 », « 70.951 »
        VALLEJO_CODE.findAll(norm).forEach { m ->
            val code = "${m.groupValues[1]}.${m.groupValues[2]}"
            paints.firstOrNull { it.brand == Brand.VALLEJO && it.code == code }?.let { p ->
                val before = norm.substring(0, m.range.first).split(" ").count { it.isNotEmpty() }
                hits += Hit(p, before, before + 2, 1.1)
            }
        }

        // Priorité : passage le plus long, meilleur score, nom le plus long, marque citée, pot possédé.
        fun rank(h: Hit) = listOf(
            -(h.end - h.start).toDouble(),
            -h.score,
            -h.paint.name.length.toDouble(),
            if (h.paint.brand in brandsSaid) 0.0 else 1.0,
            if (h.paint.id in ownedIds) 0.0 else 1.0,
        )
        val sorted = hits.sortedWith { a, b ->
            val ra = rank(a); val rb = rank(b)
            ra.indices.map { ra[it].compareTo(rb[it]) }.firstOrNull { it != 0 } ?: 0
        }
        val taken = BooleanArray(tokens.size + 2)
        val result = mutableListOf<Pair<Int, String>>()
        for (h in sorted) {
            if ((h.start until h.end).any { taken.getOrElse(it) { false } }) continue
            (h.start until h.end).forEach { if (it < taken.size) taken[it] = true }
            result += h.start to h.paint.id
        }
        return result.sortedBy { it.first }.map { it.second }.distinct()
    }

    /** Gammes repérables sur une étiquette : elles départagent les pots de même nom. */
    private val RANGE_WORDS = mapOf(
        "base" to "Base", "layer" to "Layer", "shade" to "Shade", "contrast" to "Contrast",
        "dry" to "Dry", "technical" to "Technical", "model color" to "Model Color", "game color" to "Game Color",
        "model air" to "Model Air", "xpress" to "Xpress Color", "speedpaint" to "Speedpaint",
        "warpaints" to "Warpaints", "fanatic" to "Warpaints", "quickshade" to "Quickshade Wash", "wash" to "Wash",
    )

    /**
     * Pots candidats pour un texte libre (étiquette lue par la caméra, contenu d'un QR code),
     * du plus probable au moins probable, avec un score.
     */
    fun candidates(text: String, paints: List<Paint>, ownedIds: Set<String>, max: Int = 5): List<Pair<Paint, Double>> {
        val norm = normalize(text)
        val tokens = norm.split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val brandsSaid = BRAND_WORDS.filter { (_, words) -> words.any { norm.contains(it) } }.keys
        val rangesSaid = RANGE_WORDS.filter { (w, _) -> Regex("""\b$w\b""").containsMatchIn(norm) }.values.toSet()

        val best = HashMap<String, Pair<Paint, Double>>()
        fun offer(p: Paint, base: Double) {
            var score = base + kotlin.math.min(normalize(p.name).replace(" ", "").length, 20) / 100.0
            if (p.brand in brandsSaid) score += 0.3
            if (p.range in rangesSaid) score += 0.3
            if (p.id in ownedIds) score += 0.05
            val prev = best[p.id]
            if (prev == null || prev.second < score) best[p.id] = p to score
        }
        for (p in paints) {
            val nameTokens = normalize(p.name).split(" ").filter { it.isNotEmpty() }
            if (nameTokens.isEmpty()) continue
            val compactName = nameTokens.joinToString("")
            val n = nameTokens.size
            val threshold = when {
                compactName.length <= 6 -> 1.0
                compactName.length >= 7 -> 0.75
                else -> 0.8
            }
            for (size in max(1, n - 1)..n + 1) {
                for (start in 0..tokens.size - size) {
                    val window = tokens.subList(start, start + size).joinToString("")
                    val score = if (window == compactName) 1.0 else similarity(window, compactName)
                    if (score >= threshold) offer(p, score)
                }
            }
        }
        // Référence Vallejo imprimée (« 70.951 ») : quasi certaine.
        VALLEJO_CODE.findAll(norm).forEach { m ->
            val code = "${m.groupValues[1]}.${m.groupValues[2]}"
            paints.filter { it.brand == Brand.VALLEJO && it.code == code }.forEach { offer(it, 2.0) }
        }
        return best.values.sortedByDescending { it.second }.take(max)
    }

    /** Similarité 0–1 basée sur la distance de Levenshtein. */
    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (kotlin.math.abs(a.length - b.length) > max(a.length, b.length) * 0.3) return 0.0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            cur.copyInto(prev)
        }
        return 1.0 - prev[b.length].toDouble() / max(a.length, b.length)
    }

    /** Applique une dictée à une étape existante (complète sans écraser). */
    fun merge(step: Step, parsed: ParsedStep): Step = step.copy(
        title = step.title.ifBlank { parsed.title },
        technique = parsed.technique ?: step.technique,
        paintIds = (step.paintIds + parsed.paintIds).distinct(),
        notes = listOf(step.notes, parsed.notes).filter { it.isNotBlank() }.joinToString("\n"),
    )
}
