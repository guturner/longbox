package com.guturner.longbox.domain

import com.guturner.longbox.domain.Fixtures.absoluteBatman
import com.guturner.longbox.domain.Fixtures.absolutePower
import com.guturner.longbox.domain.Fixtures.batman
import com.guturner.longbox.domain.Fixtures.comic
import com.guturner.longbox.domain.Fixtures.dragotta
import com.guturner.longbox.domain.Fixtures.entry
import com.guturner.longbox.domain.Fixtures.janin
import com.guturner.longbox.domain.Fixtures.king
import com.guturner.longbox.domain.Fixtures.marvel
import com.guturner.longbox.domain.Fixtures.penciller
import com.guturner.longbox.domain.Fixtures.snyder
import com.guturner.longbox.domain.Fixtures.writer
import com.guturner.longbox.domain.Fixtures.xMen
import com.guturner.longbox.domain.library.LibraryFilter
import com.guturner.longbox.domain.library.LibraryQuery
import com.guturner.longbox.domain.library.LibrarySort
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.LookupStatus
import com.guturner.longbox.domain.usecase.ObserveLibraryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryTest {

    private val batman10 = entry(comic(1, batman, "10", credits = listOf(writer(king), penciller(janin))), added = 1)
    private val batman2 = entry(comic(2, batman, "2", credits = listOf(writer(king), penciller(janin)), arcs = listOf(absolutePower)), added = 2)
    private val absolute1 = entry(comic(3, absoluteBatman, "1", storeDate = CalendarDate(2024, 10, 9), credits = listOf(writer(snyder), penciller(dragotta))), added = 3)
    private val xMen1 = entry(comic(4, xMen, "1", publisher = marvel, storeDate = CalendarDate(2024, 7, 10)), added = 4)
    private val pending = entry(null, added = 5, status = LookupStatus.PENDING)

    private val all = listOf(xMen1, batman10, pending, absolute1, batman2)

    private fun query(filter: LibraryFilter = LibraryFilter(), sort: LibrarySort = LibrarySort.SERIES_THEN_ISSUE) =
        all.filter(filter::matches).sortedWith(sort.comparator).map { it.id.value }

    @Test
    fun testDefaultSortIsSeriesThenIssueWithPendingLast() {
        assertEquals(listOf("e3", "e2", "e1", "e4", "e5"), query())
    }

    @Test
    fun testFilterByWriter() {
        assertEquals(listOf("e2", "e1"), query(LibraryFilter(writerIds = setOf(king.id))))
    }

    @Test
    fun testFilterByArtistMatchesPencillers() {
        assertEquals(listOf("e3"), query(LibraryFilter(artistIds = setOf(dragotta.id))))
    }

    @Test
    fun testFilterByEventAndWriterIsAnd() {
        val filter = LibraryFilter(writerIds = setOf(king.id, snyder.id), storyArcIds = setOf(absolutePower.id))

        assertEquals(listOf("e2"), query(filter))
    }

    @Test
    fun testTextSearchCoversCreatorsAndEvents() {
        assertEquals(listOf("e2"), query(LibraryFilter(text = "absolute power")))
        assertEquals(listOf("e3"), query(LibraryFilter(text = "dragotta")))
    }

    @Test
    fun testReleaseDateSortPutsUndatedLast() {
        assertEquals(listOf("e3", "e4"), query(sort = LibrarySort.RELEASE_DATE_NEWEST).take(2))
    }

    @Test
    fun testObserveLibraryBuildsFacetsFromWholeCollection() = runTest {
        // Given
        val useCase = ObserveLibraryUseCase(FakeCollectionRepository(all))

        // When
        val view = useCase(flowOf(LibraryQuery(LibraryFilter(publisherIds = setOf(marvel.id))))).first()

        // Then
        assertEquals(1, view.entries.size)
        assertEquals(5, view.totalInCollection)
        assertEquals(listOf("Tom King", "Scott Snyder"), view.facets.writers.map { it.label })
        assertEquals(2, view.facets.writers.first().count)
        assertEquals(listOf("Absolute Power"), view.facets.storyArcs.map { it.label })
    }
}
