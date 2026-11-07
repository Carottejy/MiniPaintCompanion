package com.carottejy.minipaintcompanion.data

import java.text.Normalizer

/**
 * Catalogues de peintures. Chaque ligne : "référence|nom|#teinte".
 * La référence peut être vide. Les teintes sont des approximations d'écran,
 * utiles pour reconnaître un pot d'un coup d'œil.
 */
object Catalog {

    val all: List<Paint> by lazy {
        buildList {
            CITADEL.forEach { (range, data) -> addAll(parse(Brand.CITADEL, range, data)) }
            VALLEJO.forEach { (range, data) -> addAll(parse(Brand.VALLEJO, range, data)) }
            ARMY.forEach { (range, data) -> addAll(parse(Brand.ARMY_PAINTER, range, data)) }
            PRINCE.forEach { (range, data) -> addAll(parse(Brand.PRINCE_AUGUST, range, data)) }
            AK.forEach { (range, data) -> addAll(parse(Brand.AK, range, data)) }
            SCALE75.forEach { (range, data) -> addAll(parse(Brand.SCALE75, range, data)) }
        }.distinctBy { it.id }
    }

    // ------------------------------------------------------------ codes-barres (EAN-13)

    /** Ajoute la clé de contrôle EAN-13 à 12 chiffres. */
    fun ean13(base12: String): String {
        val sum = base12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
        return base12 + ((10 - sum % 10) % 10)
    }

    /**
     * Codes-barres d'un pot :
     *  - Vallejo : 8429551 + référence à 5 chiffres (70.951 -> 70951) + clé ;
     *  - Army Painter : 5713799 + numéro WP + 0 (ou 2 sur certains pots) + clé ;
     *  - Citadel : relevés un par un chez les revendeurs (plusieurs codes selon l'étiquette) ;
     *  - AK 3rd Gen : numérotation séquentielle 843556830 + (référence − 10767) + clé.
     * Vérifié sur des codes publiés (Vallejo 70.951 et 70.919, Army Painter WP3001, WP3012, WP3118, WP3199…).
     */
    fun barcodesFor(p: Paint): List<String> = when (p.brand) {
        Brand.VALLEJO -> {
            val digits = p.code.filter { it.isDigit() }
            if (digits.length == 5) listOf(ean13("8429551$digits")) else emptyList()
        }
        Brand.ARMY_PAINTER -> {
            val digits = p.code.removePrefix("WP").filter { it.isDigit() }
            if (digits.length == 4) listOf("0", "2").map { ean13("5713799$digits$it") } else emptyList()
        }
        Brand.CITADEL -> if (isPrimer(p)) emptyList() else CITADEL_EAN[p.name].orEmpty()
        // Scale75 : pas de numérotation régulière, codes relevés un par un.
        Brand.SCALE75 -> SCALE75_EAN[p.code].orEmpty()
        Brand.AK -> {
            // 843556830 + (référence − 10767) + clé, vérifié sur AK11044, 11049, 11050, 11056 et 11087.
            val n = p.code.removePrefix("AK").toIntOrNull()
            if (n != null && n in 11001..11230) listOf(ean13("843556830" + (n - 10767).toString().padStart(3, '0')))
            else emptyList()
        }
        else -> emptyList()
    }

    // ------------------------------------------------------------ sous-couches

    /** Une sous-couche : bombe, primer, ou pot perso classé « Sous-couche ». */
    fun isPrimer(p: Paint): Boolean {
        val r = p.range.lowercase()
        return "primer" in r || "bombe" in r || "spray" in r || "sous-couche" in r
    }

    /** Liste de sous-couches proposée par défaut (modifiable par l'utilisateur). */
    val defaultPrimerIds: Set<String> by lazy {
        val wanted = listOf(
            Brand.CITADEL to "Chaos Black", Brand.CITADEL to "Wraithbone", Brand.CITADEL to "Grey Seer",
            Brand.CITADEL to "Corax White", Brand.VALLEJO to "Black", Brand.VALLEJO to "White", Brand.VALLEJO to "Grey",
            Brand.ARMY_PAINTER to "Matt Black", Brand.ARMY_PAINTER to "Matt White", Brand.ARMY_PAINTER to "Uniform Grey",
            Brand.AK to "Black Primer", Brand.AK to "White Primer",
        )
        all.filter { isPrimer(it) && (it.brand to it.name) in wanted }.map { it.id }.toSet()
    }

    /** Index code-barres -> identifiant du pot. */
    val barcodeIndex: Map<String, String> by lazy {
        buildMap { all.forEach { p -> barcodesFor(p).forEach { put(it, p.id) } } }
    }

    private val SCALE75_EAN: Map<String, List<String>> = mapOf(
        "SC-20" to listOf("8412548247151"),
        "SC-23" to listOf("8412548247182"),
        "SC-25" to listOf("8412548248110"),
        "SC-41" to listOf("8412548251417"),
        "SC-56" to listOf("8412548252087"),
    )

    private val CITADEL_EAN: Map<String, List<String>> = mapOf(
        "Abaddon Black" to listOf("5011921187720"),
        "Corax White" to listOf("5011921188253", "5011921196791", "5011921121144"),
        "Wraithbone" to listOf("5011921188260", "5011921121151"),
        "Leadbelcher" to listOf("5011921187751", "5011921196401", "5011921026555"),
        "Retributor Armour" to listOf("5011921187805", "5011921058808"),
        "Macragge Blue" to listOf("5011921185986"),
        "Mephiston Red" to listOf("5011921185931", "5011921026203"),
        "Naggaroth Night" to listOf("5011921185955"),
        "Castellan Green" to listOf("5011921186860"),
        "Death Guard Green" to listOf("5011921187829"),
        "Dryad Bark" to listOf("5011921026500"),
        "White Scar" to listOf("5011921186754", "5011921027934", "5011921235285"),
        "Russ Grey" to listOf("5011921186839"),
        "Nurgling Green" to listOf("5011921186327", "5011921197361"),
        "Ogryn Camo" to listOf("5011921027606"),
        "Sotek Green" to listOf("5011921027224"),
        "Nuln Oil" to listOf("5011921176557"),
        "Agrax Earthshade" to listOf("5011921176441", "5011921068777"),
        "Ultramarines Blue" to listOf("5011921185405"),
        "Creed Camo" to listOf("5011921120802"),
        "Plaguebearer Flesh" to listOf("5011921185696"),
    )

    private fun parse(brand: Brand, range: String, data: String): List<Paint> =
        data.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val (code, name, hex) = line.split("|").map { it.trim() }
            Paint(
                id = "${brand.name.lowercase()}_${slug(range)}_${slug(name)}",
                brand = brand,
                range = range,
                name = name,
                hex = hex,
                code = code,
            )
        }

    fun slug(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    // ---------------------------------------------------------------- Citadel
    private val CITADEL = listOf(
        "Base" to """
            CIT21-25|Abaddon Black|#231F20
            CIT21-01|Averland Sunset|#FDB825
            CIT21-29|Balthasar Gold|#A47552
            CIT21-49|Barak-Nar Burgundy|#5A1E2E
            CIT21-18|Bugman's Glow|#834F44
            CIT21-09|Caledor Sky|#366699
            CIT21-12|Caliban Green|#00401F
            CIT21-14|Castellan Green|#314821
            CIT21-50|Catachan Flesh|#5A3A2C
            CIT21-26|Celestra Grey|#90A8A8
            CIT21-52|Corax White|#F2F2F2
            CIT21-44|Corvus Black|#1E1C21
            CIT21-06|Daemonette Hide|#696684
            CIT21-37|Death Guard Green|#848A66
            CIT21-40|Death Korps Drab|#3E4231
            CIT21-15|Death World Forest|#5D6732
            CIT22-45|Doombull Brown|#5D0009
            CIT21-23|Dryad Bark|#33312D
            CIT21-41|Gal Vorbak Red|#4B213C
            CIT21-47|Grey Knights Steel|#9AA0A4
            CIT21-54|Grey Seer|#A2A5A7
            CIT21-57|Hobgrot Hide|#9A7A4A
            |Incubi Darkness|#0B474A
            CIT21-38|Ionrach Skin|#C9B8A0
            CIT21-46|Iron Hands Steel|#8E9294
            CIT21-48|Iron Warriors|#5A5E60
            CIT21-02|Jokaero Orange|#EE3823
            CIT21-07|Kantor Blue|#02134E
            CIT21-04|Khorne Red|#6A0001
            CIT21-28|Leadbelcher|#888D8F
            CIT21-45|Lupercal Green|#002C2B
            CIT21-08|Macragge Blue|#0D407F
            CIT21-24|Mechanicus Standard Grey|#3D4B4D
            CIT21-03|Mephiston Red|#9A1115
            CIT21-51|Morghast Bone|#C6BB98
            CIT21-20|Mournfang Brown|#5F2B17
            CIT21-05|Naggaroth Night|#3D3354
            CIT21-42|Night Lords Blue|#002A50
            CIT21-43|Nocturne Green|#1E3A2A
            CIT21-56|Orruk Flesh|#6E8A4A
            CIT21-39|Phoenician Purple|#4A1E4A
            CIT21-27|Rakarth Flesh|#A29E91
            CIT21-19|Ratskin Flesh|#AE6B4E
            CIT21-35|Retributor Armour|#B0874A
            CIT21-22|Rhinox Hide|#462F30
            CIT21-55|Runelord Brass|#BCAFA0
            CIT21-33|Screamer Pink|#7C1645
            CIT21-30|Screaming Bell|#B3562B
            CIT21-17|Steel Legion Drab|#5E5134
            CIT21-10|Stegadon Scale Green|#074863
            CIT21-32|The Fang|#436276
            CIT21-58|Thondia Brown|#4A3524
            CIT21-36|Thousand Sons Blue|#00506F
            CIT21-13|Waaagh! Flesh|#20552B
            CIT21-31|Warplock Bronze|#4E3B30
            CIT21-53|Wraithbone|#DBD1B2
            CIT21-21|XV-88|#72481E
            CIT21-16|Zandri Dust|#988E56
        """,
        "Layer" to """
            CIT22-50|Administratum Grey|#949B95
            CIT22-76|Ahriman Blue|#1F8C9C
            CIT22-13|Alaitoc Blue|#295788
            CIT22-15|Altdorf Guard Blue|#1F56A7
            CIT22-62|Auric Armour Gold|#D4AF37
            CIT22-79|Baharroth Blue|#58C1CD
            CIT22-43|Balor Brown|#8B5910
            CIT22-48|Baneblade Brown|#927F6E
            CIT22-38|Bestigor Flesh|#D38A57
            CIT22-92|Bloodreaver Flesh|#6A4848
            CIT22-84|Blue Horror|#A2BAD2
            CIT22-65|Brass Scorpion|#B7885F
            CIT22-36|Cadian Fleshtone|#C77958
            CIT22-16|Calgar Blue|#2A497F
            CIT22-94|Canoptek Alloy|#C8A86A
            CIT22-89|Castellax Bronze|#8A6A3A
            CIT22-52|Dark Reaper|#3C5051
            CIT22-49|Dawnstone|#70756E
            CIT22-41|Deathclaw Brown|#AF634F
            CIT22-82|Dechala Lilac|#B59ECC
            CIT22-77|Deepkin Flesh|#D2D4C7
            CIT22-80|Dorn Yellow|#FFF55A
            CIT22-30|Elysian Green|#6B8C37
            CIT22-70|Emperor's Children|#B94278
            CIT22-51|Eshin Grey|#4A4F53
            CIT22-05|Evil Sunz Scarlet|#C01411
            CIT22-68|Fenrisian Grey|#6D94B3
            CIT22-04|Fire Dragon Bright|#F4874E
            CIT22-02|Flash Gitz Yellow|#FFEE00
            CIT22-72|Flayed One Flesh|#CBB48B
            CIT22-81|Fulgrim Pink|#F4AFCC
            CIT22-74|Fulgurite Copper|#C77E4D
            CIT22-78|Gauss Blaster Green|#84C3AA
            CIT22-61|Gehenna's Gold|#DBA674
            CIT22-10|Genestealer Purple|#7761AB
            CIT22-47|Gorthor Brown|#654641
            CIT22-63|Hashut Copper|#C69169
            CIT22-14|Hoeth Blue|#4C78AF
            CIT22-59|Ironbreaker|#A1A6A9
            CIT22-21|Kabalite Green|#038C67
            CIT22-86|Kakophoni Purple|#8D74A8
            CIT22-35|Karak Stone|#BB9662
            CIT22-37|Kislev Flesh|#D6A875
            CIT22-93|Knight-Questor Flesh|#A86A5A
            CIT22-83|Krieg Khaki|#B7AD78
            CIT22-71|Liberator Gold|#D3B587
            CIT22-27|Loren Forest|#50702D
            CIT22-18|Lothern Blue|#34A2CF
            CIT22-85|Lugganath Orange|#F89E86
            CIT22-24|Moot Green|#52B244
            CIT22-29|Nurgling Green|#849C63
            CIT22-31|Ogryn Camo|#9DA94B
            CIT22-58|Pallid Wych Flesh|#CDCEBE
            CIT22-88|Phalanx Yellow|#F2D533
            CIT22-69|Pink Horror|#90305D
            CIT22-60|Runefang Steel|#C3CACE
            CIT22-67|Russ Grey|#507085
            CIT22-33|Screaming Skull|#B9C099
            CIT22-26|Skarsnik Green|#5F9370
            CIT22-54|Skavenblight Dinge|#47413B
            CIT22-40|Skrag Brown|#904A0F
            CIT22-73|Skullcrusher Brass|#E9C58B
            CIT22-12|Slaanesh Grey|#8B8893
            CIT22-87|Sons of Horus Green|#00545E
            CIT22-19|Sotek Green|#0B6974
            CIT22-08|Squig Orange|#AA4F44
            CIT22-75|Stormhost Silver|#BBC6C9
            CIT22-55|Stormvermin Fur|#736C66
            CIT22-28|Straken Green|#628026
            CIT22-22|Sybarite Green|#17A166
            CIT22-64|Sycorax Bronze|#CBB394
            CIT22-34|Tallarn Sand|#A67610
            CIT22-42|Tau Light Ochre|#BC6B10
            CIT22-17|Teclis Blue|#3877BF
            CIT22-20|Temple Guard Blue|#239489
            CIT22-53|Thunderhawk Blue|#417074
            CIT22-03|Troll Slayer Orange|#F36D2D
            CIT22-46|Tuskgor Fur|#883736
            CIT22-56|Ulthuan Grey|#C7E0D9
            CIT22-39|Ungor Flesh|#D6A766
            CIT22-32|Ushabti Bone|#BBBB7F
            CIT22-90|Vulkan Green|#3E6B4B
            CIT22-25|Warboss Green|#317E57
            CIT22-11|Warpfiend Grey|#6C6A75
            CIT22-23|Warpstone Glow|#1E7331
            CIT22-07|Wazdakka Red|#880804
            CIT22-57|White Scar|#FFFFFF
            CIT22-06|Wild Rider Red|#EA2F28
            CIT22-91|Word Bearers Red|#8E2A22
            CIT22-09|Xereus Purple|#471F5F
            CIT22-01|Yriel Yellow|#FFDA00
            CIT22-44|Zamesi Desert|#DDA024
        """,
        "Shade" to """
            CIT24-15|Agrax Earthshade|#3D2F1E
            CIT24-21|Athonian Camoshade|#4A5A2B
            CIT24-34|Berserker Bloodshade|#5A1520
            CIT24-19|Biel-Tan Green|#1E4F2A
            CIT24-13|Carroburg Crimson|#6B1A2E
            CIT24-18|Casandora Yellow|#C68A2A
            CIT24-22|Coelia Greenshade|#0D4F4C
            CIT24-17|Drakenhof Nightshade|#1B3A5E
            CIT24-16|Druchii Violet|#4A2A55
            CIT24-20|Fuegan Orange|#9B4A1E
            CIT24-29|Kroak Green|#4A6A2A
            CIT24-32|Mortarion Grime|#6A6A3A
            CIT24-14|Nuln Oil|#14100E
            CIT24-30|Poxwalker|#6E7A5A
            CIT24-24|Reikland Fleshshade|#6E3A1F
            CIT24-23|Seraphim Sepia|#8C5A2B
            CIT24-35|Soulblight Grey|#4A4E5A
            CIT24-31|Targor Rageshade|#4A2A3A
            CIT24-33|Tyran Blue|#3A5A8A
        """,
        "Contrast" to """
            CIT29-48|Aeldari Emerald|#0A6A5A
            CIT29-41|Aethermatic Blue|#6AA5C4
            CIT29-25|Aggaros Dunes|#B0884A
            CIT29-19|Akhelian Green|#126A68
            CIT29-34|Apothecary White|#E3E6E6
            CIT29-59|Asurmen Blue|#1E4A8A
            CIT29-67|Baal Red|#B0141E
            CIT29-53|Bad Moon Yellow|#E8C21A
            CIT29-37|Basilicanum Grey|#4A4A4A
            CIT29-45|Black Legion|#2A2A2E
            CIT29-38|Black Templar|#1C1C1C
            CIT29-12|Blood Angels Red|#A3141A
            CIT29-56|Briar Queen Chill|#9AB0B8
            CIT29-60|Celestium Blue|#3A7AB0
            CIT29-23|Creed Camo|#5A6A3A
            CIT29-29|Cygor Brown|#3E2A1F
            CIT29-20|Dark Angels Green|#1E3A24
            CIT29-33|Darkoath Flesh|#8A5A44
            CIT29-66|Doomfire Magenta|#B01E6A
            CIT29-65|Dreadful Visage|#3A3A40
            CIT29-13|Flesh Tearers Red|#6E1414
            CIT29-57|Frostheart|#6AB0D0
            CIT29-31|Fyreslayer Flesh|#A85A3A
            CIT29-44|Garaghak's Sewer|#6A5A3A
            CIT29-28|Gore-Grunta Fur|#7A3A1E
            CIT29-35|Gryph-Charger Grey|#6A8AA0
            CIT29-11|Gryph-Hound Orange|#D85A1F
            CIT29-32|Guilliman Flesh|#B07355
            CIT29-49|Gutrippa Flesh|#6A8A4A
            CIT29-54|Imperial Fist|#E8B81E
            CIT29-52|Ironjawz Yellow|#D9A21A
            CIT29-10|Iyanden Yellow|#E8A01C
            CIT29-50|Karandras Green|#2A4A2E
            CIT29-55|Kroxigor Scales|#2A6A8A
            CIT29-17|Leviadon Blue|#1C2D4F
            CIT29-63|Luxion Purple|#6A3A9A
            CIT29-68|Magmadroth Flame|#D8501E
            CIT29-16|Magos Purple|#8A5A9E
            CIT29-47|Mantis Warriors Green|#4A8A3A
            CIT29-24|Militarum Green|#5E6B2E
            CIT29-21|Nazdreg Yellow|#A2721D
            CIT29-22|Ork Flesh|#4A6A2A
            CIT29-42|Plaguebearer Flesh|#A0A060
            CIT29-58|Pylar Glacier|#8AC8E0
            CIT29-46|Ratling Grime|#4A3E2E
            CIT29-15|Shyish Purple|#4A2A6E
            CIT29-64|Sigvald Burgundy|#6A1E2E
            CIT29-26|Skeleton Horde|#9A8A5A
            CIT29-27|Snakebite Leather|#8A5A2B
            CIT29-36|Space Wolves Grey|#6A8094
            CIT29-61|Stormfiend|#4A4640
            CIT29-51|Striking Scorpion Green|#2A7A3A
            CIT29-39|Talassar Blue|#1E5C9E
            CIT29-43|Terradon Turquoise|#0F5C66
            CIT29-18|Ultramarines Blue|#1A3D7C
            CIT29-14|Volupus Pink|#7A2A5A
            CIT29-40|Warp Lightning|#3A8A2C
            CIT29-30|Wyldwood|#4A2F1E
        """,
        "Dry" to """
            |Astorath Red|#DD482B
            |Golden Griffon|#A99058
            |Hexos Palesun|#FFF56E
            |Longbeard Grey|#CECEAF
            |Necron Compound|#A9AAAB
            |Praxeti White|#FFFFFF
            |Ryza Rust|#EC631A
            |Sigmarite|#CAAD76
            |Terminatus Stone|#BDB192
            |Tyrant Skull|#CDC586
            |Underhive Ash|#BAB78F
            |Verminlord Hide|#A16954
            |Wrack White|#D8D8D8
        """,
        "Spray" to """
            |Chaos Black|#1A1A1A
            |Corax White|#F2F2F2
            |Wraithbone|#DBD1B2
            |Grey Seer|#A2A5A7
            |Mechanicus Standard Grey|#3D4B4D
            |Leadbelcher|#888D8F
            |Retributor Armour|#B0874A
            |Macragge Blue|#0D407F
            |Mephiston Red|#9A1115
            |Death Guard Green|#848A66
            |Zandri Dust|#988E56
            |Averland Sunset|#FDB825
            |Caliban Green|#00401F
        """,
        "Technical" to """
            |Ardcoat|#EDEDED
            |Armageddon Dust|#D3A907
            |Blood for the Blood God|#6C0001
            |Lahmian Medium|#EDEDED
            |Martian Ironearth|#8A3A2A
            |Mordant Earth|#2A2A2A
            |Nihilakh Oxide|#66B39A
            |Nurgle's Rot|#9B8F22
            |Soulstone Blue|#1A4A9A
            |Spiritstone Red|#9A1A1A
            |Stirland Mud|#492B00
            |Tesseract Glow|#4ACF4A
            |Typhus Corrosion|#3B342E
            |Valhallan Blizzard|#F0F0F0
            |Waystone Green|#1A7A3A
        """,
    )

    // ---------------------------------------------------------------- Vallejo
    private val VALLEJO = listOf(
        "Model Color" to """
            70.951|White|#F4F4F2
            70.950|Black|#1B1B1B
            70.918|Ivory|#E8DFC4
            70.917|Beige|#D9C69A
            70.976|Buff|#C8A97A
            70.847|Dark Sand|#A99A70
            70.819|Iraqui Sand|#C9B48A
            70.837|Pale Sand|#DCCFA8
            70.928|Light Flesh|#E8C2A0
            70.955|Flat Flesh|#D9A07A
            70.860|Medium Fleshtone|#C9845E
            70.845|Sunny Skin Tone|#DDA37C
            70.815|Basic Skin Tone|#C88F6B
            70.803|Brown Rose|#9A5E4E
            70.909|Vermillion|#D83A23
            70.957|Flat Red|#B8202A
            70.926|Red|#A5212A
            70.908|Carmine Red|#B11E2A
            70.946|Dark Red|#6E1A1C
            70.814|Burnt Red|#7A2A1E
            70.859|Black Red|#3E1A1A
            70.910|Orange Red|#E0552A
            70.851|Bright Orange|#F07A1E
            70.953|Flat Yellow|#F5D21E
            70.915|Deep Yellow|#F2B21C
            70.948|Golden Yellow|#F5C431
            70.949|Light Yellow|#F7E27A
            70.913|Yellow Ochre|#C98E2A
            70.912|Tan Yellow|#C9A46A
            70.954|Yellow Green|#8DB43A
            70.942|Light Green|#5FA048
            70.968|Flat Green|#2E7A3A
            70.969|Park Green Flat|#3E8A3A
            70.970|Deep Green|#1E4A2A
            70.893|US Dark Green|#3A4A2E
            70.924|Russian Uniform WWII|#5A5E3A
            70.887|Brown Violet|#5E5A3A
            70.921|English Uniform|#6E5A3A
            70.988|Khaki|#8A7A50
            70.961|Sky Blue|#7ABEDE
            70.902|Pastel Blue|#9EBED4
            70.844|Deep Sky Blue|#3E8ABE
            70.966|Turquoise|#1E8A8A
            70.808|Blue Green|#1E6A6A
            70.925|Blue|#1E4A9A
            70.930|Dark Blue|#1E2A4A
            70.899|Dark Prussian Blue|#1E2E3E
            70.962|Flat Blue|#1E3E8A
            70.963|Medium Blue|#2A4E8A
            70.809|Royal Blue|#2A3E9E
            70.960|Violet|#5A3A7A
            70.959|Purple|#4A2A5E
            70.945|Magenta|#A02A5A
            70.958|Pink|#E07A9E
            70.984|Flat Brown|#6A3E22
            70.871|Leather Brown|#7A4A2A
            70.875|Beige Brown|#7A5A3A
            70.826|German Camo Medium Brown|#6A4A2E
            70.822|German Camo Black Brown|#3A2A1E
            70.940|Saddle Brown|#7A4A2E
            70.982|Cavalry Brown|#6A2E22
            70.983|Flat Earth|#8A6A3E
            70.941|Burnt Umber|#4A3022
            70.872|Chocolate Brown|#4A2A1E
            70.856|Ochre Brown|#9A6A2A
            70.877|Goldbrown|#A06A2A
            70.873|US Field Drab|#7A6A3A
            70.992|Neutral Grey|#7A7A72
            70.990|Light Grey|#A8A8A0
            70.987|Medium Grey|#7E8282
            70.995|German Grey|#3A4044
            70.994|Dark Grey|#4E5254
            70.886|Green Grey|#8A9480
            70.997|Silver|#B8B8B8
            70.996|Gold|#C8A040
            70.998|Bronze|#8A6A3A
            70.999|Copper|#A8643A
            70.863|Gunmetal Grey|#5A5E62
            70.865|Oily Steel|#6A6E72
            70.864|Natural Steel|#8A8E92
            70.801|Brass|#B08A3A
            70.878|Old Gold|#9A7A3A
        """,
        "Game Color" to """
            72.001|Dead White|#F4F4F2
            72.051|Black|#1B1B1B
            72.007|Gold Yellow|#F5B21E
            72.008|Orange Fire|#E8641E
            72.009|Hot Orange|#E04A1E
            72.010|Bloody Red|#B81E1E
            72.011|Gory Red|#8A1A1E
            72.012|Scarlet Red|#7A141E
            72.013|Squid Pink|#C86E7A
            72.014|Warlord Purple|#8A1E4A
            72.015|Hexed Lichen|#4A2A6A
            72.016|Royal Purple|#5A3A7A
            72.019|Night Blue|#1E2A4A
            72.020|Imperial Blue|#1E3A6E
            72.021|Magic Blue|#2A5AA0
            72.022|Ultramarine Blue|#2A4A8A
            72.023|Electric Blue|#4A8ABE
            72.024|Turquoise|#1E8A8A
            72.025|Foul Green|#4AA08A
            72.026|Jade Green|#2A8A5A
            72.028|Dark Green|#1E3A2A
            72.029|Sick Green|#3A7A3A
            72.030|Goblin Green|#4A8A2A
            72.032|Scorpy Green|#7AB43A
            72.033|Livery Green|#8AA03A
            72.034|Bone White|#D9CDA0
            72.035|Dead Flesh|#9AA07A
            72.036|Bronze Fleshtone|#B8784A
            72.040|Leather Brown|#7A4A2A
            72.041|Dwarf Skin|#C88A6A
            72.042|Parasite Brown|#A05A2A
            72.043|Beasty Brown|#6A3E22
            72.044|Dark Fleshtone|#6A3A2A
            72.045|Charred Brown|#3A2A22
            72.046|Ghost Grey|#C8D0D4
            72.047|Wolf Grey|#7A8A9A
            72.048|Sombre Grey|#3A4A5A
            72.049|Stonewall Grey|#8A8A82
            72.050|Cold Grey|#5A6266
            72.052|Silver|#B8B8B8
            72.053|Chainmail Silver|#9A9EA2
            72.054|Gunmetal Metal|#5A5E62
            72.055|Polished Gold|#C8A040
            72.056|Glorious Gold|#B08A3A
            72.057|Bright Bronze|#9A6A3A
            72.058|Brassy Brass|#A88A3A
            72.060|Tinny Tin|#7A5A3A
            72.061|Khaki|#9A8A5A
            72.062|Earth|#8A6A3E
        """,
        "Model Air" to """
            71.001|White|#F4F4F2
            71.057|Black|#1B1B1B
            |Red|#A5212A
            |Orange|#E8641E
            |Yellow|#F5C431
            |Light Grey|#A8A8A0
            |Medium Grey|#7E8282
            |Dark Grey|#4E5254
            |Black Grey|#2E3234
            |German Grey|#3A4044
            |Olive Drab|#4E4A2E
            |Dark Green|#2E3E2A
            |Dark Yellow|#B8964A
            |Sand Yellow|#C8A46A
            |Light Brown|#8A6A3E
            |Hull Red|#5A2A22
            |Rust|#8A4A2A
            |Dark Rust|#5A2E1E
            |Blue|#2A4A8A
            |Aluminium|#B8BCC0
            |Steel|#8A8E92
            |Gun Metal|#5A5E62
            |Gold|#C8A040
            |Copper|#A8643A
            |Burnt Iron|#5A4A4A
        """,
        "Xpress Color" to """
            |Templar White|#E8E4DC
            |Black Lotus|#1E1E22
            |Velvet Red|#8A1A22
            |Wicked Purple|#5A2A6A
            |Imperial Yellow|#E8A41E
            |Dwarf Skin|#B8784A
            |Copper Brown|#8A4A22
            |Plague Brown|#8A7A3A
            |Desert Ochre|#B0884A
            |Mahogany|#5A2A1E
        """,
        "Wash" to """
            |Black Wash|#14100E
            |Sepia Wash|#7A4E22
            |Umber Wash|#4A3022
            |Red Wash|#6E1A1E
            |Blue Wash|#1B3A5E
            |Green Wash|#1E4F2A
            |Fleshtone Wash|#8A4A2E
            |Dark Grey Wash|#3A3A3A
        """,
        "Surface Primer" to """
            70.602|Black|#1B1B1B
            70.601|White|#F4F4F2
            70.600|Grey|#7E8282
        """,
    )

    // ---------------------------------------------------------- Army Painter
    // Gamme Warpaints Fanatic (références WP3xxx). Les teintes des pots ajoutés en 2026 sont
    // estimées d'après le nom ; les anciens Warpaints sans équivalent restent sans référence.
    private val ARMY = listOf(
        "Warpaints" to """
            WP3012|Matt White|#F4F4F2
            WP3001|Matt Black|#1B1B1B
            WP3002|Deep Grey|#47494C
            WP3003|Uniform Grey|#5A6266
            WP3004|Ash Grey|#B8BCB4
            WP3005|Company Grey|#777B7F
            WP3006|Brigade Grey|#777B7F
            WP3007|Grey Castle|#777B7F
            WP3008|Gargoyle Grey|#777B7F
            WP3009|Great Hall Grey|#B2B5B8
            WP3010|Worn Stone|#B2B5B8
            WP3011|Brainmatter Beige|#F8E5C4
            WP3013|Night Sky|#172E54
            WP3025|Imperial Navy|#172E54
            WP3019|Triumphant Navy|#172E54
            WP3026|Regal Blue|#172E54
            WP3021|Ultramarine Blue|#2A4A8A
            WP3020|Gothic Blue|#31528C
            WP3027|Royal Blue|#31528C
            WP3014|Thunderous Blue|#172E54
            WP3015|Stratos Blue|#31528C
            WP3016|Wolf Grey|#7A8A9A
            WP3017|Runic Cobalt|#31528C
            WP3018|Frost Blue|#7A97C8
            WP3022|Alpha Blue|#7A97C8
            WP3023|Baron Blue|#7A97C8
            WP3024|Augur Blue|#7A97C8
            WP3028|Crystal Blue|#2A6ABE
            WP3029|Arctic Gem|#7A97C8
            WP3030|Bright Sapphire|#31528C
            WP3031|Deep Ocean Blue|#172E54
            WP3032|Abyssal Blue|#172E54
            WP3033|Tidal Blue|#31528C
            WP3034|Phalanx Blue|#31528C
            WP3035|Shieldwall Blue|#31528C
            WP3036|Aegis Aqua|#388C8C
            WP3037|Deep Azure|#172E54
            WP3038|Hydra Turquoise|#1E8A8A
            WP3039|Turquoise Siren|#388C8C
            WP3040|Aquamarine|#388C8C
            WP3041|Neptune Glow|#80C8C8
            WP3042|Marine Mist|#80C8C8
            WP3051|Greenskin|#4A8A2A
            WP3053|Wild Green|#6A9F73
            WP3054|Ferocious Green|#306B3A
            WP3049|Angel Green|#1E4A2A
            WP3050|Guardian Green|#306B3A
            WP3055|Emerald Forest|#306B3A
            WP3056|Leafy Green|#6A9F73
            WP3057|Rainforest|#306B3A
            WP3058|Electric Lime|#83BF2F
            WP3059|Vivid Volt|#83BF2F
            WP3060|Afterglow|#CFFF8C
            WP3061|Evergreen Fog|#306B3A
            WP3062|Medieval Forest|#306B3A
            WP3063|Patagon Pine|#306B3A
            WP3064|Autumn Sage|#606B40
            WP3065|Forest Faun|#606B40
            WP3066|Mossy Green|#606B40
            WP3067|Woodland Camo|#606B40
            WP3068|Army Green|#4E4A2E
            WP3069|Camouflage Green|#606B40
            WP3070|Olive Drab|#606B40
            WP3071|Necrotic Flesh|#9AA07A
            WP3072|Grotesque Green|#606B40
            WP3109|Oak Brown|#4A3022
            WP3110|Tree Ancient|#352215
            WP3111|Dryad Brown|#593C28
            WP3112|Fur Brown|#8A4A22
            WP3073|Brigandine Brown|#593C28
            WP3074|Bootstrap Brown|#593C28
            WP3075|Leather Brown|#7A4A2A
            WP3113|Ruddy Umber|#593C28
            WP3114|Buffed Hide|#593C28
            WP3076|Paratrooper Tan|#B29B74
            WP3077|Command Khaki|#B29B74
            WP3078|Urban Buff|#F8E5C4
            WP3079|Tundra Taupe|#F8E5C4
            WP3080|Prairie Ochre|#B29B74
            WP3081|Desert Yellow|#B8964A
            WP3082|Wasteland Clay|#B29B74
            WP3083|Burnt Turf|#6B5B41
            WP3084|Barren Dune|#B29B74
            WP3085|Dusty Skull|#F8E5C4
            WP3086|Tomb King Tan|#B29B74
            WP3087|Skeleton Bone|#D9C69A
            WP3088|Ancient Stone|#777B7F
            WP3089|Boney Spikes|#F8E5C4
            WP3090|Pale Sand|#F8E5C4
            WP3093|Daemonic Yellow|#F5C431
            WP3092|Fiendish Yellow|#E5C02D
            WP3094|Warped Yellow|#E5C02D
            WP3095|Space Dust|#FFE684
            WP3096|Ice Yellow|#FFE684
            WP3091|Demigod Flames|#E06D21
            WP3097|Molten Lava|#E06D21
            WP3098|Burning Ore|#E06D21
            WP3099|Lava Orange|#E8641E
            WP3100|Flickering Flame|#E06D21
            WP3101|Glowing Inferno|#FFB07C
            WP3102|Inner Light|#FFB07C
            WP3117|Dragon Red|#8A1A1E
            WP3118|Pure Red|#B81E1E
            WP3115|Basilisk Red|#9E1F23
            WP3116|Wyvern Fury|#593C28
            WP3103|Resplendent Red|#9E1F23
            WP3104|Angelic Red|#9E1F23
            WP3105|Legendary Red|#9E1F23
            WP3106|Sacred Scarlet|#9E1F23
            WP3107|Violent Vermilion|#9E1F23
            WP3108|Raging Rouge|#9E1F23
            WP3119|Blood Chalice|#904446
            WP3120|Raging Rose|#D17392
            WP3121|Wicked Pink|#7D3F54
            WP3122|Impish Rouge|#9E1F23
            WP3123|Pixie Pink|#FFBAD1
            WP3124|Weird Elixir|#D17392
            WP3125|Pink Potion|#D17392
            WP3126|Doomfire Drab|#394023
            WP3127|Terrestrial Titan|#B29B74
            WP3128|Alien Purple|#5A3A7A
            WP3129|Cultist Purple|#3F2244
            WP3130|Hexed Violet|#3F2244
            WP3131|Violet Coven|#6A3F72
            WP3132|Kraken Lavender|#6A3F72
            WP3133|Diabolic Plum|#6A3F72
            WP3134|Magecast Magenta|#6A3F72
            WP3135|Warlock Magenta|#6A3F72
            WP3136|Spellbound Fuchsia|#6A3F72
            WP3137|Enchanted Pink|#FFBAD1
            WP3138|Diviner Light|#A17BA8
            WP3139|Mulled Berry|#3F2244
            WP3140|Moldy Wine|#3F2244
            WP3141|Elder Flower|#A17BA8
            WP3142|Forbidden Fruit|#6A3F72
            WP3143|Figgy Pink|#D17392
            WP3144|Wilted Rose|#D17392
            WP3147|Barbarian Flesh|#D9A07A
            WP3145|Moonstone Skin|#B2B5B8
            WP3146|Agate Skin|#BF9176
            WP3148|Ruby Skin|#9E1F23
            WP3149|Opal Skin|#BF9176
            WP3150|Pearl Skin|#FFDAC4
            WP3151|Carnelian Skin|#9E1F23
            WP3152|Tiger's Eye|#BF9176
            WP3153|Topaz Skin|#BF9176
            WP3154|Jasper Skin|#BF9176
            WP3155|Tourmaline Skin|#BF9176
            WP3156|Leopard Stone Skin|#47494C
            WP3157|Obsidian Skin|#725442
            WP3158|Onyx Skin|#725442
            WP3159|Mocca Skin|#352215
            WP3160|Amber Skin|#BF9176
            WP3161|Dorado Skin|#BF9176
            WP3162|Quartz Skin|#FFDAC4
            WP3192|Plate Mail Metal|#9A9EA2
            WP3191|Shining Silver|#C0C4C8
            WP3190|Mithril|#D0D4D8
            WP3181|Rough Iron|#5A4A44
            WP3193|Gun Metal|#5A5E62
            WP3185|Evil Chrome|#B8BEC6
            WP3194|Cobalt Metal|#4A6A9A
            WP3195|Death Metal|#3A3C40
            WP3188|Greedy Gold|#C8A040
            WP3189|Bright Gold|#D4B04A
            WP3187|Tainted Gold|#9A7A3A
            WP3183|Weapon Bronze|#8A6A3A
            WP3184|True Copper|#A8643A
            WP3182|Red Copper|#8A4A32
            WP3186|True Brass|#B08A3A
            WP3196|Dark Emerald|#1E5A3A
            WP3197|Glittering Green|#3A8A4A
            WP3198|Gemstone Red|#8A1A22
            |Chaotic Red|#6E1A1E
            |Goblin Green|#5A9A3A
            |Elf Green|#3E7A3A
            |Electric Blue|#6AB4DE
            |Deep Blue|#1E2A4A
            |Warlock Purple|#8A2A5A
            |Kobold Skin|#C8A46A
            |Tanned Flesh|#9A5E3E
            |Monster Brown|#7A6A3A
            |Mummy Robes|#E8DFC4
            |Hardened Carapace|#6A3A2A
        """,
        "Quickshade Wash" to """
            WP3199|Dark Tone|#14100E
            WP3200|Strong Tone|#4A3022
            WP3201|Soft Tone|#8A6A3E
            WP3202|Light Tone|#B89A6A
            WP3203|Sepia Tone|#7A4E22
            WP3204|Rust Tone|#7A3A1E
            WP3205|Dark Red Tone|#4A1418
            WP3206|Red Tone|#6E1A1E
            WP3207|Orange Tone|#9B4A1E
            WP3208|Green Tone|#1E4F2A
            WP3209|Military Shader|#4A4A2A
            WP3210|Blue Tone|#1B3A5E
            WP3211|Dark Blue Tone|#141E3A
            WP3212|Purple Tone|#4A2A55
            WP3213|Magenta Tone|#6A1A4A
            WP3214|Strong Skin Shade|#7A3E2A
            WP3215|Dark Skin Shade|#4A2418
            WP3216|Wash Medium|#EDEDED
            |Flesh Wash|#8A4A2E
        """,
        "Speedpaint" to """
            |Grim Black|#1C1C1C
            |Holy White|#E3E6E6
            |Blood Red|#A3141A
            |Slaughter Red|#6E1414
            |Fire Giant Orange|#D85A1F
            |Zealot Yellow|#E8A01C
            |Sand Golem|#B0884A
            |Hardened Leather|#7A4A22
            |Crusader Skin|#B07355
            |Pallid Bone|#C8B88A
            |Gravelord Grey|#4A4A4A
            |Highlord Blue|#1A3D7C
            |Magic Blue|#1E5C9E
            |Purple Swarm|#4A2A6E
            |Hive Dweller Purple|#7A2A5A
            |Orc Skin|#4A6A2A
            |Absolution Green|#1E3A24
            |Malignant Green|#3A8A6A
            |Camo Cloak|#5A6A3A
            |Speedpaint Medium|#EDEDED
        """,
        "Colour Primer (bombe)" to """
            |Matt Black|#1B1B1B
            |Matt White|#F4F4F2
            |Uniform Grey|#5A6266
            |Skeleton Bone|#D9C69A
            |Leather Brown|#7A4A2A
            |Army Green|#4E4A2E
            |Pure Red|#B81E1E
            |Ultramarine Blue|#2A4A8A
            |Plate Mail Metal|#9A9EA2
        """,
    )

    // -------------------------------------------------------------- AK Interactive
    // Gamme 3rd Generation (références AK11001 à AK11230). Teintes estimées d'après le nom.
    private val AK = listOf(
        "Primer" to """
            AK11242|Black Primer|#1A1A1A
            |White Primer|#F2F2F2
            |Grey Primer|#8A8E92
        """,
        "3rd Gen" to """
            AK11001|Intense White|#F6F6F4
            AK11002|Offwhite|#EAE6DA
            AK11003|White Grey|#B8B8B8
            AK11004|Ivory|#E8DFC4
            AK11005|Greenish Grey|#7F7F7F
            AK11006|Silver Grey|#B8B8B8
            AK11007|Rock Grey|#7F7F7F
            AK11008|Grimy Grey|#4C4C4C
            AK11009|Warm Grey|#B8B8B8
            AK11010|Medium Grey|#7F7F7F
            AK11011|Blue Grey|#77818C
            AK11012|Sky Grey|#B6BFC8
            AK11013|Pale Grey|#B8B8B8
            AK11014|Medium Sea Grey|#B6BFC8
            AK11015|Dark Sea Grey|#464D54
            AK11016|Grey Green|#6F7F6C
            AK11017|Reddish Grey|#7F7F7F
            AK11018|Neutral Grey|#7F7F7F
            AK11019|Graphite|#3E4044
            AK11020|English Grey|#7F7F7F
            AK11021|Basalt Grey|#4C4C4C
            AK11022|Dark Grey|#4C4C4C
            AK11023|Lead Grey|#4C4C4C
            AK11024|Ash Grey|#7F7F7F
            AK11025|German Grey|#4C4C4C
            AK11026|Tenebrous Grey|#4C4C4C
            AK11027|Rubber Black|#2A2A2C
            AK11028|Smoke Black|#303236
            AK11029|Black|#161616
            AK11030|Beige|#D9C69A
            AK11031|Buff|#C8A97A
            AK11032|Pale Sand|#DFCBA9
            AK11033|Dark Sand|#5E4F35
            AK11034|Medium Sand|#9E865E
            AK11035|Sand Yellow|#9E865E
            AK11036|Ice Yellow|#FFE684
            AK11037|Pastel Yellow|#FFE684
            AK11038|Pale Yellow|#FFE684
            AK11039|Purulent Yellow|#E0BC2C
            AK11040|Sahara Yellow|#E0BC2C
            AK11041|Golden Yellow|#E0BC2C
            AK11042|Volcanic Yellow|#E0BC2C
            AK11043|Dirty Yellow|#866E10
            AK11044|Yellow|#E0BC2C
            AK11045|Deep Yellow|#866E10
            AK11046|Radiant Yellow|#FFE684
            AK11047|Lemon Yellow|#F2EB24
            AK11048|Laser Yellow|#F2EB24
            AK11049|Fluorescent Yellow|#F2EB24
            AK11050|Light Flesh|#FFD8C1
            AK11051|Luminous Flesh|#FFD8C1
            AK11052|Basic Skin Tone|#C69477
            AK11053|Radiant Flesh|#FFD8C1
            AK11054|Medium Flesh Tone|#C69477
            AK11055|Sunny Skin Tone|#C69477
            AK11056|Dark Flesh|#775642
            AK11057|Vampiric Flesh|#C69477
            AK11058|Decomposed Flesh|#C69477
            AK11059|Pastel Pink|#FFB2D8
            AK11060|Sickly Pink|#C66395
            AK11061|Salmon|#E88A7A
            AK11062|Old Rose|#B8787A
            AK11063|Brown Rose|#603C26
            AK11064|Beige Red|#992125
            AK11065|Intense Pink|#FFB2D8
            AK11066|Laser Magenta|#C66395
            AK11067|Magenta|#C66395
            AK11068|Fluorescent Magenta|#C66395
            AK11069|Pastel Violet|#9376A8
            AK11070|Blue Violet|#5A3972
            AK11071|Lilac|#B89AC8
            AK11072|Deep Violet|#351E44
            AK11073|Purple|#5A3972
            AK11074|Deep Purple|#351E44
            AK11075|Violet Red|#5A3972
            AK11076|Pastel Peach|#FFD8C1
            AK11077|Light Orange|#FFB07C
            AK11078|Medium Orange|#E06D21
            AK11079|Burn Orange|#E06D21
            AK11080|Deep Orange|#863B08
            AK11081|Fluorescent Orange|#E06D21
            AK11082|Luminous Orange|#FFB07C
            AK11083|Dead Orange|#863B08
            AK11084|Ruby|#8A1A2A
            AK11085|Cadmium Red|#992125
            AK11086|Amaranth Red|#992125
            AK11087|Scarlet Red|#992125
            AK11088|Deep Red|#5B0D0F
            AK11089|Blood Red|#992125
            AK11090|Vermillion|#D83A23
            AK11091|Carmine|#9A1A2A
            AK11092|Matt Red|#992125
            AK11093|Brick Red|#992125
            AK11094|Bordeaux Red|#5B0D0F
            AK11095|Dirty Red|#5B0D0F
            AK11096|Wine Red|#5B0D0F
            AK11097|Burnt Red|#5B0D0F
            AK11098|Black Red|#5B0D0F
            AK11099|Ocher Orange|#9E865E
            AK11100|Light Brown|#92715D
            AK11101|Orange Brown|#603C26
            AK11102|Deep Brown|#3A2113
            AK11103|Medium Rust|#603C26
            AK11104|Saddle Brown|#603C26
            AK11105|Light Rust|#92715D
            AK11106|Mahogany Brown|#603C26
            AK11107|Dark Rust|#3A2113
            AK11108|Hull Red|#603C26
            AK11109|Dark Brown|#3A2113
            AK11110|Leather Brown|#603C26
            AK11111|Burnt Umber|#3A2113
            AK11112|Grim Brown|#3A2113
            AK11113|Chocolate Chipping|#603C26
            AK11114|Deck Tan|#9E865E
            AK11115|Light Earth|#DFCBA9
            AK11116|Tan Yellow|#9E865E
            AK11117|Golden Brown|#603C26
            AK11118|Ochre|#9E865E
            AK11119|Cork|#A8784A
            AK11120|Mud Brown|#603C26
            AK11121|Tan Earth|#9E865E
            AK11122|Green Ochre|#9E865E
            AK11123|Japanese Brown|#603C26
            AK11124|Middle Stone|#9E865E
            AK11125|Grey Brown|#7F7F7F
            AK11126|Green Brown|#603C26
            AK11127|British Khaki|#9E865E
            AK11128|Luminous Green|#CFFF8C
            AK11129|Fluorescent Green|#83BF2F
            AK11130|Pistachio|#A8C07A
            AK11131|Pastel Green|#6A9F73
            AK11132|Green Grey|#6F7F6C
            AK11133|Dark Green Grey|#414C3F
            AK11134|Green Sky|#ABB8A8
            AK11135|Faded Green|#576B3A
            AK11136|Frog Green|#576B3A
            AK11137|Lime Green|#83BF2F
            AK11138|Interior Yellow Green|#E0BC2C
            AK11139|Golden Olive|#576B3A
            AK11140|Grass Green|#306B3A
            AK11141|Light Green|#6A9F73
            AK11142|Deep Green|#19401F
            AK11143|Mint Green|#9AD4B0
            AK11144|Emerald|#1E8A5A
            AK11145|Lizard Green|#576B3A
            AK11146|Dark Green|#19401F
            AK11147|Olive Green|#576B3A
            AK11148|Medium Olive Green|#576B3A
            AK11149|Intermediate Green|#306B3A
            AK11150|Gunship Green|#576B3A
            AK11151|Brownish Green|#603C26
            AK11152|Alga Green|#576B3A
            AK11153|Extra Dark Green|#19401F
            AK11154|German Field Grey|#414C3F
            AK11155|Command Green|#576B3A
            AK11156|Camouflage Green|#576B3A
            AK11157|US Dark Green|#334020
            AK11158|Reflective Green|#576B3A
            AK11159|Russian Green|#576B3A
            AK11160|Black Green|#19401F
            AK11161|Pale Blue|#7E99C8
            AK11162|Spectrum Blue|#35558C
            AK11163|Intermediate Blue|#35558C
            AK11164|Dark Blue Grey|#464D54
            AK11165|Grey Blue|#77818C
            AK11166|French Blue|#35558C
            AK11167|Anthracite Grey|#7F7F7F
            AK11168|Pastel Blue|#7E99C8
            AK11169|Blue Green|#306B3A
            AK11170|Aquatic Turquoise|#3D9999
            AK11171|Turquoise|#3D9999
            AK11172|Archaic Turquoise|#3D9999
            AK11173|Ocean Blue|#35558C
            AK11174|Snow Blue|#7E99C8
            AK11175|Sky Blue|#7E99C8
            AK11176|Deep Sky Blue|#4D6082
            AK11177|Ducat Blue|#35558C
            AK11178|Fluorescent Blue|#35558C
            AK11179|Ultramarine|#35558C
            AK11180|Imperial Blue|#35558C
            AK11181|Dark Blue|#1A2F54
            AK11182|Deep Blue|#1A2F54
            AK11183|Amethyst Blue|#5A3972
            AK11184|Medium Blue|#35558C
            AK11185|Star Blue|#35558C
            AK11186|Light Prussian Blue|#8D9F74
            AK11187|Strong Dark Blue|#1A2F54
            AK11188|Oxford|#1E2A44
            AK11189|Dark Prussian Blue|#334020
            AK11190|Dark Sea Blue|#1A2F54
            AK11191|Gold|#C8A040
            AK11192|Old Gold|#9A7A3A
            AK11193|Rusty Gold|#8A6A30
            AK11194|Brass|#B08A3A
            AK11195|Rusty Brass|#8A6A3A
            AK11196|Bronze|#8A6A3A
            AK11197|Copper|#A8643A
            AK11198|Burnt Tin|#5A5048
            AK11199|Metallic Blue|#3A5A9A
            AK11200|Astral Beryllium|#8AA0B8
            AK11201|Cobalt Blue|#2A4A8A
            AK11202|Anodized Violet|#6A4A8A
            AK11203|Foundry Red|#8A2A22
            AK11204|Emerald Metallic Green|#1E6A4A
            AK11205|Metallic Green|#3A7A4A
            AK11206|Pearl|#E6E2DA
            AK11207|Aluminium|#B8BCC0
            AK11208|Dark Aluminum|#8A8E92
            AK11209|Silver|#C0C4C8
            AK11210|Natural Steel|#8A8E92
            AK11211|Oily Steel|#6A6E72
            AK11212|Gun Metal|#5A5E62
            AK11213|Clear Red|#C8202A
            AK11214|Clear Blue|#2A5AC8
            AK11215|Clear Smoke|#4A4440
            AK11216|Clear Green|#2A9A3A
            AK11217|Clear Yellow|#F0D020
            AK11218|Clear Orange|#F07A1E
            AK11219|Sepia Ink|#7A4E22
            AK11220|Turquoise Ink|#1E7A7A
            AK11221|Skin Ink|#8A4A2E
            AK11222|Sooty Black Ink|#1C1A18
            AK11223|Carbon Black Ink|#121212
            AK11224|Purple Ink|#4A2A55
            AK11225|Luminous Green Ink|#3AAA2A
            AK11226|Dark Green Ink|#1E3F2A
            AK11227|Penetrating Red Ink|#6E1A1E
            AK11228|Night Blue Ink|#141E3A
            AK11229|Burnt Umber Ink|#4A3022
            AK11230|Titanium White|#FAFAF8
        """,
    )

    // --------------------------------------------------------------- Scale75
    // Gamme Scalecolor (SC-00 à SC-62, d'après la charte officielle). Teintes approximatives.
    private val SCALE75 = listOf(
        "Scalecolor" to """
            SC-00|Black|#141414
            SC-01|White|#F6F6F2
            SC-02|Nacar|#E8E2D6
            SC-03|Graphite|#3A3C40
            SC-04|Anthracite Grey|#4A4E52
            SC-05|Arctic Blue|#C8DCE6
            SC-06|Bering Blue|#8AA8BC
            SC-07|Caspian Blue|#4A6A84
            SC-08|Abyssal Blue|#1E2E40
            SC-09|White Sands|#EDE3C8
            SC-10|Tenere Yellow|#E2C88A
            SC-11|Sahara Yellow|#C8A060
            SC-12|Gobi Brown|#9A7444
            SC-13|Dubai Brown|#6E4E2E
            SC-14|Kalahari Orange|#B06A2E
            SC-15|Adriatic Blue|#5A7A8E
            SC-16|Eclipse Grey|#2E3236
            SC-17|Pale Skin|#F0D2BC
            SC-18|Light Skin|#E6B89A
            SC-19|Golden Skin|#D9A47A
            SC-20|Basic Flesh|#C88A64
            SC-21|Pink Flesh|#D8968A
            SC-22|Arabic Shadow|#8E5A44
            SC-23|Indian Shadow|#7D454E
            SC-24|African Shadow|#4E2E26
            SC-25|Birch|#D6BE92
            SC-26|Sandalwood|#B08A5A
            SC-27|Iroko|#8A5A30
            SC-28|Orange Leather|#A85A2A
            SC-29|Walnut|#5A3A22
            SC-30|Red Leather|#7A2E22
            SC-31|Brown Leather|#5E3A26
            SC-32|Black Leather|#2E2420
            SC-33|Sunset Purple|#6A2A4A
            SC-34|Fuchsia|#B8306A
            SC-35|Deep Red|#7A141E
            SC-36|Blood Red|#9A1A1E
            SC-37|Antares Red|#C42A22
            SC-38|Aldebaran Red|#E04A2A
            SC-39|Mars Orange|#E8742A
            SC-40|Sol Yellow|#F2C82A
            SC-41|Black Forest Green|#1A3D1C
            SC-42|Boreal Green|#2E5A2E
            SC-43|Irati Green|#4A7A3A
            SC-44|Sherwood Green|#7A9A4A
            SC-45|Ardennes Green|#4A5034
            SC-46|Field Grey|#6A6E5A
            SC-47|Spring Green|#9AC060
            SC-48|Fall Green|#8A8A3A
            SC-49|Caribbean Blue|#2A8A9A
            SC-50|Sky Blue|#7AB0D4
            SC-51|Mediterranean Blue|#2A6AA0
            SC-52|Tesla Blue|#3A84C4
            SC-53|Cantabric Blue|#2A4A6A
            SC-54|Navy Blue|#1E2A54
            SC-55|Deep Blue|#141E3A
            SC-56|Violet|#4A2A6A
            SC-57|Petroleum Grey|#3A4A4E
            SC-58|Graphene Grey|#5A5E62
            SC-59|Brown Grey|#6A5E52
            SC-60|Rainy Grey|#9AA0A2
            SC-61|Thar Brown|#7A5A3E
            SC-62|Mojave White|#E8DCC4
        """,
    )

    // --------------------------------------------------------- Prince August
    private val PRINCE = listOf(
        "Classic" to """
            |White|#F4F4F2
            |Black|#1B1B1B
            |Ivory|#E8DFC4
            |Buff|#C8A97A
            |Light Flesh|#E8C2A0
            |Flat Flesh|#D9A07A
            |Medium Fleshtone|#C9845E
            |Brown Rose|#9A5E4E
            |Flat Red|#B8202A
            |Carmine Red|#B11E2A
            |Dark Red|#6E1A1C
            |Vermillion|#D83A23
            |Orange|#F07A1E
            |Flat Yellow|#F5D21E
            |Golden Yellow|#F5C431
            |Yellow Ochre|#C98E2A
            |Light Green|#5FA048
            |Flat Green|#2E7A3A
            |Deep Green|#1E4A2A
            |Olive Green|#5A5E3A
            |Khaki|#8A7A50
            |Sky Blue|#7ABEDE
            |Turquoise|#1E8A8A
            |Flat Blue|#1E3E8A
            |Dark Blue|#1E2A4A
            |Prussian Blue|#1E2E3E
            |Violet|#5A3A7A
            |Purple|#4A2A5E
            |Magenta|#A02A5A
            |Flat Brown|#6A3E22
            |Leather Brown|#7A4A2A
            |Beige Brown|#7A5A3A
            |Saddle Brown|#7A4A2E
            |Burnt Umber|#4A3022
            |Flat Earth|#8A6A3E
            |Neutral Grey|#7A7A72
            |Light Grey|#A8A8A0
            |Dark Grey|#4E5254
            |German Grey|#3A4044
        """,
        "Métallisées" to """
            |Silver|#B8B8B8
            |Gold|#C8A040
            |Old Gold|#9A7A3A
            |Bronze|#8A6A3A
            |Copper|#A8643A
            |Gunmetal|#5A5E62
            |Natural Steel|#8A8E92
            |Brass|#B08A3A
        """,
        "Lavis & vernis" to """
            |Black Wash|#14100E
            |Sepia Wash|#7A4E22
            |Matt Varnish|#EDEDED
            |Gloss Varnish|#F4F4F4
        """,
    )
}
