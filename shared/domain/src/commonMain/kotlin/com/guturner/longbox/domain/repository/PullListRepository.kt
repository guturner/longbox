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
     * Emits the set of comic IDs the user has checked off (picked up) for the week starting on [weekStart].
     */
    fun observeWeekChecks(weekStart: CalendarDate): Flow<Set<ComicId>>

    /** Adds [comicId] to the week's check set, or removes it if already present. */
    suspend fun toggleCheck(weekStart: CalendarDate, comicId: ComicId)

    /**
     * Emits the set of comic IDs the user has explicitly skipped (not buying) for the week.
     */
    fun observeWeekSkips(weekStart: CalendarDate): Flow<Set<ComicId>>

    /** Adds [comicId] to the week's skip set, or removes it if already present (undo). */
    suspend fun toggleSkip(weekStart: CalendarDate, comicId: ComicId)
}
