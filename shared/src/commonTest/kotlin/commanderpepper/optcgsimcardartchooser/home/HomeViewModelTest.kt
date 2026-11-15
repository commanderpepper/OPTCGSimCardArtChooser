package commanderpepper.optcgsimcardartchooser.home

import androidx.compose.ui.graphics.ImageBitmap
import commanderpepper.optcgsimcardartchooser.cards.CardArt
import commanderpepper.optcgsimcardartchooser.cards.CatalogRepository
import commanderpepper.optcgsimcardartchooser.cards.CustomImageInfo
import commanderpepper.optcgsimcardartchooser.cards.ReapplyOutcome
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet
import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import commanderpepper.optcgsimcardartchooser.simfolder.FolderCheck
import commanderpepper.optcgsimcardartchooser.simfolder.SimCardsFolder
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun card(id: String) = Card(id, id, id.substringBefore('-'), Category.Character, emptyList(),
        variants = listOf(Variant(id, VariantKind.Regular), Variant("${id}_p1", VariantKind.AltArt)))

    private val catalog = Catalog(2, "t", listOf(CardSet("OP01", "x")), listOf(card("OP01-001"), card("OP01-002"), card("OP01-003")))

    /** [outcomes] per card ID; a missing entry throws. */
    private class FakeArt(
        var selections: Map<String, String>,
        private val outcomes: Map<String, ReapplyOutcome>,
    ) : CardArt {
        val reapplied = mutableListOf<String>()
        override suspend fun appliedVariants() = selections
        override suspend fun appliedVariant(cardId: String) = selections[cardId]
        override suspend fun reapply(cardsPath: String, card: Card, variant: Variant): ReapplyOutcome {
            reapplied += "$cardsPath ${variant.id}"
            return outcomes[card.id] ?: error("disk full")
        }
        override suspend fun variantPreview(card: Card, variant: Variant): ImageBitmap? = null
        override suspend fun originalPreview(cardsPath: String, card: Card): ImageBitmap? = null
        override suspend fun apply(cardsPath: String, card: Card, variant: Variant) = Unit
        override suspend fun restoreOriginal(cardsPath: String, card: Card) = Unit
        override suspend fun setCustomImage(card: Card, sourcePath: String) = CustomImageInfo(480, 671)
        override suspend fun customPreview(card: Card): ImageBitmap? = null
        override suspend fun removeCustomImage(card: Card) = Unit
    }

    private class FakeStore(private val saved: String?) : SimFolderStore {
        override fun load() = saved
        override fun save(path: String) = Unit
        override fun clear() = Unit
    }

    private object FakeLocator : SimFolderLocator {
        override fun suggestions() = emptyList<String>()
        override fun check(path: String): FolderCheck =
            if (path == "/sim") FolderCheck.Valid(path, "/sim/Cards", 1, writable = true) else FolderCheck.Invalid(path, "no")
    }

    private fun home(art: FakeArt, savedFolder: String? = "/sim") = HomeViewModel(
        art,
        object : CatalogRepository { override suspend fun load() = catalog },
        SimCardsFolder(FakeStore(savedFolder), FakeLocator),
    )

    @Test
    fun refreshCountsModifiedCards() {
        val art = FakeArt(mapOf("OP01-001" to "OP01-001_p1"), emptyMap())
        val vm = home(art)
        vm.refresh()
        assertEquals(1, vm.state.value.modifiedCount)

        art.selections = emptyMap()
        vm.refresh()
        assertEquals(0, vm.state.value.modifiedCount)
    }

    @Test
    fun reapplyAllPutsEveryChosenArtBackAndSummarises() {
        val art = FakeArt(
            selections = mapOf("OP01-001" to "OP01-001_p1", "OP01-002" to "OP01-002_p1", "OP01-003" to "OP01-003_p1"),
            outcomes = mapOf("OP01-001" to ReapplyOutcome.Reapplied, "OP01-002" to ReapplyOutcome.Reapplied, "OP01-003" to ReapplyOutcome.AlreadyApplied),
        )
        val vm = home(art)
        vm.reapplyAll()

        assertEquals(listOf("/sim/Cards OP01-001_p1", "/sim/Cards OP01-002_p1", "/sim/Cards OP01-003_p1"), art.reapplied)
        assertEquals("Reapplied 2 cards. 1 card already had its art.", vm.state.value.message)
        assertFalse(vm.state.value.isReapplying)
        assertEquals(3, vm.state.value.modifiedCount)
    }

    @Test
    fun failuresAreListedWithoutStoppingTheRest() {
        val art = FakeArt(
            selections = mapOf("OP01-001" to "OP01-001_p1", "OP01-002" to "OP01-002_p1", "OP99-999" to "OP99-999_p1"),
            outcomes = mapOf("OP01-001" to ReapplyOutcome.Reapplied), // OP01-002 throws
        )
        val vm = home(art)
        vm.reapplyAll()
        assertEquals(
            "Reapplied 1 card. Couldn't reapply 2: OP01-002 (disk full); OP99-999 (no longer in the catalog)",
            vm.state.value.message,
        )
        vm.dismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun customImagesAreReappliedToo() {
        val art = FakeArt(mapOf("OP01-001" to "OP01-001_custom"), mapOf("OP01-001" to ReapplyOutcome.Reapplied))
        val vm = home(art)
        vm.reapplyAll()
        assertEquals(listOf("/sim/Cards OP01-001_custom"), art.reapplied)
        assertEquals("Reapplied 1 card.", vm.state.value.message)
    }

    @Test
    fun withoutAVerifiedFolderNothingIsReapplied() {
        val art = FakeArt(mapOf("OP01-001" to "OP01-001_p1"), mapOf("OP01-001" to ReapplyOutcome.Reapplied))
        val vm = home(art, savedFolder = null)
        vm.reapplyAll()
        assertTrue(art.reapplied.isEmpty())
        assertTrue(vm.state.value.message!!.contains("isn't verified"))
    }
}
