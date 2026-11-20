package com.modernlghub.remote.data

/**
 * Who wants status polling, and how often. The poll loop runs while at least one client is
 * registered and uses the most demanding client's numbers.
 *
 * @param intervalMs delay between polls while the hub answers.
 * @param maxOfflineIntervalMs upper bound of the back-off while the hub does not answer
 *   (TV off, phone away from home). Visible UI keeps this short so the TV is picked up quickly
 *   after switching it on; the background notification backs off much further.
 */
data class PollClient(val intervalMs: Long, val maxOfflineIntervalMs: Long)

object PollPolicy {
    /** Default for visible UI. */
    val UI = PollClient(intervalMs = 3_000L, maxOfflineIntervalMs = 10_000L)

    /** Default for the background live notification. */
    val BACKGROUND = PollClient(intervalMs = 5_000L, maxOfflineIntervalMs = 60_000L)

    /**
     * Delay before the next poll. Online: the shortest client interval. Offline: doubles with
     * each consecutive failure, starting at twice the interval, capped by the most demanding
     * client's maximum.
     */
    fun nextDelay(clients: Collection<PollClient>, online: Boolean, consecutiveFailures: Int): Long {
        if (clients.isEmpty()) return UI.intervalMs
        val interval = clients.minOf { it.intervalMs }.coerceAtLeast(500L)
        if (online || consecutiveFailures <= 0) return interval
        val cap = clients.minOf { it.maxOfflineIntervalMs }.coerceAtLeast(interval)
        val shift = (consecutiveFailures).coerceAtMost(16)
        val backoff = interval shl shift
        return backoff.coerceIn(interval, cap)
    }
}
