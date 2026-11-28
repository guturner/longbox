package com.guturner.longbox.domain

import com.guturner.longbox.domain.model.CalendarDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CalendarDateTest {

    // ── construction ────────────────────────────────────────────────────────

    @Test fun validDateConstructs() {
        val d = CalendarDate(2024, 10, 9)
        assertEquals(2024, d.year)
        assertEquals(10, d.month)
        assertEquals(9, d.day)
    }

    @Test fun invalidMonthThrows() {
        assertFailsWith<IllegalArgumentException> { CalendarDate(2024, 0, 1) }
        assertFailsWith<IllegalArgumentException> { CalendarDate(2024, 13, 1) }
    }

    @Test fun invalidDayThrows() {
        assertFailsWith<IllegalArgumentException> { CalendarDate(2024, 1, 0) }
        assertFailsWith<IllegalArgumentException> { CalendarDate(2024, 1, 32) }
        assertFailsWith<IllegalArgumentException> { CalendarDate(2024, 4, 31) } // April has 30
    }

    @Test fun leapDayValid() {
        assertNotNull(CalendarDate(2024, 2, 29))  // 2024 is a leap year
    }

    @Test fun leapDayInvalidNonLeapYear() {
        assertFailsWith<IllegalArgumentException> { CalendarDate(2023, 2, 29) }
    }

    @Test fun centuryYearNotLeap() {
        assertFailsWith<IllegalArgumentException> { CalendarDate(1900, 2, 29) }
    }

    @Test fun quadCenturyYearIsLeap() {
        assertNotNull(CalendarDate(2000, 2, 29))
    }

    // ── toString / parse roundtrip ──────────────────────────────────────────

    @Test fun toStringIso8601() {
        assertEquals("2024-10-09", CalendarDate(2024, 10, 9).toString())
        assertEquals("2026-01-01", CalendarDate(2026, 1, 1).toString())
    }

    @Test fun parseValidIso() {
        assertEquals(CalendarDate(2024, 10, 9), CalendarDate.parse("2024-10-09"))
    }

    @Test fun parseStripsTimeComponent() {
        assertEquals(CalendarDate(2026, 11, 23), CalendarDate.parse("2026-11-23T00:00:00"))
    }

    @Test fun parseNullOnMalformed() {
        assertNull(CalendarDate.parse("not-a-date"))
        assertNull(CalendarDate.parse("2024/10/09"))
        assertNull(CalendarDate.parse(""))
    }

    @Test fun parseNullOnInvalidDate() {
        assertNull(CalendarDate.parse("2024-13-01"))
        assertNull(CalendarDate.parse("2024-00-01"))
    }

    // ── epochDay / ofEpochDay roundtrip ─────────────────────────────────────

    @Test fun epochDayRoundtrip() {
        val dates = listOf(
            CalendarDate(1970, 1, 1),
            CalendarDate(2000, 1, 1),
            CalendarDate(2024, 10, 9),
            CalendarDate(2026, 11, 23),
        )
        dates.forEach { d ->
            assertEquals(d, CalendarDate.ofEpochDay(d.epochDay), "Roundtrip failed for $d")
        }
    }

    @Test fun epochDayUnixEpoch() {
        assertEquals(0L, CalendarDate(1970, 1, 1).epochDay)
    }

    // ── dayOfWeek ───────────────────────────────────────────────────────────

    @Test fun dayOfWeekThursday1970() {
        assertEquals(4, CalendarDate(1970, 1, 1).dayOfWeek) // 1970-01-01 was a Thursday
    }

    @Test fun dayOfWeekMonday() {
        assertEquals(1, CalendarDate(2026, 11, 23).dayOfWeek) // known Monday
    }

    @Test fun dayOfWeekWednesday() {
        assertEquals(3, CalendarDate(2026, 11, 25).dayOfWeek) // new-comic Wednesday
    }

    // ── plusDays ────────────────────────────────────────────────────────────

    @Test fun plusDaysBasic() {
        val mon = CalendarDate(2026, 11, 23)
        assertEquals(CalendarDate(2026, 11, 25), mon.plusDays(2))
        assertEquals(CalendarDate(2026, 11, 30), mon.plusDays(7))
    }

    @Test fun plusDaysCrossesMonthBoundary() {
        assertEquals(CalendarDate(2026, 12, 1), CalendarDate(2026, 11, 30).plusDays(1))
    }

    @Test fun plusDaysCrossesYearBoundary() {
        assertEquals(CalendarDate(2027, 1, 1), CalendarDate(2026, 12, 31).plusDays(1))
    }

    // ── compareTo ───────────────────────────────────────────────────────────

    @Test fun compareToOrdering() {
        val earlier = CalendarDate(2024, 1, 1)
        val later = CalendarDate(2024, 1, 2)
        assert(earlier < later)
        assert(later > earlier)
        assertEquals(0, earlier.compareTo(earlier))
    }
}
