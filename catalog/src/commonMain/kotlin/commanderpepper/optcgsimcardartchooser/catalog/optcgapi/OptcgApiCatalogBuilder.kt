package commanderpepper.optcgsimcardartchooser.catalog.optcgapi

import commanderpepper.optcgsimcardartchooser.catalog.CatalogCorrections
import commanderpepper.optcgsimcardartchooser.catalog.CatalogBuildResult
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet
import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.ImageSource
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import commanderpepper.optcgsimcardartchooser.catalog.traits.TraitSplitter

/**
 * Turns optcgapi rows (one per printing) into catalog cards (one per card ID, with variants).
 * Cleanup rules, each based on problems seen in the real data:
 * - Card ID comes from the `card_set_id` prefix; some IDs carry a variant suffix (`P-029_r1`).
 * - Variant IDs are normalized: `-r1` → `_r1`, `_P1` → `_p1`.
 * - The same printing can be listed under two sets; the copy from the card's own set wins.
 * - Card details come from the base printing, since variants sometimes disagree. Traits are the
 *   exception: if the base has none (or junk, e.g. OP08-001's "5000"), other printings fill in.
 * - [CatalogCorrections] (hand-verified) are applied last and always win.
 * - Names lose their printing labels: `Roronoa Zoro (001) (Parallel)` → `Roronoa Zoro`.
 * - Set IDs `OP-01` → `OP01`; combined sets like `OP14-EB04` count for both IDs.
 * - Space-joined traits are split with [TraitSplitter]; unknown ones are kept and warned about.
 */
class OptcgApiCatalogBuilder(
    private val traitSplitter: TraitSplitter = TraitSplitter(),
    private val corrections: CatalogCorrections = CatalogCorrections(),
) {

    fun build(
        sets: List<ApiSet>,
        decks: List<ApiDeck>,
        rows: List<ApiCardRow>,
        generatedAt: String,
    ): CatalogBuildResult {
        val warnings = mutableListOf<String>()
        val printings = rows.mapNotNull { row ->
            toPrinting(row) ?: null.also { warnings += "Skipped row without a card ID: ${row.cardSetId} / ${row.cardImageId}" }
        }

        val cards = printings.groupBy { it.cardId }
            .mapNotNull { (cardId, group) -> toCard(cardId, group, warnings) }
            .sortedBy { it.id }

        val setNames = buildMap {
            sets.forEach { s -> s.setName?.let { name -> setTokens(s.setId).forEach { putIfAbsent(it, name) } } }
            decks.forEach { d -> d.deckName?.let { name -> setTokens(d.deckId).forEach { putIfAbsent(it, name) } } }
        }
        val catalogSets = cards.map { it.setId }.distinct().sorted().map { id ->
            corrections.apply(CardSet(id = id, name = setNames[id] ?: FALLBACK_SET_NAMES[id] ?: id))
        }

        return CatalogBuildResult(
            catalog = Catalog(Catalog.SCHEMA_VERSION, generatedAt, catalogSets, cards),
            warnings = warnings,
        )
    }

    private class Printing(
        val row: ApiCardRow,
        val cardId: String,
        val setId: String,
        val variantId: String,
        val sourceSetId: String?,
        val tags: List<String>,
    ) {
        val isBase get() = variantId == cardId
    }

    private fun toPrinting(row: ApiCardRow): Printing? {
        val match = CARD_ID.find(row.cardSetId.orEmpty().trim()) ?: CARD_ID.find(row.cardImageId.orEmpty().trim()) ?: return null
        val cardId = match.value
        val setId = match.groupValues[1]
        val tokens = setTokens(row.setId)
        return Printing(
            row = row,
            cardId = cardId,
            setId = setId,
            variantId = normalizeVariantId(row.cardImageId?.takeIf { it.isNotBlank() } ?: cardId),
            sourceSetId = tokens.firstOrNull { it == setId } ?: tokens.firstOrNull(),
            tags = nameTags(row.cardName.orEmpty()),
        )
    }

    private fun toCard(cardId: String, group: List<Printing>, warnings: MutableList<String>): Card? {
        // One printing per variant ID, preferring the listing under the card's own set.
        val printings = group.groupBy { it.variantId }.values.map { dupes ->
            dupes.firstOrNull { it.sourceSetId == it.setId } ?: dupes.first()
        }
        val details = printings.firstOrNull { it.isBase } ?: printings.first()
        val row = details.row

        val category = Category.entries.firstOrNull { it.name.equals(row.cardType?.trim(), ignoreCase = true) }
        if (category == null) {
            warnings += "Skipped $cardId: unknown card type '${row.cardType}'"
            return null
        }
        val colors = words(row.cardColor).mapNotNull { word ->
            CardColor.entries.firstOrNull { it.name.equals(word, ignoreCase = true) }
                ?: null.also { warnings += "$cardId: unknown color '$word'" }
        }
        // Traits only: other printings fill in when the base has none or junk (OP08-001's is "5000").
        // Not done for other fields, where reprint rows carry placeholder 0s and disclaimer text.
        val traitSplit = (listOf(details) + printings.filter { it !== details }).firstNotNullOfOrNull { p ->
            traitSplitter.split(p.row.subTypes).takeIf { it.traits.isNotEmpty() }?.let { p.row to it }
        }
        traitSplit?.let { (r, split) -> split.unknown.forEach { warnings += "$cardId: unknown trait '$it' (from '${r.subTypes}')" } }

        val card = Card(
            id = cardId,
            name = cleanName(row.cardName).ifBlank { cardId },
            setId = details.setId,
            category = category,
            colors = colors,
            cost = row.cardCost.asIntOrNull(),
            power = row.cardPower.asIntOrNull(),
            counter = row.counterAmount.asIntOrNull(),
            life = row.life.asIntOrNull(),
            attributes = words(row.attribute).filterNot { it.equals("null", ignoreCase = true) || it.equals("N/A", ignoreCase = true) },
            traits = traitSplit?.second?.traits.orEmpty(),
            rarity = row.rarity?.trim()?.ifBlank { null },
            text = row.cardText?.trim().orEmpty(),
            variants = printings.map { p ->
                Variant(
                    id = p.variantId,
                    kind = variantKind(p.tags, p.variantId, p.isBase),
                    tags = p.tags,
                    sourceSetId = p.sourceSetId,
                    images = listOfNotNull(
                        p.row.cardImage?.trim()?.takeIf { it.isNotEmpty() }?.let { ImageSource(it, ORIGIN) },
                    ),
                )
            },
        )
        return corrections.apply(card)
    }

    companion object {
        const val ORIGIN = "optcgapi"

        private val CARD_ID = Regex("""^([A-Z]+\d*)-(\d{3})""")
        private val VARIANT_SUFFIX = Regex("""[-_]([pPrR])(\d+)$""")
        private val SET_DASH = Regex("""([A-Z]+)-(\d+)""")
        private val TRAILING_TAG = Regex("""\s*\(([^()]*)\)\s*$""")
        private val TRAILING_CARD_ID = Regex("""\s+-\s+[A-Z]+\d*-\d{3}\s*$""")
        private val NOT_A_LABEL = Regex("""^(\d+|[A-Z]+\d*-\d{3})$""")
        private val FALLBACK_SET_NAMES = mapOf("P" to "Promotion")

        /** `OP09-078-r1` → `OP09-078_r1`, `OP04-016_P1` → `OP04-016_p1`. */
        internal fun normalizeVariantId(raw: String): String =
            raw.trim().replace(VARIANT_SUFFIX) { "_" + it.groupValues[1].lowercase() + it.groupValues[2] }

        /** `OP-01` → `[OP01]`, `OP14-EB04` → `[OP14, EB04]`. */
        internal fun setTokens(raw: String?): List<String> =
            raw.orEmpty().trim().uppercase()
                .replace(SET_DASH) { it.groupValues[1] + it.groupValues[2] }
                .split('-')
                .filter { it.isNotBlank() }

        /** Printing labels at the end of a name, minus numbering: `Zoro (001) (Parallel)` → `[Parallel]`. */
        internal fun nameTags(name: String): List<String> {
            var rest = name.replace(TRAILING_CARD_ID, "")
            val tags = mutableListOf<String>()
            while (true) {
                val match = TRAILING_TAG.find(rest) ?: break
                tags.add(0, match.groupValues[1].trim())
                rest = rest.removeRange(match.range)
            }
            return tags.filter { it.isNotEmpty() && !NOT_A_LABEL.matches(it) }
        }

        internal fun cleanName(name: String?): String {
            var rest = name.orEmpty().replace(TRAILING_CARD_ID, "")
            while (true) {
                val match = TRAILING_TAG.find(rest) ?: break
                rest = rest.removeRange(match.range)
            }
            return rest.trim()
        }

        /** Name labels decide the kind; without labels, fall back to the ID suffix (`_p` alt, `_r` reprint). */
        internal fun variantKind(tags: List<String>, variantId: String, isBase: Boolean): VariantKind {
            val t = tags.map { it.lowercase() }
            fun any(vararg labels: String) = t.any { it in labels }
            return when {
                any("manga") -> VariantKind.Manga
                any("sp", "spr") -> VariantKind.SP
                any("alternate art", "parallel", "full art") -> VariantKind.AltArt
                t.any { "foil" in it } -> VariantKind.Foil
                any("dash pack", "box topper") -> VariantKind.Promo
                any("reprint") -> VariantKind.Reprint
                isBase -> VariantKind.Regular
                variantId.contains(Regex("""_p\d+$""")) -> VariantKind.AltArt
                variantId.contains(Regex("""_r\d+$""")) -> VariantKind.Reprint
                else -> VariantKind.Other
            }
        }

        private fun words(raw: String?): List<String> =
            raw.orEmpty().split(Regex("""\s+""")).filter { it.isNotBlank() }
    }
}
