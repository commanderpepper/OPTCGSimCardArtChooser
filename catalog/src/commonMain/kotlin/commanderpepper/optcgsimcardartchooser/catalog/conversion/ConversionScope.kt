package commanderpepper.optcgsimcardartchooser.catalog.conversion

import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind

/** Which variants the app offers. Per the user: reprints and Jolly Roger / Textured foils aren't needed. */
object ConversionScope {
    private val EXCLUDED_TAGS = setOf("jolly roger foil", "textured foil", "reprint")

    fun includes(variant: Variant): Boolean =
        variant.kind != VariantKind.Reprint && variant.tags.none { it.lowercase() in EXCLUDED_TAGS }
}
