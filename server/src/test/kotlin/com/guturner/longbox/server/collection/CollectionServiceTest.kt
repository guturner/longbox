package com.guturner.longbox.server.collection

import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.BarcodeLookup
import com.guturner.longbox.domain.usecase.ResolveBarcodeUseCase
import com.guturner.longbox.server.events.EntryEvent
import com.guturner.longbox.server.events.RecordingEventPublisher
import com.guturner.longbox.server.persistence.CollectionStore
import com.guturner.longbox.server.persistence.QueuedScan
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CollectionServiceTest {

    private val userId = UUID.randomUUID()

    /** Minimal in-memory CollectionStore — only the operations CollectionService touches. */
    private class FakeStore : CollectionStore {
        val rows = mutableMapOf<UUID, CollectionEntry>()

        override suspend fun insertPending(userId: UUID, code: String, addedAt: Instant, clientMutationId: String?): UUID {
            val id = UUID.randomUUID()
            rows[id] = CollectionEntry(EntryId(id.toString()), code, EntryState.PENDING, null, null, addedAt.toEpochMilli())
            return id
        }

        override suspend fun find(userId: UUID, entryId: UUID): CollectionEntry? = rows[entryId]
        override suspend fun delete(userId: UUID, entryId: UUID): Boolean = rows.remove(entryId) != null
        override suspend fun list(userId: UUID): List<CollectionEntry> = rows.values.toList()
        override suspend fun ping(): Boolean = true
        override suspend fun countToday(userId: UUID, since: Instant): Int = 0
        override suspend fun claimNextDue(now: Instant, maxNotFoundRetries: Int): QueuedScan? = null
        override suspend fun markResolved(entryId: UUID, issueId: Long, ownedCoverName: String?) {}
        override suspend fun markNotFound(entryId: UUID, retryAt: Instant) {}
        override suspend fun reschedule(entryId: UUID, retryAt: Instant, countAttempt: Boolean) {}
    }

    private fun buildService(store: FakeStore, events: RecordingEventPublisher): CollectionService {
        val noOpLookup = object : BarcodeLookup { override suspend fun findByCode(code: String) = Outcome.Success<Comic?>(null) }
        val worker = ScanWorker(store, ResolveBarcodeUseCase(noOpLookup), events)
        return CollectionService(store, worker, events)
    }

    @Test
    fun testScanPublishesScannedEvent() = runTest {
        val events = RecordingEventPublisher()
        val service = buildService(FakeStore(), events)

        val result = service.addScan(userId, "759606095582", "00111")

        assertIs<Outcome.Success<*>>(result)
        assertEquals(1, events.recorded.size)
        val event = assertIs<EntryEvent.Scanned>(events.recorded[0])
        assertEquals("75960609558200111", event.code)
    }

    @Test
    fun testRemovePublishesRemovedEvent() = runTest {
        val events = RecordingEventPublisher()
        val store = FakeStore()
        val service = buildService(store, events)

        // Insert an entry directly so we have an ID to remove.
        val entryId = store.insertPending(userId, "75960609558200111", Instant.EPOCH, null)
        events.recorded.clear() // ignore the insert; we're testing remove

        val result = service.remove(userId, entryId.toString())

        assertIs<Outcome.Success<*>>(result)
        assertEquals(1, events.recorded.size)
        val event = assertIs<EntryEvent.Removed>(events.recorded[0])
        assertEquals(entryId, event.entryId)
    }
}
