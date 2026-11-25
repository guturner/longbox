package com.guturner.longbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.repository.PullListRepository
import com.guturner.longbox.domain.usecase.FOLLOWED_PUBLISHERS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PullListUiState(
    val entries: List<PullListEntry> = emptyList(),
    /** Publisher names from the user's own library — offered as quick-add options. */
    val knownPublishers: List<String> = emptyList(),
)

class PullListViewModel(
    private val pullList: PullListRepository,
    collection: CollectionRepository,
) : ViewModel() {

    val state: StateFlow<PullListUiState> = pullList.observe()
        .map { entries ->
            PullListUiState(entries = entries.sortedBy { it.displayName })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PullListUiState())

    /** FOLLOWED_PUBLISHERS plus any publishers seen in the user's library, for the picker. */
    val knownPublishers: StateFlow<List<String>> = collection.observeEntries()
        .map { entries ->
            val fromCollection = entries.mapNotNull { it.comic?.publisher?.name }
            (FOLLOWED_PUBLISHERS + fromCollection).distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FOLLOWED_PUBLISHERS.sorted())

    fun addPublisher(name: String) {
        viewModelScope.launch { pullList.add(PullListEntry.ByPublisher(name)) }
    }

    fun addSeries(entry: PullListEntry.BySeries) {
        viewModelScope.launch { pullList.add(entry) }
    }

    fun remove(entry: PullListEntry) {
        viewModelScope.launch { pullList.remove(entry) }
    }
}
