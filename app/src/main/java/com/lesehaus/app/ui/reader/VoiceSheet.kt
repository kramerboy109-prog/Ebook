package com.lesehaus.app.ui.reader

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lesehaus.app.AppContainer
import com.lesehaus.app.data.ReaderSettings
import java.util.Locale
import kotlin.math.roundToInt

private data class VoicePreset(val label: String, val pitch: Float, val rate: Float)

private val Presets = listOf(
    VoicePreset("Tief & warm", 0.8f, 0.9f),
    VoicePreset("Ruhige Gutenachtgeschichte", 0.92f, 0.82f),
    VoicePreset("Hörbuch", 0.96f, 0.95f),
    VoicePreset("Neutral", 1f, 1f),
    VoicePreset("Hell & lebhaft", 1.18f, 1.06f),
)

@Composable
fun VoiceSheet(app: AppContainer, s: ReaderSettings, vm: ReaderViewModel, bookLanguage: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val voices by vm.tts.voices.collectAsState()
    val appSettings by app.settings.app.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var langFilter by remember { mutableStateOf("BOOK") }
    LaunchedEffect(Unit) { vm.tts.refreshVoices() }
    fun upd(block: (ReaderSettings) -> ReaderSettings) = app.settings.updateReader(block)
    val bookLang = bookLanguage.ifBlank { Locale.getDefault().language }.take(2)
    val shown = voices.filter {
        when (langFilter) {
            "BOOK" -> it.locale.language == Locale.forLanguageTag(bookLang).language
            "DE" -> it.locale.language == "de"
            else -> true
        }
    }.sortedWith(compareBy({ it.network }, { -it.quality }, { it.label }))

    ModalBottomSheet(onDismissRequest = { vm.tts.stop(); onDismiss() }, sheetState = state) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text("Vorlesestimme", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = s.ttsEngine != "AI", onClick = { upd { it.copy(ttsEngine = "SYSTEM") } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Gerätestimmen") }
                SegmentedButton(selected = s.ttsEngine == "AI", onClick = { upd { it.copy(ttsEngine = "AI") } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("KI-Hörbuchstimme") }
            }
            Spacer(Modifier.height(10.dp))
            if (s.ttsEngine != "AI") {
                // ---------- Gerätestimmen ----------
                SwitchRow("Erzählmodus", "Natürliche Pausen, eigene Stimme für wörtliche Rede, Betonung von Fragen und Ausrufen", s.ttsSmart) { v -> upd { it.copy(ttsSmart = v) } }
                Text("Klang", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Presets.forEach { p ->
                        FilterChip(
                            selected = (s.ttsPitch - p.pitch) * (s.ttsPitch - p.pitch) < 0.0004f && (s.ttsRate - p.rate) * (s.ttsRate - p.rate) < 0.0004f,
                            onClick = { upd { it.copy(ttsPitch = p.pitch, ttsRate = p.rate) }; vm.tts.preview(s.ttsVoice, p.pitch, p.rate) },
                            label = { Text(p.label) },
                        )
                    }
                }
                LabeledSlider("Stimmhöhe (tief ↔ hoch)", s.ttsPitch, 0.5f..1.6f, "%.2f".format(s.ttsPitch)) { v -> upd { it.copy(ttsPitch = (v * 100).roundToInt() / 100f) } }
                LabeledSlider("Tempo", s.ttsRate, 0.5f..2f, "%.2fx".format(s.ttsRate)) { v -> upd { it.copy(ttsRate = (v * 100).roundToInt() / 100f) } }
                if (s.ttsSmart) LabeledSlider("Dialoge höher/tiefer (wenn keine eigene Dialogstimme)", s.ttsDialogPitch, 0.7f..1.4f, "%.2f".format(s.ttsDialogPitch)) { v -> upd { it.copy(ttsDialogPitch = (v * 100).roundToInt() / 100f) } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (s.ttsSmart) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Erzähler") }
                    SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Dialoge") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    FilterChip(selected = langFilter == "BOOK", onClick = { langFilter = "BOOK" }, label = { Text("Sprache des Buchs") })
                    FilterChip(selected = langFilter == "DE", onClick = { langFilter = "DE" }, label = { Text("Deutsch") })
                    FilterChip(selected = langFilter == "ALL", onClick = { langFilter = "ALL" }, label = { Text("Alle") })
                }
                val current = if (tab == 0 || !s.ttsSmart) s.ttsVoice else s.ttsDialogVoice
                VoiceRow(
                    label = if (tab == 1 && s.ttsSmart) "Wie der Erzähler (mit anderer Tonhöhe)" else "Standardstimme des Geräts",
                    detail = null, selected = current.isBlank(),
                    onSelect = { if (tab == 0 || !s.ttsSmart) upd { it.copy(ttsVoice = "") } else upd { it.copy(ttsDialogVoice = "") } },
                    onPreview = { vm.tts.preview(if (tab == 1) s.ttsVoice else "", if (tab == 1) s.ttsPitch * s.ttsDialogPitch else s.ttsPitch, s.ttsRate) },
                )
                if (shown.isEmpty()) Text("Keine Stimmen für diese Sprache installiert.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                shown.forEach { v ->
                    val detail = listOfNotNull(
                        v.gender,
                        when { v.quality >= Voice.QUALITY_VERY_HIGH -> "sehr hohe Qualität"; v.quality >= Voice.QUALITY_HIGH -> "hohe Qualität"; else -> null },
                        if (v.network) "online" else "offline",
                    ).joinToString(" · ")
                    VoiceRow(
                        label = v.label, detail = detail, selected = current == v.name, stars = v.quality >= Voice.QUALITY_HIGH,
                        onSelect = { if (tab == 0 || !s.ttsSmart) upd { it.copy(ttsVoice = v.name) } else upd { it.copy(ttsDialogVoice = v.name) } },
                        onPreview = { vm.tts.preview(v.name, s.ttsPitch, s.ttsRate) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tipp: Die natürlichsten Stimmen erhältst du in den Android-Einstellungen unter „Sprachausgabe“ (z. B. Google-Sprachdienste → Stimmdaten installieren, Varianten mit „hoher Qualität“). Online-Stimmen klingen oft noch lebendiger.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Sprachausgabe öffnen") }
                    TextButton(onClick = { runCatching { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Stimmen installieren") }
                }
            } else {
                // ---------- KI-Stimme ----------
                Text(
                    "Eine KI-Stimme versteht, was sie vorliest: Sie betont Dialoge passend zu den Figuren, macht natürliche Pausen und klingt wie ein Hörbuchsprecher. Dafür wird der vorgelesene Text an OpenAI gesendet (eigener API-Schlüssel nötig, kostet pro Zeichen ein paar Cent).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                var key by remember { mutableStateOf(appSettings.aiKey) }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim(); app.settings.updateApp { a -> a.copy(aiKey = key) } },
                    label = { Text("OpenAI-API-Schlüssel") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Der Schlüssel bleibt nur auf diesem Gerät.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("Stimme", style = MaterialTheme.typography.titleSmall)
                AiVoices.forEach { (id, label) ->
                    VoiceRow(label = label, detail = null, selected = s.aiVoice == id, onSelect = { upd { it.copy(aiVoice = id) } }, onPreview = {
                        if (key.isBlank()) android.widget.Toast.makeText(context, "Bitte zuerst einen API-Schlüssel eintragen", android.widget.Toast.LENGTH_SHORT).show()
                        else vm.tts.previewCloud(key, id, s.aiStyle)
                    })
                }
                Spacer(Modifier.height(8.dp))
                Text("Vortragsstil", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Hörbuch" to com.lesehaus.app.data.DEFAULT_AI_STYLE,
                        "Gutenachtgeschichte" to "Lies ganz ruhig, sanft und langsam vor, wie eine Gutenachtgeschichte. Leise, warm, mit langen Pausen.",
                        "Dramatisch" to "Lies wie ein dramatischer Hörspielsprecher: spannend, mit deutlichen Stimmungswechseln, Figuren mit unterschiedlichen Stimmfarben.",
                        "Gemütlich am Kamin" to "Lies vor wie ein Großvater am Kaminfeuer: gemütlich, warm, mit einem Lächeln in der Stimme.",
                    ).forEach { (l, style) -> FilterChip(selected = s.aiStyle == style, onClick = { upd { it.copy(aiStyle = style) } }, label = { Text(l) }) }
                }
                OutlinedTextField(value = s.aiStyle, onValueChange = { v -> upd { it.copy(aiStyle = v) } }, label = { Text("Eigene Anweisung an die Stimme") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun VoiceRow(label: String, detail: String?, selected: Boolean, stars: Boolean = false, onSelect: () -> Unit, onPreview: () -> Unit) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    if (stars) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Star, null, tint = Color(0xFFE0A93B), modifier = Modifier.height(16.dp)) }
                }
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPreview) { Icon(Icons.Filled.PlayArrow, "Hörprobe") }
        }
    }
}
