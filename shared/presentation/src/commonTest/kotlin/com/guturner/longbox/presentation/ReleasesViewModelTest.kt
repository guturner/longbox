package com.guturner.longbox.presentation

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.repository.ComicCatalog
import com.guturner.longbox.domain.usecase.FOLLOWED_PUBLISHERS
import com.guturner.longbox.domain.usecase.GetWeeklyReleasesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReleasesViewModelTest {

    private val dc = Publisher(PublisherId(1), "DC Comics")
    private val image = Publisher(PublisherId(2), "Image Comics")
    private val dynamite = Publisher(PublisherId(3), "Dynamite Entertainment")

    private fun makeComic(id: Long, publisher: Publisher) = Comic(
        id = ComicId(id),
        publisher = publisher,
        series = com.guturner.longbox.domain.model.Series(
            com.guturner.longbox.domain.model.SeriesId(id),
            "Series $id", "Series $id", 1, 2024
        ),
        number = com.guturner.longbox.domain.model.IssueNumber("1"),
        title = null, coverDate = null, storeDate = null, summary = null,
        coverA = com.guturner.longbox.domain.model.Cover("Cover A", null, null),
        variantCovers = emptyList(), credits = emptyList(), storyArcs = emptyList(), upc = null,
    )

    private val dcComic = makeComic(1, dc)
    private val imageComic = makeComic(2, image)
    private val dynamiteComic = makeComic(3, dynamite)

    private fun fakeCatalog(comics: List<Comic>): ComicCatalog = object : ComicCatalog {
        override suspend fun releases(from: CalendarDate, to: CalendarDate) = Outcome.Success(comics)
        override suspend fun comic(id: ComicId) = Outcome.Success(comics.first { it.id == id })
        override suspend fun creator(id: CreatorId): Outcome<Creator> = error("not needed")
        override suspend fun storyArc(id: StoryArcId): Outcome<StoryArc> = error("not needed")
    }

    private val fakeCollection = object : CollectionRepository {
        override fun observeEntries() = MutableStateFlow(emptyList<com.guturner.longbox.domain.model.CollectionEntry>())
        override suspend fun refresh() = Outcome.Success(Unit)
        override suspend fun addScan(barcode: com.guturner.longbox.domain.barcode.ComicBarcode, targetContainerId: com.guturner.longbox.domain.model.ContainerId?) = error("not needed")
        override suspend fun remove(id: com.guturner.longbox.domain.model.EntryId) = Outcome.Success(Unit)
    }

    private val today = CalendarDate(2024, 10, 2)
    private val clock = Clock { today }

    @BeforeTest
    fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(comics: List<Comic>): ReleasesViewModel {
        val useCase = GetWeeklyReleasesUseCase(fakeCatalog(comics), clock)
        return ReleasesViewModel(useCase, fakeCollection, clock)
    }

    @Test
    fun initialStateShowsOnlyFollowedPublishers() = runTest {
        val vm = viewModel(listOf(dcComic, imageComic))
        val state = vm.state.value
        val ready = assertIs<Loadable.Ready<List<Comic>>>(state.comics)
        // Image is not in FOLLOWED_PUBLISHERS, so it should be filtered out
        assertEquals(listOf(dcComic), ready.value)
        assertEquals(FOLLOWED_PUBLISHERS, state.enabledPublishers)
    }

    @Test
    fun availablePublishersListsAllFromWeek() = runTest {
        val vm = viewModel(listOf(dcComic, imageComic, dynamiteComic))
        val available = vm.state.value.availablePublishers
        assertTrue("DC Comics" in available)
        assertTrue("Image Comics" in available)
        assertTrue("Dynamite Entertainment" in available)
    }

    @Test
    fun togglePublisherRemovesItFromFilter() = runTest {
        val vm = viewModel(listOf(dcComic, dynamiteComic))
        // Both start enabled (both in FOLLOWED_PUBLISHERS); remove DC
        vm.togglePublisher("DC Comics")
        val state = vm.state.value
        assertFalse("DC Comics" in state.enabledPublishers)
        val ready = assertIs<Loadable.Ready<List<Comic>>>(state.comics)
        // Only Dynamite should remain
        assertEquals(listOf(dynamiteComic), ready.value)
    }

    @Test
    fun togglePublisherAddsItBack() = runTest {
        val vm = viewModel(listOf(dcComic, imageComic))
        // Add Image (not in defaults)
        vm.togglePublisher("Image Comics")
        val state = vm.state.value
        assertTrue("Image Comics" in state.enabledPublishers)
        val ready = assertIs<Loadable.Ready<List<Comic>>>(state.comics)
        // Both DC and Image now shown
        val ids = ready.value.map { it.id }.toSet()
        assertTrue(dcComic.id in ids)
        assertTrue(imageComic.id in ids)
    }

    @Test
    fun togglePublisherTwiceRestoresOriginalSet() = runTest {
        val vm = viewModel(listOf(dcComic))
        val originalEnabled = vm.state.value.enabledPublishers
        vm.togglePublisher("DC Comics")
        vm.togglePublisher("DC Comics")
        assertEquals(originalEnabled, vm.state.value.enabledPublishers)
    }

    @Test
    fun publisherFilterPersistsAcrossWeekNavigation() = runTest {
        val vm = viewModel(listOf(dcComic, imageComic))
        // Enable Image
        vm.togglePublisher("Image Comics")
        assertTrue("Image Comics" in vm.state.value.enabledPublishers)
        // Navigate to previous week — filter should carry over
        vm.previousWeek()
        assertTrue("Image Comics" in vm.state.value.enabledPublishers)
    }
}
