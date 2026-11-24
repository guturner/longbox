package com.guturner.longbox.domain.model

/** Catalog identifiers are Metron ids today; wrapping them keeps that detail out of use cases. */
data class ComicId(val value: Long)
data class SeriesId(val value: Long)
data class CreatorId(val value: Long)
data class StoryArcId(val value: Long)
data class PublisherId(val value: Long)

data class Publisher(
    val id: PublisherId,
    val name: String,
)

data class Series(
    val id: SeriesId,
    val name: String,
    val sortName: String,
    val volume: Int,
    val yearBegan: Int,
) {
    /** "Batman (2016)" — how collectors disambiguate relaunches. */
    val displayName: String get() = "$name ($yearBegan)"
}

enum class CreatorRole {
    WRITER,
    ARTIST,
    PENCILLER,
    INKER,
    COLORIST,
    LETTERER,
    COVER_ARTIST,
    EDITOR,
    OTHER;

    /** Interior art roles. "Filter by artist" should match any of these. */
    val isInteriorArt: Boolean get() = this == ARTIST || this == PENCILLER || this == INKER

    companion object {
        fun fromCatalogName(name: String): CreatorRole = when (name.trim().lowercase()) {
            "writer", "script", "plot", "story" -> WRITER
            "artist" -> ARTIST
            "penciller", "pencils" -> PENCILLER
            "inker", "inks" -> INKER
            "colorist", "colors", "colourist" -> COLORIST
            "letterer", "letters" -> LETTERER
            "cover", "cover artist" -> COVER_ARTIST
            "editor", "assistant editor", "group editor", "editor in chief" -> EDITOR
            else -> OTHER
        }
    }
}

data class CreatorRef(
    val id: CreatorId,
    val name: String,
)

data class Credit(
    val creator: CreatorRef,
    val roles: Set<CreatorRole>,
)

data class StoryArcRef(
    val id: StoryArcId,
    val name: String,
)

data class Cover(
    val name: String,       // "Cover A", "Cover B Jim Lee Variant", ...
    val imageUrl: String?,
    val upc: String?,
)

data class Comic(
    val id: ComicId,
    val publisher: Publisher,
    val series: Series,
    val number: IssueNumber,
    val title: String?,
    val coverDate: CalendarDate?,
    /** In-store (release) date, which usually differs from the cover date. */
    val storeDate: CalendarDate?,
    val summary: String?,
    /** Main cover (Cover A); used as the library thumbnail. */
    val coverA: Cover,
    val variantCovers: List<Cover>,
    val credits: List<Credit>,
    /** Story arcs, which is where crossover events such as "Absolute Power" live. */
    val storyArcs: List<StoryArcRef>,
    val upc: String?,
) {
    val displayTitle: String get() = "${series.name} #${number.raw}"

    val writers: List<CreatorRef>
        get() = credits.filter { CreatorRole.WRITER in it.roles }.map { it.creator }

    val artists: List<CreatorRef>
        get() = credits.filter { credit -> credit.roles.any { it.isInteriorArt } }.map { it.creator }

    val coverArtists: List<CreatorRef>
        get() = credits.filter { CreatorRole.COVER_ARTIST in it.roles }.map { it.creator }

    /** Cover A plus every variant, in catalog order. */
    val allCovers: List<Cover> get() = listOf(coverA) + variantCovers
}

data class Creator(
    val id: CreatorId,
    val name: String,
    val bio: String?,
    val imageUrl: String?,
    val birth: CalendarDate?,
    val death: CalendarDate?,
)

data class StoryArc(
    val id: StoryArcId,
    val name: String,
    val description: String?,
    val imageUrl: String?,
)
