package com.guturner.longbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.repository.SessionRepository
import com.guturner.longbox.domain.usecase.FOLLOWED_PUBLISHERS
import com.guturner.longbox.domain.usecase.GetWeeklyReleasesUseCase
import com.guturner.longbox.domain.usecase.ReleaseWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SessionPhase { RESTORING, SIGNED_OUT, SIGNED_IN }

class SessionViewModel(
    private val session: SessionRepository,
) : ViewModel() {

    private val restoring = MutableStateFlow(true)
    private val messageState = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = messageState.asStateFlow()

    val phase: StateFlow<SessionPhase> = combine(restoring, session.isSignedIn) { r, signedIn ->
        when {
            r -> SessionPhase.RESTORING
            signedIn -> SessionPhase.SIGNED_IN
            else -> SessionPhase.SIGNED_OUT
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionPhase.RESTORING)

    init {
        viewModelScope.launch {
            session.restore()
            restoring.value = false
        }
    }

    /** [idToken] comes from the platform's Google sign-in (Credential Manager, browser, GIS). */
    fun onGoogleIdToken(idToken: String?) {
        if (idToken == null) {
            messageState.value = UiMessage("Sign-in was cancelled.")
            return
        }
        viewModelScope.launch {
            val result = session.signInWithGoogle(idToken)
            messageState.value = (result as? Outcome.Failure)?.let {
                if (it.error is DomainError.Unauthorized) {
                    UiMessage("That Google account isn't allowed on this server.")
                } else {
                    it.error.toUiMessage()
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }
}

data class ReleasesUiState(
    val week: ReleaseWeek,
    val comics: Loadable<List<Comic>> = Loadable.Loading,
    /** Publishers currently checked; empty = show all. Defaults to the well-known set. */
    val enabledPublishers: Set<String> = FOLLOWED_PUBLISHERS,
    /** All publisher names seen in the unfiltered week data, for rendering checkboxes. */
    val availablePublishers: List<String> = emptyList(),
    /** Raw unfiltered list from the catalog — kept so toggling publishers can show/hide without a network round-trip. */
    val allComics: List<Comic> = emptyList(),
)

class ReleasesViewModel(
    private val getWeeklyReleases: GetWeeklyReleasesUseCase,
    collection: CollectionRepository,
    clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ReleasesUiState(ReleaseWeek.containing(clock.today())))
    val state: StateFlow<ReleasesUiState> = mutableState.asStateFlow()

    val ownedIds: StateFlow<Set<ComicId>> = collection.observeEntries()
        .map { entries -> entries.mapNotNull { it.comic?.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        load(mutableState.value.week)
    }

    fun previousWeek() = load(mutableState.value.week.previous())
    fun nextWeek() = load(mutableState.value.week.next())

    fun togglePublisher(name: String) {
        val current = mutableState.value
        val next = if (name in current.enabledPublishers) current.enabledPublishers - name else current.enabledPublishers + name
        mutableState.value = current.copy(
            enabledPublishers = next,
            comics = Loadable.Ready(applyFilter(current.allComics, next)),
        )
    }

    private fun load(week: ReleaseWeek) {
        mutableState.value = mutableState.value.copy(week = week, comics = Loadable.Loading, allComics = emptyList())
        viewModelScope.launch {
            // Fetch unfiltered so we can populate the publisher list and re-filter client-side
            val result = getWeeklyReleases(week, publishers = emptySet())
            if (mutableState.value.week != week) return@launch
            val enabled = mutableState.value.enabledPublishers
            mutableState.value = mutableState.value.copy(
                allComics = (result as? Outcome.Success)?.value ?: emptyList(),
                availablePublishers = (result as? Outcome.Success)
                    ?.value?.map { it.publisher.name }?.distinct()?.sorted() ?: emptyList(),
                comics = when (result) {
                    is Outcome.Success -> Loadable.Ready(applyFilter(result.value, enabled))
                    is Outcome.Failure -> Loadable.Failed(result.error.toUiMessage())
                },
            )
        }
    }

    private fun applyFilter(comics: List<Comic>, enabled: Set<String>): List<Comic> =
        comics.filter { enabled.isEmpty() || it.publisher.name in enabled }
}
