package com.guturner.longbox.domain

import com.guturner.longbox.domain.model.IssueNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssueNumberTest {

    private fun n(s: String) = IssueNumber(s)

    // ── ordering ────────────────────────────────────────────────────────────

    @Test fun integerOrdering() {
        assertTrue(n("1") < n("2"))
        assertTrue(n("10") > n("9"))
        assertTrue(n("100") > n("10"))
    }

    @Test fun zeroIsSmallest() {
        assertTrue(n("0") < n("1"))
    }

    @Test fun negativeIssues() {
        assertTrue(n("-1") < n("0"))
    }

    @Test fun halfIssueOrdering() {
        assertTrue(n("0") < n("½"))
        assertTrue(n("½") < n("1"))
        assertTrue(n("1") < n("1 1/2"))
        assertTrue(n("1 1/2") < n("2"))
    }

    @Test fun halfFractionForms() {
        // "½" and "1/2" both parse to numeric 0.5 and are equivalent
        assertEquals(0, n("½").compareTo(n("1/2")))
        // "1½" parses as issue 1 with suffix "½" (numeric=1.0), while
        // "1 1/2" parses as numeric=1.5 — they are distinct and 1½ < 1 1/2
        assertTrue(n("1½") < n("1 1/2"))
    }

    @Test fun suffixSameNumberOrdersAlphabetically() {
        // e.g. "12AU" vs "12MU"
        assertTrue(n("12AU") < n("12MU"))
    }

    @Test fun annualSortedAfterNumericIssues() {
        // "Annual 1" has no leading number → sorts after all numeric issues
        assertTrue(n("1000000") < n("Annual 1"))
        assertTrue(n("Annual 1") < n("Special"))
    }

    @Test fun pureTextOrdersAlphabeticallyAmongThemselves() {
        assertTrue(n("Annual") < n("Special"))
    }

    @Test fun equalIssues() {
        assertEquals(0, n("1").compareTo(n("1")))
        assertEquals(0, n("Annual 1").compareTo(n("Annual 1")))
    }

    @Test fun decimalIssues() {
        assertTrue(n("1") < n("1.5"))
        assertTrue(n("1.5") < n("2"))
    }

    @Test fun toStringReturnsRaw() {
        assertEquals("1.MU", n("1.MU").toString())
        assertEquals("Annual 1", n("Annual 1").toString())
    }

    @Test fun sortingListOfIssues() {
        val issues = listOf("10", "Annual 1", "2", "0", "1 1/2", "1").map(::IssueNumber).sorted()
        assertEquals(listOf("0", "1", "1 1/2", "2", "10", "Annual 1"), issues.map { it.raw })
    }
}
