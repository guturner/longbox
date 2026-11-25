package com.guturner.longbox.data

import com.guturner.longbox.data.local.InMemoryKeyValueStore
import com.guturner.longbox.data.repository.KeyValueStorePullListRepository
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.SeriesId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullListRepositoryTest {

    private val scope = TestScope(UnconfinedTestDispatcher())

    private fun repo(store: InMemoryKeyValueStore = InMemoryKeyValueStore()) =
        KeyValueStorePullListRepository(store, scope)

    private val dcEntry = PullListEntry.ByPublisher("DC Comics")
    private val batmanEntry = PullListEntry.BySeries(SeriesId(10), "Batman (2016)", "DC Comics")

    @Test
    fun startsEmpty() = runTest {
        val entries = repo().observe().first()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun addPublisherAppearsInObservable() = runTest {
        val r = repo()
        r.add(dcEntry)
        val entries = r.observe().first()
        assertEquals(listOf(dcEntry), entries)
    }

    @Test
    fun addSeriesAppearsInObservable() = runTest {
        val r = repo()
        r.add(batmanEntry)
        val entries = r.observe().first()
        assertEquals(listOf(batmanEntry), entries)
    }

    @Test
    fun removedEntryDisappearsFromObservable() = runTest {
        val r = repo()
        r.add(dcEntry)
        r.add(batmanEntry)
        r.remove(dcEntry)
        val entries = r.observe().first()
        assertEquals(listOf(batmanEntry), entries)
        assertFalse(dcEntry in entries)
    }

    @Test
    fun duplicateAddIsIdempotent() = runTest {
        val r = repo()
        r.add(dcEntry)
        r.add(dcEntry)
        assertEquals(1, r.observe().first().size)
    }

    @Test
    fun persistedAcrossRestart() = runTest {
        val store = InMemoryKeyValueStore()
        val r1 = repo(store)
        r1.add(dcEntry)
        r1.add(batmanEntry)

        // Simulate app restart with same store
        val r2 = repo(store)
        val entries = r2.observe().first()
        assertEquals(2, entries.size)
        assertTrue(dcEntry in entries)
        assertTrue(batmanEntry in entries)
    }

    @Test
    fun isOnPullListMatchesPublisher() = runTest {
        val r = repo()
        r.add(dcEntry)
        val comic = makeDcComic()
        assertTrue(r.isOnPullList(comic))
    }

    @Test
    fun isOnPullListFalseForNonFollowedPublisher() = runTest {
        val r = repo()
        r.add(PullListEntry.ByPublisher("Marvel"))
        val comic = makeDcComic()
        assertFalse(r.isOnPullList(comic))
    }

    @Test
    fun isOnPullListMatchesSeries() = runTest {
        val r = repo()
        r.add(batmanEntry)
        val comic = makeBatmanComic()
        assertTrue(r.isOnPullList(comic))
    }

    // — week check tests —

    private val week1 = CalendarDate(2026, 11, 23)  // Monday
    private val week2 = CalendarDate(2026, 11, 30)  // Following Monday
    private val comic1 = ComicId(101)
    private val comic2 = ComicId(202)

    @Test
    fun weekChecksStartEmpty() = runTest {
        val checked = repo().observeWeekChecks(week1).first()
        assertTrue(checked.isEmpty())
    }

    @Test
    fun toggleCheckAddsComicId() = runTest {
        val r = repo()
        r.toggleCheck(week1, comic1)
        val checked = r.observeWeekChecks(week1).first()
        assertTrue(comic1 in checked)
    }

    @Test
    fun toggleCheckRemovesIfAlreadyChecked() = runTest {
        val r = repo()
        r.toggleCheck(week1, comic1)
        r.toggleCheck(week1, comic1)
        val checked = r.observeWeekChecks(week1).first()
        assertFalse(comic1 in checked)
    }

    @Test
    fun checksAreWeekScoped() = runTest {
        val r = repo()
        r.toggleCheck(week1, comic1)
        val week2Checks = r.observeWeekChecks(week2).first()
        assertTrue(week2Checks.isEmpty())
    }

    @Test
    fun multipleComicsInSameWeek() = runTest {
        val r = repo()
        r.toggleCheck(week1, comic1)
        r.toggleCheck(week1, comic2)
        val checked = r.observeWeekChecks(week1).first()
        assertEquals(setOf(comic1, comic2), checked)
    }

    @Test
    fun weekChecksPersistAcrossRestart() = runTest {
        val store = InMemoryKeyValueStore()
        val r1 = repo(store)
        r1.toggleCheck(week1, comic1)
        r1.toggleCheck(week1, comic2)

        val r2 = repo(store)
        val checked = r2.observeWeekChecks(week1).first()
        assertEquals(setOf(comic1, comic2), checked)
    }

    // — helpers —

    private fun makeDcComic(): com.guturner.longbox.domain.model.Comic {
        val dc = Publisher(PublisherId(2), "DC Comics")
        val series = com.guturner.longbox.domain.model.Series(SeriesId(99), "Superman", "Superman", 1, 2024)
        return makeComic(dc, series)
    }

    private fun makeBatmanComic(): com.guturner.longbox.domain.model.Comic {
        val dc = Publisher(PublisherId(2), "DC Comics")
        val batman = com.guturner.longbox.domain.model.Series(SeriesId(10), "Batman", "Batman", 3, 2016)
        return makeComic(dc, batman)
    }

    private fun makeComic(publisher: Publisher, series: com.guturner.longbox.domain.model.Series): com.guturner.longbox.domain.model.Comic {
        return com.guturner.longbox.domain.model.Comic(
            id = com.guturner.longbox.domain.model.ComicId(1),
            publisher = publisher,
            series = series,
            number = com.guturner.longbox.domain.model.IssueNumber("1"),
            title = null, coverDate = null, storeDate = null, summary = null,
            coverA = com.guturner.longbox.domain.model.Cover("Cover A", null, null),
            variantCovers = emptyList(), credits = emptyList(), storyArcs = emptyList(), upc = null,
        )
    }
}
