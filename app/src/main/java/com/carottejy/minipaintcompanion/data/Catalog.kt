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
            |Abaddon Black|#231F20
            |Averland Sunset|#FDB825
            |Balthasar Gold|#A47552
            |Bugman's Glow|#834F44
            |Caledor Sky|#366699
            |Caliban Green|#00401F
            |Castellan Green|#314821
            |Celestra Grey|#90A8A8
            |Corax White|#F2F2F2
            |Daemonette Hide|#696684
            |Death Guard Green|#848A66
            |Doombull Brown|#5D0009
            |Dryad Bark|#33312D
            |Gal Vorbak Red|#4B213C
            |Grey Seer|#A2A5A7
            |Incubi Darkness|#0B474A
            |Iron Hands Steel|#8E9294
            |Jokaero Orange|#EE3823
            |Kantor Blue|#02134E
            |Khorne Red|#6A0001
            |Leadbelcher|#888D8F
            |Lupercal Green|#002C2B
            |Macragge Blue|#0D407F
            |Mechanicus Standard Grey|#3D4B4D
            |Mephiston Red|#9A1115
            |Morghast Bone|#C6BB98
            |Mournfang Brown|#5F2B17
            |Naggaroth Night|#3D3354
            |Night Lords Blue|#002A50
            |Rakarth Flesh|#A29E91
            |Retributor Armour|#B0874A
            |Rhinox Hide|#462F30
            |Screamer Pink|#7C1645
            |Steel Legion Drab|#5E5134
            |Stegadon Scale Green|#074863
            |Thousand Sons Blue|#00506F
            |Warplock Bronze|#4E3B30
            |Wraithbone|#DBD1B2
            |Zandri Dust|#988E56
        """,
        "Layer" to """
            |Administratum Grey|#949B95
            |Ahriman Blue|#1F8C9C
            |Alaitoc Blue|#295788
            |Altdorf Guard Blue|#1F56A7
            |Auric Armour Gold|#D4AF37
            |Baharroth Blue|#58C1CD
            |Balor Brown|#8B5910
            |Bestigor Flesh|#D38A57
            |Bloodreaver Flesh|#6A4848
            |Brass Scorpion|#B7885F
            |Cadian Fleshtone|#C77958
            |Calgar Blue|#2A497F
            |Dawnstone|#70756E
            |Deathclaw Brown|#AF634F
            |Dorn Yellow|#FFF55A
            |Elysian Green|#6B8C37
            |Emperor's Children|#B94278
            |Evil Sunz Scarlet|#C01411
            |Fenrisian Grey|#6D94B3
            |Fire Dragon Bright|#F4874E
            |Flash Gitz Yellow|#FFEE00
            |Fulgurite Copper|#C77E4D
            |Gauss Blaster Green|#84C3AA
            |Gehenna's Gold|#DBA674
            |Genestealer Purple|#7761AB
            |Hoeth Blue|#4C78AF
            |Ironbreaker|#A1A6A9
            |Kabalite Green|#038C67
            |Karak Stone|#BB9662
            |Kislev Flesh|#D6A875
            |Liberator Gold|#D3B587
            |Loren Forest|#50702D
            |Lothern Blue|#34A2CF
            |Moot Green|#52B244
            |Nurgling Green|#849C63
            |Ogryn Camo|#9DA94B
            |Pallid Wych Flesh|#CDCEBE
            |Pink Horror|#90305D
            |Runefang Steel|#C3CACE
            |Russ Grey|#507085
            |Screaming Skull|#B9C099
            |Skarsnik Green|#5F9370
            |Skavenblight Dinge|#47413B
            |Slaanesh Grey|#8B8893
            |Sotek Green|#0B6974
            |Squig Orange|#AA4F44
            |Stormhost Silver|#BBC6C9
            |Sybarite Green|#17A166
            |Sycorax Bronze|#CBB394
            |Tallarn Sand|#A67610
            |Tau Light Ochre|#BC6B10
            |Teclis Blue|#3877BF
            |Temple Guard Blue|#239489
            |Thunderhawk Blue|#417074
            |Troll Slayer Orange|#F36D2D
            |Ulthuan Grey|#C7E0D9
            |Ungor Flesh|#D6A766
            |Ushabti Bone|#BBBB7F
            |Warboss Green|#317E57
            |Warpstone Glow|#1E7331
            |Wazdakka Red|#880804
            |White Scar|#FFFFFF
            |Wild Rider Red|#EA2F28
            |Xereus Purple|#471F5F
            |Yriel Yellow|#FFDA00
            |Zamesi Desert|#DDA024
        """,
        "Shade" to """
            |Agrax Earthshade|#3D2F1E
            |Athonian Camoshade|#4A5A2B
            |Biel-Tan Green|#1E4F2A
            |Carroburg Crimson|#6B1A2E
            |Casandora Yellow|#C68A2A
            |Coelia Greenshade|#0D4F4C
            |Drakenhof Nightshade|#1B3A5E
            |Druchii Violet|#4A2A55
            |Fuegan Orange|#9B4A1E
            |Nuln Oil|#14100E
            |Reikland Fleshshade|#6E3A1F
            |Seraphim Sepia|#8C5A2B
        """,
        "Contrast" to """
            |Aethermatic Blue|#6AA5C4
            |Aggaros Dunes|#B0884A
            |Akhelian Green|#126A68
            |Apothecary White|#E3E6E6
            |Basilicanum Grey|#4A4A4A
            |Black Templar|#1C1C1C
            |Blood Angels Red|#A3141A
            |Creed Camo|#5A6A3A
            |Cygor Brown|#3E2A1F
            |Dark Angels Green|#1E3A24
            |Darkoath Flesh|#8A5A44
            |Flesh Tearers Red|#6E1414
            |Gore-Grunta Fur|#7A3A1E
            |Gryph-Charger Grey|#6A8AA0
            |Gryph-Hound Orange|#D85A1F
            |Guilliman Flesh|#B07355
            |Iyanden Yellow|#E8A01C
            |Karandras Green|#2A4A2E
            |Leviadon Blue|#1C2D4F
            |Magos Purple|#8A5A9E
            |Militarum Green|#5E6B2E
            |Nazdreg Yellow|#A2721D
            |Ork Flesh|#4A6A2A
            |Plaguebearer Flesh|#A0A060
            |Shyish Purple|#4A2A6E
            |Skeleton Horde|#9A8A5A
            |Snakebite Leather|#8A5A2B
            |Space Wolves Grey|#6A8094
            |Talassar Blue|#1E5C9E
            |Terradon Turquoise|#0F5C66
            |Ultramarines Blue|#1A3D7C
            |Volupus Pink|#7A2A5A
            |Warp Lightning|#3A8A2C
            |Wyldwood|#4A2F1E
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
