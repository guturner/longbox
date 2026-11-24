package com.guturner.longbox.server.metron

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client-side pacing for Metron's published throttles (20 requests/minute burst,
 * 5,000/day sustained). Every outbound request awaits a slot. 429 responses push the
 * next slot out by the server's Retry-After value.
 */
class MetronRateLimiter(
    private val perMinute: Int,
    private val perDay: Int,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private val minuteWindow = ArrayDeque<Long>()
    private val dayWindow = ArrayDeque<Long>()
    private var blockedUntil = 0L

    /** Returns false (without waiting) if today's budget is spent; callers re-queue the work. */
    suspend fun acquire(): Boolean {
        while (true) {
            // Compute the wait under the lock, but sleep outside it so backOff() is never blocked.
            val wait = mutex.withLock {
                val now = nowMillis()
                minuteWindow.dropOlderThan(now - MINUTE)
                dayWindow.dropOlderThan(now - DAY)

                if (dayWindow.size >= perDay) return false

                val waitForBlock = blockedUntil - now
                val waitForMinute = if (minuteWindow.size >= perMinute) minuteWindow.first() + MINUTE - now else 0L
                val wait = maxOf(waitForBlock, waitForMinute)
                if (wait <= 0) {
                    minuteWindow.addLast(now)
                    dayWindow.addLast(now)
                    return true
                }
                wait
            }
            sleep(wait)
        }
    }

    suspend fun backOff(retryAfterSeconds: Long) = mutex.withLock {
        blockedUntil = maxOf(blockedUntil, nowMillis() + retryAfterSeconds * 1_000)
    }

    private fun ArrayDeque<Long>.dropOlderThan(cutoff: Long) {
        while (isNotEmpty() && first() <= cutoff) removeFirst()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
    }
}
