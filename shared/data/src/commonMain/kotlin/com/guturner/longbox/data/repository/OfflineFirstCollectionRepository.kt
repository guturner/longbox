package com.guturner.longbox.data.repository

import com.guturner.longbox.contract.API_V1
import com.guturner.longbox.contract.CollectionResponse
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.contract.CreatorDto
import com.guturner.longbox.contract.ScanRequest
import com.guturner.longbox.contract.StoryArcDto
import com.guturner.longbox.data.local.CachedLibrary
import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.data.local.Outbox
import com.guturner.longbox.data.local.PendingChange
import com.guturner.longbox.data.local.StoreKeys
import com.guturner.longbox.data.remote.LongboxApi
import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.repository.SyncController
import com.guturner.longbox.domain.repository.SyncState
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Offline-first collection (outbox pattern on the device):
 *  - Reads come from the last synced library cache merged with queued local changes, so the
 *    library and "do I own this?" work with no connection.
 *  - Writes go to a durable outbox first and are replayed, in order, whenever the home server
 *    (and its database) is reachable. Each queued scan carries a client id, so replays are idempotent.
 *  - After the outbox drains, the cache is replaced by the server's copy ("cache bust").
 */
@OptIn(ExperimentalUuidApi::class)
class OfflineFirstCollectionRepository(
    private val api: LongboxApi,
    private val store: KeyValueStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) : CollectionRepository, SyncController {

    private val json = api.json
    private val cache = MutableStateFlow<CachedLibrary?>(null)
    private val outbox = MutableStateFlow(Outbox())
    private val syncState = MutableStateFlow(SyncState(serverReachable = false, pendingChanges = 0, lastSyncedAtEpochMillis = null))
    private val syncMutex = Mutex()
    private val outboxMutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()

    override val state: StateFlow<SyncState> = syncState.asStateFlow()

    init {
        scope.launch {
            cache.value = store.read(StoreKeys.LIBRARY)?.let { runCatching { json.decodeFromString(CachedLibrary.serializer(), it) }.getOrNull() }
            outbox.value = store.read(StoreKeys.OUTBOX)?.let { runCatching { json.decodeFromString(Outbox.serializer(), it) }.getOrNull() } ?: Outbox()
            syncState.update { it.copy(pendingChanges = outbox.value.changes.size, lastSyncedAtEpochMillis = cache.value?.syncedAtEpochMillis) }
            loaded.complete(Unit)
            syncNow()
            // While roaming, keep probing for home quietly; once there, the outbox drains on its own.
            while (true) {
                delay(if (syncState.value.serverReachable) ONLINE_INTERVAL_MS else OFFLINE_PROBE_INTERVAL_MS)
                if (!syncState.value.serverReachable || outbox.value.changes.isNotEmpty()) syncNow()
            }
        }
    }

    override fun observeEntries(): Flow<List<CollectionEntry>> =
        combine(cache, outbox) { cached, queued -> merge(cached, queued) }

    override suspend fun refresh(): Outcome<Unit> = syncNow()

    override suspend fun addScan(barcode: ComicBarcode, targetContainerId: ContainerId?): Outcome<CollectionEntry> {
        loaded.await()
        val change = PendingChange.AddScan(Uuid.random().toString(), nowMillis(), barcode.upc, barcode.supplement, targetContainerId?.value)
        enqueue(change)
        scope.launch { syncNow() }
        return Outcome.Success(asLocalEntry(change))
    }

    override suspend fun remove(id: EntryId): Outcome<Unit> {
        loaded.await()
        if (id.value.startsWith(LOCAL_PREFIX)) {
            // Never reached the server: just drop it from the outbox.
            val changeId = id.value.removePrefix(LOCAL_PREFIX)
            updateOutbox { changes -> changes.filterNot { it.id == changeId } }
        } else {
            enqueue(PendingChange.RemoveEntry(Uuid.random().toString(), nowMillis(), id.value))
            scope.launch { syncNow() }
        }
        return Outcome.Success(Unit)
    }

    override suspend fun syncNow(): Outcome<Unit> = syncMutex.withLock {
        loaded.await()
        syncState.update { it.copy(isSyncing = true) }
        try {
            if (!api.isServerReachable()) {
                syncState.update { it.copy(serverReachable = false) }
                return Outcome.Failure(DomainError.Offline)
            }
            syncState.update { it.copy(serverReachable = true) }

            val done = mutableSetOf<String>()
            var stoppedWith: Outcome.Failure? = null
            for (change in outbox.value.changes) {
                if (outbox.value.changes.none { it.id == change.id }) continue // cancelled meanwhile
                when (val result = replay(change)) {
                    is Outcome.Success -> done += change.id
                    is Outcome.Failure -> when (result.error) {
                        // Permanent: the server will never accept it. Drop it rather than block the queue.
                        is DomainError.InvalidBarcode, DomainError.NotFound -> done += change.id
                        // Transient (connection dropped, signed out, throttled): stop, keep order, retry later.
                        else -> { stoppedWith = result; break }
                    }
                }
            }

            // Fetch before dropping replayed changes, so queued items don't blink out of the library
            // between "sent" and "server copy cached". Replays are idempotent if this fetch fails.
            val fresh = if (stoppedWith == null) api.get<CollectionResponse> { url.takeFrom("$API_V1/collection") } else null
            if (fresh is Outcome.Success) {
                val previous = cache.value
                persistCache(
                    CachedLibrary(
                        entries = fresh.value.entries,
                        syncedAtEpochMillis = nowMillis(),
                        creators = previous?.creators.orEmpty(),
                        arcs = previous?.arcs.orEmpty(),
                    ),
                )
            }
            if (fresh is Outcome.Success) {
                updateOutbox { changes -> changes.filterNot { it.id in done } }
            } else {
                // No fresh copy: drop only completed removals (and hide them in the cache);
                // completed adds stay queued and are safely re-sent next time.
                val removals = outbox.value.changes.filterIsInstance<PendingChange.RemoveEntry>().filter { it.id in done }
                if (removals.isNotEmpty()) {
                    val gone = removals.map { it.entryId }.toSet()
                    cache.value?.let { persistCache(it.copy(entries = it.entries.filterNot { e -> e.id in gone })) }
                    updateOutbox { changes -> changes.filterNot { c -> removals.any { it.id == c.id } } }
                }
            }
            stoppedWith?.let { return it }
            if (fresh is Outcome.Failure) return fresh
            Outcome.Success(Unit)
        } finally {
            syncState.update { it.copy(isSyncing = false) }
        }
    }

    // ---- read-through helpers used by the catalog for offline detail views ----

    internal fun cachedComicDto(id: Long) = cache.value?.entries?.firstNotNullOfOrNull { e -> e.comic?.takeIf { it.id == id } }
    internal fun cachedCreator(id: Long) = cache.value?.creators?.get(id)
    internal fun cachedArc(id: Long) = cache.value?.arcs?.get(id)

    internal suspend fun rememberCreator(dto: CreatorDto) =
        cache.value?.let { persistCache(it.copy(creators = it.creators + (dto.id to dto))) }

    internal suspend fun rememberArc(dto: StoryArcDto) =
        cache.value?.let { persistCache(it.copy(arcs = it.arcs + (dto.id to dto))) }

    // ---- internals ----

    private suspend fun replay(change: PendingChange): Outcome<Unit> = when (change) {
        is PendingChange.AddScan -> {
            // targetContainerId is included in the request so the server assigns the container
            // atomically — no second round-trip needed, no race between scan and assignment.
            val scanResult = api.send {
                method = HttpMethod.Post
                url.takeFrom("$API_V1/collection/scans")
                contentType(ContentType.Application.Json)
                setBody(ScanRequest(change.code, change.addOn, clientMutationId = change.id, scannedAtEpochMillis = change.createdAtEpochMillis, targetContainerId = change.targetContainerId))
            }
            if (scanResult is Outcome.Failure) return scanResult
            Outcome.Success(Unit)
        }

        is PendingChange.RemoveEntry -> api.send {
            method = HttpMethod.Delete
            url.takeFrom("$API_V1/collection/${change.entryId}")
        }.let { result ->
            // Already gone on the server counts as done (DELETE is idempotent).
            if (result is Outcome.Failure && result.error != DomainError.NotFound) result else Outcome.Success(Unit)
        }

        // Container changes are replayed by OfflineFirstContainerRepository; they don't belong
        // in the collection outbox. Skip them so the when is exhaustive.
        is PendingChange.CreateContainer,
        is PendingChange.MoveToContainer,
        is PendingChange.RemoveFromContainer -> Outcome.Success(Unit)
    }

    private fun merge(cached: CachedLibrary?, queued: Outbox): List<CollectionEntry> {
        val removed = queued.changes.filterIsInstance<PendingChange.RemoveEntry>().map { it.entryId }.toSet()
        val server = cached?.entries.orEmpty().filter { it.id !in removed }
            .mapNotNull(ContractMapper::asEntry).toMutableList()
        val newLocal = mutableListOf<CollectionEntry>()
        for (change in queued.changes.filterIsInstance<PendingChange.AddScan>()) {
            val code = change.code + (change.addOn ?: "")
            val idx = server.indexOfFirst { it.scannedCode == code }
            if (idx >= 0) server[idx] = server[idx].copy(copyCount = server[idx].copyCount + 1)
            else newLocal += asLocalEntry(change)
        }
        return server + newLocal
    }

    private fun asLocalEntry(change: PendingChange.AddScan) = CollectionEntry(
        id = EntryId(LOCAL_PREFIX + change.id),
        scannedCode = change.code + (change.addOn ?: ""),
        state = EntryState.PENDING,
        comic = null,
        ownedCoverName = null,
        addedAtEpochMillis = change.createdAtEpochMillis,
        isQueuedOnDevice = true,
        containerId = change.targetContainerId?.let { ContainerId(it) },
    )

    private suspend fun enqueue(change: PendingChange) = updateOutbox { it + change }

    private suspend fun updateOutbox(change: (List<PendingChange>) -> List<PendingChange>) = outboxMutex.withLock {
        val value = Outbox(change(outbox.value.changes))
        // Persist before publishing, so a crash never loses a scan the UI already showed as added.
        store.write(StoreKeys.OUTBOX, json.encodeToString(Outbox.serializer(), value))
        outbox.value = value
        syncState.update { it.copy(pendingChanges = value.changes.size) }
    }

    private suspend fun persistCache(value: CachedLibrary) {
        store.write(StoreKeys.LIBRARY, json.encodeToString(CachedLibrary.serializer(), value))
        cache.value = value
        syncState.update { it.copy(lastSyncedAtEpochMillis = value.syncedAtEpochMillis) }
    }

    private companion object {
        const val LOCAL_PREFIX = "local-"
        const val OFFLINE_PROBE_INTERVAL_MS = 30_000L
        const val ONLINE_INTERVAL_MS = 60_000L
    }
}
