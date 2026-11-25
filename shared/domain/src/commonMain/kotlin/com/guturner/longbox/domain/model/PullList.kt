package com.guturner.longbox.domain.model

/**
 * A single entry on the user's pull list — either every issue from a publisher
 * or every issue of a specific series.
 */
sealed interface PullListEntry {
    data class ByPublisher(val publisherName: String) : PullListEntry
    data class BySeries(val seriesId: SeriesId, val seriesName: String, val publisherName: String) : PullListEntry

    val displayName: String get() = when (this) {
        is ByPublisher -> publisherName
        is BySeries -> "$seriesName (${publisherName})"
    }
}

/** Returns true if [comic] is covered by this pull-list entry. */
fun PullListEntry.matches(comic: Comic): Boolean = when (this) {
    is PullListEntry.ByPublisher -> comic.publisher.name == publisherName
    is PullListEntry.BySeries -> comic.series.id == seriesId
}
