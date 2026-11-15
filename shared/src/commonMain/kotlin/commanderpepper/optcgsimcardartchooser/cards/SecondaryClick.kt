package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.Modifier

/**
 * Calls [onClick] on a right-click (secondary mouse button). Mouse-button details are a
 * desktop-only API, so the implementation lives in the platform source set.
 */
expect fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier
