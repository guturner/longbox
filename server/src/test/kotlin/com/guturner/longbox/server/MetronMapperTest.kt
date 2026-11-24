package com.guturner.longbox.server

import com.guturner.longbox.domain.model.CreatorRole
import com.guturner.longbox.server.metron.MetronCredit
import com.guturner.longbox.server.metron.MetronGeneric
import com.guturner.longbox.server.metron.MetronIssue
import com.guturner.longbox.server.metron.MetronIssueSeries
import com.guturner.longbox.server.metron.MetronMapper
import com.guturner.longbox.server.metron.MetronVariant
import kotlin.test.Test
import kotlin.test.assertEquals

class MetronMapperTest {

    private val issue = MetronIssue(
        id = 140_000,
        publisher = MetronGeneric(2, "DC Comics"),
        series = MetronIssueSeries(9000, "Absolute Batman", "", 1, 2024),
        number = "1",
        name = listOf("The Zoo, Part One"),
        storeDate = "2024-10-09",
        coverDate = "2024-12-01",
        upc = "76194138548000111",
        desc = "A new Batman.",
        image = "https://static.metron.cloud/media/issue/a.jpg",
        arcs = listOf(MetronGeneric(77, "The Zoo")),
        credits = listOf(
            MetronCredit(10, "Scott Snyder", listOf(MetronGeneric(1, "Writer"))),
            MetronCredit(11, "Nick Dragotta", listOf(MetronGeneric(2, "Artist"), MetronGeneric(3, "Cover"))),
            MetronCredit(12, "Frank Martin", listOf(MetronGeneric(4, "Colorist"))),
        ),
        variants = listOf(MetronVariant("Cover B Jim Lee", "76194138548000121", "https://static.metron.cloud/b.jpg")),
    )

    @Test
    fun testMapsIssueDetail() {
        // When
        val comic = MetronMapper.asComic(issue)

        // Then
        assertEquals("Absolute Batman #1", comic.displayTitle)
        assertEquals("Absolute Batman", comic.series.sortName) // blank sort_name falls back to name
        assertEquals("2024-10-09", comic.storeDate.toString())
        assertEquals("The Zoo, Part One", comic.title)
        assertEquals(listOf("Scott Snyder"), comic.writers.map { it.name })
        assertEquals(listOf("Nick Dragotta"), comic.artists.map { it.name })
        assertEquals(listOf("Nick Dragotta"), comic.coverArtists.map { it.name })
        assertEquals(setOf(CreatorRole.COLORIST), comic.credits.single { it.creator.name == "Frank Martin" }.roles)
        assertEquals(listOf("The Zoo"), comic.storyArcs.map { it.name })
        assertEquals("Cover B Jim Lee", comic.variantCovers.single().name)
    }
}
