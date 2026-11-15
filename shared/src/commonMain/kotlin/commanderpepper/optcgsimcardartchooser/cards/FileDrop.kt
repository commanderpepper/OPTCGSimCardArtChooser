package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Accepts files (or images) dragged in from other apps. [onHoverChange] reports when something is
 * dragged over (to show a hint), [onDrop] receives the dropped files' paths, and [onDropFailed]
 * is called when the drop can't be read. Desktop-only API, so the implementation lives in the
 * platform source set.
 */
@Composable
expect fun Modifier.fileDropTarget(
    enabled: Boolean,
    onHoverChange: (Boolean) -> Unit,
    onDrop: (List<String>) -> Unit,
    onDropFailed: () -> Unit,
): Modifier
