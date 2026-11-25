package com.guturner.longbox.domain

import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.matches
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullListTest {

    private val dc = Fixtures.dc
    private val marvel = Fixtures.marvel
    private val dynamite = Publisher(PublisherId(3), "Dynamite Entertainment")

    private val batman1 = Fixtures.comic(1, Fixtures.batman, "1", publisher = dc)
    private val absoluteBatman1 = Fixtures.comic(2, Fixtures.absoluteBatman, "1", publisher = dc)
    private val xMen1 = Fixtures.comic(3, Fixtures.xMen, "1", publisher = marvel)

    @Test
    fun byPublisherMatchesSamePublisher() {
        val entry = PullListEntry.ByPublisher("DC Comics")
        assertTrue(entry.matches(batman1))
        assertTrue(entry.matches(absoluteBatman1))
        assertFalse(entry.matches(xMen1))
    }

    @Test
    fun byPublisherIsCaseSensitive() {
        val entry = PullListEntry.ByPublisher("dc comics")
        // publisher names are stored as-is from the catalog
        assertFalse(entry.matches(batman1)) // "DC Comics" ≠ "dc comics"
    }

    @Test
    fun bySeriesMatchesSameSeries() {
        val entry = PullListEntry.BySeries(
            seriesId = Fixtures.batman.id,
            seriesName = Fixtures.batman.displayName,
            publisherName = dc.name,
        )
        assertTrue(entry.matches(batman1))
        assertFalse(entry.matches(absoluteBatman1)) // different series
        assertFalse(entry.matches(xMen1))
    }

    @Test
    fun bySeriesIgnoresPublisherName() {
        // series match is by ID only — publisher name in the entry is just for display
        val entry = PullListEntry.BySeries(
            seriesId = Fixtures.batman.id,
            seriesName = "whatever",
            publisherName = "wrong publisher",
        )
        assertTrue(entry.matches(batman1)) // series ID matches regardless of publisher label
    }

    @Test
    fun displayNameFormatsCorrectly() {
        val publisherEntry = PullListEntry.ByPublisher("DC Comics")
        assertTrue(publisherEntry.displayName.contains("DC Comics"))

        val seriesEntry = PullListEntry.BySeries(SeriesId(10), "Batman (2016)", "DC Comics")
        assertTrue(seriesEntry.displayName.contains("Batman (2016)"))
        assertTrue(seriesEntry.displayName.contains("DC Comics"))
    }
}
