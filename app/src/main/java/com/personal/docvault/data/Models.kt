package com.personal.docvault.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

data class Attachment(
    val blobId: String,
    /** "image" is viewable in the app; "file" is an original such as a PDF. */
    val kind: String,
    val name: String,
    val mime: String,
)

data class CustomField(val label: String, val value: String)

/** A family member. "Me" always exists and can be renamed but not removed. */
data class Profile(
    val id: String,
    val name: String,
    /** Encrypted blob holding this person's photo, or blank to show their initial. */
    val photo: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("photo", photo)

    companion object {
        const val ME = "me"
        val default = Profile(ME, "Me")
        const val MAX_NAME = 20

        fun fromJson(o: JSONObject) = Profile(o.getString("id"), o.getString("name"), o.optString("photo"))
    }
}

data class Doc(
    val id: String,
    val category: String,
    val title: String,
    val profile: String = Profile.ME,
    val holder: String = "",
    val number: String = "",
    /** ISO date (yyyy-MM-dd), or blank when the document does not expire. */
    val expiry: String = "",
    val notes: String = "",
    val fields: List<CustomField> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    /** Text read from the document's photos, used only for search. */
    val photoText: String = "",
    val favorite: Boolean = false,
    /** The expiry date a WhatsApp reminder was last prepared for. */
    val reminderFor: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val images get() = attachments.filter { it.kind == "image" }

    fun daysToExpiry(today: LocalDate = LocalDate.now()): Long? = try {
        if (expiry.isBlank()) null else ChronoUnit.DAYS.between(today, LocalDate.parse(expiry))
    } catch (_: DateTimeParseException) {
        null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("category", category).put("title", title).put("profile", profile)
        .put("holder", holder)
        .put("number", number).put("expiry", expiry).put("notes", notes)
        .put("photoText", photoText).put("favorite", favorite).put("reminderFor", reminderFor)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("fields", JSONArray().also { a ->
            fields.forEach { a.put(JSONObject().put("label", it.label).put("value", it.value)) }
        })
        .put("attachments", JSONArray().also { a ->
            attachments.forEach {
                a.put(
                    JSONObject().put("blobId", it.blobId).put("kind", it.kind)
                        .put("name", it.name).put("mime", it.mime),
                )
            }
        })

    companion object {
        fun fromJson(o: JSONObject): Doc {
            val fields = o.optJSONArray("fields") ?: JSONArray()
            val atts = o.optJSONArray("attachments") ?: JSONArray()
            return Doc(
                id = o.getString("id"),
                category = o.optString("category", "other"),
                title = o.optString("title"),
                profile = o.optString("profile", Profile.ME),
                holder = o.optString("holder"),
                number = o.optString("number"),
                expiry = o.optString("expiry"),
                notes = o.optString("notes"),
                photoText = o.optString("photoText"),
                favorite = o.optBoolean("favorite"),
                reminderFor = o.optString("reminderFor"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
                fields = List(fields.length()) {
                    val f = fields.getJSONObject(it)
                    CustomField(f.optString("label"), f.optString("value"))
                },
                attachments = List(atts.length()) {
                    val a = atts.getJSONObject(it)
                    Attachment(
                        a.getString("blobId"), a.optString("kind", "image"),
                        a.optString("name"), a.optString("mime"),
                    )
                },
            )
        }
    }
}

data class SearchHit(val doc: Doc, val snippet: String?)

object Search {
    private fun digitsOnly(s: String) = s.filter(Char::isDigit)

    private fun haystack(d: Doc, categoryLabel: String, person: String) = buildString {
        append(person).append(' ')
        append(d.title).append(' ').append(d.holder).append(' ').append(d.number).append(' ')
        append(d.number.replace(" ", "")).append(' ').append(categoryLabel).append(' ')
        append(d.notes).append(' ')
        d.fields.forEach { append(it.label).append(' ').append(it.value).append(' ') }
        append(d.photoText).append(' ')
        append(digitsOnly(d.photoText))
    }.lowercase()

    /** A document matches when every word of the query appears somewhere in it. */
    fun run(
        docs: List<Doc>,
        query: String,
        personName: (String) -> String = { "" },
        label: (String) -> String,
    ): List<SearchHit> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return docs.map { SearchHit(it, null) }
        return docs.mapNotNull { d ->
            val text = haystack(d, label(d.category), personName(d.profile))
            if (words.all { it in text }) SearchHit(d, snippet(d, words)) else null
        }
    }

    private fun snippet(d: Doc, words: List<String>): String? {
        val visible = "${d.title} ${d.holder} ${d.number}".lowercase()
        if (words.all { it in visible }) return null
        for (f in d.fields) {
            if (words.any { it in f.value.lowercase() }) return "${f.label}: ${f.value}"
        }
        val line = (d.notes + "\n" + d.photoText).lines()
            .firstOrNull { l -> words.any { it in l.lowercase() } }
        return line?.trim()?.take(90)
    }
}
