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

enum class UpcScanResult { CHECKED, NOT_FOUND, NOT_READY }

data class PullListUiState(
    val entries: List<PullListEntry> = emptyList(),
    /** FOLLOWED_PUBLISHERS plus any publishers seen in the user's library, for the picker. */
    val knownPublishers: List<String> = emptyList(),
    val week: ReleaseWeek,
    /** Comics for [week] that match at least one pull list entry. */
    val comics: Loadable<List<Comic>> = Loadable.Loading,
    val checkedComicIds: Set<ComicId> = emptySet(),
    val skippedComicIds: Set<ComicId> = emptySet(),
) {
    /** True when comics have been loaded and at least one matched the pull list. */
    val hasComics: Boolean get() = (comics as? Loadable.Ready)?.value?.isNotEmpty() == true
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

    private val skippedIds: StateFlow<Set<ComicId>> = week
        .flatMapLatest { w -> pullList.observeWeekSkips(w.start) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val knownPublishers: StateFlow<List<String>> = collection.observeEntries()
        .map { entries ->
            val fromCollection = entries.mapNotNull { it.comic?.publisher?.name }
            (FOLLOWED_PUBLISHERS + fromCollection).distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FOLLOWED_PUBLISHERS.sorted())

    val state: StateFlow<PullListUiState> = combine(
        combine(pullList.observe(), week, loadedComics) { entries, w, comics -> Triple(entries, w, comics) },
        combine(checkedIds, skippedIds, knownPublishers) { checked, skipped, publishers -> Triple(checked, skipped, publishers) },
    ) { (entries, currentWeek, comics), (checked, skipped, publishers) ->
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
            skippedComicIds = skipped,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PullListUiState(week = week.value))

    init {
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

    fun toggleSkip(comicId: ComicId) {
        viewModelScope.launch { pullList.toggleSkip(week.value.start, comicId) }
    }

    /**
     * Called when a barcode is scanned from the pull list screen. Checks off the matching comic if found.
     *
     * Matching strategy (handles publishers that store 12-digit vs 17-digit UPCs in Metron):
     * 1. Full exact match on 12-digit + 5-digit supplement (most precise, when addOn is available)
     * 2. Prefix match on 12-digit main code only (covers Titan Comics and others whose Metron UPC
     *    includes the supplement but the scanner only returns the main code)
     */
    fun checkOffByUpc(mainCode: String, addOn: String? = null): UpcScanResult {
        val comics = (loadedComics.value as? Loadable.Ready)?.value
            ?: return UpcScanResult.NOT_READY
        val normMain = mainCode.trimStart('0')
        val normFull = addOn?.let { (mainCode + it).trimStart('0') }
        val comic = normFull?.let { full -> comics.firstOrNull { it.upc?.trimStart('0') == full } }
            ?: comics.firstOrNull { it.upc?.trimStart('0').let { upc -> upc != null && upc.startsWith(normMain) } }
            ?: return UpcScanResult.NOT_FOUND
        viewModelScope.launch { pullList.toggleCheck(week.value.start, comic.id) }
        return UpcScanResult.CHECKED
    }

    private fun loadWeek(w: ReleaseWeek) {
        loadedComics.value = Loadable.Loading
        viewModelScope.launch {
            val result = getWeeklyReleases(w, publishers = emptySet())
            if (week.value != w) return@launch
            loadedComics.value = when (result) {
                is com.guturner.longbox.domain.model.Outcome.Success -> Loadable.Ready(result.value)
                is com.guturner.longbox.domain.model.Outcome.Failure -> Loadable.Failed(result.error.toUiMessage())
            }
        }
    }
}
