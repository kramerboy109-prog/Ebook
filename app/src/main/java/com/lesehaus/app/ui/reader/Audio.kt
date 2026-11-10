package com.lesehaus.app.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.lesehaus.app.data.ReaderSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import com.lesehaus.app.data.AmbientSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

// ------------------------------------------------------------------------------------------------
// Vorlesen
// ------------------------------------------------------------------------------------------------
data class SpokenRange(val chapter: Int, val start: Int, val end: Int, val wordStart: Int = -1, val wordEnd: Int = -1)

/** Eine auswählbare Gerätestimme. */
data class VoiceInfo(
    val name: String,
    val label: String,
    val locale: Locale,
    val quality: Int,
    val network: Boolean,
    val gender: String?,
)

/** Absatz eines Kapitels für den Erzählmodus. */
data class NarrationParagraph(val start: Int, val end: Int, val heading: Boolean)

internal data class Segment(val start: Int, val end: Int, val dialog: Boolean)

/** Teilt einen Satz in Erzähl- und Dialogteile (wörtliche Rede in »…«, „…“, "…"). */
internal object DialogSplitter {
    private val pairs = mapOf('»' to '«', '«' to '»', '„' to '“', '“' to '”', '"' to '"', '‚' to '‘')

    /** Liefert Segmente; [inQuote] trägt den Zustand über Satzgrenzen hinweg. */
    fun split(text: String, start: Int, end: Int, state: BooleanArray, closer: CharArray): List<Segment> {
        val out = ArrayList<Segment>()
        var segStart = start
        var i = start
        while (i < end) {
            val c = text[i]
            if (!state[0] && pairs.containsKey(c) && !(c == '‚' && i > 0 && text[i - 1].isLetter())) {
                if (i > segStart) out += Segment(segStart, i, false)
                state[0] = true; closer[0] = pairs[c]!!; segStart = i
            } else if (state[0] && (c == closer[0] || (closer[0] == '“' && c == '"'))) {
                out += Segment(segStart, i + 1, true)
                state[0] = false; segStart = i + 1
            }
            i++
        }
        if (segStart < end) out += Segment(segStart, end, state[0])
        return out.filter { s -> text.substring(s.start, s.end).any { it.isLetterOrDigit() } }
    }
}

class TtsController(context: Context) {
    private val appContext = context.applicationContext
    private var ready = false
    private var pending: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) { refreshVoices(); pending?.invoke() }
        pending = null
    }

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private val _range = MutableStateFlow<SpokenRange?>(null)
    val range: StateFlow<SpokenRange?> = _range.asStateFlow()
    private val _sleepAt = MutableStateFlow(0L)
    val sleepAt: StateFlow<Long> = _sleepAt.asStateFlow()
    private val _voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    val voices: StateFlow<List<VoiceInfo>> = _voices.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _engineLabel = MutableStateFlow("")
    val engineLabel: StateFlow<String> = _engineLabel.asStateFlow()

    /** Wird aufgerufen, wenn das Kapitel fertig vorgelesen ist. */
    var onChapterDone: ((Int) -> Unit)? = null

    private val sentences = HashMap<String, SpokenRange>()
    private var lastId: String? = null
    private val cloud = CloudNarrator(appContext)

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                sentences[id]?.let { _range.value = it }
            }

            override fun onRangeStart(id: String, start: Int, end: Int, frame: Int) {
                val s = sentences[id] ?: return
                _range.value = s.copy(wordStart = s.start + start, wordEnd = s.start + end)
            }

            override fun onDone(id: String) {
                if (_sleepAt.value in 1 until System.currentTimeMillis()) {
                    stop(); _sleepAt.value = 0L; return
                }
                if (id == lastId) {
                    val ch = sentences[id]?.chapter ?: return
                    onChapterDone?.invoke(ch)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String) {
                _speaking.value = false
            }
        })
        cloud.onRange = { _range.value = it }
        cloud.onChapterDone = { ch ->
            if (_sleepAt.value in 1 until System.currentTimeMillis()) { stop(); _sleepAt.value = 0L } else onChapterDone?.invoke(ch)
        }
        cloud.onError = { msg -> _error.value = msg; _speaking.value = false }
        cloud.onQuota = { ch, pos ->
            // Kontingent erschöpft: nahtlos mit der Gerätestimme weiterlesen
            _error.value = "Das kostenlose KI-Kontingent für heute ist aufgebraucht – ich lese mit der Gerätestimme weiter."
            val st = lastSettings
            if (st != null) speakSystem(ch, lastText, pos, lastParagraphs, st)
        }
    }

    private var language: Locale = Locale.getDefault()

    fun setLanguage(lang: String) {
        language = if (lang.isNotBlank()) Locale.forLanguageTag(lang) else Locale.getDefault()
        runCatching { if (tts.isLanguageAvailable(language) >= TextToSpeech.LANG_AVAILABLE) tts.language = language }
    }

    fun clearError() { _error.value = null }

    fun refreshVoices() {
        val list = runCatching { tts.voices?.toList() }.getOrNull().orEmpty()
            .filter { v -> v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
        val counters = HashMap<String, Int>()
        _voices.value = list.map { v ->
            val n = v.name.lowercase()
            val gender = when {
                n.contains("female") || Regex("[-_]f\\d{2}|smtf\\d").containsMatchIn(n) -> "weiblich"
                n.contains("male") || Regex("[-_]m\\d{2}|smtm\\d").containsMatchIn(n) -> "männlich"
                else -> null
            }
            val key = v.locale.toLanguageTag()
            val num = (counters[key] ?: 0) + 1
            counters[key] = num
            val country = v.locale.getDisplayCountry(Locale.GERMAN).ifBlank { v.locale.getDisplayLanguage(Locale.GERMAN) }
            VoiceInfo(
                name = v.name,
                label = "Stimme $num" + (if (country.isNotBlank()) " ($country)" else ""),
                locale = v.locale,
                quality = v.quality,
                network = v.isNetworkConnectionRequired,
                gender = gender,
            )
        }
    }

    private fun voiceByName(name: String): Voice? = runCatching { tts.voices?.firstOrNull { it.name == name } }.getOrNull()

    private fun apply(voice: Voice?, pitch: Float, rate: Float) {
        if (voice != null) tts.voice = voice else runCatching { tts.language = language }
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)
    }

    /** Spielt eine kurze Hörprobe. */
    fun preview(voiceName: String, pitch: Float, rate: Float, text: String = "Es war einmal ein kleines Haus voller Bücher, in dem es immer nach Tee und Kaminholz duftete.") {
        if (!ready) { pending = { preview(voiceName, pitch, rate, text) }; return }
        stop()
        apply(voiceByName(voiceName), pitch, rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    fun previewCloud(cfg: CloudConfig) {
        stop()
        cloud.preview(cfg)
    }

    /** Liest ein Kapitel ab [from] vor – je nach Einstellung mit Gerätestimme oder KI-Stimme. */
    private var lastSettings: ReaderSettings? = null
    private var lastParagraphs: List<NarrationParagraph> = emptyList()
    private var lastText: String = ""

    fun speak(chapter: Int, text: String, from: Int, paragraphs: List<NarrationParagraph>, s: ReaderSettings, keys: AiKeys) {
        lastSettings = s; lastParagraphs = paragraphs; lastText = text
        val cfg = when {
            s.ttsEngine == "GEMINI" && keys.gemini.isNotBlank() -> CloudConfig(CloudProvider.GEMINI, keys.gemini, s.geminiVoice, s.geminiDialogVoice, s.geminiTwoVoices, s.aiStyle, s.geminiModel)
            s.ttsEngine == "AI" && keys.openAi.isNotBlank() -> CloudConfig(CloudProvider.OPENAI, keys.openAi, s.aiVoice, s.aiVoice, false, s.aiStyle, "gpt-4o-mini-tts")
            else -> null
        }
        if (cfg != null) {
            tts.stop()
            _engineLabel.value = if (cfg.provider == CloudProvider.GEMINI) "KI-Stimme (Gemini)" else "KI-Stimme (OpenAI)"
            _speaking.value = true
            cloud.speak(chapter, text, from, paragraphs, cfg)
            return
        }
        speakSystem(chapter, text, from, paragraphs, s)
    }

    private fun speakSystem(chapter: Int, text: String, from: Int, paragraphs: List<NarrationParagraph>, s: ReaderSettings) {
        if (!ready) { pending = { speakSystem(chapter, text, from, paragraphs, s) }; return }
        cloud.stop()
        _engineLabel.value = "Gerätestimme"
        tts.stop()
        sentences.clear()
        val narrator = voiceByName(s.ttsVoice)
        val dialogVoice = voiceByName(s.ttsDialogVoice) ?: narrator
        val paras = paragraphs.filter { it.end > from }.ifEmpty { listOf(NarrationParagraph(from, text.length, false)) }
        var n = 0
        val it = BreakIterator.getSentenceInstance(language)
        for (p in paras) {
            val pStart = maxOf(p.start, from)
            if (pStart >= p.end) continue
            it.setText(text.substring(0, p.end))
            var start = if (pStart == p.start) p.start else it.preceding((pStart + 1).coerceIn(1, p.end)).let { b -> if (b == BreakIterator.DONE || b < p.start) p.start else b }
            val quoteState = BooleanArray(1)
            val closer = CharArray(1)
            while (start < p.end) {
                var end = it.following(start)
                if (end == BreakIterator.DONE || end > p.end) end = p.end
                val sentence = text.substring(start, end)
                val trimmed = sentence.trim()
                val segments = if (s.ttsSmart) DialogSplitter.split(text, start, end, quoteState, closer) else listOf(Segment(start, end, false))
                for (seg in segments) {
                    val raw = text.substring(seg.start, seg.end).replace('\u200B', ' ').replace('\uFFFC', ' ')
                    if (raw.isBlank()) continue
                    var pitch = s.ttsPitch
                    var rate = s.ttsRate
                    if (s.ttsSmart) {
                        if (p.heading) rate *= 0.88f
                        if (seg.dialog) pitch *= s.ttsDialogPitch.takeIf { s.ttsDialogVoice.isBlank() } ?: 1f
                        val tail = raw.trimEnd().lastOrNull { !it.isWhitespace() && it !in "«»„“”\"‚‘)" }
                        if (tail == '?') pitch *= 1.05f
                        if (tail == '!') { pitch *= 1.04f; rate *= 1.05f }
                    }
                    apply(if (seg.dialog && s.ttsSmart) dialogVoice else narrator, pitch, rate)
                    val id = "s_${chapter}_${n++}"
                    sentences[id] = SpokenRange(chapter, seg.start, seg.end)
                    tts.speak(raw, TextToSpeech.QUEUE_ADD, null, id)
                    lastId = id
                    if (s.ttsSmart && raw.trimEnd().endsWith("…")) tts.playSilentUtterance(350, TextToSpeech.QUEUE_ADD, "p_${n}")
                }
                if (s.ttsSmart && trimmed.isNotEmpty() && end < p.end) tts.playSilentUtterance(120, TextToSpeech.QUEUE_ADD, "sp_$n")
                start = end
            }
            if (s.ttsSmart) tts.playSilentUtterance(if (p.heading) 800 else 420, TextToSpeech.QUEUE_ADD, "pp_$n")
        }
        if (n == 0) onChapterDone?.invoke(chapter)
        _speaking.value = true
    }

    fun stop() {
        tts.stop()
        cloud.stop()
        _speaking.value = false
        _range.value = null
    }

    fun setSleepTimer(minutes: Int) {
        _sleepAt.value = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        cloud.stop()
    }
}

data class AiKeys(val openAi: String, val gemini: String)

enum class CloudProvider { OPENAI, GEMINI }

data class CloudConfig(
    val provider: CloudProvider,
    val key: String,
    val voice: String,
    val dialogVoice: String,
    val twoVoices: Boolean,
    val style: String,
    val model: String,
)

class QuotaException : Exception("Kontingent aufgebraucht")

/**
 * KI-Stimmen: Google Gemini (kostenloses Kontingent, versteht Regieanweisungen, zwei Stimmen
 * für Erzähler und Figuren) oder OpenAI. Liest absatzweise und lädt im Voraus.
 */
class CloudNarrator(private val context: Context) {
    var onRange: ((SpokenRange) -> Unit)? = null
    var onChapterDone: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onQuota: ((Int, Int) -> Unit)? = null

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var job: kotlinx.coroutines.Job? = null
    private var player: android.media.MediaPlayer? = null

    private data class Chunk(val start: Int, val end: Int, val text: String)

    private fun chunks(text: String, from: Int, paragraphs: List<NarrationParagraph>, maxLen: Int): List<Chunk> {
        val out = ArrayList<Chunk>()
        var curStart = -1
        var curEnd = -1
        val sb = StringBuilder()
        for (p in paragraphs.filter { it.end > from }.ifEmpty { listOf(NarrationParagraph(from, text.length, false)) }) {
            val s = maxOf(p.start, from)
            val t = text.substring(s, p.end).replace('\u200B', ' ').replace('\uFFFC', ' ').trim()
            if (t.isEmpty()) continue
            if (sb.isNotEmpty() && sb.length + t.length > maxLen) {
                out += Chunk(curStart, curEnd, sb.toString()); sb.setLength(0); curStart = -1
            }
            if (curStart < 0) curStart = s
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(t)
            curEnd = p.end
            if (sb.length > 3500) { out += Chunk(curStart, curEnd, sb.toString().take(3900)); sb.setLength(0); curStart = -1 }
        }
        if (sb.isNotEmpty()) out += Chunk(curStart, curEnd, sb.toString())
        return out
    }

    /** Wandelt Text in ein „Drehbuch“ für zwei Stimmen (Erzähler/Figur). */
    private fun script(text: String): String {
        val sb = StringBuilder()
        text.split("\n\n").forEach { para ->
            val segs = DialogSplitter.split(para, 0, para.length, BooleanArray(1), CharArray(1))
            segs.forEach { seg ->
                val t = para.substring(seg.start, seg.end).trim().trim('»', '«', '„', '“', '”', '"').trim()
                if (t.isNotEmpty()) sb.append(if (seg.dialog) "Figur: " else "Erzähler: ").append(t).append('\n')
            }
        }
        return sb.toString()
    }

    private fun post(url: String, headers: Map<String, String>, body: String): ByteArray {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 429) throw QuotaException()
            if (code !in 200..299) {
                val err = conn.errorStream?.use { String(it.readBytes()) } ?: ""
                val msg = runCatching { org.json.JSONObject(err).getJSONObject("error").getString("message") }.getOrNull()
                throw IllegalStateException(if (code == 401 || code == 403) "API-Schlüssel ungültig oder nicht freigeschaltet" else "KI-Stimme nicht erreichbar ($code${msg?.let { ": " + it.take(160) } ?: ""})")
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun request(cfg: CloudConfig, input: String): java.io.File {
        val dir = java.io.File(context.cacheDir, "narration").apply { mkdirs() }
        return when (cfg.provider) {
            CloudProvider.OPENAI -> {
                val body = org.json.JSONObject()
                    .put("model", "gpt-4o-mini-tts").put("voice", cfg.voice).put("input", input)
                    .put("instructions", cfg.style).put("response_format", "mp3")
                val bytes = post("https://api.openai.com/v1/audio/speech", mapOf("Authorization" to "Bearer ${cfg.key}"), body.toString())
                java.io.File(dir, "n_${System.nanoTime()}.mp3").apply { writeBytes(bytes) }
            }
            CloudProvider.GEMINI -> {
                val two = cfg.twoVoices && input.any { it in "»«„“\"" }
                val prompt = if (two) {
                    "${cfg.style}\nLies das folgende Hörbuch-Drehbuch auf Deutsch vor. Der Erzähler erzählt, die Figur spricht die wörtliche Rede – passe ihre Stimmung dem Inhalt an. Lies die Sprechernamen nicht mit.\n\n" + script(input)
                } else "${cfg.style}\nLies den folgenden Text auf Deutsch vor:\n\n$input"
                fun voice(name: String) = org.json.JSONObject().put("prebuiltVoiceConfig", org.json.JSONObject().put("voiceName", name))
                val speech = if (two) org.json.JSONObject().put(
                    "multiSpeakerVoiceConfig", org.json.JSONObject().put(
                        "speakerVoiceConfigs", org.json.JSONArray()
                            .put(org.json.JSONObject().put("speaker", "Erzähler").put("voiceConfig", voice(cfg.voice)))
                            .put(org.json.JSONObject().put("speaker", "Figur").put("voiceConfig", voice(cfg.dialogVoice)))
                    )
                ) else org.json.JSONObject().put("voiceConfig", voice(cfg.voice))
                val body = org.json.JSONObject()
                    .put("contents", org.json.JSONArray().put(org.json.JSONObject().put("parts", org.json.JSONArray().put(org.json.JSONObject().put("text", prompt)))))
                    .put("generationConfig", org.json.JSONObject().put("responseModalities", org.json.JSONArray().put("AUDIO")).put("speechConfig", speech))
                val resp = org.json.JSONObject(String(post("https://generativelanguage.googleapis.com/v1beta/models/${cfg.model}:generateContent", mapOf("x-goog-api-key" to cfg.key), body.toString())))
                val inline = resp.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData")
                val pcm = android.util.Base64.decode(inline.getString("data"), android.util.Base64.DEFAULT)
                val rate = Regex("rate=(\\d+)").find(inline.optString("mimeType"))?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                java.io.File(dir, "n_${System.nanoTime()}.wav").apply { writeBytes(wav(pcm, rate)) }
            }
        }
    }

    /** Verpackt 16-Bit-PCM (mono) in eine WAV-Datei. */
    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun int(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); int(36 + pcm.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(1); int(rate); int(rate * 2); short(2); short(16)
        out.write("data".toByteArray()); int(pcm.size); out.write(pcm)
        return out.toByteArray()
    }

    private suspend fun play(file: java.io.File) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
        val mp = android.media.MediaPlayer()
        player = mp
        try {
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { file.delete(); if (cont.isActive) cont.resumeWith(Result.success(Unit)) }
            mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resumeWith(Result.success(Unit)); true }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWith(Result.failure(e))
        }
        cont.invokeOnCancellation { runCatching { mp.stop(); mp.release() } }
    }

    fun speak(chapter: Int, text: String, from: Int, paragraphs: List<NarrationParagraph>, cfg: CloudConfig) {
        stop()
        // Gemini: größere Abschnitte, um das kostenlose Kontingent zu schonen
        val list = chunks(text, from, paragraphs, if (cfg.provider == CloudProvider.GEMINI) 2400 else 900)
        job = scope.launch {
            var current = from
            try {
                val io = kotlinx.coroutines.Dispatchers.IO
                val ahead = if (cfg.provider == CloudProvider.GEMINI) 2 else 3
                val pending = ArrayDeque<kotlinx.coroutines.Deferred<java.io.File>>()
                var next = 0
                fun fill() { while (pending.size < ahead && next < list.size) { val c = list[next++]; pending.addLast(async(io) { request(cfg, c.text) }) } }
                fill()
                var i = 0
                while (pending.isNotEmpty()) {
                    val c = list[i]
                    current = c.start
                    val file = pending.removeFirst().await()
                    fill()
                    i++
                    onRange?.invoke(SpokenRange(chapter, c.start, c.end))
                    play(file)
                    runCatching { player?.release() }
                }
                onChapterDone?.invoke(chapter)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: QuotaException) {
                onQuota?.invoke(chapter, current)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "KI-Stimme konnte nicht geladen werden")
            }
        }
    }

    fun preview(cfg: CloudConfig) {
        stop()
        job = scope.launch {
            try {
                val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    request(cfg, "»Was für ein gemütlicher Abend«, sagte sie leise und zog die Decke enger um sich. Draußen prasselte der Regen gegen das Fenster.")
                }
                play(f)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: QuotaException) {
                onError?.invoke("Kostenloses Kontingent gerade aufgebraucht – bitte später erneut versuchen.")
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "Hörprobe fehlgeschlagen")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}

/** Gemini-Stimmen (kostenloses Kontingent) mit Beschreibung. */
val GeminiVoices = listOf(
    "Charon" to "Charon – tief, ruhig, männlich",
    "Algieba" to "Algieba – samtig, männlich",
    "Rasalgethi" to "Rasalgethi – erzählerisch, männlich",
    "Orus" to "Orus – fest, männlich",
    "Iapetus" to "Iapetus – klar, männlich",
    "Algenib" to "Algenib – rau, männlich",
    "Enceladus" to "Enceladus – hauchig, männlich",
    "Puck" to "Puck – fröhlich, männlich",
    "Fenrir" to "Fenrir – lebhaft, männlich",
    "Achird" to "Achird – freundlich, männlich",
    "Kore" to "Kore – klar, fest, weiblich",
    "Sulafat" to "Sulafat – warm, weiblich",
    "Achernar" to "Achernar – weich, weiblich",
    "Despina" to "Despina – sanft, weiblich",
    "Vindemiatrix" to "Vindemiatrix – sanftmütig, weiblich",
    "Gacrux" to "Gacrux – reif, erwachsen, weiblich",
    "Aoede" to "Aoede – luftig, leicht, weiblich",
    "Leda" to "Leda – jugendlich, weiblich",
    "Zephyr" to "Zephyr – hell, weiblich",
    "Callirrhoe" to "Callirrhoe – entspannt, weiblich",
)

/** Verfügbare KI-Stimmen mit kurzer Beschreibung. */
val AiVoices = listOf(
    "coral" to "Coral – warm, weiblich",
    "nova" to "Nova – hell, freundlich, weiblich",
    "shimmer" to "Shimmer – sanft, weiblich",
    "sage" to "Sage – ruhig, ausgeglichen",
    "fable" to "Fable – erzählerisch",
    "ballad" to "Ballad – weich, männlich",
    "ash" to "Ash – warm, männlich",
    "onyx" to "Onyx – tief, männlich",
    "echo" to "Echo – klar, männlich",
    "verse" to "Verse – ausdrucksstark",
    "alloy" to "Alloy – neutral",
)

// ------------------------------------------------------------------------------------------------
// Ambiente-Klänge (prozedural erzeugt)
// ------------------------------------------------------------------------------------------------
class AmbientPlayer {
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var volume = 0.5f
    @Volatile private var sound: AmbientSound = AmbientSound.NONE

    fun play(s: AmbientSound, vol: Float) {
        volume = vol
        if (s == sound && running) return
        stop()
        sound = s
        if (s == AmbientSound.NONE) return
        running = true
        thread = Thread({ loop(s) }, "ambient").apply { isDaemon = true; start() }
    }

    fun setVolume(v: Float) { volume = v }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
        sound = AmbientSound.NONE
    }

    private fun loop(s: AmbientSound) {
        val rate = 22050
        val bufSize = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val gen = Generator(s, rate)
        val buf = ShortArray(1024)
        try {
            track.play()
            var fade = 0f
            while (running) {
                for (i in buf.indices) {
                    fade = (fade + 1f / (rate * 2f)).coerceAtMost(1f)
                    val v = gen.next() * volume * fade
                    buf[i] = (v.coerceIn(-1f, 1f) * 32000).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private class Generator(val s: AmbientSound, val rate: Int) {
        private val rnd = Random(System.nanoTime())
        private var t = 0L
        // Rauschfilter
        private var brown = 0f
        private var pinkB0 = 0f; private var pinkB1 = 0f; private var pinkB2 = 0f
        private var lp = 0f
        private var lp2 = 0f
        // Ereignisse (Knistern, Tropfen, Ticks, Noten)
        private val events = ArrayList<FloatArray>() // [restSamples, amp, decay, freq, phase, kind]
        private var nextEvent = 0
        private val delay = FloatArray(rate / 3)
        private var delayPos = 0

        private fun white() = rnd.nextFloat() * 2f - 1f
        private fun pink(): Float {
            val w = white()
            pinkB0 = 0.99765f * pinkB0 + w * 0.0990460f
            pinkB1 = 0.96300f * pinkB1 + w * 0.2965164f
            pinkB2 = 0.57000f * pinkB2 + w * 1.0526913f
            return (pinkB0 + pinkB1 + pinkB2 + w * 0.1848f) * 0.11f
        }
        private fun brownNoise(): Float {
            brown = (brown + white() * 0.02f) / 1.02f
            return brown * 3.2f
        }

        fun next(): Float {
            t++
            val sec = t / rate.toFloat()
            var out = 0f
            when (s) {
                AmbientSound.FIRE -> {
                    out += brownNoise() * 0.55f
                    lp += 0.08f * (pink() - lp)
                    out += lp * 0.35f * (0.7f + 0.3f * sin(sec * 0.7f))
                    if (--nextEvent <= 0) {
                        events.add(floatArrayOf(rate * 0.02f, 0.4f + rnd.nextFloat() * 0.6f, 0.9f + rnd.nextFloat() * 0.08f, 0f, 0f, 0f))
                        nextEvent = (rate * (0.03f + rnd.nextFloat() * (if (rnd.nextFloat() < 0.2f) 0.6f else 0.15f))).toInt()
                    }
                }
                AmbientSound.RAIN, AmbientSound.STORM -> {
                    lp += 0.35f * (pink() - lp)
                    out += lp * 0.9f
                    if (--nextEvent <= 0) {
                        events.add(floatArrayOf(rate * 0.012f, 0.15f + rnd.nextFloat() * 0.3f, 0.82f, 0f, 0f, 0f))
                        nextEvent = (rate * rnd.nextFloat() * 0.03f).toInt()
                    }
                    if (s == AmbientSound.STORM) {
                        // fernes Grollen
                        lp2 += 0.004f * (brownNoise() - lp2)
                        val env = (sin(sec * 0.13f) * sin(sec * 0.31f)).coerceAtLeast(0f)
                        out += lp2 * 9f * env
                    }
                }
                AmbientSound.WIND -> {
                    val cutoff = 0.01f + 0.03f * (0.5f + 0.5f * sin(sec * 0.21f) * sin(sec * 0.07f + 1f))
                    lp += cutoff * (white() - lp)
                    lp2 += cutoff * (lp - lp2)
                    out += lp2 * 6f
                }
                AmbientSound.WAVES -> {
                    lp += 0.06f * (pink() - lp)
                    val env = 0.25f + 0.75f * ((sin(sec * 2 * PI.toFloat() / 9f) + 1f) / 2f).let { it * it }
                    out += lp * 2.2f * env
                }
                AmbientSound.CLOCK -> {
                    if (--nextEvent <= 0) {
                        events.add(floatArrayOf(rate * 0.04f, 0.5f, 0.0f, if ((t / rate) % 2 == 0L) 2400f else 2000f, 0f, 1f))
                        nextEvent = rate
                    }
                    out += brownNoise() * 0.05f
                }
                AmbientSound.PIANO -> {
                    if (--nextEvent <= 0) {
                        val scale = intArrayOf(0, 2, 4, 7, 9, 12, 14, 16, 19)
                        val base = 220f * Math.pow(2.0, ((listOf(0, 5, 7, 3).random(rnd)) / 12.0)).toFloat()
                        val note = scale[rnd.nextInt(scale.size)]
                        val f = base * Math.pow(2.0, note / 12.0).toFloat()
                        events.add(floatArrayOf(rate * 3.5f, 0.22f, 0f, f, 0f, 2f))
                        if (rnd.nextFloat() < 0.35f) events.add(floatArrayOf(rate * 4f, 0.12f, 0f, f / 2f, 0f, 2f))
                        nextEvent = (rate * (0.6f + rnd.nextFloat() * 1.6f)).toInt()
                    }
                }
                AmbientSound.NONE -> Unit
            }
            // Ereignisse mischen
            var i = 0
            while (i < events.size) {
                val e = events[i]
                when (e[5]) {
                    0f -> { // Knacken / Tropfen: gefiltertes Rauschen mit Abklingen
                        out += white() * e[1]
                        e[1] *= e[2]
                    }
                    1f -> { // Tick
                        e[4] += e[3] / rate
                        out += sin(e[4] * 2 * PI.toFloat()) * e[1]
                        e[1] *= 0.9985f
                    }
                    2f -> { // Klavier-Ton (additiv mit Obertönen)
                        e[4] += 1f / rate
                        val ph = e[4] * e[3] * 2 * PI.toFloat()
                        val env = exp(-e[4] * 1.6f) * (e[4] * 300f).coerceAtMost(1f)
                        out += (sin(ph) + 0.4f * sin(ph * 2f) + 0.15f * sin(ph * 3f) + 0.05f * sin(ph * 4.01f)) * e[1] * env
                    }
                }
                e[0] -= 1f
                if (e[0] <= 0f) events.removeAt(i) else i++
            }
            if (s == AmbientSound.PIANO) {
                // etwas Hall
                val d = delay[delayPos]
                delay[delayPos] = out + d * 0.45f
                delayPos = (delayPos + 1) % delay.size
                out += d * 0.35f
            }
            return out * 0.8f
        }
    }
}
