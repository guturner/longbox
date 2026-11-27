package com.guturner.longbox.server.collection

import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Cover
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.IssueNumber
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.Series
import com.guturner.longbox.domain.model.SeriesId
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScanWorkerTest {

    private class FakeStore : CollectionStore {
        override suspend fun list(userId: UUID): List<CollectionEntry> = emptyList()
        override suspend fun find(userId: UUID, entryId: UUID): CollectionEntry? = null
        override suspend fun insertPending(userId: UUID, code: String, addedAt: Instant, clientMutationId: String?): Pair<UUID, Boolean> = UUID.randomUUID() to false
        override suspend fun ping(): Boolean = true
        override suspend fun delete(userId: UUID, entryId: UUID): Boolean = false
        override suspend fun countToday(userId: UUID, since: Instant): Int = 0
        override suspend fun claimNextDue(now: Instant, maxNotFoundRetries: Int): QueuedScan? = null
        override suspend fun markResolved(entryId: UUID, issueId: Long, ownedCoverName: String?) {}
        override suspend fun markNotFound(entryId: UUID, retryAt: Instant) {}
        override suspend fun reschedule(entryId: UUID, retryAt: Instant, countAttempt: Boolean) {}
    }

    private val fixedNow = Instant.EPOCH
    private val entryId = UUID.randomUUID()

    private fun comic(id: Long) = Comic(
        id = ComicId(id),
        publisher = Publisher(PublisherId(1), "DC Comics"),
        series = Series(SeriesId(1), "Batman", "Batman", 1, 2024),
        number = IssueNumber("1"),
        title = null,
        coverDate = null,
        storeDate = null,
        summary = null,
        coverA = Cover("Cover A", null, null),
        variantCovers = emptyList(),
        credits = emptyList(),
        storyArcs = emptyList(),
        upc = null,
    )

    private fun lookup(result: Outcome<Comic?>) = object : BarcodeLookup {
        override suspend fun findByCode(code: String) = result
    }

    private fun worker(lookup: BarcodeLookup, events: RecordingEventPublisher) =
        ScanWorker(FakeStore(), ResolveBarcodeUseCase(lookup), events, now = { fixedNow })

    @Test
    fun testSuccessfulLookupPublishesLookupSucceeded() = runTest {
        val events = RecordingEventPublisher()
        val w = worker(lookup(Outcome.Success(comic(42))), events)
        val scan = QueuedScan(entryId, "75960609558200111", attempts = 0, state = EntryState.PENDING)

        w.process(scan)

        assertEquals(1, events.recorded.size)
        val event = assertIs<EntryEvent.LookupSucceeded>(events.recorded[0])
        assertEquals(entryId, event.entryId)
        assertEquals(42L, event.comicId)
    }

    @Test
    fun testPermanentlyFailedLookupPublishesLookupFailedPermanent() = runTest {
        val events = RecordingEventPublisher()
        val w = worker(lookup(Outcome.Success(null)), events)
        // "INVALID" is non-numeric — parse() returns BarcodeParseResult.Invalid.
        val scan = QueuedScan(entryId, "INVALID", attempts = 0, state = EntryState.PENDING)

        w.process(scan)

        assertEquals(1, events.recorded.size)
        val event = assertIs<EntryEvent.LookupFailed>(events.recorded[0])
        assertEquals(entryId, event.entryId)
        assertTrue(event.permanent)
    }

    @Test
    fun testRetryableLookupPublishesLookupFailedNotPermanent() = runTest {
        val events = RecordingEventPublisher()
        val w = worker(lookup(Outcome.Success(null)), events)
        val scan = QueuedScan(entryId, "75960609558200111", attempts = 0, state = EntryState.PENDING)

        w.process(scan)

        assertEquals(1, events.recorded.size)
        val event = assertIs<EntryEvent.LookupFailed>(events.recorded[0])
        assertEquals(entryId, event.entryId)
        assertEquals(1, event.attempts)
        assertFalse(event.permanent)
    }
}
