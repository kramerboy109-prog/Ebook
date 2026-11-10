package com.lesehaus.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReaderTheme(val label: String, val bg: Int, val fg: Int, val accent: Int, val dark: Boolean) {
    PAPER("Papier", 0xFFFBF7EF.toInt(), 0xFF2B2722.toInt(), 0xFF8A5A33.toInt(), false),
    SEPIA("Sepia", 0xFFF1E3C6.toInt(), 0xFF4A3622.toInt(), 0xFF8C5A2B.toInt(), false),
    PARCHMENT("Pergament", 0xFFE9D8B4.toInt(), 0xFF3D2B1A.toInt(), 0xFF7A3F22.toInt(), false),
    MINT("Salbei", 0xFFE3EBDD.toInt(), 0xFF26332A.toInt(), 0xFF4D6B4F.toInt(), false),
    FIRELIGHT("Kaminlicht", 0xFF2A1B12.toInt(), 0xFFF0C995.toInt(), 0xFFE39A52.toInt(), true),
    DUSK("Dämmerung", 0xFF2B2B33.toInt(), 0xFFD9D4CA.toInt(), 0xFFB9A27B.toInt(), true),
    MIDNIGHT("Mitternachtsblau", 0xFF141B2B.toInt(), 0xFFC9D3E6.toInt(), 0xFF8FA7D9.toInt(), true),
    NIGHT("Nacht (OLED)", 0xFF000000.toInt(), 0xFFB8B2A7.toInt(), 0xFFB08D57.toInt(), true),
}

const val DEFAULT_AI_STYLE = "Du bist ein erfahrener deutscher Hörbuchsprecher. Lies warm, ruhig und lebendig vor. Gib Dialogen die passende Stimmung der Figuren, mache natürliche Pausen an Absätzen und lies Überschriften etwas langsamer."

enum class PageMode(val label: String) { PAGED("Blättern"), SCROLL("Scrollen") }
enum class PageAnimation(val label: String) { SLIDE("Schieben"), CURL("Umblättern"), FADE("Überblenden"), STACK("Stapel"), NONE("Keine") }
enum class TextAlignMode(val label: String) { JUSTIFY("Blocksatz"), START("Linksbündig") }
enum class AppThemeMode(val label: String) { SYSTEM("System"), LIGHT("Hell"), DARK("Dunkel") }

data class ReaderSettings(
    val fontKey: String = "literata",
    val fontSize: Float = 19f,
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.55f,
    val letterSpacing: Float = 0f,
    val paragraphSpacing: Float = 0.35f,
    val indent: Boolean = true,
    val marginH: Int = 26,
    val marginV: Int = 34,
    val align: TextAlignMode = TextAlignMode.JUSTIFY,
    val hyphenation: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val autoNightTheme: Boolean = false,
    val nightTheme: ReaderTheme = ReaderTheme.FIRELIGHT,
    val pageMode: PageMode = PageMode.PAGED,
    val pageAnimation: PageAnimation = PageAnimation.CURL,
    val tapZones: Boolean = true,
    val volumeKeys: Boolean = true,
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val brightness: Float = -1f,
    val warmFilter: Float = 0f,
    val paperTexture: Boolean = true,
    val cozyMode: Boolean = false,
    val cozyGlow: Float = 0.6f,
    val ambientSound: AmbientSound = AmbientSound.NONE,
    val ambientVolume: Float = 0.5f,
    val showHeader: Boolean = true,
    val showFooter: Boolean = true,
    val showClock: Boolean = true,
    val showTimeLeft: Boolean = true,
    val showImages: Boolean = true,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    /** Name der Erzählerstimme (leer = Standard des Geräts). */
    val ttsVoice: String = "",
    /** Stimme für wörtliche Rede (leer = Erzählerstimme mit anderer Tonhöhe). */
    val ttsDialogVoice: String = "",
    val ttsDialogPitch: Float = 1.12f,
    /** Erzählmodus: natürliche Pausen, Dialoge mit eigener Stimme, Betonung von Fragen/Ausrufen. */
    val ttsSmart: Boolean = true,
    /** "SYSTEM" oder "AI" (KI-Stimme online). */
    val ttsEngine: String = "SYSTEM",
    val aiVoice: String = "coral",
    val aiStyle: String = DEFAULT_AI_STYLE,
    /** Gemini (kostenloses Kontingent): Erzähler- und Figurenstimme. */
    val geminiVoice: String = "Charon",
    val geminiDialogVoice: String = "Kore",
    val geminiTwoVoices: Boolean = true,
    val geminiModel: String = "gemini-2.5-flash-preview-tts",
) {
    /** Einstellungen, die das Seitenlayout beeinflussen (für Pagination-Cache). */
    fun layoutKey(): String = listOf(fontKey, fontSize, fontWeight, lineHeight, letterSpacing, paragraphSpacing, indent, marginH, marginV, align, hyphenation, showHeader, showFooter, showImages, pageMode).joinToString("|")
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val roomAnimations: Boolean = true,
    val bookAnimations: Boolean = true,
    val onlineCovers: Boolean = true,
    val autoScan: Boolean = true,
    val dailyGoalMinutes: Int = 20,
    val onboarded: Boolean = false,
    val roomSounds: Boolean = false,
    val customFonts: List<String> = emptyList(),
    /** API-Schlüssel für die KI-Stimme (bleibt nur auf dem Gerät). */
    val aiKey: String = "",
    /** Kostenloser Gemini-API-Schlüssel aus Google AI Studio. */
    val geminiKey: String = "",
    /** Zimmerklang beim Lesen weiterspielen. */
    val roomAmbienceInReader: Boolean = true,
    /** Gestaltung des Flurs. */
    val hallWall: Int = 0xFFD9C7A6.toInt(),
    val hallPattern: String = "STRIPES",
    val hallFloor: String = "PARQUET",
    val hallRunner: Int = 0xFF7B2D3B.toInt(),
    val hallWainscot: Boolean = true,
    val hallCeiling: String = "COFFERED",
    /** Flurform: Breite, Türabstand, Türseiten (BOTH/LEFT/RIGHT), Abschluss des Gangs. */
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lesehaus_settings", Context.MODE_PRIVATE)

    private val _reader = MutableStateFlow(loadReader())
    val reader: StateFlow<ReaderSettings> = _reader.asStateFlow()

    private val _app = MutableStateFlow(loadApp())
    val app: StateFlow<AppSettings> = _app.asStateFlow()

    fun updateReader(block: (ReaderSettings) -> ReaderSettings) {
        val n = block(_reader.value)
        _reader.value = n
        saveReader(n)
    }

    fun updateApp(block: (AppSettings) -> AppSettings) {
        val n = block(_app.value)
        _app.value = n
        saveApp(n)
    }

    private fun loadReader(): ReaderSettings {
        val d = ReaderSettings()
        return with(prefs) {
            ReaderSettings(
                fontKey = getString("r_font", d.fontKey) ?: d.fontKey,
                fontSize = getFloat("r_size", d.fontSize),
                fontWeight = getInt("r_weight", d.fontWeight),
                lineHeight = getFloat("r_lh", d.lineHeight),
                letterSpacing = getFloat("r_ls", d.letterSpacing),
                paragraphSpacing = getFloat("r_ps", d.paragraphSpacing),
                indent = getBoolean("r_indent", d.indent),
                marginH = getInt("r_mh", d.marginH),
                marginV = getInt("r_mv", d.marginV),
                align = enumOr(getString("r_align", null), d.align),
                hyphenation = getBoolean("r_hyph", d.hyphenation),
                theme = enumOr(getString("r_theme", null), d.theme),
                autoNightTheme = getBoolean("r_autonight", d.autoNightTheme),
                nightTheme = enumOr(getString("r_nighttheme", null), d.nightTheme),
                pageMode = enumOr(getString("r_mode", null), d.pageMode),
                pageAnimation = enumOr(getString("r_anim", null), d.pageAnimation),
                tapZones = getBoolean("r_tap", d.tapZones),
                volumeKeys = getBoolean("r_vol", d.volumeKeys),
                keepScreenOn = getBoolean("r_keepon", d.keepScreenOn),
                fullscreen = getBoolean("r_full", d.fullscreen),
                brightness = getFloat("r_bright", d.brightness),
                warmFilter = getFloat("r_warm", d.warmFilter),
                paperTexture = getBoolean("r_tex", d.paperTexture),
                cozyMode = getBoolean("r_cozy", d.cozyMode),
                cozyGlow = getFloat("r_cozyglow", d.cozyGlow),
                ambientSound = enumOr(getString("r_amb", null), d.ambientSound),
                ambientVolume = getFloat("r_ambvol", d.ambientVolume),
                showHeader = getBoolean("r_header", d.showHeader),
                showFooter = getBoolean("r_footer", d.showFooter),
                showClock = getBoolean("r_clock", d.showClock),
                showTimeLeft = getBoolean("r_timeleft", d.showTimeLeft),
                showImages = getBoolean("r_images", d.showImages),
                ttsRate = getFloat("r_ttsrate", d.ttsRate),
                ttsPitch = getFloat("r_ttspitch", d.ttsPitch),
                ttsVoice = getString("r_ttsvoice", d.ttsVoice) ?: "",
                ttsDialogVoice = getString("r_ttsdialog", d.ttsDialogVoice) ?: "",
                ttsDialogPitch = getFloat("r_ttsdialogpitch", d.ttsDialogPitch),
                ttsSmart = getBoolean("r_ttssmart", d.ttsSmart),
                ttsEngine = getString("r_ttsengine", d.ttsEngine) ?: "SYSTEM",
                aiVoice = getString("r_aivoice", d.aiVoice) ?: d.aiVoice,
                aiStyle = getString("r_aistyle", d.aiStyle) ?: d.aiStyle,
                geminiVoice = getString("r_gvoice", d.geminiVoice) ?: d.geminiVoice,
                geminiDialogVoice = getString("r_gdialog", d.geminiDialogVoice) ?: d.geminiDialogVoice,
                geminiTwoVoices = getBoolean("r_gtwo", d.geminiTwoVoices),
                geminiModel = getString("r_gmodel", d.geminiModel) ?: d.geminiModel,
            )
        }
    }

    private fun saveReader(s: ReaderSettings) {
        prefs.edit()
            .putString("r_font", s.fontKey)
            .putFloat("r_size", s.fontSize)
            .putInt("r_weight", s.fontWeight)
            .putFloat("r_lh", s.lineHeight)
            .putFloat("r_ls", s.letterSpacing)
            .putFloat("r_ps", s.paragraphSpacing)
            .putBoolean("r_indent", s.indent)
            .putInt("r_mh", s.marginH)
            .putInt("r_mv", s.marginV)
            .putString("r_align", s.align.name)
            .putBoolean("r_hyph", s.hyphenation)
            .putString("r_theme", s.theme.name)
            .putBoolean("r_autonight", s.autoNightTheme)
            .putString("r_nighttheme", s.nightTheme.name)
            .putString("r_mode", s.pageMode.name)
            .putString("r_anim", s.pageAnimation.name)
            .putBoolean("r_tap", s.tapZones)
            .putBoolean("r_vol", s.volumeKeys)
            .putBoolean("r_keepon", s.keepScreenOn)
            .putBoolean("r_full", s.fullscreen)
            .putFloat("r_bright", s.brightness)
            .putFloat("r_warm", s.warmFilter)
            .putBoolean("r_tex", s.paperTexture)
            .putBoolean("r_cozy", s.cozyMode)
            .putFloat("r_cozyglow", s.cozyGlow)
            .putString("r_amb", s.ambientSound.name)
            .putFloat("r_ambvol", s.ambientVolume)
            .putBoolean("r_header", s.showHeader)
            .putBoolean("r_footer", s.showFooter)
            .putBoolean("r_clock", s.showClock)
            .putBoolean("r_timeleft", s.showTimeLeft)
            .putBoolean("r_images", s.showImages)
            .putFloat("r_ttsrate", s.ttsRate)
            .putFloat("r_ttspitch", s.ttsPitch)
            .putString("r_ttsvoice", s.ttsVoice)
            .putString("r_ttsdialog", s.ttsDialogVoice)
            .putFloat("r_ttsdialogpitch", s.ttsDialogPitch)
            .putBoolean("r_ttssmart", s.ttsSmart)
            .putString("r_ttsengine", s.ttsEngine)
            .putString("r_aivoice", s.aiVoice)
            .putString("r_aistyle", s.aiStyle)
            .putString("r_gvoice", s.geminiVoice)
            .putString("r_gdialog", s.geminiDialogVoice)
            .putBoolean("r_gtwo", s.geminiTwoVoices)
            .putString("r_gmodel", s.geminiModel)
            .apply()
    }

    private fun loadApp(): AppSettings {
        val d = AppSettings()
        return with(prefs) {
            AppSettings(
                themeMode = enumOr(getString("a_theme", null), d.themeMode),
                roomAnimations = getBoolean("a_roomanim", d.roomAnimations),
                bookAnimations = getBoolean("a_bookanim", d.bookAnimations),
                onlineCovers = getBoolean("a_online", d.onlineCovers),
                autoScan = getBoolean("a_autoscan", d.autoScan),
                dailyGoalMinutes = getInt("a_goal", d.dailyGoalMinutes),
                onboarded = getBoolean("a_onboarded", d.onboarded),
                roomSounds = getBoolean("a_roomsounds", d.roomSounds),
                customFonts = (getString("a_fonts", "") ?: "").split('\n').filter { it.isNotBlank() },
                aiKey = getString("a_aikey", "") ?: "",
                geminiKey = getString("a_geminikey", "") ?: "",
                roomAmbienceInReader = getBoolean("a_roomamb", d.roomAmbienceInReader),
                hallWall = getInt("a_hallwall", d.hallWall),
                hallPattern = getString("a_hallpattern", d.hallPattern) ?: d.hallPattern,
                hallFloor = getString("a_hallfloor", d.hallFloor) ?: d.hallFloor,
                hallRunner = getInt("a_hallrunner", d.hallRunner),
                hallWainscot = getBoolean("a_hallwainscot", d.hallWainscot),
                hallCeiling = getString("a_hallceiling", d.hallCeiling) ?: d.hallCeiling,
            )
        }
    }

    private fun saveApp(s: AppSettings) {
        prefs.edit()
            .putString("a_theme", s.themeMode.name)
            .putBoolean("a_roomanim", s.roomAnimations)
            .putBoolean("a_bookanim", s.bookAnimations)
            .putBoolean("a_online", s.onlineCovers)
            .putBoolean("a_autoscan", s.autoScan)
            .putInt("a_goal", s.dailyGoalMinutes)
            .putBoolean("a_onboarded", s.onboarded)
            .putBoolean("a_roomsounds", s.roomSounds)
            .putString("a_fonts", s.customFonts.joinToString("\n"))
            .putString("a_aikey", s.aiKey)
            .putString("a_geminikey", s.geminiKey)
            .putBoolean("a_roomamb", s.roomAmbienceInReader)
            .putInt("a_hallwall", s.hallWall)
            .putString("a_hallpattern", s.hallPattern)
            .putString("a_hallfloor", s.hallFloor)
            .putInt("a_hallrunner", s.hallRunner)
            .putBoolean("a_hallwainscot", s.hallWainscot)
            .putString("a_hallceiling", s.hallCeiling)
            .apply()
    }
}
