package com.guturner.longbox.server.metron

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Cover
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.CreatorRef
import com.guturner.longbox.domain.model.CreatorRole
import com.guturner.longbox.domain.model.Credit
import com.guturner.longbox.domain.model.IssueNumber
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.Series
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.model.StoryArcRef

/** Metron DTO -> domain. Anti-corruption layer: Metron's shape stops here. */
object MetronMapper {

    fun asComic(issue: MetronIssue): Comic = Comic(
        id = ComicId(issue.id),
        publisher = Publisher(PublisherId(issue.publisher.id), issue.publisher.name),
        series = Series(
            id = SeriesId(issue.series.id),
            name = issue.series.name,
            sortName = issue.series.sortName.ifBlank { issue.series.name },
            volume = issue.series.volume,
            yearBegan = issue.series.yearBegan,
        ),
        number = IssueNumber(issue.number),
        title = issue.name.firstOrNull()?.takeIf { it.isNotBlank() } ?: issue.title.takeIf { it.isNotBlank() },
        coverDate = issue.coverDate?.let(CalendarDate::parse),
        storeDate = issue.storeDate?.let(CalendarDate::parse),
        summary = issue.desc.takeIf { it.isNotBlank() },
        coverA = Cover("Cover A", issue.image, issue.upc.ifBlank { null }),
        variantCovers = issue.variants.mapIndexed { index, variant ->
            Cover(
                name = variant.name.ifBlank { "Variant ${index + 1}" },
                imageUrl = variant.image,
                upc = variant.upc.ifBlank { null },
            )
        },
        credits = issue.credits
            .groupBy { it.id }
            .map { (id, credits) ->
                Credit(
                    creator = CreatorRef(CreatorId(id), credits.first().creator),
                    roles = credits.flatMap { it.role }.map { CreatorRole.fromCatalogName(it.name) }.toSet(),
                )
            },
        storyArcs = issue.arcs.map { StoryArcRef(StoryArcId(it.id), it.name) },
        upc = issue.upc.ifBlank { null },
    )

    /** List results lack credits and publisher, so the caller supplies the publisher it filtered by. */
    fun asReleaseComic(item: MetronIssueListItem, publisher: Publisher): Comic = Comic(
        id = ComicId(item.id),
        publisher = publisher,
        series = Series(SeriesId(item.series.id), item.series.name, item.series.name, item.series.volume, item.series.yearBegan),
        number = IssueNumber(item.number),
        title = null,
        coverDate = item.coverDate?.let(CalendarDate::parse),
        storeDate = item.storeDate?.let(CalendarDate::parse),
        summary = null,
        coverA = Cover("Cover A", item.image, null),
        variantCovers = emptyList(),
        credits = emptyList(),
        storyArcs = emptyList(),
        upc = null,
    )

    fun asCreator(dto: MetronCreator) = Creator(
        id = CreatorId(dto.id),
        name = dto.name,
        bio = dto.desc.takeIf { it.isNotBlank() },
        imageUrl = dto.image,
        birth = dto.birth?.let(CalendarDate::parse),
        death = dto.death?.let(CalendarDate::parse),
    )

    fun asStoryArc(dto: MetronArc) = StoryArc(
        id = StoryArcId(dto.id),
        name = dto.name,
        description = dto.desc.takeIf { it.isNotBlank() },
        imageUrl = dto.image,
    )
}
