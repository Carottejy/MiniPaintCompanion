package com.carottejy.minipaintcompanion.data

import kotlinx.serialization.Serializable

enum class Brand(val label: String) {
    CITADEL("Citadel"),
    VALLEJO("Vallejo"),
    ARMY_PAINTER("The Army Painter"),
    PRINCE_AUGUST("Prince August"),
    AK("AK Interactive"),
    SCALE75("Scale75"),
    CUSTOM("Perso"),
}

@Serializable
data class Paint(
    val id: String,
    val brand: Brand,
    val range: String,
    val name: String,
    val hex: String,
    val code: String = "",
) {
    val displayName: String get() = if (code.isBlank()) name else "$name ($code)"
}

enum class Technique(val label: String) {
    BRUSH("Pinceau"),
    AIRBRUSH("Aérographe"),
    SPRAY("Bombe"),
    SPONGE("Éponge"),
    PEN("Stylo"),
}

@Serializable
data class Step(
    val id: String,
    val title: String = "",
    val technique: Technique = Technique.BRUSH,
    val paintIds: List<String> = emptyList(),
    val notes: String = "",
    /** Dilution en % (eau ou medium), 0 = pur. */
    val dilution: Int = 0,
    /** Étape de sous-couche : les pots se choisissent dans la liste dédiée. */
    val primer: Boolean = false,
    /** Mélanges de deux pots. */
    val mixes: List<Mix> = emptyList(),
    /** En-tête de groupe : seul le titre compte ; regroupe les étapes qui suivent. */
    val group: Boolean = false,
) {
    /** Tous les pots de l'étape, y compris ceux des mélanges. */
    val allPaintIds: List<String> get() = (paintIds + mixes.flatMap { listOf(it.paintA, it.paintB) }).distinct()
}

/** Mélange de deux pots : [ratioA] % du premier, le reste du second. */
@Serializable
data class Mix(
    val paintA: String,
    val paintB: String,
    val ratioA: Int = 50,
) {
    val ratioB: Int get() = 100 - ratioA
}

@Serializable
data class Project(
    val id: String,
    val name: String = "",
    val notes: String = "",
    val photo: String? = null, // nom de fichier dans filesDir/photos
    val steps: List<Step> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class AppData(
    val projects: List<Project> = emptyList(),
    val ownedPaintIds: Set<String> = emptySet(),
    val customPaints: List<Paint> = emptyList(),
    /** Pots à acheter ou à racheter. */
    val toBuyIds: Set<String> = emptySet(),
    /** Codes-barres appris : code EAN/UPC -> identifiant du pot. */
    val barcodes: Map<String, String> = emptyMap(),
    /** Ma liste de sous-couches ; null = liste par défaut. */
    val primerIds: Set<String>? = null,
)

/** Contenu d'un fichier de partage de schéma. */
@Serializable
data class SharedSchema(
    val format: Int = 1,
    val project: Project,
    val customPaints: List<Paint> = emptyList(),
)

/** Groupe auquel appartient chaque étape (id de l'en-tête), null hors groupe. */
fun List<Step>.groupOwners(): List<String?> {
    var current: String? = null
    return map { s -> if (s.group) { current = s.id; null } else current }
}

/** Nombre d'étapes contenues dans chaque groupe. */
fun List<Step>.groupSizes(): Map<String, Int> =
    groupOwners().filterNotNull().groupingBy { it }.eachCount()

/** Déplace un groupe avec toutes ses étapes d'un cran vers le haut (-1) ou le bas (+1). */
fun List<Step>.moveGroup(groupId: String, delta: Int): List<Step> {
    val owners = groupOwners()
    val start = indexOfFirst { it.id == groupId }
    if (start < 0) return this
    var end = start + 1
    while (end < size && owners[end] == groupId) end++
    val block = subList(start, end)
    val rest = toMutableList().apply { subList(start, end).clear() }
    // Bloc voisin : un autre groupe entier ou une étape isolée.
    val restOwners = rest.groupOwners()
    val insertAt = if (delta < 0) {
        if (start == 0) return this
        val prev = start - 1
        val prevOwner = restOwners[prev]
        when {
            prevOwner != null -> rest.indexOfFirst { it.id == prevOwner }
            rest[prev].group -> prev
            // Une étape hors groupe reste au-dessus des groupes (sinon elle serait absorbée).
            else -> return this
        }
    } else {
        if (start >= rest.size) return this
        val next = start
        if (rest[next].group) {
            var e = next + 1
            while (e < rest.size && restOwners[e] == rest[next].id) e++
            e
        } else next + 1
    }
    rest.addAll(insertAt, block)
    return rest
}

/**
 * Glisser-déposer : déplace l'élément [fromId] à la place de [toId] dans la liste visible.
 * Un groupe replié (dans [collapsed]) se déplace avec toutes ses étapes.
 * Retourne null si le déplacement ferait entrer des étapes hors groupe dans un groupe.
 */
fun List<Step>.reorderBlocks(collapsed: Set<String>, fromId: String, toId: String): List<Step>? {
    val owners = groupOwners()
    val blocks = mutableListOf<MutableList<Step>>()
    forEachIndexed { i, s ->
        if (owners[i] != null && owners[i] in collapsed && blocks.isNotEmpty()) blocks.last().add(s)
        else blocks.add(mutableListOf(s))
    }
    val from = blocks.indexOfFirst { it.first().id == fromId }
    val to = blocks.indexOfFirst { it.first().id == toId }
    if (from < 0 || to < 0 || from == to) return null
    val moved = blocks.removeAt(from)
    blocks.add(to, moved)
    val result = blocks.flatten()
    if (moved.first().group) {
        // Les étapes qui n'étaient dans aucun groupe doivent le rester.
        val before = owners.indices.filter { owners[it] == null && !this[it].group }.map { this[it].id }.toSet()
        val newOwners = result.groupOwners()
        if (result.indices.any { result[it].id in before && newOwners[it] != null }) return null
    }
    return result
}
