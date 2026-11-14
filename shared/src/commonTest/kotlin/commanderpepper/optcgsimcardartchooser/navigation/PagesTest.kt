package commanderpepper.optcgsimcardartchooser.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PagesTest {

    @Test
    fun confirmingFromFirstRunSetupMakesHomeTheOnlyPage() {
        val stack = mutableListOf<NavKey>(Pages.SimFolderSetup)
        stack.showHome()
        assertEquals(listOf<NavKey>(Pages.Home), stack)
    }

    @Test
    fun confirmingAChangedFolderReturnsToTheExistingHome() {
        val stack = mutableListOf<NavKey>(Pages.Home, Pages.SimFolderSetup)
        stack.showHome()
        assertEquals(listOf<NavKey>(Pages.Home), stack)
    }

    @Test
    fun everyPageIsRegisteredForSavingTheBackStack() {
        // Fails if a new page is added to Pages but not to savedStateConfiguration.
        val json = Json { serializersModule = Pages.savedStateConfiguration.serializersModule }
        val serializer = PolymorphicSerializer(NavKey::class)
        val pages: List<Pages> = listOf(Pages.SimFolderSetup, Pages.Home)
        pages.forEach { page ->
            assertEquals(page, json.decodeFromString(serializer, json.encodeToString(serializer, page)))
        }
        // A sealed serializer's "value" element lists every subtype, so this catches a page missing above.
        val declaredPages = Pages.serializer().descriptor.getElementDescriptor(1).elementsCount
        assertEquals(declaredPages, pages.size, "update this list when adding a page")
    }
}
