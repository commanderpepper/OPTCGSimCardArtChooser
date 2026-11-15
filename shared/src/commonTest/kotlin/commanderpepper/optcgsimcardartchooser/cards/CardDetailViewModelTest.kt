package commanderpepper.optcgsimcardartchooser.cards

import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardDetailViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val chopper = Card(
        id = "EB01-006", name = "Tony Tony.Chopper", setId = "EB01", category = Category.Character, colors = emptyList(),
        variants = listOf(
            Variant("EB01-006", VariantKind.Regular),
            Variant("EB01-006_p1", VariantKind.AltArt, listOf("Alternate Art")),
            Variant("EB01-006_p2", VariantKind.Manga, listOf("Alternate Art", "Manga")),
            Variant("EB01-006_p3", VariantKind.Foil, listOf("Jolly Roger Foil")),
            Variant("EB01-006_r1", VariantKind.Reprint, listOf("Reprint")),
        ),
    )
    private val catalog = Catalog(2, "t", listOf(CardSet("EB01", "Memorial Collection")), listOf(chopper))

    /** Records calls; previews exist unless listed in [unavailable]; [failApply] makes apply throw. */
    private class FakeArt(
        var applied: String? = null,
        private val unavailable: Set<String> = emptySet(),
        private val failApply: Boolean = false,
    ) : CardArt {
        val calls = mutableListOf<String>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun variantPreview(card: Card, variant: Variant): ImageBitmap? =
            if (variant.id in unavailable) null else ImageBitmap(48, 67)

        override suspend fun originalPreview(cardsPath: String, card: Card): ImageBitmap? = ImageBitmap(48, 67)

        override suspend fun apply(cardsPath: String, card: Card, variant: Variant) {
            calls += "apply $cardsPath ${variant.id}"
            gate?.await()
            if (failApply) error("disk full")
            applied = variant.id
        }

        override suspend fun restoreOriginal(cardsPath: String, card: Card) {
            calls += "restore $cardsPath"
            applied = null
        }

        override suspend fun appliedVariant(cardId: String): String? = applied

        override suspend fun appliedVariants(): Map<String, String> = applied?.let { mapOf("EB01-006" to it) }.orEmpty()

        override suspend fun reapply(cardsPath: String, card: Card, variant: Variant) = ReapplyOutcome.AlreadyApplied

        /** The card's custom image, if any; paths ending in ".txt" are rejected like unreadable files. */
        var custom: ImageBitmap? = null
        var customSize = CustomImageInfo(600, 838)

        override suspend fun setCustomImage(card: Card, sourcePath: String): CustomImageInfo {
            calls += "custom $sourcePath"
            if (sourcePath.endsWith(".txt")) error("only JPG and PNG images are supported")
            custom = ImageBitmap(48, 67)
            return customSize
        }

        override suspend fun customPreview(card: Card): ImageBitmap? = custom

        override suspend fun removeCustomImage(card: Card) {
            calls += "remove custom"
            custom = null
        }
    }

    private class FakeThumbnails : CardThumbnails {
        val invalidated = mutableListOf<String>()
        override suspend fun load(cardsPath: String, card: Card): ImageBitmap? = null
        override fun invalidate(cardId: String) { invalidated += cardId }
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

    private fun detail(
        cardId: String = "EB01-006",
        art: FakeArt = FakeArt(),
        thumbnails: FakeThumbnails = FakeThumbnails(),
        savedFolder: String? = "/sim",
    ) = CardDetailViewModel(
        cardId,
        object : CatalogRepository { override suspend fun load() = catalog },
        art, thumbnails, SimCardsFolder(FakeStore(savedFolder), FakeLocator),
    )

    @Test
    fun optionsAreTheSimOriginalThenInScopeVariantsWithPreviews() {
        val vm = detail()
        val state = vm.state.value
        assertEquals("Tony Tony.Chopper", state.card?.name)
        assertEquals(
            listOf(CardDetailState.ORIGINAL_ID, "EB01-006", "EB01-006_p1", "EB01-006_p2"),
            state.options.map { it.id },
            "Jolly Roger foil and reprint are excluded",
        )
        assertEquals(listOf("Sim original", "Regular", "Alternate art", "Manga"), state.options.map { it.title })
        assertTrue(state.options.all { it.image is ArtImage.Loaded })
        assertEquals(CardDetailState.ORIGINAL_ID, state.appliedId)
        assertTrue(state.canApply)
    }

    @Test
    fun selectingAVariantAppliesItAndRefreshesTheThumbnail() {
        val art = FakeArt()
        val thumbnails = FakeThumbnails()
        val vm = detail(art = art, thumbnails = thumbnails)

        vm.select("EB01-006_p2")

        assertEquals(listOf("apply /sim/Cards EB01-006_p2"), art.calls)
        assertEquals("EB01-006_p2", vm.state.value.appliedId)
        assertNull(vm.state.value.applyingId)
        assertEquals(listOf("EB01-006"), thumbnails.invalidated)
        assertEquals("Manga is now used in the sim.", vm.state.value.message)
    }

    @Test
    fun selectingTheSimOriginalRestoresIt() {
        val art = FakeArt(applied = "EB01-006_p1")
        val vm = detail(art = art)
        assertEquals("EB01-006_p1", vm.state.value.appliedId, "starts from the saved selection")

        vm.select(CardDetailState.ORIGINAL_ID)
        assertEquals(listOf("restore /sim/Cards"), art.calls)
        assertEquals(CardDetailState.ORIGINAL_ID, vm.state.value.appliedId)
    }

    @Test
    fun selectingTheOptionAlreadyInUseDoesNothing() {
        val art = FakeArt(applied = "EB01-006_p1")
        detail(art = art).select("EB01-006_p1")
        assertTrue(art.calls.isEmpty())
    }

    @Test
    fun onlyOneApplyRunsAtATime() {
        val art = FakeArt().apply { gate = CompletableDeferred() }
        val vm = detail(art = art)
        vm.select("EB01-006_p1")
        assertEquals("EB01-006_p1", vm.state.value.applyingId)

        vm.select("EB01-006_p2") // ignored while the first is running
        art.gate!!.complete(Unit)
        assertEquals(listOf("apply /sim/Cards EB01-006_p1"), art.calls)
        assertEquals("EB01-006_p1", vm.state.value.appliedId)
    }

    @Test
    fun failedApplyKeepsThePreviousSelectionAndExplains() {
        val vm = detail(art = FakeArt(failApply = true))
        vm.select("EB01-006_p1")
        assertEquals(CardDetailState.ORIGINAL_ID, vm.state.value.appliedId)
        assertEquals("Couldn't apply Alternate art: disk full", vm.state.value.message)
        vm.dismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun unavailableArtShowsThePlaceholderAndCantBeSelected() {
        val art = FakeArt(unavailable = setOf("EB01-006_p1"))
        val vm = detail(art = art)
        assertIs<ArtImage.Unavailable>(vm.state.value.options.first { it.id == "EB01-006_p1" }.image)
        vm.select("EB01-006_p1")
        assertTrue(art.calls.isEmpty())
    }

    @Test
    fun withoutAVerifiedFolderNothingCanBeApplied() {
        val art = FakeArt()
        val vm = detail(art = art, savedFolder = null)
        assertFalse(vm.state.value.canApply)
        assertIs<ArtImage.Unavailable>(vm.state.value.options.first().image, "no folder → no original preview")
        vm.select("EB01-006_p1")
        assertTrue(art.calls.isEmpty())
    }

    @Test
    fun choosingAnImageConvertsItAddsItAsAnOptionAndAppliesIt() {
        val art = FakeArt()
        val thumbnails = FakeThumbnails()
        val vm = detail(art = art, thumbnails = thumbnails)
        assertTrue(vm.state.value.canAddCustomImage)

        vm.useCustomImage("C:/pics/chopper.png")

        assertEquals(listOf("custom C:/pics/chopper.png", "apply /sim/Cards EB01-006_custom"), art.calls)
        val custom = vm.state.value.options.last()
        assertEquals("EB01-006_custom" to "Custom image", custom.id to custom.title)
        assertIs<ArtImage.Loaded>(custom.image)
        assertEquals("EB01-006_custom", vm.state.value.appliedId)
        assertFalse(vm.state.value.canAddCustomImage, "one custom image per card; replace it instead")
        assertEquals("Your image is now used in the sim.", vm.state.value.message)
        assertEquals(listOf("EB01-006"), thumbnails.invalidated)
    }

    @Test
    fun lowResolutionImagesWorkWithANote() {
        val art = FakeArt().apply { customSize = CustomImageInfo(300, 420) }
        val vm = detail(art = art)
        vm.useCustomImage("small.jpg")
        assertEquals(
            "Your image is now used in the sim. It's 300×420, smaller than the sim's 480×671, so it may look soft.",
            vm.state.value.message,
        )
    }

    @Test
    fun cancelledPickerDoesNothingAndBadFilesExplainWhy() {
        val art = FakeArt()
        val vm = detail(art = art)
        vm.useCustomImage(null)
        assertTrue(art.calls.isEmpty())

        vm.useCustomImage("notes.txt")
        assertEquals("Couldn't use that image: only JPG and PNG images are supported", vm.state.value.message)
        assertTrue(vm.state.value.canAddCustomImage)
        assertEquals(CardDetailState.ORIGINAL_ID, vm.state.value.appliedId)
    }

    @Test
    fun existingCustomImageAppearsAndCanBeSelected() {
        val art = FakeArt(applied = "EB01-006_custom").apply { custom = ImageBitmap(48, 67) }
        val vm = detail(art = art)
        assertEquals("EB01-006_custom", vm.state.value.options.last().id)
        assertEquals("EB01-006_custom", vm.state.value.appliedId, "the saved custom selection is recognised")
    }

    @Test
    fun removingTheCustomImageInUseRestoresTheOriginalFirst() {
        val art = FakeArt()
        val vm = detail(art = art)
        vm.useCustomImage("me.jpg")
        art.calls.clear()

        vm.removeCustomImage()
        assertEquals(listOf("restore /sim/Cards", "remove custom"), art.calls)
        assertEquals(CardDetailState.ORIGINAL_ID, vm.state.value.appliedId)
        assertTrue(vm.state.value.options.none { it.isCustom })
        assertTrue(vm.state.value.canAddCustomImage)
    }

    @Test
    fun removingAnUnusedCustomImageLeavesTheSimAlone() {
        val art = FakeArt().apply { custom = ImageBitmap(48, 67) }
        val vm = detail(art = art)
        vm.removeCustomImage()
        assertEquals(listOf("remove custom"), art.calls)
    }

    @Test
    fun unknownCardShowsAnError() {
        val vm = detail(cardId = "XX99-999")
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }
}
