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

    private fun repo(
        store: InMemoryKeyValueStore = InMemoryKeyValueStore(),
        nowMillis: () -> Long = { 0L },
    ) = KeyValueStorePullListRepository(store, scope, nowMillis)

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

    // — week skip tests —

    @Test
    fun weekSkipsStartEmpty() = runTest {
        val skipped = repo().observeWeekSkips(week1).first()
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun toggleSkipAddsComicId() = runTest {
        val r = repo()
        r.toggleSkip(week1, comic1)
        val skipped = r.observeWeekSkips(week1).first()
        assertTrue(comic1 in skipped)
    }

    @Test
    fun toggleSkipRemovesIfAlreadySkipped() = runTest {
        val r = repo()
        r.toggleSkip(week1, comic1)
        r.toggleSkip(week1, comic1)
        val skipped = r.observeWeekSkips(week1).first()
        assertFalse(comic1 in skipped)
    }

    @Test
    fun skipsAreWeekScoped() = runTest {
        val r = repo()
        r.toggleSkip(week1, comic1)
        val week2Skips = r.observeWeekSkips(week2).first()
        assertTrue(week2Skips.isEmpty())
    }

    @Test
    fun skipsAndChecksAreIndependent() = runTest {
        val r = repo()
        r.toggleCheck(week1, comic1)
        r.toggleSkip(week1, comic2)
        assertTrue(comic1 in r.observeWeekChecks(week1).first())
        assertFalse(comic2 in r.observeWeekChecks(week1).first())
        assertFalse(comic1 in r.observeWeekSkips(week1).first())
        assertTrue(comic2 in r.observeWeekSkips(week1).first())
    }

    @Test
    fun weekSkipsPersistAcrossRestart() = runTest {
        val store = InMemoryKeyValueStore()
        val r1 = repo(store)
        r1.toggleSkip(week1, comic1)
        r1.toggleSkip(week1, comic2)

        val r2 = repo(store)
        val skipped = r2.observeWeekSkips(week1).first()
        assertEquals(setOf(comic1, comic2), skipped)
    }

    // — prune tests —

    // "today" = 2026-11-28; cutoff = 56 days prior = 2026-10-03
    // epoch day for 2026-11-28 = CalendarDate(2026,11,28).epochDay
    private val nowEpochMillis: Long = CalendarDate(2026, 11, 28).epochDay * 86_400_000L

    // older than 8 weeks before today → should be pruned
    private val oldWeek = CalendarDate(2026, 9, 1)   // 2026-09-01, well before 2026-10-03

    @Test
    fun oldChecksArePrunedOnLoad() = runTest {
        val store = InMemoryKeyValueStore()
        // Populate old + recent checks via a "timeless" repo (nowMillis=0 → no pruning)
        val r1 = repo(store, nowMillis = { 0L })
        r1.toggleCheck(oldWeek, comic1)
        r1.toggleCheck(week1,   comic2)   // week1 = 2026-11-23, recent

        // Reload with real clock — old week should be pruned
        val r2 = repo(store, nowMillis = { nowEpochMillis })
        val oldChecked    = r2.observeWeekChecks(oldWeek).first()
        val recentChecked = r2.observeWeekChecks(week1).first()
        assertTrue(oldChecked.isEmpty(), "Old week checks should be pruned")
        assertEquals(setOf(comic2), recentChecked)
    }

    @Test
    fun oldSkipsArePrunedOnLoad() = runTest {
        val store = InMemoryKeyValueStore()
        val r1 = repo(store, nowMillis = { 0L })
        r1.toggleSkip(oldWeek, comic1)
        r1.toggleSkip(week1,   comic2)

        val r2 = repo(store, nowMillis = { nowEpochMillis })
        assertTrue(r2.observeWeekSkips(oldWeek).first().isEmpty(), "Old week skips should be pruned")
        assertEquals(setOf(comic2), r2.observeWeekSkips(week1).first())
    }

    @Test
    fun recentChecksAreRetainedOnLoad() = runTest {
        val store = InMemoryKeyValueStore()
        val r1 = repo(store, nowMillis = { 0L })
        r1.toggleCheck(week1, comic1)
        r1.toggleCheck(week2, comic2)   // week2 = 2026-11-30, also recent

        val r2 = repo(store, nowMillis = { nowEpochMillis })
        assertEquals(setOf(comic1), r2.observeWeekChecks(week1).first())
        assertEquals(setOf(comic2), r2.observeWeekChecks(week2).first())
    }

    @Test
    fun prunedDataIsRePersisted() = runTest {
        val store = InMemoryKeyValueStore()
        val r1 = repo(store, nowMillis = { 0L })
        r1.toggleCheck(oldWeek, comic1)
        r1.toggleCheck(week1,   comic2)

        // Load with pruning — triggers re-persist of trimmed data
        repo(store, nowMillis = { nowEpochMillis })

        // A third repo load (nowMillis=0 again) should still not see the old week,
        // because the pruned snapshot was written back to the store
        val r3 = repo(store, nowMillis = { 0L })
        assertTrue(r3.observeWeekChecks(oldWeek).first().isEmpty())
        assertEquals(setOf(comic2), r3.observeWeekChecks(week1).first())
    }

    @Test
    fun noPruningWhenNowMillisIsZero() = runTest {
        // Default nowMillis={0L} means cutoff is day 0 − 56 = negative → nothing pruned
        val store = InMemoryKeyValueStore()
        val r1 = repo(store, nowMillis = { 0L })
        r1.toggleCheck(oldWeek, comic1)

        val r2 = repo(store, nowMillis = { 0L })
        // With zero clock, nothing is pruned — data survives
        assertEquals(setOf(comic1), r2.observeWeekChecks(oldWeek).first())
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
