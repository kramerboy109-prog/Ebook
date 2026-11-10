package com.lesehaus.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesehaus.app.app
import com.lesehaus.app.ui.theme.Fonts
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val shortFmt = SimpleDateFormat("EEEEE", Locale.GERMANY)

@Composable
fun StatsScreen(bottomBar: @Composable () -> Unit) {
    val app = LocalContext.current.app
    val days by app.db.stats().observeDays().collectAsState(initial = emptyList())
    val total by app.db.stats().observeTotal().collectAsState(initial = 0L)
    val finished by app.db.books().observeFinishedCount().collectAsState(initial = 0)
    val bookTimes by app.db.stats().observeBookTimes().collectAsState(initial = emptyList())
    val books by app.db.books().observeAll().collectAsState(initial = emptyList())
    val settings by app.settings.app.collectAsState()
    val byDay = remember(days) { days.associateBy { it.day } }
    val today = dayFmt.format(Date())
    val todayMin = ((byDay[today]?.total ?: 0L) / 60_000f)
    val goal = settings.dailyGoalMinutes
    // Lesesträhne
    val streak = remember(days) {
        val cal = Calendar.getInstance()
        var n = 0
        if ((byDay[dayFmt.format(cal.time)]?.total ?: 0L) < 60_000) cal.add(Calendar.DAY_OF_YEAR, -1)
        while ((byDay[dayFmt.format(cal.time)]?.total ?: 0L) >= 60_000) { n++; cal.add(Calendar.DAY_OF_YEAR, -1) }
        n
    }
    val last14 = remember(days) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -13)
        List(14) {
            val d = cal.time
            val key = dayFmt.format(d)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            Triple(shortFmt.format(d), (byDay[key]?.total ?: 0L) / 60_000f, key == today)
        }
    }
    val avg = remember(days) { if (days.isEmpty()) 0f else days.take(30).sumOf { it.total } / 60_000f / days.take(30).size }

    Scaffold(bottomBar = bottomBar) { pad ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text("Lesestatistik", style = MaterialTheme.typography.headlineMedium)
                    Text("Kleine Schritte, große Geschichten.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val primary = MaterialTheme.colorScheme.primary
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(110.dp)) {
                                val stroke = 12.dp.toPx()
                                drawArc(track, -90f, 360f, false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                                drawArc(primary, -90f, 360f * (todayMin / goal).coerceIn(0f, 1f), false, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${todayMin.roundToInt()}", style = TextStyle(fontFamily = Fonts.Playfair, fontSize = 30.sp))
                                Text("von $goal Min.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text(if (todayMin >= goal) "Tagesziel erreicht! ✨" else "Heute", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (streak > 0) "🔥 $streak ${if (streak == 1) "Tag" else "Tage"} in Folge gelesen" else "Lies heute eine Minute, um eine Serie zu starten.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Tagesziel: $goal Minuten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(value = goal.toFloat(), onValueChange = { v -> app.settings.updateApp { it.copy(dailyGoalMinutes = (v / 5).roundToInt() * 5) } }, valueRange = 5f..120f)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Gesamt", "${total / 3_600_000} Std. ${(total / 60_000) % 60} Min.", Modifier.weight(1f))
                    StatTile("Ausgelesen", "$finished ${if (finished == 1) "Buch" else "Bücher"}", Modifier.weight(1f))
                    StatTile("Ø pro Tag", "${avg.roundToInt()} Min.", Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Letzte 14 Tage", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        val primary = MaterialTheme.colorScheme.primary
                        val secondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
                        val goalColor = MaterialTheme.colorScheme.tertiary
                        val maxV = (last14.maxOf { it.second }).coerceAtLeast(goal.toFloat()).coerceAtLeast(1f)
                        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                            val bw = size.width / 14f
                            last14.forEachIndexed { i, (_, v, isToday) ->
                                val h = size.height * (v / maxV)
                                drawRoundRect(if (isToday) primary else secondary, Offset(i * bw + bw * 0.18f, size.height - h), Size(bw * 0.64f, h.coerceAtLeast(2f)), CornerRadius(6f, 6f))
                            }
                            val gy = size.height * (1 - goal / maxV)
                            drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                        }
                        Row(Modifier.fillMaxWidth()) {
                            last14.forEach { (label, _, _) -> Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                        }
                    }
                }
            }
            if (bookTimes.isNotEmpty()) {
                item { Text("Meistgelesen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp)) }
                val byId = books.associateBy { it.id }
                val maxT = bookTimes.maxOf { it.total }.coerceAtLeast(1)
                items(bookTimes.take(8)) { bt ->
                    val b = byId[bt.bookId] ?: return@items
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Row {
                            Text(b.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${bt.total / 60_000} Min.", style = MaterialTheme.typography.labelMedium)
                        }
                        val primary = MaterialTheme.colorScheme.primary
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                            drawRoundRect(track, cornerRadius = CornerRadius(6f))
                            drawRoundRect(primary, size = Size(size.width * bt.total / maxT, size.height), cornerRadius = CornerRadius(6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}
