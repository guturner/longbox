package com.guturner.longbox.domain

import com.guturner.longbox.domain.Fixtures.absoluteBatman
import com.guturner.longbox.domain.Fixtures.batman
import com.guturner.longbox.domain.Fixtures.comic
import com.guturner.longbox.domain.Fixtures.dc
import com.guturner.longbox.domain.Fixtures.entry
import com.guturner.longbox.domain.Fixtures.janin
import com.guturner.longbox.domain.Fixtures.king
import com.guturner.longbox.domain.Fixtures.marvel
import com.guturner.longbox.domain.Fixtures.penciller
import com.guturner.longbox.domain.Fixtures.snyder
import com.guturner.longbox.domain.Fixtures.writer
import com.guturner.longbox.domain.Fixtures.xMen
import com.guturner.longbox.domain.Fixtures.absolutePower
import com.guturner.longbox.domain.library.LibraryFacets
import com.guturner.longbox.domain.library.LibraryFilter
import com.guturner.longbox.domain.library.LibrarySort
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.usecase.ObserveLibraryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryFilterTest {

    private val container1 = ContainerId("00000000-0000-0000-0000-000000000001")
    private val container2 = ContainerId("00000000-0000-0000-0000-000000000002")

    private val batman10 = entry(comic(1, batman, "10", credits = listOf(writer(king), penciller(janin))), added = 1)
    private val batman2  = entry(comic(2, batman, "2",  credits = listOf(writer(king))), added = 2)
    private val absolute1 = entry(comic(3, absoluteBatman, "1", credits = listOf(writer(snyder))), added = 3)
    private val xMen1    = entry(comic(4, xMen, "1", publisher = marvel), added = 4)
    private val pending  = entry(null, added = 5, state = EntryState.PENDING)

    // entries with container assignments
    private val inContainer1 = batman10.copy(containerId = container1, containerName = "Box A")
    private val inContainer2 = absolute1.copy(containerId = container2, containerName = "Box B")

    // ── isEmpty ──────────────────────────────────────────────────────────────

    @Test fun emptyFilterIsEmpty() {
        assertTrue(LibraryFilter().isEmpty)
    }

    @Test fun filterWithTextIsNotEmpty() {
        assertFalse(LibraryFilter(text = "batman").isEmpty)
    }

    @Test fun filterWithContainerIsNotEmpty() {
        assertFalse(LibraryFilter(containerIds = setOf(container1)).isEmpty)
    }

    // ── container filter ─────────────────────────────────────────────────────

    @Test fun containerFilterMatchesExactId() {
        val filter = LibraryFilter(containerIds = setOf(container1))
        assertTrue(filter.matches(inContainer1))
        assertFalse(filter.matches(inContainer2))
        assertFalse(filter.matches(batman2))   // no container
    }

    @Test fun containerFilterExcludesNullContainerId() {
        val filter = LibraryFilter(containerIds = setOf(container1))
        assertFalse(filter.matches(batman10)) // batman10 has no containerId
    }

    @Test fun containerFilterOrAcrossMultipleContainers() {
        val filter = LibraryFilter(containerIds = setOf(container1, container2))
        assertTrue(filter.matches(inContainer1))
        assertTrue(filter.matches(inContainer2))
        assertFalse(filter.matches(batman2))
    }

    @Test fun pendingEntryWithNoContainerExcludedByContainerFilter() {
        val filter = LibraryFilter(containerIds = setOf(container1))
        assertFalse(filter.matches(pending))
    }

    // ── container + other filters AND together ───────────────────────────────

    @Test fun containerAndSeriesFilterIsAnd() {
        // inContainer1 is batman series in container1 — should match both
        val filter = LibraryFilter(
            containerIds = setOf(container1),
            writerIds = setOf(king.id),
        )
        assertTrue(filter.matches(inContainer1))
        // batman2 has right writer but wrong container
        assertFalse(filter.matches(batman2))
    }

    // ── text search on pending entry ─────────────────────────────────────────

    @Test fun pendingEntryMatchedByScannedCode() {
        val scannedPending = pending.copy(scannedCode = "759606095582")
        assertTrue(LibraryFilter(text = "759606095582").matches(scannedPending))
        assertFalse(LibraryFilter(text = "000000000000").matches(scannedPending))
    }

    @Test fun pendingEntryWithEmptyFilterMatches() {
        assertTrue(LibraryFilter().matches(pending))
    }

    @Test fun pendingEntryWithNonTextFilterDoesNotMatch() {
        // A pending entry with no comic cannot match series/publisher/writer filters
        assertFalse(LibraryFilter(writerIds = setOf(king.id)).matches(pending))
    }

    // ── series filter ────────────────────────────────────────────────────────

    @Test fun filterBySeriesId() {
        val filter = LibraryFilter(seriesIds = setOf(batman.id))
        assertTrue(filter.matches(batman10))
        assertTrue(filter.matches(batman2))
        assertFalse(filter.matches(absolute1))
        assertFalse(filter.matches(xMen1))
    }

    // ── publisher filter ─────────────────────────────────────────────────────

    @Test fun filterByPublisherId() {
        val filter = LibraryFilter(publisherIds = setOf(marvel.id))
        assertTrue(filter.matches(xMen1))
        assertFalse(filter.matches(batman10))
    }

    // ── story arc filter ─────────────────────────────────────────────────────

    @Test fun filterByStoryArc() {
        val withArc = entry(comic(10, batman, "5", arcs = listOf(absolutePower)), added = 10)
        val filter = LibraryFilter(storyArcIds = setOf(absolutePower.id))
        assertTrue(filter.matches(withArc))
        assertFalse(filter.matches(batman10))
    }

    // ── LibraryFacets.from() with container scoping (FIX-39 path) ────────────

    @Test fun facetsFromAllEntriesIncludeAllWriters() {
        val entries = listOf(batman10, batman2, absolute1, xMen1)
        val facets = LibraryFacets.from(entries)
        val writerLabels = facets.writers.map { it.label }
        assertTrue(writerLabels.contains("Tom King"))
        assertTrue(writerLabels.contains("Scott Snyder"))
    }

    @Test fun facetCountsReflectOccurrences() {
        // king writes 2 batman issues, snyder writes 1
        val entries = listOf(batman10, batman2, absolute1)
        val facets = LibraryFacets.from(entries)
        val kingFacet = facets.writers.first { it.label == "Tom King" }
        val snyderFacet = facets.writers.first { it.label == "Scott Snyder" }
        assertEquals(2, kingFacet.count)
        assertEquals(1, snyderFacet.count)
    }

    @Test fun facetsSortedByCountDescThenLabel() {
        val entries = listOf(batman10, batman2, absolute1)
        val facets = LibraryFacets.from(entries)
        // King(2) should appear before Snyder(1)
        assertEquals("Tom King", facets.writers.first().label)
    }

    @Test fun facetsEmptyForEmptyList() {
        val facets = LibraryFacets.from(emptyList())
        assertTrue(facets.writers.isEmpty())
        assertTrue(facets.series.isEmpty())
    }

    @Test fun observeLibraryScopesFacetsToContainerWhenFilterActive() = runTest {
        // FIX-39: when containerIds filter is set, facets should come from that container's
        // entries only, not the whole collection.
        val entriesWithContainers = listOf(inContainer1, inContainer2, batman2)
        val useCase = ObserveLibraryUseCase(FakeCollectionRepository(entriesWithContainers))

        val view = useCase(flowOf(
            com.guturner.longbox.domain.library.LibraryQuery(
                filter = LibraryFilter(containerIds = setOf(container1))
            )
        )).first()

        // Only inContainer1 (batman10 by King) is in container1
        assertEquals(1, view.entries.size)
        // Facets should only include King (not Snyder from container2 or batman2)
        assertEquals(listOf("Tom King"), view.facets.writers.map { it.label })
    }

    @Test fun observeLibraryBuildsFacetsFromWholeCollectionWithNoContainerFilter() = runTest {
        val entries = listOf(batman10, batman2, absolute1, xMen1)
        val useCase = ObserveLibraryUseCase(FakeCollectionRepository(entries))

        val view = useCase(flowOf(
            com.guturner.longbox.domain.library.LibraryQuery(
                filter = LibraryFilter(writerIds = setOf(king.id))
            )
        )).first()

        // Filtered to King's issues only, but facets span all entries
        assertEquals(2, view.entries.size)
        val writerLabels = view.facets.writers.map { it.label }
        assertTrue(writerLabels.contains("Tom King"))
        assertTrue(writerLabels.contains("Scott Snyder"))
    }

    // ── LibrarySort ──────────────────────────────────────────────────────────

    @Test fun recentlyAddedSortNewestFirst() {
        val entries = listOf(batman10, batman2, absolute1) // added 1,2,3
        val sorted = entries.sortedWith(LibrarySort.RECENTLY_ADDED.comparator)
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.addedAtEpochMillis })
    }

    @Test fun releaseDateSortNewestFirst() {
        val dated1 = entry(comic(1, batman, "1", storeDate = CalendarDate(2024, 1, 1)), added = 1)
        val dated2 = entry(comic(2, batman, "2", storeDate = CalendarDate(2024, 6, 1)), added = 2)
        val undated = entry(comic(3, batman, "3"), added = 3)
        val sorted = listOf(undated, dated1, dated2).sortedWith(LibrarySort.RELEASE_DATE_NEWEST.comparator)
        // newest date first, undated last
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.addedAtEpochMillis })
    }

    @Test fun seriesThenIssueSortGroupsBySeries() {
        val e1 = entry(comic(1, batman, "1"), added = 1)
        val e2 = entry(comic(2, xMen,  "1", publisher = marvel), added = 2)
        val e3 = entry(comic(3, batman, "2"), added = 3)
        val sorted = listOf(e2, e3, e1).sortedWith(LibrarySort.SERIES_THEN_ISSUE.comparator)
        // Batman sorts before X-Men alphabetically; within batman: issue 1 then 2
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.addedAtEpochMillis })
    }
}
