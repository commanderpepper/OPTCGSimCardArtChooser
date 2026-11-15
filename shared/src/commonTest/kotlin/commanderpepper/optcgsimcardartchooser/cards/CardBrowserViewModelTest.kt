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

    private fun browser(
        repository: CatalogRepository,
        thumbnails: CardThumbnails = FakeThumbnails(),
        savedFolder: String? = "/sim",
    ) = CardBrowserViewModel(repository, thumbnails, FakeStore(savedFolder), FakeLocator)

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
