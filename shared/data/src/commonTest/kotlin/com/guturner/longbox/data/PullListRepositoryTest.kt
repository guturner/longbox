package com.guturner.longbox.data

import com.guturner.longbox.data.local.InMemoryKeyValueStore
import com.guturner.longbox.data.repository.KeyValueStorePullListRepository
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
