package com.guturner.longbox.server

import com.guturner.longbox.server.metron.MetronRateLimiter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronRateLimiterTest {

    private var clock = 0L
    private val sleeps = mutableListOf<Long>()

    private fun limiter(perMinute: Int, perDay: Int) =
        MetronRateLimiter(perMinute, perDay, nowMillis = { clock }, sleep = { sleeps += it; clock += it })

    @Test
    fun testWaitsForMinuteWindowOnceBurstIsSpent() = runTest {
        // Given
        val limiter = limiter(perMinute = 3, perDay = 100)

        // When
        repeat(4) { assertTrue(limiter.acquire()); clock += 1_000 }

        // Then: the 4th request waited until the first slot aged out of the 60s window
        assertEquals(listOf(57_000L), sleeps)
    }

    @Test
    fun testRefusesWhenDailyBudgetIsSpent() = runTest {
        val limiter = limiter(perMinute = 100, perDay = 2)

        assertTrue(limiter.acquire())
        assertTrue(limiter.acquire())
        assertFalse(limiter.acquire())
    }

    @Test
    fun testHonoursRetryAfter() = runTest {
        val limiter = limiter(perMinute = 100, perDay = 100)

        limiter.backOff(retryAfterSeconds = 30)
        limiter.acquire()

        assertEquals(listOf(30_000L), sleeps)
    }
}
