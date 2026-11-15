package commanderpepper.optcgsimcardartchooser.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Every destination in the app. Navigation 3 keeps a back stack of these keys; each one maps to
 * a screen in `AppNavigation`. To add a page: add a `@Serializable` subtype here, register it in
 * [Pages.savedStateConfiguration], and add an `entry<...>` for it in `AppNavigation`.
 * Pages that need arguments are data classes, e.g. `data class CardDetail(val cardId: String) : Pages`.
 */
@Serializable
sealed interface Pages : NavKey {

    /** Choose and verify the OPTCGSim install folder. */
    @Serializable
    data object SimFolderSetup : Pages

    /** Main screen once a folder is verified. */
    @Serializable
    data object Home : Pages

    /** Search and filter the card catalog. */
    @Serializable
    data object CardBrowser : Pages

    /** One card's art options; choosing one copies it into the sim. */
    @Serializable
    data class CardDetail(val cardId: String) : Pages

    companion object {
        /**
         * Non-Android targets have no reflection for saving the back stack, so every page must be
         * registered for polymorphic serialization.
         */
        val savedStateConfiguration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(SimFolderSetup::class, SimFolderSetup.serializer())
                    subclass(Home::class, Home.serializer())
                    subclass(CardBrowser::class, CardBrowser.serializer())
                    subclass(CardDetail::class, CardDetail.serializer())
                }
            }
        }
    }
}
