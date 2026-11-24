package com.guturner.longbox.server.collection

import com.guturner.longbox.domain.barcode.BarcodeParseResult
import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.usecase.ResolveBarcodeUseCase
import com.guturner.longbox.server.persistence.CollectionStore
import com.guturner.longbox.server.persistence.QueuedScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.math.min

/**
 * Drains the scan queue at Metron's pace. Scanning a longbox can produce 100+ codes in a few
 * minutes; Metron allows 20 requests/minute and a new comic costs ~2 requests (UPC search +
 * issue detail), so scans are accepted instantly and resolved in the background.
 */
class ScanWorker(
    private val store: CollectionStore,
    private val resolve: ResolveBarcodeUseCase,
    private val now: () -> Instant = Instant::now,
) {
    private val log = LoggerFactory.getLogger(ScanWorker::class.java)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun wakeUp() {
        wake.trySend(Unit)
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val scan = runCatching { store.claimNextDue(now(), MAX_NOT_FOUND_RETRIES) }
                .onFailure { log.error("Queue poll failed", it) }
                .getOrNull()
            if (scan == null) {
                withTimeoutOrNull(IDLE_POLL.toMillis()) { wake.receive() }
                continue
            }
            runCatching { process(scan) }.onFailure { log.error("Scan {} failed", scan.entryId, it) }
        }
    }

    private suspend fun process(scan: QueuedScan) {
        val barcode = (ComicBarcode.parse(scan.code) as? BarcodeParseResult.Valid)?.barcode
        if (barcode == null) {
            store.markNotFound(scan.entryId, now().plus(Duration.ofDays(3650)))
            return
        }

        when (val result = resolve(barcode)) {
            is Outcome.Success -> {
                val resolved = result.value
                if (resolved != null) {
                    store.markResolved(scan.entryId, resolved.comic.id.value, resolved.ownedCoverName)
                } else {
                    // New releases often reach Metron a few days after street date; keep checking daily.
                    store.markNotFound(scan.entryId, now().plus(NOT_FOUND_RECHECK))
                }
            }
            is Outcome.Failure -> when (val error = result.error) {
                is DomainError.RateLimited ->
                    store.reschedule(scan.entryId, now().plusSeconds(error.retryAfterSeconds ?: 60), countAttempt = false)
                is DomainError.Offline ->
                    // Network/timeout: don't burn an attempt — retry once the connection is back.
                    store.reschedule(scan.entryId, now().plus(OFFLINE_RECHECK), countAttempt = false)
                is DomainError.Unauthorized -> {
                    // Metron rejected our credentials. Defer for a long interval so we don't
                    // hammer a 401 loop, but allow recovery if the admin updates credentials.
                    log.error("Metron credentials rejected; deferring entry {} for {}h", scan.entryId, CREDENTIAL_RECHECK.toHours())
                    store.reschedule(scan.entryId, now().plus(CREDENTIAL_RECHECK), countAttempt = false)
                }
                else -> {
                    // 5xx or other unexpected errors: exponential backoff, count the attempt.
                    val backoffMinutes = min(60L, 1L shl min(scan.attempts, 6))
                    store.reschedule(scan.entryId, now().plus(Duration.ofMinutes(backoffMinutes)), countAttempt = true)
                }
            }
        }
    }

    private companion object {
        val IDLE_POLL: Duration = Duration.ofSeconds(30)
        val NOT_FOUND_RECHECK: Duration = Duration.ofDays(1)
        val OFFLINE_RECHECK: Duration = Duration.ofMinutes(5)
        val CREDENTIAL_RECHECK: Duration = Duration.ofHours(24)
        const val MAX_NOT_FOUND_RETRIES = 14
    }
}
