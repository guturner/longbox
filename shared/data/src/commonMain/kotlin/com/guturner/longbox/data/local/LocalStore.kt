package com.guturner.longbox.data.local

import com.guturner.longbox.contract.CollectionEntryDto
import com.guturner.longbox.contract.CreatorDto
import com.guturner.longbox.contract.StoryArcDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Durable on-device storage, implemented per platform (Android app-private files, a file under
 * %LOCALAPPDATA% on Windows, localStorage on web). Writes must be atomic (write-then-rename).
 */
interface KeyValueStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun delete(key: String)
}

/** The last library the server returned, kept so it can be browsed while away from home. */
@Serializable
data class CachedLibrary(
    val entries: List<CollectionEntryDto>,
    val syncedAtEpochMillis: Long,
    val creators: Map<Long, CreatorDto> = emptyMap(),
    val arcs: Map<Long, StoryArcDto> = emptyMap(),
)

/** A change made while the server was unreachable, replayed in order once it is reachable. */
@Serializable
sealed interface PendingChange {
    val id: String
    val createdAtEpochMillis: Long

    @Serializable
    @SerialName("add_scan")
    data class AddScan(
        override val id: String,
        override val createdAtEpochMillis: Long,
        val code: String,
        val addOn: String?,
    ) : PendingChange

    @Serializable
    @SerialName("remove_entry")
    data class RemoveEntry(
        override val id: String,
        override val createdAtEpochMillis: Long,
        val entryId: String,
    ) : PendingChange
}

@Serializable
data class Outbox(val changes: List<PendingChange> = emptyList())

object StoreKeys {
    const val LIBRARY = "library-cache-v1"
    const val OUTBOX = "outbox-v1"
    const val COVER_SIZE = "cover-size-v1"
}

/** Used in tests and as a fallback when a platform has no persistent storage. */
class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override suspend fun read(key: String) = map[key]
    override suspend fun write(key: String, value: String) { map[key] = value }
    override suspend fun delete(key: String) { map.remove(key) }
}
