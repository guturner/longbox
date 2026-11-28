package com.guturner.longbox.presentation

import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.repository.ContainerRepository
import com.guturner.longbox.domain.usecase.CheckOwnershipUseCase
import com.guturner.longbox.domain.usecase.ScanComicUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
        override suspend fun addScan(barcode: ComicBarcode, targetContainerId: com.guturner.longbox.domain.model.ContainerId?): Outcome<CollectionEntry> {
            scans += barcode.fullCode
            val entry = CollectionEntry(EntryId("local-${scans.size}"), barcode.fullCode, EntryState.PENDING, null, null, 0, isQueuedOnDevice = true)
            entries.value = entries.value + entry
            return Outcome.Success(entry)
        }
    }

    private object NoOpContainerRepository : ContainerRepository {
        override fun observeContainers(): Flow<List<Container>> = flowOf(emptyList())
        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun createContainer(name: String, kind: ContainerKind, clientMutationId: String?): Outcome<Container> =
            Outcome.Failure(com.guturner.longbox.domain.model.DomainError.Unexpected("stub"))
        override suspend fun renameContainer(id: ContainerId, newName: String): Outcome<Container> =
            Outcome.Failure(com.guturner.longbox.domain.model.DomainError.Unexpected("stub"))
        override suspend fun deleteContainer(id: ContainerId): Outcome<Unit> = Outcome.Success(Unit)
        override suspend fun moveToContainer(entryId: EntryId, containerId: ContainerId): Outcome<CollectionEntry> =
            Outcome.Failure(com.guturner.longbox.domain.model.DomainError.Unexpected("stub"))
        override suspend fun removeFromContainer(entryId: EntryId): Outcome<CollectionEntry> =
            Outcome.Failure(com.guturner.longbox.domain.model.DomainError.Unexpected("stub"))
    }

    private var now = 0L
    private val repo = RecordingRepository()
    private lateinit var viewModel: ScanViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ScanViewModel(ScanComicUseCase(repo), CheckOwnershipUseCase(repo), nowMillis = { now }, containerRepository = NoOpContainerRepository)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun testCameraRepeatsAreIgnoredWithinWindow() {
        // Given / When
        assertTrue(viewModel.onDetected("759606095582", "00111"))  // accepted; added to sessionCodes
        now += 500
        assertFalse(viewModel.onDetected("759606095582", "00111")) // rejected: within repeat window
        now += 5_000
        // Session de-dup keeps this blocked even after the window expires (ADD mode).
        // The user must close and reopen the scanner to scan the same code again.
        assertFalse(viewModel.onDetected("759606095582", "00111"))

        // Then: only one successful scan
        assertEquals(1, repo.scans.size)
        assertEquals(1, viewModel.state.value.sessionCount)
    }

    @Test
    fun testCheckModeIsNotSessionDeduped() {
        // After adding a comic in ADD mode, switching to CHECK mode and re-scanning it should
        // still produce a fresh ownership result (not be silently blocked by sessionCodes).
        viewModel.onDetected("759606095582", "00111")           // ADD → added, in sessionCodes
        now += 5_000                                            // advance past the repeat window
        viewModel.setMode(ScanMode.CHECK)
        assertTrue(viewModel.onDetected("759606095582", "00111")) // CHECK → allowed, not de-duped
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
