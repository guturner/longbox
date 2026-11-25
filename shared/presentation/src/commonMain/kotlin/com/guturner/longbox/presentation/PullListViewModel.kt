package com.guturner.longbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.matches
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.repository.PullListRepository
import com.guturner.longbox.domain.usecase.FOLLOWED_PUBLISHERS
import com.guturner.longbox.domain.usecase.GetWeeklyReleasesUseCase
import com.guturner.longbox.domain.usecase.ReleaseWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PullListUiState(
    val entries: List<PullListEntry> = emptyList(),
    /** FOLLOWED_PUBLISHERS plus any publishers seen in the user's library, for the picker. */
    val knownPublishers: List<String> = emptyList(),
    val week: ReleaseWeek,
    /** Comics for [week] that match at least one pull list entry. */
    val comics: Loadable<List<Comic>> = Loadable.Loading,
    val checkedComicIds: Set<ComicId> = emptySet(),
) {
    /** True when comics have been loaded and at least one matched the pull list. */
    val hasComics: Boolean get() = comics is Loadable.Ready && (comics as Loadable.Ready).value.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PullListViewModel(
    private val pullList: PullListRepository,
    collection: CollectionRepository,
    private val getWeeklyReleases: GetWeeklyReleasesUseCase,
    clock: Clock,
) : ViewModel() {

    private val week = MutableStateFlow(ReleaseWeek.containing(clock.today()))
    private val loadedComics = MutableStateFlow<Loadable<List<Comic>>>(Loadable.Loading)

    private val checkedIds: StateFlow<Set<ComicId>> = week
        .flatMapLatest { w -> pullList.observeWeekChecks(w.start) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val knownPublishers: StateFlow<List<String>> = collection.observeEntries()
        .map { entries ->
            val fromCollection = entries.mapNotNull { it.comic?.publisher?.name }
            (FOLLOWED_PUBLISHERS + fromCollection).distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FOLLOWED_PUBLISHERS.sorted())

    val state: StateFlow<PullListUiState> = combine(
        pullList.observe(),
        week,
        loadedComics,
        checkedIds,
        knownPublishers,
    ) { entries, currentWeek, comics, checked, publishers ->
        // Filter loaded comics to only those matched by a pull list entry
        val filtered = when (comics) {
            is Loadable.Ready -> Loadable.Ready(comics.value.filter { comic -> entries.any { it.matches(comic) } })
            else -> comics
        }
        PullListUiState(
            entries = entries.sortedBy { it.displayName },
            knownPublishers = publishers,
            week = currentWeek,
            comics = filtered,
            checkedComicIds = checked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PullListUiState(week = week.value))

    init {
        // Load comics whenever the week changes
        viewModelScope.launch {
            week.collect { w -> loadWeek(w) }
        }
    }

    fun previousWeek() { week.update { it.previous() } }
    fun nextWeek() { week.update { it.next() } }

    fun addPublisher(name: String) {
        viewModelScope.launch { pullList.add(PullListEntry.ByPublisher(name)) }
    }

    fun addSeries(entry: PullListEntry.BySeries) {
        viewModelScope.launch { pullList.add(entry) }
    }

    fun remove(entry: PullListEntry) {
        viewModelScope.launch { pullList.remove(entry) }
    }

    fun toggleCheck(comicId: ComicId) {
        viewModelScope.launch { pullList.toggleCheck(week.value.start, comicId) }
    }

    private fun loadWeek(w: ReleaseWeek) {
        loadedComics.value = Loadable.Loading
        viewModelScope.launch {
            val result = getWeeklyReleases(w, publishers = emptySet())
            if (week.value != w) return@launch // week changed while loading
            loadedComics.value = when (result) {
                is com.guturner.longbox.domain.model.Outcome.Success -> Loadable.Ready(result.value)
                is com.guturner.longbox.domain.model.Outcome.Failure -> Loadable.Failed(result.error.toUiMessage())
            }
        }
    }
}
