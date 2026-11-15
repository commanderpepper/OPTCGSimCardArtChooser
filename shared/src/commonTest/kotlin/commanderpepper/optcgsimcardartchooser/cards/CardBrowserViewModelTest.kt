package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
import commanderpepper.optcgsimcardartchooser.catalog.model.Card
import commanderpepper.optcgsimcardartchooser.catalog.model.CardColor
import commanderpepper.optcgsimcardartchooser.catalog.model.CardSet
import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
import commanderpepper.optcgsimcardartchooser.catalog.model.Category
import commanderpepper.optcgsimcardartchooser.catalog.model.Variant
import commanderpepper.optcgsimcardartchooser.catalog.model.VariantKind
import commanderpepper.optcgsimcardartchooser.simfolder.FolderCheck
import commanderpepper.optcgsimcardartchooser.simfolder.SimCardsFolder
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardBrowserViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun card(id: String, name: String, color: CardColor, category: Category, traits: List<String>, alts: Int = 0) = Card(
        id = id, name = name, setId = id.substringBefore('-'), category = category, colors = listOf(color), traits = traits,
        variants = List(alts + 1) { Variant(if (it == 0) id else "${id}_p$it", if (it == 0) VariantKind.Regular else VariantKind.AltArt) },
    )

    private val catalog = Catalog(
        schemaVersion = 2, generatedAt = "t",
        sets = listOf(CardSet("EB01", "Memorial Collection"), CardSet("OP01", "Romance Dawn")),
        cards = listOf(
            card("OP01-001", "Roronoa Zoro", CardColor.Red, Category.Leader, listOf("Supernovas", "Straw Hat Crew"), alts = 1),
            card("OP01-061", "Kaido", CardColor.Purple, Category.Leader, listOf("Animal Kingdom Pirates")),
            card("EB01-006", "Tony Tony.Chopper", CardColor.Red, Category.Character, listOf("Animal", "Straw Hat Crew"), alts = 2),
        ),
    )

    private class FakeRepository(private val result: suspend () -> Catalog) : CatalogRepository {
        var loads = 0
        override suspend fun load(): Catalog { loads++; return result() }
    }

    /** Records requests; "returns" an image only for cards in [available]. */
    private class FakeThumbnails(private val available: Set<String> = emptySet()) : CardThumbnails {
        val requests = mutableListOf<Pair<String, String>>()
        override suspend fun load(cardsPath: String, card: Card): ImageBitmap? {
            requests += cardsPath to card.id
            return if (card.id in available) ImageBitmap(120, 167) else null
        }

        override fun invalidate(cardId: String) = Unit
    }

    private class FakeStore(private val saved: String?) : SimFolderStore {
        override fun load() = saved
        override fun save(path: String) = Unit
        override fun clear() = Unit
    }

    /** Only "/sim" is a valid install; its cards folder is "/sim/Cards". */
    private object FakeLocator : SimFolderLocator {
        override fun suggestions() = emptyList<String>()
        override fun check(path: String): FolderCheck =
            if (path == "/sim") FolderCheck.Valid(path, "/sim/Cards", setCount = 2, writable = true) else FolderCheck.Invalid(path, "no")
    }

    /** Only [appliedVariants] matters to the browser; [modified] can change between reads. */
    private class FakeArt(var modified: Map<String, String> = emptyMap()) : CardArt {
        override suspend fun appliedVariants() = modified
        override suspend fun appliedVariant(cardId: String) = modified[cardId]
        override suspend fun variantPreview(card: Card, variant: Variant): ImageBitmap? = null
        override suspend fun originalPreview(cardsPath: String, card: Card): ImageBitmap? = null
        override suspend fun apply(cardsPath: String, card: Card, variant: Variant) = Unit
        override suspend fun restoreOriginal(cardsPath: String, card: Card) = Unit
        override suspend fun reapply(cardsPath: String, card: Card, variant: Variant) = ReapplyOutcome.AlreadyApplied
    }

    private fun browser(
        repository: CatalogRepository,
        thumbnails: CardThumbnails = FakeThumbnails(),
        savedFolder: String? = "/sim",
        art: CardArt = FakeArt(),
    ) = CardBrowserViewModel(repository, thumbnails, art, SimCardsFolder(FakeStore(savedFolder), FakeLocator))

    @Test
    fun artFilterShowsModifiedOrOriginalCards() {
        val vm = browser(FakeRepository { catalog }, art = FakeArt(mapOf("OP01-001" to "OP01-001_p1")))
        assertEquals(setOf("OP01-001"), vm.state.value.modifiedCardIds)

        vm.toggleArtStatus(ArtStatus.Modified)
        assertEquals(listOf("OP01-001"), ids(vm))
        assertTrue(vm.state.value.hasActiveFilter)

        vm.toggleArtStatus(ArtStatus.Original) // both ticked = everything
        assertEquals(3, ids(vm).size)

        vm.toggleArtStatus(ArtStatus.Modified)
        assertEquals(listOf("OP01-061", "EB01-006"), ids(vm))

        vm.toggleColor(CardColor.Red) // combines with the other filters
        assertEquals(listOf("EB01-006"), ids(vm))

        vm.clearFilters()
        assertEquals(3, ids(vm).size)
        assertFalse(vm.state.value.hasActiveFilter)
    }

    @Test
    fun deckListShowsItsCardsInDeckOrderAndReportsUnknownOnes() {
        val vm = browser(FakeRepository { catalog })
        vm.applyDeckList("4xEB01-006\n1xOP01-001\n3xOP18-077")

        assertEquals(listOf("EB01-006", "OP01-001"), ids(vm), "deck order, not catalog order")
        assertEquals(listOf("EB01-006", "OP01-001"), vm.state.value.deckCardIds)
        assertEquals("1 card wasn't found: OP18-077", vm.state.value.deckMessage)
        assertTrue(vm.state.value.hasActiveFilter)

        vm.dismissDeckMessage()
        assertNull(vm.state.value.deckMessage)
        assertEquals(2, ids(vm).size, "dismissing the message keeps the deck filter")
    }

    @Test
    fun deckListCombinesWithOtherFiltersAndCanBeCleared() {
        val vm = browser(FakeRepository { catalog })
        vm.applyDeckList("OP01-001\nOP01-061\nEB01-006")
        vm.toggleCategory(Category.Leader)
        assertEquals(listOf("OP01-001", "OP01-061"), ids(vm))

        vm.clearDeckList()
        assertNull(vm.state.value.deckCardIds)
        assertEquals(listOf("OP01-001", "OP01-061"), ids(vm), "other filters stay")

        vm.applyDeckList("EB01-006")
        vm.clearFilters()
        assertNull(vm.state.value.deckCardIds, "clear filters also clears the deck list")
        assertEquals(3, ids(vm).size)
    }

    @Test
    fun deckListWithNothingRecognisableKeepsTheCurrentView() {
        val vm = browser(FakeRepository { catalog })
        vm.applyDeckList("just some text")
        assertNull(vm.state.value.deckCardIds)
        assertEquals("No card numbers were found in that list.", vm.state.value.deckMessage)
        assertEquals(3, ids(vm).size)

        vm.applyDeckList("4xOP18-077\n4xOP18-078")
        assertNull(vm.state.value.deckCardIds)
        assertEquals("None of the 2 cards in that list are in the catalog: OP18-077, OP18-078", vm.state.value.deckMessage)
        assertEquals("4xOP18-077\n4xOP18-078", vm.state.value.deckText, "kept to pre-fill the dialog for fixing")
    }

    @Test
    fun refreshPicksUpArtChangedOnTheCardPage() {
        val art = FakeArt()
        val vm = browser(FakeRepository { catalog }, art = art)
        vm.toggleArtStatus(ArtStatus.Modified)
        assertTrue(ids(vm).isEmpty())

        art.modified = mapOf("EB01-006" to "EB01-006_p2") // changed while the browser waited underneath
        vm.refreshModified()
        assertEquals(listOf("EB01-006"), ids(vm))

        art.modified = emptyMap() // restored to the original
        vm.refreshModified()
        assertTrue(ids(vm).isEmpty())
    }

    private fun ids(vm: CardBrowserViewModel) = vm.state.value.cards.map { it.id }

    @Test
    fun thumbnailsComeFromTheVerifiedSimCardsFolder() = runTest {
        val thumbnails = FakeThumbnails(available = setOf("OP01-001"))
        val vm = browser(FakeRepository { catalog }, thumbnails)
        val zoro = catalog.cards.first { it.id == "OP01-001" }
        val kaido = catalog.cards.first { it.id == "OP01-061" }

        assertNotNull(vm.thumbnail(zoro))
        assertNull(vm.thumbnail(kaido), "missing image → placeholder")
        assertEquals(listOf("/sim/Cards" to "OP01-001", "/sim/Cards" to "OP01-061"), thumbnails.requests)
    }

    @Test
    fun withoutAVerifiedFolderEveryThumbnailIsThePlaceholder() = runTest {
        val thumbnails = FakeThumbnails(available = setOf("OP01-001"))
        val vm = browser(FakeRepository { catalog }, thumbnails, savedFolder = "/moved")
        assertNull(vm.thumbnail(catalog.cards.first()))
        assertTrue(thumbnails.requests.isEmpty())
    }

    @Test
    fun loadsTheCatalogAndShowsEveryCard() {
        val vm = browser(FakeRepository { catalog })
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(3, state.totalCards)
        assertEquals(listOf("OP01-001", "OP01-061", "EB01-006"), ids(vm))
        assertEquals(listOf("OP01", "EB01"), state.sets.map { it.id }, "OP before EB, not alphabetical")
        assertEquals(listOf("Animal", "Animal Kingdom Pirates", "Straw Hat Crew", "Supernovas"), state.traits)
    }

    @Test
    fun staysLoadingUntilTheCatalogArrives() {
        val pending = CompletableDeferred<Catalog>()
        val vm = browser(FakeRepository { pending.await() })
        assertTrue(vm.state.value.isLoading)
        pending.complete(catalog)
        assertFalse(vm.state.value.isLoading)
        assertEquals(3, vm.state.value.cards.size)
    }

    @Test
    fun filtersCombineAndCanBeCleared() {
        val vm = browser(FakeRepository { catalog })
        vm.toggleColor(CardColor.Red)
        assertEquals(listOf("OP01-001", "EB01-006"), ids(vm))
        vm.toggleCategory(Category.Leader)
        assertEquals(listOf("OP01-001"), ids(vm))
        assertTrue(vm.state.value.hasActiveFilter)

        vm.toggleColor(CardColor.Red) // toggling again removes it
        assertEquals(listOf("OP01-001", "OP01-061"), ids(vm))

        vm.clearFilters()
        assertEquals(3, ids(vm).size)
        assertFalse(vm.state.value.hasActiveFilter)
    }

    @Test
    fun searchSetTraitAndAltArtFilters() {
        val vm = browser(FakeRepository { catalog })
        vm.onQueryChanged("chop")
        assertEquals(listOf("EB01-006"), ids(vm))
        vm.onQueryChanged("")

        vm.toggleSet("OP01")
        assertEquals(listOf("OP01-001", "OP01-061"), ids(vm))
        vm.toggleSet("OP01")

        vm.toggleTrait("Straw Hat Crew")
        assertEquals(listOf("OP01-001", "EB01-006"), ids(vm))
        vm.toggleTrait("Straw Hat Crew")

        vm.setOnlyWithAlternateArt(true)
        assertEquals(listOf("OP01-001", "EB01-006"), ids(vm))
    }

    @Test
    fun loadFailureShowsAnErrorAndRetryRecovers() {
        var fail = true
        val repository = FakeRepository { if (fail) error("disk on fire") else catalog }
        val vm = browser(repository)
        assertEquals("disk on fire", vm.state.value.error)
        assertTrue(vm.state.value.cards.isEmpty())

        fail = false
        vm.retry()
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.cards.firstOrNull())
        assertEquals(2, repository.loads)
    }
}
