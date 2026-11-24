package com.guturner.longbox.domain

import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.IssueNumber
import com.guturner.longbox.domain.usecase.ReleaseWeek
import kotlin.test.Test
import kotlin.test.assertEquals

class IssueNumberAndDateTest {

    @Test
    fun testIssueNumbersSortLikeALongbox() {
        // Given
        val shuffled = listOf("10", "Annual 1", "1.MU", "2", "½", "0", "1", "100", "-1", "1/2", "1000000")

        // When
        val sorted = shuffled.map(::IssueNumber).sorted().map { it.raw }

        // Then
        assertEquals(listOf("-1", "0", "½", "1/2", "1", "1.MU", "2", "10", "100", "1000000", "Annual 1"), sorted)
    }

    @Test
    fun testCalendarDateRoundTripsThroughEpochDay() {
        val dates = listOf("1970-01-01", "2000-02-29", "2026-11-25", "1938-04-18")

        dates.forEach { iso ->
            val date = CalendarDate.parse(iso)!!
            assertEquals(iso, CalendarDate.ofEpochDay(date.epochDay).toString())
        }
    }

    @Test
    fun testDayOfWeek() {
        assertEquals(4, CalendarDate(1970, 1, 1).dayOfWeek)   // Thursday
        assertEquals(1, CalendarDate(2026, 11, 23).dayOfWeek) // Monday
        assertEquals(7, CalendarDate(2026, 11, 22).dayOfWeek) // Sunday
    }

    @Test
    fun testReleaseWeekRunsSundayToSaturday() {
        val week = ReleaseWeek.containing(CalendarDate(2026, 11, 23))

        assertEquals(CalendarDate(2026, 11, 22), week.start)
        assertEquals(CalendarDate(2026, 11, 28), week.end)
        assertEquals(CalendarDate(2026, 11, 25), week.wednesday)
        assertEquals(CalendarDate(2026, 11, 15), week.previous().start)
    }

    @Test
    fun testParseRejectsImpossibleDates() {
        assertEquals(null, CalendarDate.parse("2026-02-30"))
        assertEquals(null, CalendarDate.parse("not-a-date"))
    }
}
