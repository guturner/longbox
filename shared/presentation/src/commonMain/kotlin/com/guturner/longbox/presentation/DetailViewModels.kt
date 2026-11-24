package com.guturner.longbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.usecase.GetComicDetailUseCase
import com.guturner.longbox.domain.usecase.GetCreatorUseCase
import com.guturner.longbox.domain.usecase.GetStoryArcUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val message: UiMessage) : Loadable<Nothing>
}

private fun <T> Outcome<T>.toLoadable(): Loadable<T> = when (this) {
    is Outcome.Success -> Loadable.Ready(value)
    is Outcome.Failure -> Loadable.Failed(error.toUiMessage())
}

class ComicDetailViewModel(
    private val comicId: ComicId,
    private val getComic: GetComicDetailUseCase,
    collection: CollectionRepository,
) : ViewModel() {

    private val comicState = MutableStateFlow<Loadable<Comic>>(Loadable.Loading)
    val comic: StateFlow<Loadable<Comic>> = comicState.asStateFlow()

    /** Copies of this issue the user owns (e.g. Cover A and a variant). */
    val ownedCopies: StateFlow<List<CollectionEntry>> = collection.observeEntries()
        .map { entries -> entries.filter { it.comic?.id == comicId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            comicState.value = Loadable.Loading
            comicState.value = getComic(comicId).toLoadable()
        }
    }
}

class CreatorViewModel(
    private val creatorId: CreatorId,
    private val getCreator: GetCreatorUseCase,
) : ViewModel() {
    private val creatorState = MutableStateFlow<Loadable<Creator>>(Loadable.Loading)
    val creator: StateFlow<Loadable<Creator>> = creatorState.asStateFlow()

    init {
        viewModelScope.launch { creatorState.value = getCreator(creatorId).toLoadable() }
    }
}

class StoryArcViewModel(
    private val arcId: StoryArcId,
    private val getStoryArc: GetStoryArcUseCase,
) : ViewModel() {
    private val arcState = MutableStateFlow<Loadable<StoryArc>>(Loadable.Loading)
    val arc: StateFlow<Loadable<StoryArc>> = arcState.asStateFlow()

    init {
        viewModelScope.launch { arcState.value = getStoryArc(arcId).toLoadable() }
    }
}
