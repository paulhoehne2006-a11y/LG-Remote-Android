package com.modernlghub.remote.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Ordered, bounded queue for remote keys.
 *
 * Keys are sent one after another (the hub handles requests in parallel threads, so sending them
 * concurrently could reorder a fast swipe). The queue is built so that keys never pile up and
 * never arrive late:
 *
 * - **Repeat cap**: at most [maxRepeats] identical keys may wait in a row. Hold-to-repeat fires
 *   about every 110 ms; on a slow network the extra repeats are dropped instead of queued, so
 *   the TV stops moving as soon as the finger lifts.
 * - **Size cap**: at most [maxPending] keys wait in total; further keys are refused.
 * - **Staleness**: a key that waited longer than [maxAgeMs] is dropped when its turn comes.
 * - **Failure flush**: when the hub is unreachable, everything still waiting is dropped – it
 *   would otherwise trickle in seconds later once the network recovers.
 * - [cancelPending] drops whatever waits (finger lifted, connection reset).
 *
 * Volume steps use a larger repeat cap ([maxVolumeRepeats]) so a "set volume to X" from the
 * system slider is not truncated; they are still bounded and expire like every other key.
 *
 * Pure Kotlin (clock and transport injected) so the policy is unit-tested.
 */
class KeyDispatcher(
    scope: CoroutineScope,
    private val send: suspend (key: String) -> HubResult,
    private val clock: () -> Long = ::monotonicMillis,
    private val onDropped: (key: String, reason: DropReason) -> Unit = { _, _ -> },
    private val onSent: (key: String, result: HubResult) -> Unit = { _, _ -> },
    val maxPending: Int = 10,
    val maxRepeats: Int = 3,
    val maxVolumeRepeats: Int = 10,
    val maxAgeMs: Long = 1_000L,
) {
    enum class DropReason(val afterQueued: Boolean) {
        /** Refused by [offer] (it returned false). */
        REPEAT_CAP(false),
        QUEUE_FULL(false),

        /** Accepted, but dropped before it was sent. */
        STALE(true),
        FLUSHED_AFTER_FAILURE(true),
        CANCELLED(true),
    }

    private class Entry(val key: String, val enqueuedAt: Long)

    private val lock = Any()
    private val pending = ArrayDeque<Entry>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Key currently on the wire (for tests and diagnostics). */
    @Volatile var inFlight: String? = null
        private set

    val pendingCount: Int get() = synchronized(lock) { pending.size }

    private val worker: Job = scope.launch {
        while (true) {
            val entry = synchronized(lock) {
                pending.removeFirstOrNull()?.also { inFlight = it.key }
            }
            if (entry == null) {
                wake.receive()
                continue
            }
            if (clock() - entry.enqueuedAt > maxAgeMs) {
                inFlight = null
                onDropped(entry.key, DropReason.STALE)
                continue
            }
            val result = try {
                send(entry.key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HubResult.Unreachable
            } finally {
                inFlight = null
            }
            if (result is HubResult.Unreachable) {
                drain().forEach { onDropped(it.key, DropReason.FLUSHED_AFTER_FAILURE) }
            }
            onSent(entry.key, result)
        }
    }

    /**
     * Queues [key]. Returns false if it was dropped by the repeat or size cap. Never blocks and is
     * safe to call from any thread.
     */
    fun offer(key: String): Boolean {
        val reason = synchronized(lock) {
            val cap = if (isVolumeKey(key)) maxVolumeRepeats else maxRepeats
            // Count identical keys at the tail, plus the one on the wire if the tail is empty
            // or of the same key (it still has to arrive before the next one).
            var run = 0
            for (i in pending.indices.reversed()) {
                if (pending[i].key == key) run++ else break
            }
            if (run == pending.size && inFlight == key) run++
            when {
                run >= cap -> DropReason.REPEAT_CAP
                pending.size >= maxPending -> DropReason.QUEUE_FULL
                else -> {
                    pending.addLast(Entry(key, clock()))
                    null
                }
            }
        }
        if (reason != null) {
            onDropped(key, reason)
            return false
        }
        wake.trySend(Unit)
        return true
    }

    /** Drops every key that has not been sent yet; the one on the wire still completes. */
    fun cancelPending(): Int {
        val dropped = drain()
        dropped.forEach { onDropped(it.key, DropReason.CANCELLED) }
        return dropped.size
    }

    /** Stops the worker (tests; the app's queue lives as long as the process). */
    fun close() {
        worker.cancel()
        drain()
    }

    private fun drain(): List<Entry> = synchronized(lock) {
        val all = pending.toList()
        pending.clear()
        all
    }

    companion object {
        fun isVolumeKey(key: String) = key == "VOLUMEUP" || key == "VOLUMEDOWN"
    }
}

/** Monotonic milliseconds (unaffected by wall-clock changes). */
fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L
