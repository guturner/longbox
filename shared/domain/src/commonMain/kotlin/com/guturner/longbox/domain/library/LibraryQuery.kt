package com.guturner.longbox.domain.library

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.SeriesId
import com.guturner.longbox.domain.model.StoryArcId

/**
 * Library filters combine with AND across dimensions and OR within one dimension
 * (e.g. writer = King OR Taylor, AND event = Absolute Power).
 */
data class LibraryFilter(
    val text: String = "",
    val seriesIds: Set<SeriesId> = emptySet(),
    val publisherIds: Set<PublisherId> = emptySet(),
    val writerIds: Set<CreatorId> = emptySet(),
    val artistIds: Set<CreatorId> = emptySet(),
    val storyArcIds: Set<StoryArcId> = emptySet(),
) {
    val isEmpty: Boolean
        get() = text.isBlank() && seriesIds.isEmpty() && publisherIds.isEmpty() &&
            writerIds.isEmpty() && artistIds.isEmpty() && storyArcIds.isEmpty()

    fun matches(entry: CollectionEntry): Boolean {
        val comic = entry.comic
            ?: return isEmpty || (text.isNotBlank() && entry.scannedCode.contains(text.trim()))

        if (seriesIds.isNotEmpty() && comic.series.id !in seriesIds) return false
        if (publisherIds.isNotEmpty() && comic.publisher.id !in publisherIds) return false
        if (writerIds.isNotEmpty() && comic.writers.none { it.id in writerIds }) return false
        if (artistIds.isNotEmpty() && comic.artists.none { it.id in artistIds }) return false
        if (storyArcIds.isNotEmpty() && comic.storyArcs.none { it.id in storyArcIds }) return false

        val needle = text.trim().lowercase()
        if (needle.isEmpty()) return true
        return sequenceOf(comic.displayTitle, comic.title.orEmpty(), comic.series.name)
            .plus(comic.credits.map { it.creator.name })
            .plus(comic.storyArcs.map { it.name })
            .any { it.lowercase().contains(needle) }
    }
}

enum class LibrarySort {
    /** Grouped by series (sort name, then start year), then issue number. The default. */
    SERIES_THEN_ISSUE,
    RELEASE_DATE_NEWEST,
    RECENTLY_ADDED;

    val comparator: Comparator<CollectionEntry>
        get() = when (this) {
            SERIES_THEN_ISSUE -> compareBy<CollectionEntry> { it.state != EntryState.IN_STACK }
                .thenBy(nullsLast()) { it.comic?.series?.sortName?.lowercase() }
                .thenBy(nullsLast()) { it.comic?.series?.yearBegan }
                .thenBy(nullsLast()) { it.comic?.number }
                .thenBy { it.addedAtEpochMillis }
            // Undated entries sort last.
            RELEASE_DATE_NEWEST -> compareBy<CollectionEntry, CalendarDate?>(nullsLast(reverseOrder())) {
                it.comic?.storeDate ?: it.comic?.coverDate
            }.thenByDescending { it.addedAtEpochMillis }
            RECENTLY_ADDED -> compareByDescending { it.addedAtEpochMillis }
        }
}

data class LibraryQuery(
    val filter: LibraryFilter = LibraryFilter(),
    val sort: LibrarySort = LibrarySort.SERIES_THEN_ISSUE,
)

/** One selectable filter value, with how many collection entries it would match. */
data class Facet<K>(val key: K, val label: String, val count: Int)

data class LibraryFacets(
    val series: List<Facet<SeriesId>>,
    val publishers: List<Facet<PublisherId>>,
    val writers: List<Facet<CreatorId>>,
    val artists: List<Facet<CreatorId>>,
    val storyArcs: List<Facet<StoryArcId>>,
) {
    companion object {
        val EMPTY = LibraryFacets(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        fun from(entries: List<CollectionEntry>): LibraryFacets {
            val comics = entries.mapNotNull { it.comic }
            return LibraryFacets(
                series = comics.facet({ it.series.id }, { it.series.displayName }),
                publishers = comics.facet({ it.publisher.id }, { it.publisher.name }),
                writers = comics.flatMap { it.writers.distinct() }.facet({ it.id }, { it.name }),
                artists = comics.flatMap { it.artists.distinct() }.facet({ it.id }, { it.name }),
                storyArcs = comics.flatMap { it.storyArcs.distinct() }.facet({ it.id }, { it.name }),
            )
        }

        private fun <T, K> List<T>.facet(key: (T) -> K, label: (T) -> String): List<Facet<K>> =
            groupBy(key)
                .map { (k, items) -> Facet(k, label(items.first()), items.size) }
                .sortedWith(compareByDescending<Facet<K>> { it.count }.thenBy { it.label.lowercase() })
    }
}
