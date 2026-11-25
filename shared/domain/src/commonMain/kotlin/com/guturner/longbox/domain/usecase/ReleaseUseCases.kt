package com.guturner.longbox.domain.usecase

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.map
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.domain.repository.ComicCatalog

/**
 * Publishers the pull list cares about. Names must match Metron's publisher index exactly.
 * Verified against https://metron.cloud/api/publisher/ — "Titan Books" does not exist in Metron;
 * the correct entry is "Titan Comics".
 */
val FOLLOWED_PUBLISHERS = setOf("DC Comics", "Marvel", "Dynamite Entertainment", "Titan Comics")

data class ReleaseWeek(val start: CalendarDate, val end: CalendarDate) {
    companion object {
        /**
         * The Sunday-to-Saturday week containing [day]. New comics reach US stores on
         * Wednesdays, so a week labelled by its Wednesday is unambiguous.
         */
        fun containing(day: CalendarDate): ReleaseWeek {
            val daysSinceSunday = day.dayOfWeek % 7 // Sunday (7) -> 0
            val start = day.plusDays(-daysSinceSunday.toLong())
            return ReleaseWeek(start, start.plusDays(6))
        }
    }

    val wednesday: CalendarDate get() = start.plusDays(3)
    fun previous(): ReleaseWeek = containing(start.plusDays(-1))
    fun next(): ReleaseWeek = containing(end.plusDays(1))
}

class GetWeeklyReleasesUseCase(
    private val catalog: ComicCatalog,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        week: ReleaseWeek = ReleaseWeek.containing(clock.today()),
        publishers: Set<String> = FOLLOWED_PUBLISHERS,
    ): Outcome<List<Comic>> =
        catalog.releases(week.start, week.end).map { comics ->
            comics
                .filter { publishers.isEmpty() || it.publisher.name in publishers }
                .sortedWith(compareBy<Comic> { it.publisher.name }.thenBy { it.series.sortName }.thenBy { it.number })
        }
}
