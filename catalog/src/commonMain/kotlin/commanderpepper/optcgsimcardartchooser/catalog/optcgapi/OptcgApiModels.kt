package commanderpepper.optcgsimcardartchooser.catalog.optcgapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Raw optcgapi.com response shapes. Everything is nullable: the data is scraped and inconsistent.

@Serializable
data class ApiSet(
    @SerialName("set_id") val setId: String? = null,
    @SerialName("set_name") val setName: String? = null,
)

@Serializable
data class ApiDeck(
    @SerialName("structure_deck_id") val deckId: String? = null,
    @SerialName("structure_deck_name") val deckName: String? = null,
)

/** One row per printing; several rows share a [cardSetId]. */
@Serializable
data class ApiCardRow(
    @SerialName("card_set_id") val cardSetId: String? = null,
    @SerialName("card_image_id") val cardImageId: String? = null,
    @SerialName("card_name") val cardName: String? = null,
    @SerialName("set_id") val setId: String? = null,
    @SerialName("set_name") val setName: String? = null,
    @SerialName("card_color") val cardColor: String? = null,
    @SerialName("card_type") val cardType: String? = null,
    @SerialName("card_text") val cardText: String? = null,
    @SerialName("sub_types") val subTypes: String? = null,
    @SerialName("attribute") val attribute: String? = null,
    @SerialName("rarity") val rarity: String? = null,
    @SerialName("card_image") val cardImage: String? = null,
    // Numbers arrive as strings ("3"), numbers (1000) or null.
    @SerialName("card_cost") val cardCost: JsonPrimitive? = null,
    @SerialName("card_power") val cardPower: JsonPrimitive? = null,
    @SerialName("counter_amount") val counterAmount: JsonPrimitive? = null,
    @SerialName("life") val life: JsonPrimitive? = null,
)

internal fun JsonPrimitive?.asIntOrNull(): Int? =
    this?.contentOrNull?.trim()?.toDoubleOrNull()?.toInt()
