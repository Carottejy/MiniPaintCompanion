package com.carottejy.minipen.data

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
        }.distinctBy { it.id }
    }

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
    private val ARMY = listOf(
        "Warpaints" to """
            |Matt Black|#1B1B1B
            |Matt White|#F4F4F2
            |Pure Red|#B81E1E
            |Dragon Red|#8A1A1E
            |Chaotic Red|#6E1A1E
            |Lava Orange|#E8641E
            |Daemonic Yellow|#F5C431
            |Desert Yellow|#B8964A
            |Greenskin|#4A8A2A
            |Goblin Green|#5A9A3A
            |Angel Green|#1E4A2A
            |Army Green|#4E4A2E
            |Elf Green|#3E7A3A
            |Necrotic Flesh|#9AA07A
            |Hydra Turquoise|#1E8A8A
            |Electric Blue|#6AB4DE
            |Crystal Blue|#2A6ABE
            |Ultramarine Blue|#2A4A8A
            |Deep Blue|#1E2A4A
            |Alien Purple|#5A3A7A
            |Warlock Purple|#8A2A5A
            |Wolf Grey|#7A8A9A
            |Uniform Grey|#5A6266
            |Ash Grey|#B8BCB4
            |Skeleton Bone|#D9C69A
            |Kobold Skin|#C8A46A
            |Barbarian Flesh|#D9A07A
            |Tanned Flesh|#9A5E3E
            |Leather Brown|#7A4A2A
            |Oak Brown|#4A3022
            |Fur Brown|#8A4A22
            |Monster Brown|#7A6A3A
            |Mummy Robes|#E8DFC4
            |Hardened Carapace|#6A3A2A
            |Plate Mail Metal|#9A9EA2
            |Gun Metal|#5A5E62
            |Shining Silver|#C0C4C8
            |Greedy Gold|#C8A040
            |Bright Gold|#D4B04A
            |Weapon Bronze|#8A6A3A
            |True Copper|#A8643A
            |Rough Iron|#5A4A44
        """,
        "Quickshade Wash" to """
            |Dark Tone|#14100E
            |Strong Tone|#4A3022
            |Soft Tone|#8A6A3E
            |Red Tone|#6E1A1E
            |Green Tone|#1E4F2A
            |Blue Tone|#1B3A5E
            |Purple Tone|#4A2A55
            |Flesh Wash|#8A4A2E
            |Military Shader|#4A4A2A
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
