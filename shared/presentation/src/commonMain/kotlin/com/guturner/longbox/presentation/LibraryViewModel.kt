package com.guturner.longbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guturner.longbox.domain.library.LibraryFacets
import com.guturner.longbox.domain.library.LibraryFilter
import com.guturner.longbox.domain.library.LibraryQuery
import com.guturner.longbox.domain.library.LibrarySort
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.repository.ContainerRepository
import com.guturner.longbox.domain.repository.SyncController
import com.guturner.longbox.domain.repository.SyncState
import com.guturner.longbox.domain.usecase.ObserveLibraryUseCase
import com.guturner.longbox.domain.usecase.RefreshCollectionUseCase
import com.guturner.longbox.domain.usecase.RemoveFromCollectionUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val entries: List<CollectionEntry> = emptyList(),
    val facets: LibraryFacets = LibraryFacets.EMPTY,
    val query: LibraryQuery = LibraryQuery(),
    val totalInCollection: Int = 0,
    val pendingCount: Int = 0,
    val isRefreshing: Boolean = false,
    val message: UiMessage? = null,
    val sync: SyncState = SyncState(serverReachable = false, pendingChanges = 0, lastSyncedAtEpochMillis = null),
) {
    /** Banner shown below the filter row; null when everything is synced and reachable. */
    val syncBanner: String?
        get() = when {
            !sync.serverReachable && sync.pendingChanges > 0 ->
                "Can't reach server · ${sync.pendingChanges} change(s) queued · open Tailscale to sync"
            !sync.serverReachable ->
                "Can't reach server · showing cached library · open Tailscale to connect"
            sync.pendingChanges > 0 -> "Syncing ${sync.pendingChanges} change(s)…"
            else -> null
        }
}

class LibraryViewModel(
    observeLibrary: ObserveLibraryUseCase,
    private val refreshCollection: RefreshCollectionUseCase,
    private val removeFromCollection: RemoveFromCollectionUseCase,
    sync: SyncController,
    private val containers: ContainerRepository? = null,
) : ViewModel() {

    private val query = MutableStateFlow(LibraryQuery())
    private val transient = MutableStateFlow(Transient())

    private data class Transient(val isRefreshing: Boolean = false, val message: UiMessage? = null)

    val state: StateFlow<LibraryUiState> =
        combine(observeLibrary(query), query, transient, sync.state) { view, q, t, syncState ->
            LibraryUiState(
                entries = view.entries,
                facets = view.facets,
                query = q,
                totalInCollection = view.totalInCollection,
                pendingCount = view.entries.count { it.state == EntryState.PENDING },
                isRefreshing = t.isRefreshing,
                message = t.message,
                sync = syncState,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        refresh()
        // Pending scans resolve server-side at the catalog's pace; poll gently until they settle.
        viewModelScope.launch {
            // Only while home: when roaming, the repository's own probe loop handles reconnection.
            state.map { it.sync.serverReachable && it.entries.any { e -> e.state == EntryState.PENDING && !e.isQueuedOnDevice } }
                .distinctUntilChanged().collectLatest { hasPending ->
                while (hasPending) {
                    delay(POLL_INTERVAL_MS)
                    refreshCollection()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            transient.update { it.copy(isRefreshing = true) }
            val result = refreshCollection()
            transient.update { it.copy(isRefreshing = false, message = (result as? Outcome.Failure)?.error?.toUiMessage()) }
        }
    }

    fun setText(text: String) = updateFilter { it.copy(text = text) }
    fun toggleSeries(id: SeriesId) = updateFilter { it.copy(seriesIds = it.seriesIds.toggle(id)) }
    fun togglePublisher(id: PublisherId) = updateFilter { it.copy(publisherIds = it.publisherIds.toggle(id)) }
    fun toggleWriter(id: CreatorId) = updateFilter { it.copy(writerIds = it.writerIds.toggle(id)) }
    fun toggleArtist(id: CreatorId) = updateFilter { it.copy(artistIds = it.artistIds.toggle(id)) }
    fun toggleStoryArc(id: StoryArcId) = updateFilter { it.copy(storyArcIds = it.storyArcIds.toggle(id)) }
    fun toggleContainer(id: ContainerId) = updateFilter { it.copy(containerIds = it.containerIds.toggle(id)) }
    fun clearFilters() = updateFilter { LibraryFilter() }
    fun setSort(sort: LibrarySort) = query.update { it.copy(sort = sort) }

    /** Deep links from the detail screens ("show everything by this writer in my library"). */
    fun showOnly(filter: LibraryFilter) = updateFilter { filter }

    fun remove(id: EntryId) {
        viewModelScope.launch {
            val result = removeFromCollection(id)
            if (result is Outcome.Failure) transient.update { it.copy(message = result.error.toUiMessage()) }
        }
    }

    fun removeAllUnresolved() {
        val unresolved = state.value.entries.filter { it.comic == null }.map { it.id }
        unresolved.forEach { id ->
            viewModelScope.launch { removeFromCollection(id) }
        }
    }

    fun moveToContainer(entryId: EntryId, containerId: ContainerId) {
        viewModelScope.launch {
            val result = containers?.moveToContainer(entryId, containerId)
            if (result is Outcome.Failure) transient.update { it.copy(message = result.error.toUiMessage()) }
        }
    }

    fun removeFromContainer(entryId: EntryId) {
        viewModelScope.launch {
            val result = containers?.removeFromContainer(entryId)
            if (result is Outcome.Failure) transient.update { it.copy(message = result.error.toUiMessage()) }
        }
    }

    fun dismissMessage() = transient.update { it.copy(message = null) }

    private fun updateFilter(change: (LibraryFilter) -> LibraryFilter) =
        query.update { it.copy(filter = change(it.filter)) }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
