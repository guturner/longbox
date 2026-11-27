package com.guturner.longbox.presentation

import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.usecase.CheckOwnershipUseCase
import com.guturner.longbox.domain.usecase.ScanComicUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    /** Behaves like the offline-first repository while roaming: adds are queued on the device. */
    private class RecordingRepository : CollectionRepository {
        val scans = mutableListOf<String>()
        val entries = MutableStateFlow(emptyList<CollectionEntry>())
        override fun observeEntries() = entries
        override suspend fun refresh() = Outcome.Success(Unit)
        override suspend fun remove(id: EntryId) = Outcome.Success(Unit)
        override suspend fun addScan(barcode: ComicBarcode): Outcome<CollectionEntry> {
            scans += barcode.fullCode
            val entry = CollectionEntry(EntryId("local-${scans.size}"), barcode.fullCode, EntryState.PENDING, null, null, 0, isQueuedOnDevice = true)
            entries.value = entries.value + entry
            return Outcome.Success(entry)
        }
    }

    private var now = 0L
    private val repo = RecordingRepository()
    private lateinit var viewModel: ScanViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ScanViewModel(ScanComicUseCase(repo), CheckOwnershipUseCase(repo), nowMillis = { now })
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun testCameraRepeatsAreIgnoredWithinWindow() {
        // Given / When
        assertTrue(viewModel.onDetected("759606095582", "00111"))
        now += 500
        assertFalse(viewModel.onDetected("759606095582", "00111"))
        now += 5_000
        assertTrue(viewModel.onDetected("759606095582", "00111"))

        // Then
        assertEquals(2, repo.scans.size)
        assertEquals(2, viewModel.state.value.sessionCount)
    }

    @Test
    fun testInvalidScanShowsErrorAndCanBeRetriedImmediately() {
        viewModel.onDetected("759606095583", "00111")

        val feedback = viewModel.state.value.recent.first()
        assertTrue(feedback.message.isError)
        assertTrue(viewModel.onDetected("759606095583", "00111"))
        assertTrue(repo.scans.isEmpty())
    }

    @Test
    fun testShopFlowCheckThenBuyWhileRoaming() {
        // Given: in a shop, checking a comic on the shelf
        viewModel.setMode(ScanMode.CHECK)
        viewModel.onDetected("759606095582", "00211")
        assertEquals("Not in your library", viewModel.state.value.ownership?.title)
        assertTrue(repo.scans.isEmpty()) // checking never adds

        // When: decide to buy it
        viewModel.addCheckedComic()

        // Then: queued on the device, and a re-check now says it's owned
        assertEquals(listOf("75960609558200211"), repo.scans)
        assertTrue(viewModel.state.value.recent.first().message.text.contains("saved on this device"))
        now += 10_000
        viewModel.onDetected("759606095582", "00211")
        assertEquals("You own this", viewModel.state.value.ownership?.title)
    }
}
