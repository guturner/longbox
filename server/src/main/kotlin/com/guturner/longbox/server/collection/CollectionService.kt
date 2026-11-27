package com.guturner.longbox.server.collection

import com.guturner.longbox.domain.barcode.BarcodeParseResult
import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.server.events.EntryEvent
import com.guturner.longbox.server.events.EntryEventPublisher
import com.guturner.longbox.server.persistence.CollectionStore
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Application service for the collection. Re-validates every barcode: never trust the client. */
class CollectionService(
    private val store: CollectionStore,
    private val worker: ScanWorker,
    private val events: EntryEventPublisher,
    private val now: () -> Instant = Instant::now,
) {

    suspend fun list(userId: UUID): List<CollectionEntry> = store.list(userId)

    suspend fun addScan(
        userId: UUID,
        code: String,
        addOn: String?,
        clientMutationId: String? = null,
        scannedAtEpochMillis: Long? = null,
    ): Outcome<CollectionEntry> {
        val barcode = when (val parsed = ComicBarcode.parse(code, addOn)) {
            is BarcodeParseResult.Invalid -> return Outcome.Failure(DomainError.InvalidBarcode(parsed.error))
            is BarcodeParseResult.Valid -> parsed.barcode
        }
        // Abuse ceiling (OWASP API4:2023 unrestricted resource consumption); generous for a box-scanning session.
        if (store.countToday(userId, now().minus(Duration.ofDays(1))) >= MAX_SCANS_PER_DAY) {
            return Outcome.Failure(DomainError.RateLimited(retryAfterSeconds = 3_600))
        }
        if (clientMutationId != null && (clientMutationId.length > 64 || !clientMutationId.all { it.isLetterOrDigit() || it == '-' })) {
            return Outcome.Failure(DomainError.Unexpected("bad clientMutationId"))
        }
        // Keep the time it was scanned in the shop, not the time the phone got home; reject clock nonsense.
        val addedAt = scannedAtEpochMillis?.let(Instant::ofEpochMilli)
            ?.takeIf { it.isBefore(now().plusSeconds(300)) && it.isAfter(now().minus(Duration.ofDays(365))) }
            ?: now()
        val (id, isCopyIncrement) = store.insertPending(userId, barcode.fullCode, addedAt, clientMutationId)
        if (isCopyIncrement) {
            val entry = store.find(userId, id)
            val newCopyCount = entry?.copyCount ?: 1
            events.publish(EntryEvent.CopyRescanned(id, now(), newCopyCount))
        } else {
            events.publish(EntryEvent.Scanned(id, now(), barcode.fullCode))
            worker.wakeUp()
        }
        return store.find(userId, id)?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.Unexpected("insert failed"))
    }

    suspend fun remove(userId: UUID, entryId: String): Outcome<Unit> {
        val id = runCatching { UUID.fromString(entryId) }.getOrNull() ?: return Outcome.Failure(DomainError.NotFound)
        return if (store.delete(userId, id)) {
            events.publish(EntryEvent.Removed(id, now()))
            Outcome.Success(Unit)
        } else Outcome.Failure(DomainError.NotFound)
    }

    private companion object {
        const val MAX_SCANS_PER_DAY = 2_000
    }
}
