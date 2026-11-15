package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import commanderpepper.optcgsimcardartchooser.catalog.model.Card

/** Card proportions (the sim's thumbnails are 120x167). */
val ThumbnailSize = DpSize(80.dp, 111.dp)

private sealed interface ThumbnailState {
    data object Loading : ThumbnailState
    data class Loaded(val image: ImageBitmap) : ThumbnailState
    data object NotFound : ThumbnailState
}

/**
 * A card's thumbnail, loaded with [load]. Shows an empty frame while loading and the
 * "image not found" placeholder when [load] returns null, so tiles never change size.
 */
@Composable
fun CardThumbnailImage(
    card: Card,
    load: suspend (Card) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    /** Change to reload, e.g. after the card's art was swapped. */
    revision: Int = 0,
) {
    val state by produceState<ThumbnailState>(ThumbnailState.Loading, card.id, revision) {
        value = load(card)?.let(ThumbnailState::Loaded) ?: ThumbnailState.NotFound
    }
    val frame = modifier.size(ThumbnailSize).clip(RoundedCornerShape(6.dp))
    when (val s = state) {
        is ThumbnailState.Loaded -> Image(
            bitmap = s.image,
            contentDescription = "${card.name} (${card.id})",
            contentScale = ContentScale.Crop,
            modifier = frame,
        )
        ThumbnailState.Loading -> Box(frame.background(MaterialTheme.colorScheme.surfaceVariant))
        ThumbnailState.NotFound -> ImageNotFoundPlaceholder(frame)
    }
}

/** Shown wherever a card image is missing or unreadable. */
@Composable
fun ImageNotFoundPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Image\nnot found",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
