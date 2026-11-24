package com.guturner.longbox.contract

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Cover
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.CreatorRef
import com.guturner.longbox.domain.model.CreatorRole
import com.guturner.longbox.domain.model.Credit
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.IssueNumber
import com.guturner.longbox.domain.model.LookupStatus
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.Series
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.model.StoryArcRef

/**
 * Domain <-> wire mapping, in the spirit of the asX/asXDTO mappers in queuehaven,
 * returning null instead of Optional.empty() when the input can't be mapped.
 */
object ContractMapper {

    fun asComicDto(comic: Comic): ComicDto = ComicDto(
        id = comic.id.value,
        publisher = PublisherDto(comic.publisher.id.value, comic.publisher.name),
        series = with(comic.series) { SeriesDto(id.value, name, sortName, volume, yearBegan) },
        number = comic.number.raw,
        title = comic.title,
        coverDate = comic.coverDate?.toString(),
        storeDate = comic.storeDate?.toString(),
        summary = comic.summary,
        coverA = asCoverDto(comic.coverA),
        variantCovers = comic.variantCovers.map(::asCoverDto),
        credits = comic.credits.map { credit ->
            CreditDto(CreatorRefDto(credit.creator.id.value, credit.creator.name), credit.roles.map { it.name })
        },
        storyArcs = comic.storyArcs.map { StoryArcRefDto(it.id.value, it.name) },
        upc = comic.upc,
    )

    fun asComic(dto: ComicDto): Comic = Comic(
        id = ComicId(dto.id),
        publisher = Publisher(PublisherId(dto.publisher.id), dto.publisher.name),
        series = with(dto.series) { Series(SeriesId(id), name, sortName, volume, yearBegan) },
        number = IssueNumber(dto.number),
        title = dto.title,
        coverDate = dto.coverDate?.let(CalendarDate::parse),
        storeDate = dto.storeDate?.let(CalendarDate::parse),
        summary = dto.summary,
        coverA = asCover(dto.coverA),
        variantCovers = dto.variantCovers.map(::asCover),
        credits = dto.credits.map { credit ->
            Credit(
                creator = CreatorRef(CreatorId(credit.creator.id), credit.creator.name),
                roles = credit.roles.mapNotNull { role -> CreatorRole.entries.firstOrNull { it.name == role } }.toSet(),
            )
        },
        storyArcs = dto.storyArcs.map { StoryArcRef(StoryArcId(it.id), it.name) },
        upc = dto.upc,
    )

    fun asEntryDto(entry: CollectionEntry): CollectionEntryDto = CollectionEntryDto(
        id = entry.id.value,
        scannedCode = entry.scannedCode,
        status = entry.status.name,
        comic = entry.comic?.let(::asComicDto),
        ownedCoverName = entry.ownedCoverName,
        addedAtEpochMillis = entry.addedAtEpochMillis,
    )

    fun asEntry(dto: CollectionEntryDto): CollectionEntry? {
        val status = LookupStatus.entries.firstOrNull { it.name == dto.status } ?: return null
        return CollectionEntry(
            id = EntryId(dto.id),
            scannedCode = dto.scannedCode,
            status = status,
            comic = dto.comic?.let(::asComic),
            ownedCoverName = dto.ownedCoverName,
            addedAtEpochMillis = dto.addedAtEpochMillis,
        )
    }

    fun asCreatorDto(creator: Creator) = CreatorDto(
        creator.id.value, creator.name, creator.bio, creator.imageUrl,
        creator.birth?.toString(), creator.death?.toString(),
    )

    fun asCreator(dto: CreatorDto) = Creator(
        CreatorId(dto.id), dto.name, dto.bio, dto.imageUrl,
        dto.birth?.let(CalendarDate::parse), dto.death?.let(CalendarDate::parse),
    )

    fun asStoryArcDto(arc: StoryArc) = StoryArcDto(arc.id.value, arc.name, arc.description, arc.imageUrl)

    fun asStoryArc(dto: StoryArcDto) = StoryArc(StoryArcId(dto.id), dto.name, dto.description, dto.imageUrl)

    private fun asCoverDto(cover: Cover) = CoverDto(cover.name, cover.imageUrl, cover.upc)

    private fun asCover(dto: CoverDto) = Cover(dto.name, dto.imageUrl, dto.upc)
}
