package com.lesehaus.app.data

import com.lesehaus.app.AppContainer
import com.lesehaus.app.data.db.Annotation
import com.lesehaus.app.data.db.Book
import com.lesehaus.app.data.db.Bookmark
import com.lesehaus.app.data.db.Favorite
import com.lesehaus.app.data.db.ReadingRoom
import com.lesehaus.app.data.db.Shelf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sicherung als JSON: Zimmer, Regale, Buch-Fortschritt, Lesezeichen, Markierungen,
 * Notizen, Lieblingsstellen und Lesestatistik. Bücher werden über ihre Datei-URI zugeordnet.
 */
object Backup {
    suspend fun export(app: AppContainer): String {
        val db = app.db
        val books = db.books().all()
        val uriOf = books.associate { it.id to it.uri }
        val root = JSONObject()
        root.put("app", "BennysLibrary").put("version", 1).put("created", System.currentTimeMillis())
        root.put("rooms", JSONArray().apply {
            db.rooms().rooms().forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("type", r.type); put("wallColor", r.wallColor); put("wallPattern", r.wallPattern)
                    put("floorType", r.floorType); put("windowView", r.windowView); put("weather", r.weather); put("lightLevel", r.lightLevel.toDouble())
                    put("lightWarmth", r.lightWarmth.toDouble()); put("furniture", r.furniture); put("ambientSound", r.ambientSound); put("curtainColor", r.curtainColor); put("sortIndex", r.sortIndex)
                })
            }
        })
        root.put("shelves", JSONArray().apply {
            db.shelves().allShelves().forEach { s ->
                put(JSONObject().apply {
                    put("roomId", s.roomId); put("name", s.name); put("wood", s.wood); put("folders", s.folders); put("recursive", s.recursive)
                    put("fileTypes", s.fileTypes); put("displayMode", s.displayMode); put("sortOrder", s.sortOrder); put("sortIndex", s.sortIndex)
                })
            }
        })
        root.put("books", JSONArray().apply {
            books.forEach { b ->
                put(JSONObject().apply {
                    put("uri", b.uri); put("fileName", b.fileName); put("title", b.title); put("author", b.author); put("progress", b.progress.toDouble())
                    put("chapter", b.chapter); put("charOffset", b.charOffset); put("pdfPage", b.pdfPage); put("status", b.status); put("rating", b.rating)
                    put("readingTimeMs", b.readingTimeMs); put("lastOpenedAt", b.lastOpenedAt)
                })
            }
        })
        root.put("bookmarks", JSONArray().apply {
            db.annotations().allBookmarks().forEach { m ->
                put(JSONObject().apply { put("book", uriOf[m.bookId]); put("chapter", m.chapter); put("charOffset", m.charOffset); put("pdfPage", m.pdfPage); put("label", m.label); put("snippet", m.snippet); put("progress", m.progress.toDouble()); put("createdAt", m.createdAt) })
            }
        })
        root.put("annotations", JSONArray().apply {
            db.annotations().allAnnotations().forEach { a ->
                put(JSONObject().apply { put("book", uriOf[a.bookId]); put("chapter", a.chapter); put("start", a.start); put("end", a.end); put("text", a.text); put("color", a.color); put("style", a.style); put("note", a.note ?: JSONObject.NULL); put("pdfPage", a.pdfPage); put("createdAt", a.createdAt); put("updatedAt", a.updatedAt) })
            }
        })
        root.put("favorites", JSONArray().apply {
            db.annotations().allFavorites().forEach { f ->
                put(JSONObject().apply { put("book", f.bookId?.let { uriOf[it] } ?: JSONObject.NULL); put("bookTitle", f.bookTitle); put("bookAuthor", f.bookAuthor); put("chapter", f.chapter); put("start", f.start); put("end", f.end); put("text", f.text); put("kind", f.kind); put("pdfPage", f.pdfPage); put("collection", f.collection); put("note", f.note ?: JSONObject.NULL); put("createdAt", f.createdAt); put("timesRead", f.timesRead) })
            }
        })
        return root.toString(2)
    }

    /** Importiert eine Sicherung (ergänzt bestehende Daten). Liefert eine kurze Zusammenfassung. */
    suspend fun import(app: AppContainer, json: String): String {
        val db = app.db
        val root = JSONObject(json)
        require(root.optString("app") in setOf("BennysLibrary", "Lesehaus")) { "Keine Sicherung von Benny's Library" }
        val roomMap = HashMap<Long, Long>()
        root.optJSONArray("rooms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val newId = db.rooms().insert(
                    ReadingRoom(
                        name = o.getString("name"), type = o.getString("type"), wallColor = o.getInt("wallColor"), wallPattern = o.getString("wallPattern"),
                        floorType = o.getString("floorType"), windowView = o.getString("windowView"), weather = o.getString("weather"),
                        lightLevel = o.getDouble("lightLevel").toFloat(), lightWarmth = o.getDouble("lightWarmth").toFloat(), furniture = o.getString("furniture"),
                        ambientSound = o.getString("ambientSound"), curtainColor = o.getInt("curtainColor"), sortIndex = o.optInt("sortIndex"),
                    )
                )
                roomMap[o.getLong("id")] = newId
            }
        }
        root.optJSONArray("shelves")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val roomId = roomMap[o.getLong("roomId")] ?: continue
                db.shelves().insert(
                    Shelf(
                        roomId = roomId, name = o.getString("name"), wood = o.getString("wood"), folders = o.optString("folders"), recursive = o.optBoolean("recursive", true),
                        fileTypes = o.optString("fileTypes", DefaultFileTypes), displayMode = o.optString("displayMode", "COVERS"), sortOrder = o.optString("sortOrder", "TITLE"), sortIndex = o.optInt("sortIndex"),
                    )
                )
            }
        }
        val bookIds = HashMap<String, Long>()
        root.optJSONArray("books")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val uri = o.getString("uri")
                val existing = db.books().bookByUri(uri)
                val base = existing ?: Book(uri = uri, fileName = o.optString("fileName"), format = o.optString("fileName").substringAfterLast('.', "").lowercase(), title = o.optString("title"))
                val merged = base.copy(
                    title = o.optString("title", base.title), author = o.optString("author", base.author), progress = o.optDouble("progress", 0.0).toFloat(),
                    chapter = o.optInt("chapter"), charOffset = o.optInt("charOffset"), pdfPage = o.optInt("pdfPage"), status = o.optString("status", base.status),
                    rating = o.optInt("rating"), readingTimeMs = o.optLong("readingTimeMs"), lastOpenedAt = o.optLong("lastOpenedAt"),
                )
                val id = if (existing != null) { db.books().update(merged); existing.id } else db.books().insert(merged)
                bookIds[uri] = id
            }
        }
        var notes = 0
        root.optJSONArray("bookmarks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = bookIds[o.optString("book")] ?: continue
                db.annotations().insertBookmark(Bookmark(bookId = id, chapter = o.getInt("chapter"), charOffset = o.getInt("charOffset"), pdfPage = o.optInt("pdfPage", -1), label = o.optString("label"), snippet = o.optString("snippet"), progress = o.optDouble("progress", 0.0).toFloat(), createdAt = o.optLong("createdAt")))
            }
        }
        root.optJSONArray("annotations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = bookIds[o.optString("book")] ?: continue
                db.annotations().insertAnnotation(Annotation(bookId = id, chapter = o.getInt("chapter"), start = o.getInt("start"), end = o.getInt("end"), text = o.optString("text"), color = o.getInt("color"), style = o.optString("style", "HIGHLIGHT"), note = if (o.isNull("note")) null else o.optString("note"), pdfPage = o.optInt("pdfPage", -1), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt")))
                notes++
            }
        }
        var favs = 0
        root.optJSONArray("favorites")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = if (o.isNull("book")) null else bookIds[o.optString("book")]
                db.annotations().insertFavorite(Favorite(bookId = id, bookTitle = o.optString("bookTitle"), bookAuthor = o.optString("bookAuthor"), chapter = o.optInt("chapter"), start = o.optInt("start"), end = o.optInt("end"), text = o.optString("text"), kind = o.optString("kind", "SELECTION"), pdfPage = o.optInt("pdfPage", -1), collection = o.optString("collection", "Lieblingsstellen"), note = if (o.isNull("note")) null else o.optString("note"), createdAt = o.optLong("createdAt"), timesRead = o.optInt("timesRead")))
                favs++
            }
        }
        return "${roomMap.size} Zimmer, ${bookIds.size} Bücher, $notes Notizen/Markierungen und $favs Lieblingsstellen importiert."
    }
}
