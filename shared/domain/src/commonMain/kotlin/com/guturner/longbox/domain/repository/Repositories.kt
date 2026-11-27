package com.guturner.longbox.domain.repository

import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * Ports (Dependency Inversion). The domain declares what it needs; outer rings implement it.
 *  - Clients implement these against the Longbox API (:shared:data).
 *  - The server implements ComicCatalog against Metron + a Postgres cache.
 */

/** The signed-in user's collection. */
interface CollectionRepository {
    fun observeEntries(): Flow<List<CollectionEntry>>
    suspend fun refresh(): Outcome<Unit>
    suspend fun addScan(barcode: ComicBarcode): Outcome<CollectionEntry>
    suspend fun remove(id: EntryId): Outcome<Unit>
}

/** Read-only comic metadata. */
interface ComicCatalog {
    suspend fun comic(id: ComicId): Outcome<Comic>
    suspend fun creator(id: CreatorId): Outcome<Creator>
    suspend fun storyArc(id: StoryArcId): Outcome<StoryArc>
    suspend fun releases(from: CalendarDate, to: CalendarDate): Outcome<List<Comic>>
}

/** Server-side only: barcode lookups are rate limited, so clients never call this directly. */
interface BarcodeLookup {
    /** Exact match on the full code (UPC + 5-digit supplement), or null if unknown. */
    suspend fun findByCode(code: String): Outcome<Comic?>
}

/**
 * Offline-first sync. The home server is only reachable on the home network (or VPN); while
 * roaming, changes wait in a durable on-device outbox and the last synced library is served
 * from cache.
 */
data class SyncState(
    val serverReachable: Boolean,
    val pendingChanges: Int,
    val lastSyncedAtEpochMillis: Long?,
    val isSyncing: Boolean = false,
)

interface SyncController {
    val state: StateFlow<SyncState>

    /** Probe the server; if reachable, replay the outbox in order and then replace the cache. */
    suspend fun syncNow(): Outcome<Unit>
}

/** Client-side session with the Longbox server. */
interface SessionRepository {
    val isSignedIn: StateFlow<Boolean>
    suspend fun restore(): Boolean
    suspend fun signInWithGoogle(idToken: String): Outcome<Unit>
    suspend fun signOut()
}

interface ContainerRepository {
    fun observeContainers(): Flow<List<Container>>
    suspend fun refresh(): Outcome<Unit>
    suspend fun createContainer(name: String, kind: ContainerKind, clientMutationId: String? = null): Outcome<Container>
    suspend fun renameContainer(id: ContainerId, newName: String): Outcome<Container>
    suspend fun deleteContainer(id: ContainerId): Outcome<Unit>
    suspend fun moveToContainer(entryId: EntryId, containerId: ContainerId): Outcome<CollectionEntry>
    suspend fun removeFromContainer(entryId: EntryId): Outcome<CollectionEntry>
}

/** Abstracts "today" so use cases are deterministic under test. */
fun interface Clock {
    fun today(): CalendarDate
}
