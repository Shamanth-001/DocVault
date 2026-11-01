package com.personal.docvault.data

/**
 * Suggests which family member a scanned document belongs to, using the name printed on it.
 * A suggestion is only made when exactly one person fits, because a family often shares a
 * surname and one person's card can print another's name, such as a father's name on an Aadhaar.
 */
object PersonMatcher {
    // A profile called "Mother" must not match the words "Mother's Name" printed on a card
    private val relationWords = setOf(
        "me", "my", "mother", "mom", "mummy", "amma", "father", "dad", "daddy", "appa", "papa",
        "sister", "brother", "wife", "husband", "son", "daughter", "name",
    )

    private fun tokens(s: String) = s.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }

    /** Two spellings of one name share a key, such as "KUMAR RAMESH" and "Ramesh Kumar". */
    fun groupKey(name: String): String = tokens(name).sorted().joinToString(" ")

    /** Cards often print names in capitals, so "HARISH KODGI M" becomes "Harish Kodgi M". */
    fun displayName(printed: String): String {
        val clean = printed.trim().replace(Regex("\\s+"), " ")
        if (clean.any(Char::isLowerCase)) return clean
        return clean.lowercase().split(' ').joinToString(" ") { word ->
            word.replaceFirstChar(Char::uppercase)
        }
    }

    private fun usable(name: String): Boolean {
        val t = tokens(name)
        return t.size >= 2 || (t.size == 1 && t[0].length >= 5 && t[0] !in relationWords)
    }

    private fun sameName(a: String, b: String): Boolean {
        val x = tokens(a).toSet()
        val y = tokens(b).toSet()
        if (x.isEmpty() || y.isEmpty()) return false
        val shorter = if (x.size <= y.size) x else y
        val longer = if (x.size <= y.size) y else x
        return longer.containsAll(shorter) && (shorter.size >= 2 || x == y)
    }

    /** [known] maps each profile id to the names known for that person. */
    fun suggest(holder: String?, text: String, known: Map<String, List<String>>): String? {
        if (!holder.isNullOrBlank()) {
            val byHolder = known.filter { (_, names) -> names.any { usable(it) && sameName(it, holder) } }.keys
            if (byHolder.size == 1) return byHolder.single()
            if (byHolder.size > 1) return null
        }
        val words = tokens(text).toSet()
        val byText = known.filter { (_, names) ->
            names.any { usable(it) && words.containsAll(tokens(it)) }
        }.keys
        return byText.singleOrNull()
    }
}
