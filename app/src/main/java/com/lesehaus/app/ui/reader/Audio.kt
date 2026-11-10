package com.lesehaus.app.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

class TtsController(context: Context) {
    private var ready = false
    private var pending: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) pending?.invoke()
        pending = null
    }

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private val _range = MutableStateFlow<SpokenRange?>(null)
    val range: StateFlow<SpokenRange?> = _range.asStateFlow()
    private val _sleepAt = MutableStateFlow(0L)
    val sleepAt: StateFlow<Long> = _sleepAt.asStateFlow()

    /** Wird aufgerufen, wenn das Kapitel fertig vorgelesen ist. */
    var onChapterDone: ((Int) -> Unit)? = null

    private val sentences = HashMap<String, SpokenRange>()
    private var lastId: String? = null

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
    }

    fun setLanguage(lang: String) {
        val locale = if (lang.isNotBlank()) Locale.forLanguageTag(lang) else Locale.getDefault()
        runCatching {
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale
        }
    }

    fun setRate(rate: Float, pitch: Float) {
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
    }

    /** Liest ein Kapitel ab [from] satzweise vor. */
    fun speak(chapter: Int, text: String, from: Int) {
        if (!ready) { pending = { speak(chapter, text, from) }; return }
        tts.stop()
        sentences.clear()
        val it = BreakIterator.getSentenceInstance()
        it.setText(text)
        var start = it.preceding((from + 1).coerceIn(1, text.length)).let { p -> if (p == BreakIterator.DONE) 0 else p }
        var end = it.following(start)
        var n = 0
        while (end != BreakIterator.DONE && start < text.length) {
            val raw = text.substring(start, end)
            val clean = raw.replace('​', ' ').replace('￼', ' ')
            if (clean.isNotBlank()) {
                val id = "s_${chapter}_${n++}"
                sentences[id] = SpokenRange(chapter, start, end)
                tts.speak(clean, TextToSpeech.QUEUE_ADD, null, id)
                lastId = id
            }
            start = end
            end = it.next()
        }
        if (n == 0) onChapterDone?.invoke(chapter)
        _speaking.value = true
    }

    fun stop() {
        tts.stop()
        _speaking.value = false
        _range.value = null
    }

    fun setSleepTimer(minutes: Int) {
        _sleepAt.value = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

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
