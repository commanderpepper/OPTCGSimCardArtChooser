package commanderpepper.optcgsimcardartchooser.catalog.traits

/**
 * Splits a space-joined trait string into individual traits using a known vocabulary.
 * Picks the split with the fewest traits that covers every word with a known trait; runs of
 * words that match nothing are kept together as one trait and reported in [Result.unknown].
 */
class TraitSplitter(
    knownTraits: Collection<String> = KNOWN_TRAITS,
    aliases: Map<String, String> = TRAIT_ALIASES,
    private val junkValues: Set<String> = JUNK_TRAIT_VALUES,
) {
    data class Result(val traits: List<String>, val unknown: List<String>)

    /** Lowercased word sequence → canonical trait. */
    private val lookup: Map<String, String> = buildMap {
        knownTraits.forEach { put(key(it), it) }
        aliases.forEach { (alias, canonical) -> put(key(alias), canonical) }
    }
    private val maxWords = lookup.keys.maxOfOrNull { it.split(' ').size } ?: 1

    fun split(raw: String?): Result {
        val text = raw.orEmpty().trim()
        if (text.isEmpty() || text.lowercase() in junkValues || text.all { it.isDigit() }) return Result(emptyList(), emptyList())

        val traits = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        text.split('/', ',', ';').forEach { part ->
            segment(part.split(WHITESPACE).filter { it.isNotEmpty() }).forEach { (trait, known) ->
                if (trait !in traits) traits += trait
                if (!known && trait !in unknown) unknown += trait
            }
        }
        return Result(traits, unknown)
    }

    private fun segment(words: List<String>): List<Pair<String, Boolean>> {
        val n = words.size
        if (n == 0) return emptyList()
        // best[i] = cheapest cover of words[0 until i]; a known trait costs 1, an unknown word 1000.
        val cost = IntArray(n + 1) { if (it == 0) 0 else Int.MAX_VALUE }
        val from = IntArray(n + 1)
        val match = arrayOfNulls<String>(n + 1)
        for (i in 0 until n) {
            if (cost[i] == Int.MAX_VALUE) continue
            for (j in i + 1..minOf(n, i + maxWords)) {
                val trait = lookup[key(words.subList(i, j).joinToString(" "))] ?: continue
                if (cost[i] + 1 < cost[j]) { cost[j] = cost[i] + 1; from[j] = i; match[j] = trait }
            }
            if (cost[i] + UNKNOWN_WORD_COST < cost[i + 1]) { cost[i + 1] = cost[i] + UNKNOWN_WORD_COST; from[i + 1] = i; match[i + 1] = null }
        }

        val pieces = ArrayDeque<Pair<String, Boolean>>()
        var j = n
        while (j > 0) {
            val i = from[j]
            val trait = match[j]
            if (trait != null) {
                pieces.addFirst(trait to true)
            } else {
                // Merge adjacent unknown words into one trait.
                val word = words[i]
                val head = pieces.firstOrNull()
                if (head != null && !head.second) pieces[0] = "$word ${head.first}" to false else pieces.addFirst(word to false)
            }
            j = i
        }
        return pieces
    }

    private companion object {
        const val UNKNOWN_WORD_COST = 1000
        val WHITESPACE = Regex("""\s+""")
        fun key(text: String) = text.trim().lowercase().split(WHITESPACE).joinToString(" ")
    }
}
