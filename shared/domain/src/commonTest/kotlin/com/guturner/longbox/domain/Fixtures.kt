package com.guturner.longbox.domain

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Cover
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.CreatorRef
import com.guturner.longbox.domain.model.CreatorRole
import com.guturner.longbox.domain.model.Credit
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.IssueNumber
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.Series
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.model.StoryArcRef

object Fixtures {
    val dc = Publisher(PublisherId(2), "DC Comics")
    val marvel = Publisher(PublisherId(1), "Marvel")

    val batman = Series(SeriesId(10), "Batman", "Batman", 3, 2016)
    val absoluteBatman = Series(SeriesId(11), "Absolute Batman", "Absolute Batman", 1, 2024)
    val xMen = Series(SeriesId(20), "X-Men", "X-Men", 7, 2024)

    val king = CreatorRef(CreatorId(100), "Tom King")
    val snyder = CreatorRef(CreatorId(101), "Scott Snyder")
    val dragotta = CreatorRef(CreatorId(102), "Nick Dragotta")
    val janin = CreatorRef(CreatorId(103), "Mikel Janín")

    val absolutePower = StoryArcRef(StoryArcId(500), "Absolute Power")

    fun comic(
        id: Long,
        series: Series,
        number: String,
        publisher: Publisher = dc,
        storeDate: CalendarDate? = null,
        credits: List<Credit> = emptyList(),
        arcs: List<StoryArcRef> = emptyList(),
        variants: List<Cover> = emptyList(),
    ) = Comic(
        id = ComicId(id),
        publisher = publisher,
        series = series,
        number = IssueNumber(number),
        title = null,
        coverDate = null,
        storeDate = storeDate,
        summary = null,
        coverA = Cover("Cover A", "https://example.test/$id.jpg", null),
        variantCovers = variants,
        credits = credits,
        storyArcs = arcs,
        upc = null,
    )

    fun writer(c: CreatorRef) = Credit(c, setOf(CreatorRole.WRITER))
    fun penciller(c: CreatorRef) = Credit(c, setOf(CreatorRole.PENCILLER))

    fun entry(comic: Comic?, added: Long, state: EntryState = EntryState.IN_STACK) =
        CollectionEntry(EntryId("e$added"), "000000000000", state, comic, null, added)
}
