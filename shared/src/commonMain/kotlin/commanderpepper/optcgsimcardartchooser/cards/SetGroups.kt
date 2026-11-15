package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet

/** Product lines, in the order sets are listed: boosters first, then extras, starters, premiums, promos. */
enum class SetGroup(val label: String, val prefix: String) {
    Booster("Booster Packs", "OP"),
    ExtraBooster("Extra Boosters", "EB"),
    StarterDeck("Starter Decks", "ST"),
    PremiumBooster("Premium Boosters", "PRB"),
    Promotion("Promotions", "P"),
    Other("Other", ""),
    ;

    companion object {
        private val ID = Regex("""^([A-Z]+)(\d*)$""")

        fun of(setId: String): SetGroup {
            val prefix = ID.find(setId)?.groupValues?.get(1) ?: return Other
            return entries.firstOrNull { it.prefix == prefix } ?: Other
        }

        /** Set number within its line (OP17 → 17); sets without a number sort first. */
        fun numberOf(setId: String): Int = ID.find(setId)?.groupValues?.get(2)?.toIntOrNull() ?: 0
    }
}

/** OP01…OP17, EB01…EB05, ST01…ST36, PRB01, PRB02, P — numeric within each line, not alphabetical. */
fun List<CardSet>.sortedByProductLine(): List<CardSet> =
    sortedWith(compareBy<CardSet>({ SetGroup.of(it.id).ordinal }, { SetGroup.numberOf(it.id) }, { it.id }))

/** Sets grouped for display, in [SetGroup] order, skipping empty groups. */
fun List<CardSet>.groupedByProductLine(): List<Pair<SetGroup, List<CardSet>>> =
    sortedByProductLine().groupBy { SetGroup.of(it.id) }.toList()
