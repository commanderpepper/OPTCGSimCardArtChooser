package commanderpepper.optcgsimcardartchooser.cards

/**
 * Reads card numbers out of a pasted deck list. Quantities are ignored. Handles the sim's export
 * format (`4xOP09-061`) as well as `4 OP09-061`, `OP09-061 x4` and lines with card names.
 */
object DeckList {
    /** A leading quantity such as `4x`, `4 x`, `4×` or `4 `. */
    private val QUANTITY = Regex("""^\s*\d+\s*[xX×]?\s*""")
    private val CARD_ID = Regex("""[A-Za-z]{1,4}\d{0,3}-\d{3}""")

    /** Card IDs in the order they first appear, uppercased and without duplicates. */
    fun parse(text: String): List<String> =
        text.lineSequence()
            .mapNotNull { line -> CARD_ID.find(line.replaceFirst(QUANTITY, ""))?.value?.uppercase() }
            .distinct()
            .toList()
}
