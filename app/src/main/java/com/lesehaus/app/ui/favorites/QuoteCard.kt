package com.lesehaus.app.ui.favorites

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.lesehaus.app.R
import java.io.File

/** Stil der Zitatkarte. */
enum class QuoteCardStyle(val label: String, val bg1: Int, val bg2: Int, val text: Int, val accent: Int) {
    PAPER("Papier", 0xFFF8F1E3.toInt(), 0xFFE9DCC2.toInt(), 0xFF2B2420.toInt(), 0xFF8A5A33.toInt()),
    FIRELIGHT("Kaminlicht", 0xFF3A2416.toInt(), 0xFF170E09.toInt(), 0xFFF0D3A6.toInt(), 0xFFE39A52.toInt()),
    NIGHT("Nachtblau", 0xFF1E2B45.toInt(), 0xFF0C1220.toInt(), 0xFFDCE4F2.toInt(), 0xFFB9A27B.toInt()),
}

/** Zeichnet ein Zitat als 1080×1350-Bild (Instagram-Format). */
fun renderQuoteCard(context: Context, text: String, title: String, author: String, style: QuoteCardStyle): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), style.bg1, style.bg2, Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    // warmer Lichtschein
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        shader = RadialGradient(w * 0.2f, h * 1.0f, w * 0.9f, (style.accent and 0x00FFFFFF) or 0x33000000, 0, Shader.TileMode.CLAMP)
    })
    // Rahmen
    val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE; color = style.accent; strokeWidth = 3f; alpha = 170 }
    c.drawRect(54f, 54f, w - 54f, h - 54f, frame)
    c.drawRect(70f, 70f, w - 70f, h - 70f, frame.apply { strokeWidth = 1.5f })
    val garamond = runCatching { ResourcesCompat.getFont(context, R.font.ebgaramond_italic) }.getOrNull() ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    val lora = runCatching { ResourcesCompat.getFont(context, R.font.lora) }.getOrNull() ?: Typeface.SERIF
    val playfair = runCatching { ResourcesCompat.getFont(context, R.font.playfair) }.getOrNull() ?: Typeface.SERIF
    // großes Anführungszeichen
    c.drawText("“", 120f, 330f, Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = playfair; textSize = 300f; color = style.accent; alpha = 110 })
    val clean = text.trim().replace(Regex("\\s+"), " ")
    val size = when { clean.length < 120 -> 66f; clean.length < 260 -> 54f; clean.length < 500 -> 44f; else -> 36f }
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = garamond; textSize = size; color = style.text }
    val maxW = w - 260
    var shown = clean
    var layout = StaticLayout.Builder.obtain(shown, 0, shown.length, tp, maxW).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.25f).build()
    while (layout.height > h - 560 && shown.length > 40) {
        shown = shown.take((shown.length * 0.85f).toInt()).trimEnd() + " …"
        layout = StaticLayout.Builder.obtain(shown, 0, shown.length, tp, maxW).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.25f).build()
    }
    c.save()
    c.translate(130f, (h - layout.height) / 2f - 40f)
    layout.draw(c)
    c.restore()
    val by = (h + layout.height) / 2f + 40f
    val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = lora; textSize = 34f; color = style.text; alpha = 200; textAlign = Paint.Align.CENTER }
    c.drawText("❦", w / 2f, by + 10f, ap.apply { color = style.accent; alpha = 255 })
    ap.color = style.text; ap.alpha = 210
    val attribution = listOf(author, title).filter { it.isNotBlank() }.joinToString(" · ")
    if (attribution.isNotBlank()) c.drawText(attribution.take(60), w / 2f, by + 70f, ap)
    c.drawText("Benny's Library", w / 2f, h - 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = playfair; textSize = 30f; color = style.accent; textAlign = Paint.Align.CENTER })
    return bmp
}

fun shareQuoteImage(context: Context, text: String, title: String, author: String, style: QuoteCardStyle = QuoteCardStyle.PAPER) {
    val bmp = renderQuoteCard(context, text, title, author, style)
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, "zitat.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Zitat als Bild teilen"))
}
