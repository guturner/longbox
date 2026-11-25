package com.guturner.longbox.data.repository

import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.matches
import com.guturner.longbox.domain.repository.PullListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private const val LIST_KEY = "pull-list-v1"
private const val CHECKS_KEY = "pull-list-checks-v1"
private const val SKIPS_KEY = "pull-list-skips-v1"

// ── pull list entries ─────────────────────────────────────────────────────────

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

// ── weekly check state ────────────────────────────────────────────────────────

/** Map of ISO week-start string (e.g. "2026-11-23") → set of checked comic ID longs. */
@Serializable
private data class StoredChecks(val weeks: Map<String, List<Long>> = emptyMap())

class KeyValueStorePullListRepository(
    private val store: KeyValueStore,
    appScope: CoroutineScope,
) : PullListRepository {

    private val state = MutableStateFlow<List<PullListEntry>>(emptyList())
    // weekStart.toString() → Set<ComicId>
    private val checks = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())
    private val skips  = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())

    init {
        appScope.launch { load() }
    }

    private suspend fun load() {
        store.read(LIST_KEY)
            ?.let { runCatching { json.decodeFromString(StoredPullList.serializer(), it) }.getOrNull() }
            ?.let { state.value = it.entries.map { e -> e.toDomain() } }

        store.read(CHECKS_KEY)
            ?.let { runCatching { json.decodeFromString(StoredChecks.serializer(), it) }.getOrNull() }
            ?.let { checks.value = it.weeks.mapValues { (_, ids) -> ids.toSet() } }

        store.read(SKIPS_KEY)
            ?.let { runCatching { json.decodeFromString(StoredChecks.serializer(), it) }.getOrNull() }
            ?.let { skips.value = it.weeks.mapValues { (_, ids) -> ids.toSet() } }
    }

    override fun observe(): Flow<List<PullListEntry>> = state.asStateFlow()

    override suspend fun add(entry: PullListEntry) {
        state.update { current -> if (current.any { it == entry }) current else current + entry }
        persistList()
    }

    override suspend fun remove(entry: PullListEntry) {
        state.update { it.filterNot { e -> e == entry } }
        persistList()
    }

    override suspend fun isOnPullList(comic: Comic): Boolean =
        state.value.any { it.matches(comic) }

    override fun observeWeekChecks(weekStart: CalendarDate): Flow<Set<ComicId>> =
        checks.map { map -> map[weekStart.toString()]?.map { ComicId(it) }?.toSet() ?: emptySet() }

    override suspend fun toggleCheck(weekStart: CalendarDate, comicId: ComicId) {
        val key = weekStart.toString()
        checks.update { map ->
            val current = map[key] ?: emptySet()
            val updated = if (comicId.value in current) current - comicId.value else current + comicId.value
            map + (key to updated)
        }
        persistChecks()
    }

    private suspend fun persistList() {
        val stored = StoredPullList(state.value.map { it.toStored() })
        store.write(LIST_KEY, json.encodeToString(StoredPullList.serializer(), stored))
    }

    override fun observeWeekSkips(weekStart: CalendarDate): Flow<Set<ComicId>> =
        skips.map { map -> map[weekStart.toString()]?.map { ComicId(it) }?.toSet() ?: emptySet() }

    override suspend fun toggleSkip(weekStart: CalendarDate, comicId: ComicId) {
        val key = weekStart.toString()
        skips.update { map ->
            val current = map[key] ?: emptySet()
            val updated = if (comicId.value in current) current - comicId.value else current + comicId.value
            map + (key to updated)
        }
        persistSkips()
    }

    private suspend fun persistChecks() {
        val stored = StoredChecks(checks.value.mapValues { (_, ids) -> ids.toList() })
        store.write(CHECKS_KEY, json.encodeToString(StoredChecks.serializer(), stored))
    }

    private suspend fun persistSkips() {
        val stored = StoredChecks(skips.value.mapValues { (_, ids) -> ids.toList() })
        store.write(SKIPS_KEY, json.encodeToString(StoredChecks.serializer(), stored))
    }
}
