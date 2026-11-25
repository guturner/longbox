package com.guturner.longbox.data.repository

import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.matches
import com.guturner.longbox.domain.repository.PullListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private const val STORE_KEY = "pull-list-v1"

@Serializable
private data class StoredPullList(val entries: List<StoredEntry> = emptyList())

@Serializable
private sealed interface StoredEntry {
    @Serializable @SerialName("publisher") data class Publisher(val name: String) : StoredEntry
    @Serializable @SerialName("series") data class Series(val id: Long, val name: String, val publisherName: String) : StoredEntry
}

private fun PullListEntry.toStored(): StoredEntry = when (this) {
    is PullListEntry.ByPublisher -> StoredEntry.Publisher(publisherName)
    is PullListEntry.BySeries -> StoredEntry.Series(seriesId.value, seriesName, publisherName)
}

private fun StoredEntry.toDomain(): PullListEntry = when (this) {
    is StoredEntry.Publisher -> PullListEntry.ByPublisher(name)
    is StoredEntry.Series -> PullListEntry.BySeries(SeriesId(id), name, publisherName)
}

class KeyValueStorePullListRepository(
    private val store: KeyValueStore,
    appScope: CoroutineScope,
) : PullListRepository {

    private val state = MutableStateFlow<List<PullListEntry>>(emptyList())

    init {
        appScope.launch { load() }
    }

    private suspend fun load() {
        val raw = store.read(STORE_KEY) ?: return
        val stored = runCatching { json.decodeFromString(StoredPullList.serializer(), raw) }.getOrNull() ?: return
        state.value = stored.entries.map { it.toDomain() }
    }

    override fun observe(): Flow<List<PullListEntry>> = state.asStateFlow()

    override suspend fun add(entry: PullListEntry) {
        state.update { current ->
            if (current.any { it == entry }) current else current + entry
        }
        persist()
    }

    override suspend fun remove(entry: PullListEntry) {
        state.update { it.filterNot { e -> e == entry } }
        persist()
    }

    override suspend fun isOnPullList(comic: Comic): Boolean =
        state.value.any { it.matches(comic) }

    private suspend fun persist() {
        val stored = StoredPullList(state.value.map { it.toStored() })
        store.write(STORE_KEY, json.encodeToString(StoredPullList.serializer(), stored))
    }
}
