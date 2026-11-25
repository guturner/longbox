package com.guturner.longbox.domain

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.domain.repository.ComicCatalog
import com.guturner.longbox.domain.usecase.FOLLOWED_PUBLISHERS
import com.guturner.longbox.domain.usecase.GetWeeklyReleasesUseCase
import com.guturner.longbox.domain.usecase.ReleaseWeek
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleasesTest {

    private val dynamite = Publisher(PublisherId(3), "Dynamite Entertainment")
    private val image = Publisher(PublisherId(4), "Image Comics")

    private val dcComic = Fixtures.comic(1, Fixtures.batman, "1")
    private val marvelComic = Fixtures.comic(2, Fixtures.xMen, "1", publisher = Fixtures.marvel)
    private val dynamiteComic = Fixtures.comic(3, Fixtures.batman, "1", publisher = dynamite)
    private val imageComic = Fixtures.comic(4, Fixtures.batman, "1", publisher = image)

    private val week = ReleaseWeek.containing(CalendarDate(2024, 10, 2))

    private fun catalog(comics: List<Comic>): ComicCatalog = object : ComicCatalog {
        override suspend fun releases(from: CalendarDate, to: CalendarDate) = Outcome.Success(comics)
        override suspend fun comic(id: ComicId) = Outcome.Success(comics.first { it.id == id })
        override suspend fun creator(id: CreatorId): Outcome<Creator> = error("not needed")
        override suspend fun storyArc(id: StoryArcId): Outcome<StoryArc> = error("not needed")
    }

    private val clock = Clock { CalendarDate(2024, 10, 2) }

    @Test
    fun defaultFilterOnlyShowsFollowedPublishers() = runTest {
        val useCase = GetWeeklyReleasesUseCase(
            catalog(listOf(dcComic, marvelComic, dynamiteComic, imageComic)),
            clock,
        )
        val result = useCase(week)
        assertTrue(result is Outcome.Success)
        val names = result.value.map { it.publisher.name }.toSet()
        // All returned publishers must be in FOLLOWED_PUBLISHERS
        assertTrue(names.all { it in FOLLOWED_PUBLISHERS }, "Got unexpected publisher: $names")
        assertTrue("DC Comics" in names)
        assertTrue("Marvel" in names)
        assertTrue("Dynamite Entertainment" in names)
        assertTrue("Image Comics" !in names)
    }

    @Test
    fun emptyPublishersSetReturnsAll() = runTest {
        val useCase = GetWeeklyReleasesUseCase(
            catalog(listOf(dcComic, marvelComic, imageComic)),
            clock,
        )
        val result = useCase(week, publishers = emptySet())
        assertTrue(result is Outcome.Success)
        assertEquals(3, result.value.size)
    }

    @Test
    fun specificPublisherFilterReturnsOnlyThatPublisher() = runTest {
        val useCase = GetWeeklyReleasesUseCase(
            catalog(listOf(dcComic, marvelComic, dynamiteComic)),
            clock,
        )
        val result = useCase(week, publishers = setOf("DC Comics"))
        assertTrue(result is Outcome.Success)
        assertEquals(1, result.value.size)
        assertEquals("DC Comics", result.value.first().publisher.name)
    }

    @Test
    fun resultsAreSortedByPublisherThenSeriesThenIssue() = runTest {
        val batman1 = Fixtures.comic(10, Fixtures.batman, "1")
        val batman2 = Fixtures.comic(11, Fixtures.batman, "2")
        val absoluteBatman1 = Fixtures.comic(12, Fixtures.absoluteBatman, "1")
        val useCase = GetWeeklyReleasesUseCase(
            catalog(listOf(batman2, absoluteBatman1, batman1)),
            clock,
        )
        val result = useCase(week, publishers = setOf("DC Comics"))
        assertTrue(result is Outcome.Success)
        // "Absolute Batman" sorts before "Batman"
        assertEquals(listOf(ComicId(12), ComicId(10), ComicId(11)), result.value.map { it.id })
    }

    @Test
    fun releaseWeekContainingWednesdayCoversWholeWeek() {
        val wednesday = CalendarDate(2024, 10, 2) // Wednesday
        val w = ReleaseWeek.containing(wednesday)
        assertEquals(CalendarDate(2024, 9, 29), w.start)  // Sunday
        assertEquals(CalendarDate(2024, 10, 5), w.end)    // Saturday
        assertEquals(wednesday, w.wednesday)
    }

    @Test
    fun releaseWeekNavigationPreviousAndNext() {
        // Oct 2, 2024 = Wednesday → week is Sep 29 – Oct 5
        val w = ReleaseWeek.containing(CalendarDate(2024, 10, 2))

        // previous: containing(Sep 28 = Saturday) → Sep 22 – Sep 28
        val prev = w.previous()
        assertEquals(CalendarDate(2024, 9, 22), prev.start)
        assertEquals(CalendarDate(2024, 9, 28), prev.end)

        // next: containing(Oct 6 = Sunday) → Oct 6 – Oct 12
        val next = w.next()
        assertEquals(CalendarDate(2024, 10, 6), next.start)
        assertEquals(CalendarDate(2024, 10, 12), next.end)
    }
}
