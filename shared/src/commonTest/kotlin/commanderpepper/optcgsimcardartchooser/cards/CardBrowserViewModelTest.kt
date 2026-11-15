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
    /** Applying/restoring updates [modified] like the real thing; card IDs in [failOn] throw. */
    private class FakeArt(var modified: Map<String, String> = emptyMap(), private val failOn: Set<String> = emptySet()) : CardArt {
        val calls = mutableListOf<String>()
        override suspend fun appliedVariants() = modified
        override suspend fun appliedVariant(cardId: String) = modified[cardId]
        override suspend fun variantPreview(card: Card, variant: Variant): ImageBitmap? = null
        override suspend fun originalPreview(cardsPath: String, card: Card): ImageBitmap? = null
        override suspend fun apply(cardsPath: String, card: Card, variant: Variant) {
            calls += "apply ${variant.id}"
            if (card.id in failOn) error("disk full")
            modified = modified + (card.id to variant.id)
        }
        override suspend fun restoreOriginal(cardsPath: String, card: Card) {
            calls += "restore ${card.id}"
            modified = modified - card.id
        }
        override suspend fun reapply(cardsPath: String, card: Card, variant: Variant) = ReapplyOutcome.AlreadyApplied
        override suspend fun customImages(card: Card) = emptyList<CustomImage>()
        override suspend fun addCustomImage(card: Card, sourcePath: String): Pair<CustomImage, CustomImageInfo> = error("unused")
        override suspend fun replaceCustomImage(card: Card, variantId: String, sourcePath: String): Pair<CustomImage, CustomImageInfo> = error("unused")
        override suspend fun removeCustomImage(card: Card, variantId: String) = Unit
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
    fun selectionTogglesSelectsAllShownAndClears() {
        val vm = browser(FakeRepository { catalog })
        vm.toggleSelection("OP01-001")
        assertEquals(setOf("OP01-001"), vm.state.value.selectedCardIds)
        assertFalse(vm.state.value.canBulkChange, "bulk actions need two or more cards")

        vm.toggleCategory(Category.Leader) // only Zoro and Kaido shown
        vm.selectAllShown()
        assertEquals(setOf("OP01-001", "OP01-061"), vm.state.value.selectedCardIds)
        assertTrue(vm.state.value.canBulkChange)

        vm.toggleSelection("OP01-001")
        assertEquals(setOf("OP01-061"), vm.state.value.selectedCardIds)
        vm.clearSelection()
        assertTrue(vm.state.value.selectedCardIds.isEmpty())
    }

    @Test
    fun selectAllTogglesToDeselectAllForTheShownCards() {
        val vm = browser(FakeRepository { catalog })
        vm.toggleSelection("EB01-006") // a Character, about to be hidden by the filter
        vm.toggleCategory(Category.Leader)
        assertFalse(vm.state.value.allShownSelected)

        vm.selectAllShown()
        assertTrue(vm.state.value.allShownSelected, "button now reads Deselect all")
        assertEquals(setOf("EB01-006", "OP01-001", "OP01-061"), vm.state.value.selectedCardIds)

        vm.deselectAllShown()
        assertEquals(setOf("EB01-006"), vm.state.value.selectedCardIds, "hidden selections are kept")
        assertFalse(vm.state.value.allShownSelected)
    }

    @Test
    fun boxSelectionAddsToTheExistingSelection() {
        val vm = browser(FakeRepository { catalog })
        vm.toggleSelection("EB01-006")
        vm.selectMany(setOf("OP01-001", "OP01-061"))
        assertEquals(setOf("EB01-006", "OP01-001", "OP01-061"), vm.state.value.selectedCardIds)
        vm.selectMany(setOf("OP01-001"))
        assertEquals(3, vm.state.value.selectedCardIds.size, "selecting again doesn't toggle off")
    }

    @Test
    fun useAltArtAppliesEachCardsAltArtAndReportsCardsWithout() {
        val art = FakeArt()
        val thumbnails = FakeThumbnails()
        val vm = browser(FakeRepository { catalog }, thumbnails = thumbnails, art = art)
        listOf("OP01-001", "OP01-061", "EB01-006").forEach(vm::toggleSelection)
        val revision = vm.state.value.thumbnailRevision

        vm.applyAltArtToSelected()

        assertEquals(listOf("apply OP01-001_p1", "apply EB01-006_p1"), art.calls, "Kaido has no alt art")
        assertEquals("Applied alt art to 2 cards. 1 has no alt art: OP01-061.", vm.state.value.bulkMessage)
        assertEquals(setOf("OP01-001", "EB01-006"), vm.state.value.modifiedCardIds)
        assertTrue(vm.state.value.selectedCardIds.isEmpty(), "selection clears afterwards")
        assertNull(vm.state.value.bulkProgress)
        assertEquals(revision + 1, vm.state.value.thumbnailRevision)
    }

    @Test
    fun useAltArtSkipsCardsAlreadyShowingItAndListsFailures() {
        val art = FakeArt(modified = mapOf("OP01-001" to "OP01-001_p1"), failOn = setOf("EB01-006"))
        val vm = browser(FakeRepository { catalog }, art = art)
        listOf("OP01-001", "EB01-006").forEach(vm::toggleSelection)
        vm.applyAltArtToSelected()
        assertEquals(listOf("apply EB01-006_p1"), art.calls)
        assertEquals("1 already had it. Couldn't change 1: EB01-006 (disk full)", vm.state.value.bulkMessage)
        vm.dismissBulkMessage()
        assertNull(vm.state.value.bulkMessage)
    }

    @Test
    fun restoreOriginalsOnlyTouchesChangedCards() {
        val art = FakeArt(modified = mapOf("OP01-001" to "OP01-001_p1", "EB01-006" to "EB01-006_custom"))
        val vm = browser(FakeRepository { catalog }, art = art)
        listOf("OP01-001", "OP01-061").forEach(vm::toggleSelection)
        vm.restoreSelected()
        assertEquals(listOf("restore OP01-001"), art.calls)
        assertEquals("Restored the original art on 1 card.", vm.state.value.bulkMessage)
        assertEquals(setOf("EB01-006"), vm.state.value.modifiedCardIds, "unselected cards keep their art")
    }

    @Test
    fun bulkActionsNeedTwoCardsAndAVerifiedFolder() {
        val art = FakeArt()
        val single = browser(FakeRepository { catalog }, art = art)
        single.toggleSelection("OP01-001")
        single.applyAltArtToSelected()
        assertTrue(art.calls.isEmpty())

        val noFolder = browser(FakeRepository { catalog }, art = art, savedFolder = null)
        listOf("OP01-001", "EB01-006").forEach(noFolder::toggleSelection)
        noFolder.applyAltArtToSelected()
        assertTrue(art.calls.isEmpty())
        assertTrue(noFolder.state.value.bulkMessage!!.contains("isn't verified"))
    }

    /** Cards with mixed rarities: Z has every tier, S has two SPs, A has only an alt art, M only manga. */
    private val rarityCatalog = Catalog(
        2, "t", listOf(CardSet("OP05", "x")),
        listOf(
            Card("OP05-001", "Z", "OP05", Category.Leader, emptyList(), variants = listOf(
                Variant("OP05-001", VariantKind.Regular),
                Variant("OP05-001_p1", VariantKind.AltArt, listOf("Alternate Art")),
                Variant("OP05-001_p2", VariantKind.SP, listOf("SP")),
                Variant("OP05-001_p3", VariantKind.Manga, listOf("Manga")),
            )),
            Card("OP05-002", "S", "OP05", Category.Character, emptyList(), variants = listOf(
                Variant("OP05-002", VariantKind.Regular),
                Variant("OP05-002_p1", VariantKind.AltArt, listOf("Alternate Art")),
                Variant("OP05-002_p2", VariantKind.SP, listOf("SP")),
                Variant("OP05-002_p3", VariantKind.SP, listOf("SPR")),
            )),
            Card("OP05-003", "A", "OP05", Category.Character, emptyList(), variants = listOf(
                Variant("OP05-003", VariantKind.Regular),
                Variant("OP05-003_p1", VariantKind.AltArt, listOf("Alternate Art")),
            )),
            Card("OP05-004", "M", "OP05", Category.Character, emptyList(), variants = listOf(
                Variant("OP05-004", VariantKind.Regular),
                Variant("OP05-004_p1", VariantKind.Manga, listOf("Manga")),
            )),
        ),
    )

    private fun rarityBrowser(art: FakeArt) = browser(FakeRepository { rarityCatalog }, art = art).also { vm ->
        listOf("OP05-001", "OP05-002", "OP05-003", "OP05-004").forEach(vm::toggleSelection)
    }

    @Test
    fun useSpPicksTheFirstSpAndReportsCardsWithoutOne() {
        val art = FakeArt()
        val vm = rarityBrowser(art)
        vm.applySpToSelected()
        assertEquals(listOf("apply OP05-001_p2", "apply OP05-002_p2"), art.calls, "first SP when there are several")
        assertEquals("Applied SP to 2 cards. 2 have no SP: OP05-003, OP05-004.", vm.state.value.bulkMessage)
    }

    @Test
    fun useSpUpgradesFromAltArtButNeverDowngradesManga() {
        val art = FakeArt(modified = mapOf("OP05-001" to "OP05-001_p3", "OP05-002" to "OP05-002_p1")) // Manga, Alt Art
        val vm = rarityBrowser(art)
        vm.applySpToSelected()
        assertEquals(listOf("apply OP05-002_p2"), art.calls)
        assertEquals("Applied SP to 1 card. 1 kept a rarer art: OP05-001. 2 have no SP: OP05-003, OP05-004.", vm.state.value.bulkMessage)
    }

    @Test
    fun useAltArtKeepsCardsAlreadyOnAnSpOrManga() {
        val art = FakeArt(modified = mapOf("OP05-001" to "OP05-001_p2", "OP05-002" to "OP05-002_p3")) // SP, SP
        val vm = rarityBrowser(art)
        vm.applyAltArtToSelected()
        assertEquals(listOf("apply OP05-003_p1"), art.calls)
        assertEquals(
            "Applied alt art to 1 card. 2 kept a rarer art: OP05-001, OP05-002. 1 has no alt art: OP05-004.",
            vm.state.value.bulkMessage,
            "OP05-004 only has manga, which Use alt art doesn't jump to",
        )
    }

    @Test
    fun useMaxRarityPicksMangaThenSpThenAltArt() {
        val art = FakeArt()
        val vm = rarityBrowser(art)
        vm.applyMaxRarityToSelected()
        assertEquals(
            listOf("apply OP05-001_p3", "apply OP05-002_p2", "apply OP05-003_p1", "apply OP05-004_p1"),
            art.calls,
            "Z → manga, S → first SP, A → alt art, M → manga",
        )
        assertEquals("Applied max rarity to 4 cards.", vm.state.value.bulkMessage)
    }

    @Test
    fun useMaxRarityUpgradesButKeepsAChosenArtOfTheSameTier() {
        // Z on its SP (manga is rarer → upgrade); S on its second SP (already the max tier → keep it).
        val art = FakeArt(modified = mapOf("OP05-001" to "OP05-001_p2", "OP05-002" to "OP05-002_p3"))
        val vm = rarityBrowser(art)
        vm.applyMaxRarityToSelected()
        assertTrue("apply OP05-001_p3" in art.calls)
        assertTrue(art.calls.none { it.startsWith("apply OP05-002") }, "the hand-picked second SP stays")
        assertTrue(vm.state.value.bulkMessage!!.contains("1 already had it."))
    }

    @Test
    fun useSpKeepsAHandPickedSecondSp() {
        val art = FakeArt(modified = mapOf("OP05-002" to "OP05-002_p3"))
        val vm = rarityBrowser(art)
        vm.applySpToSelected()
        assertTrue(art.calls.none { it.startsWith("apply OP05-002") })
    }

    @Test
    fun useMaxRarityListsCardsWithNoSpecialArt() {
        val vm = browser(FakeRepository { catalog }, art = FakeArt())
        listOf("OP01-061", "OP01-001").forEach(vm::toggleSelection) // Kaido has only regular art
        vm.applyMaxRarityToSelected()
        assertEquals("Applied max rarity to 1 card. 1 has no alt art, SP or manga art: OP01-061.", vm.state.value.bulkMessage)
    }

    @Test
    fun longGroupsInTheSummaryShowOnlyTheCount() {
        fun plainCards(n: Int) = List(n) { i ->
            val id = "ST99-%03d".format(i + 1)
            Card(id, "c$i", "ST99", Category.Character, emptyList(), variants = listOf(Variant(id, VariantKind.Regular)))
        }
        fun summaryFor(count: Int): String {
            val cards = plainCards(count)
            val vm = browser(FakeRepository { Catalog(2, "t", listOf(CardSet("ST99", "x")), cards) }, art = FakeArt())
            vm.selectMany(cards.map { it.id })
            vm.applySpToSelected()
            return vm.state.value.bulkMessage!!
        }
        assertEquals("20 have no SP: ${plainCards(20).joinToString(", ") { it.id }}.", summaryFor(20), "20 is still listed")
        assertEquals("21 have no SP.", summaryFor(21), "over 20: just the count")
    }

    @Test
    fun bulkChangesLeaveCustomImagesAlone() {
        val art = FakeArt(modified = mapOf("OP05-003" to "OP05-003_custom2"))
        val vm = rarityBrowser(art)
        vm.applyAltArtToSelected()
        assertTrue(art.calls.none { "OP05-003" in it })
        assertTrue(vm.state.value.bulkMessage!!.contains("1 kept your custom image: OP05-003."))
    }

    @Test
    fun tiersFollowTheRarityOrder() {
        assertTrue(ArtTier.Manga > ArtTier.SP && ArtTier.SP > ArtTier.AltArt && ArtTier.AltArt > ArtTier.Regular)
        assertEquals(ArtTier.AltArt, ArtTier.of(Variant("x_p1", VariantKind.Foil, listOf("Pirate Foil"))), "special printings rank with alt arts")
        val z = rarityCatalog.cards.first()
        assertEquals("OP05-001_p1", bulkTarget(z, ArtTier.AltArt)?.id)
        assertEquals("OP05-001_p3", bulkTarget(z, ArtTier.Manga)?.id)
        assertEquals("OP05-001_p3", maxRarityTarget(z)?.id)
        assertEquals("OP05-003_p1", maxRarityTarget(rarityCatalog.cards[2])?.id, "only an alt art → the alt art")
        val foilFirst = Card("X-001", "x", "X", Category.Character, emptyList(), variants = listOf(
            Variant("X-001", VariantKind.Regular), Variant("X-001_p1", VariantKind.Foil, listOf("Pirate Foil")), Variant("X-001_p2", VariantKind.AltArt),
        ))
        assertEquals("X-001_p2", bulkTarget(foilFirst, ArtTier.AltArt)?.id, "a true Alternate art beats other special printings")
        val excluded = Card("X-002", "x", "X", Category.Character, emptyList(), variants = listOf(
            Variant("X-002", VariantKind.Regular), Variant("X-002_p1", VariantKind.Foil, listOf("Jolly Roger Foil")),
        ))
        assertNull(bulkTarget(excluded, ArtTier.AltArt), "excluded printings are never targets")
    }

    @Test
    fun closingTheQuickSwapWindowRefreshesBadgesAndThumbnails() {
        val art = FakeArt()
        val vm = browser(FakeRepository { catalog }, art = art)
        val revision = vm.state.value.thumbnailRevision

        art.modified = mapOf("OP01-061" to "OP01-061_custom2") // swapped in the window
        vm.onQuickSwapClosed()

        assertEquals(setOf("OP01-061"), vm.state.value.modifiedCardIds)
        assertEquals(revision + 1, vm.state.value.thumbnailRevision, "visible thumbnails reload")
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
