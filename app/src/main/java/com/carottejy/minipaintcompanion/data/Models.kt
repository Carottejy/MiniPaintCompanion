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
