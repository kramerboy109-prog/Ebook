package com.lesehaus.app.data.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineCover(val url: String, val title: String, val author: String, val source: String)

class CoverService(private val context: Context) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Speichert Cover-Bytes (verkleinert) und liefert Pfad + Durchschnittsfarbe. */
    fun store(bookId: Long, bytes: ByteArray): Pair<String, Int>? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / sample > 900 || opts.outHeight / sample > 1300) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // sehr kleine Platzhalterbilder (z. B. 1x1 GIF von Google Books) ignorieren
        if (bmp.width < 40 || bmp.height < 40) return null
        dir.listFiles { f -> f.name.startsWith("${bookId}_") }?.forEach { it.delete() }
        val f = File(dir, "${bookId}_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val color = averageColor(bmp)
        bmp.recycle()
        f.absolutePath to color
    }.getOrNull()

    fun storeFromUri(bookId: Long, uri: Uri): Pair<String, Int>? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        store(bookId, bytes)
    }.getOrNull()

    fun delete(bookId: Long) {
        dir.listFiles { f -> f.name.startsWith("${bookId}_") }?.forEach { it.delete() }
    }

    private fun averageColor(bmp: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bmp, 8, 12, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (x in 0 until small.width) for (y in 0 until small.height) {
            val c = small.getPixel(x, y)
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        if (small != bmp) small.recycle()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private fun get(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "BennysLibrary-Android/1.0")
            if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Sucht Cover bei Open Library und Google Books. */
    suspend fun search(title: String, author: String): List<OnlineCover> = withContext(Dispatchers.IO) {
        val results = ArrayList<OnlineCover>()
        val cleanTitle = title.replace(Regex("[_\\-]+"), " ").replace(Regex("\\(.*?\\)"), "").trim()
        runCatching {
            val q = StringBuilder("https://openlibrary.org/search.json?limit=12&fields=title,author_name,cover_i&title=").append(enc(cleanTitle))
            if (author.isNotBlank()) q.append("&author=").append(enc(author.split(',').first().trim()))
            val json = get(q.toString())?.let { JSONObject(String(it)) }
            val docs = json?.optJSONArray("docs")
            if (docs != null) for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val id = d.optLong("cover_i", -1)
                if (id > 0) results.add(
                    OnlineCover(
                        "https://covers.openlibrary.org/b/id/$id-L.jpg",
                        d.optString("title"),
                        d.optJSONArray("author_name")?.optString(0) ?: "",
                        "Open Library",
                    )
                )
            }
        }
        runCatching {
            var q = "intitle:" + cleanTitle
            if (author.isNotBlank()) q += " inauthor:" + author.split(',').first().trim()
            val json = get("https://www.googleapis.com/books/v1/volumes?maxResults=12&q=" + enc(q))?.let { JSONObject(String(it)) }
            val items = json?.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) {
                val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
                val links = info.optJSONObject("imageLinks") ?: continue
                val url = (links.optString("extraLarge").ifEmpty { links.optString("large") }.ifEmpty { links.optString("medium") }
                    .ifEmpty { links.optString("thumbnail") })
                    .replace("http://", "https://").replace("&edge=curl", "").replace("zoom=1", "zoom=2")
                if (url.isNotEmpty()) results.add(
                    OnlineCover(url, info.optString("title"), info.optJSONArray("authors")?.optString(0) ?: "", "Google Books")
                )
            }
        }
        results.distinctBy { it.url }
    }

    suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) { get(url) }

    fun encodeJpeg(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}
