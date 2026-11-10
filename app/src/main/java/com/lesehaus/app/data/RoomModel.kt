package com.lesehaus.app.data

import com.lesehaus.app.data.db.ReadingRoom

enum class RoomType(val label: String, val emoji: String) {
    FIREPLACE("Kaminzimmer", "🔥"),
    PIANO("Klavierzimmer", "🎹"),
    LIVING("Wohnzimmer", "🛋️"),
    BEDROOM("Schlafzimmer", "🛏️"),
    LIBRARY("Bibliothek", "📚"),
    STUDY("Arbeitszimmer", "🖋️"),
    CONSERVATORY("Wintergarten", "🌿"),
    ATTIC("Dachboden", "🕯️"),
    NOOK("Leseecke", "☕"),
}

enum class WallPattern(val label: String) {
    PLAIN("Schlicht"),
    STRIPES("Streifen"),
    DAMASK("Damast"),
    PANELING("Holzvertäfelung"),
    BRICK("Backstein"),
    FLORAL("Blümchen"),
    WOOD("Holzwand"),
}

enum class FloorType(val label: String) {
    PARQUET("Parkett"),
    PLANKS("Dielen"),
    CARPET("Teppichboden"),
    TILES("Fliesen"),
    STONE("Stein"),
}

enum class WindowView(val label: String) {
    AUTO("Echte Tageszeit"),
    DAY("Tag"),
    SUNSET("Abendrot"),
    NIGHT("Nacht"),
    NONE("Kein Fenster"),
}

enum class Weather(val label: String) {
    CLEAR("Klar"),
    RAIN("Regen"),
    SNOW("Schnee"),
    STORM("Gewitter"),
}

enum class Furniture(val label: String) {
    FIREPLACE("Kamin"),
    PIANO("Klavier"),
    SOFA("Sofa"),
    ARMCHAIR("Ohrensessel"),
    BED("Bett"),
    DESK("Schreibtisch"),
    LAMP("Stehlampe"),
    PLANT("Zimmerpflanze"),
    RUG("Teppich"),
    PAINTING("Gemälde"),
    CLOCK("Wanduhr"),
    CANDLES("Kerzen"),
    CAT("Schlafende Katze"),
    GLOBE("Globus"),
    STRING_LIGHTS("Lichterkette"),
    BOOKSTACK("Bücherstapel"),
    TEA("Teetasse"),
}

enum class AmbientSound(val label: String) {
    NONE("Stille"),
    FIRE("Kaminknistern"),
    RAIN("Regen"),
    STORM("Gewitter & Regen"),
    WIND("Wind"),
    WAVES("Meeresrauschen"),
    CLOCK("Uhrticken"),
    PIANO("Leises Klavier"),
}

enum class Wood(val label: String, val base: Int, val dark: Int, val light: Int) {
    OAK("Eiche", 0xFFA47A4D.toInt(), 0xFF6E4C2C.toInt(), 0xFFC89B6A.toInt()),
    WALNUT("Nussbaum", 0xFF6B4630.toInt(), 0xFF3F271A.toInt(), 0xFF8C5F43.toInt()),
    CHERRY("Kirsche", 0xFF8E4A32.toInt(), 0xFF5A2A1B.toInt(), 0xFFB0654A.toInt()),
    MAHOGANY("Mahagoni", 0xFF6E2A22.toInt(), 0xFF401511.toInt(), 0xFF8E3D31.toInt()),
    EBONY("Ebenholz", 0xFF2E2622.toInt(), 0xFF171210.toInt(), 0xFF4A3E37.toInt()),
    WHITE("Weiß lackiert", 0xFFE9E3D8.toInt(), 0xFFBDB4A6.toInt(), 0xFFFFFBF3.toInt()),
    PINE("Kiefer", 0xFFD2A86E.toInt(), 0xFF9C7444.toInt(), 0xFFE8C894.toInt()),
    SAGE("Salbeigrün", 0xFF7D8C6E.toInt(), 0xFF56634A.toInt(), 0xFF9AAA8A.toInt()),
}

enum class ShelfDisplay(val label: String) { COVERS("Cover"), SPINES("Buchrücken") }

enum class ShelfSort(val label: String) {
    TITLE("Titel"), AUTHOR("Autor"), RECENT("Zuletzt gelesen"), ADDED("Neu hinzugefügt"), PROGRESS("Fortschritt")
}

/** Unterstützte Formate (Dateiendung → Anzeigename). */
val SupportedFormats: LinkedHashMap<String, String> = linkedMapOf(
    "epub" to "EPUB",
    "pdf" to "PDF",
    "txt" to "Text",
    "md" to "Markdown",
    "html" to "HTML",
    "htm" to "HTM",
    "xhtml" to "XHTML",
    "fb2" to "FB2",
    "docx" to "Word",
    "odt" to "OpenDocument",
    "rtf" to "RTF",
)

val DefaultFileTypes = "epub,pdf,txt,md,html,htm,xhtml,fb2,docx,odt,rtf"

val WallPalette: List<Int> = listOf(
    0xFF7A3B2E, 0xFF5C2E2E, 0xFF8C5A3C, 0xFFB98B5E, 0xFFE8D9BE, 0xFFF1E7D3,
    0xFF4E5D45, 0xFF6F7F5E, 0xFF2F4A3F, 0xFF34465E, 0xFF5A6F8C, 0xFF283044,
    0xFF6B4E71, 0xFF9C6B79, 0xFFC9A27E, 0xFF3A2F2A, 0xFFD8CFC4, 0xFF8E8173,
).map { it.toInt() }

val CurtainPalette: List<Int> = listOf(
    0xFF8E2C2C, 0xFF6B1F2E, 0xFFB5833B, 0xFF2F5A47, 0xFF2C3E66, 0xFFE9DDC6, 0xFF5E3A6E, 0xFF9C8466,
).map { it.toInt() }

object RoomPresets {
    fun preset(type: RoomType, name: String = type.label, sortIndex: Int = 0): ReadingRoom = when (type) {
        RoomType.FIREPLACE -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFF7A3B2E.toInt(), wallPattern = WallPattern.DAMASK.name,
            floorType = FloorType.PARQUET.name, windowView = WindowView.AUTO.name, weather = Weather.SNOW.name,
            lightLevel = 0.55f, lightWarmth = 0.85f,
            furniture = listOf(Furniture.FIREPLACE, Furniture.ARMCHAIR, Furniture.RUG, Furniture.CAT, Furniture.CLOCK, Furniture.CANDLES).joinToString(",") { it.name },
            ambientSound = AmbientSound.FIRE.name, curtainColor = 0xFF8E2C2C.toInt(), sortIndex = sortIndex,
        )
        RoomType.PIANO -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFF2F4A3F.toInt(), wallPattern = WallPattern.PANELING.name,
            floorType = FloorType.PARQUET.name, windowView = WindowView.AUTO.name, weather = Weather.RAIN.name,
            lightLevel = 0.65f, lightWarmth = 0.6f,
            furniture = listOf(Furniture.PIANO, Furniture.LAMP, Furniture.PAINTING, Furniture.PLANT, Furniture.RUG).joinToString(",") { it.name },
            ambientSound = AmbientSound.PIANO.name, curtainColor = 0xFFB5833B.toInt(), sortIndex = sortIndex,
        )
        RoomType.LIVING -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFFC9A27E.toInt(), wallPattern = WallPattern.STRIPES.name,
            floorType = FloorType.PLANKS.name, windowView = WindowView.AUTO.name, weather = Weather.CLEAR.name,
            lightLevel = 0.8f, lightWarmth = 0.5f,
            furniture = listOf(Furniture.SOFA, Furniture.LAMP, Furniture.PLANT, Furniture.RUG, Furniture.PAINTING, Furniture.TEA).joinToString(",") { it.name },
            ambientSound = AmbientSound.RAIN.name, curtainColor = 0xFF2F5A47.toInt(), sortIndex = sortIndex,
        )
        RoomType.BEDROOM -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFF34465E.toInt(), wallPattern = WallPattern.FLORAL.name,
            floorType = FloorType.CARPET.name, windowView = WindowView.NIGHT.name, weather = Weather.CLEAR.name,
            lightLevel = 0.45f, lightWarmth = 0.75f,
            furniture = listOf(Furniture.BED, Furniture.STRING_LIGHTS, Furniture.LAMP, Furniture.CAT, Furniture.BOOKSTACK).joinToString(",") { it.name },
            ambientSound = AmbientSound.WIND.name, curtainColor = 0xFFE9DDC6.toInt(), sortIndex = sortIndex,
        )
        RoomType.LIBRARY -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFF5C2E2E.toInt(), wallPattern = WallPattern.WOOD.name,
            floorType = FloorType.PARQUET.name, windowView = WindowView.AUTO.name, weather = Weather.RAIN.name,
            lightLevel = 0.6f, lightWarmth = 0.7f,
            furniture = listOf(Furniture.ARMCHAIR, Furniture.GLOBE, Furniture.LAMP, Furniture.RUG, Furniture.CLOCK, Furniture.BOOKSTACK).joinToString(",") { it.name },
            ambientSound = AmbientSound.CLOCK.name, curtainColor = 0xFF6B1F2E.toInt(), sortIndex = sortIndex,
        )
        RoomType.STUDY -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFF4E5D45.toInt(), wallPattern = WallPattern.PANELING.name,
            floorType = FloorType.PLANKS.name, windowView = WindowView.AUTO.name, weather = Weather.CLEAR.name,
            lightLevel = 0.75f, lightWarmth = 0.55f,
            furniture = listOf(Furniture.DESK, Furniture.GLOBE, Furniture.PLANT, Furniture.CLOCK, Furniture.PAINTING).joinToString(",") { it.name },
            ambientSound = AmbientSound.CLOCK.name, curtainColor = 0xFF9C8466.toInt(), sortIndex = sortIndex,
        )
        RoomType.CONSERVATORY -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFFF1E7D3.toInt(), wallPattern = WallPattern.PLAIN.name,
            floorType = FloorType.TILES.name, windowView = WindowView.DAY.name, weather = Weather.RAIN.name,
            lightLevel = 0.95f, lightWarmth = 0.35f,
            furniture = listOf(Furniture.PLANT, Furniture.ARMCHAIR, Furniture.TEA, Furniture.RUG).joinToString(",") { it.name },
            ambientSound = AmbientSound.RAIN.name, curtainColor = 0xFFE9DDC6.toInt(), sortIndex = sortIndex,
        )
        RoomType.ATTIC -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFF8C5A3C.toInt(), wallPattern = WallPattern.WOOD.name,
            floorType = FloorType.PLANKS.name, windowView = WindowView.NIGHT.name, weather = Weather.STORM.name,
            lightLevel = 0.4f, lightWarmth = 0.9f,
            furniture = listOf(Furniture.CANDLES, Furniture.BOOKSTACK, Furniture.ARMCHAIR, Furniture.STRING_LIGHTS, Furniture.CAT).joinToString(",") { it.name },
            ambientSound = AmbientSound.STORM.name, curtainColor = 0xFF5E3A6E.toInt(), sortIndex = sortIndex,
        )
        RoomType.NOOK -> ReadingRoom(
            name = name, type = type.name, wallColor = 0xFFE8D9BE.toInt(), wallPattern = WallPattern.FLORAL.name,
            floorType = FloorType.PLANKS.name, windowView = WindowView.AUTO.name, weather = Weather.RAIN.name,
            lightLevel = 0.75f, lightWarmth = 0.65f,
            furniture = listOf(Furniture.ARMCHAIR, Furniture.LAMP, Furniture.TEA, Furniture.PLANT, Furniture.CAT).joinToString(",") { it.name },
            ambientSound = AmbientSound.RAIN.name, curtainColor = 0xFF8E2C2C.toInt(), sortIndex = sortIndex,
        )
    }
}

inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
    runCatching { enumValueOf<T>(value ?: "") }.getOrDefault(default)

/** Türstile im Flur. */
enum class DoorStyle(val label: String) {
    PANEL("Kassettentür"),
    ARCH("Rundbogentür"),
    GLASS("Glastür"),
    DOUBLE("Flügeltür"),
    COTTAGE("Landhaustür"),
    BARN("Scheunentor"),
}

val DoorPalette: List<Int> = listOf(
    0, 0xFF6B4630, 0xFF3F271A, 0xFF8E3D31, 0xFF2F4A3F, 0xFF34465E, 0xFFE9E3D8, 0xFF5E3A6E, 0xFF9C6B30, 0xFF1F2F4A,
).map { it.toInt() }
