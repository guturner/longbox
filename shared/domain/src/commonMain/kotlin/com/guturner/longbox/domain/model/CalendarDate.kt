package com.guturner.longbox.domain.model

/**
 * A calendar date with no time zone. Kept in the domain (instead of kotlinx-datetime)
 * so the innermost ring has no third-party dependencies.
 */
data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<CalendarDate> {

    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $year-$month-$day" }
    }

    /** Days since 1970-01-01 (proleptic Gregorian). */
    val epochDay: Long
        get() {
            // Howard Hinnant's days_from_civil
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era.toLong() * 146097 + doe - 719468
        }

    /** ISO-8601 day of week: Monday = 1 ... Sunday = 7. */
    val dayOfWeek: Int
        get() = ((epochDay + 3).mod(7L) + 1).toInt() // 1970-01-01 was a Thursday

    fun plusDays(days: Long): CalendarDate = ofEpochDay(epochDay + days)

    override fun compareTo(other: CalendarDate): Int = epochDay.compareTo(other.epochDay)

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    companion object {

        fun parse(iso: String): CalendarDate? {
            val parts = iso.trim().take(10).split('-')
            if (parts.size != 3) return null
            val (y, m, d) = parts.map { it.toIntOrNull() ?: return null }
            return runCatching { CalendarDate(y, m, d) }.getOrNull()
        }

        fun ofEpochDay(epochDay: Long): CalendarDate {
            // Howard Hinnant's civil_from_days
            val z = epochDay + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400).toInt() + if (m <= 2) 1 else 0
            return CalendarDate(y, m, d)
        }

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeap(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun isLeap(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }
}
