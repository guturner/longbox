package com.guturner.longbox.domain.repository

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.matches
import kotlinx.coroutines.flow.Flow

/** Persists the user's pull list locally (no server round-trip needed). */
interface PullListRepository {
    /** Emits the current list whenever it changes. */
    fun observe(): Flow<List<PullListEntry>>

    suspend fun add(entry: PullListEntry)
    suspend fun remove(entry: PullListEntry)

    /** True if [comic] is covered by any entry on the pull list. */
    suspend fun isOnPullList(comic: Comic): Boolean

    /**
     * Emits the set of comic IDs the user has checked off for the week starting on [weekStart].
     * Resets automatically as [weekStart] advances — callers simply pass the current week's start.
     */
    fun observeWeekChecks(weekStart: CalendarDate): Flow<Set<ComicId>>

    /** Adds [comicId] to the week's check set, or removes it if already present. */
    suspend fun toggleCheck(weekStart: CalendarDate, comicId: ComicId)
}
