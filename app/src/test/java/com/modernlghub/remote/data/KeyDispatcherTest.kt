package com.modernlghub.remote.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the dispatcher on runBlocking's single thread with a fake clock and a fake hub whose
 * answers the test hands out one by one – fully deterministic, no real time involved.
 */
class KeyDispatcherTest {

    private class Harness(scope: CoroutineScope) {
        var now = 0L
        val sent = mutableListOf<String>()
        val dropped = mutableListOf<Pair<String, KeyDispatcher.DropReason>>()
        val completed = mutableListOf<Pair<String, HubResult>>()
        val answers = Channel<HubResult>(Channel.UNLIMITED)
        var throwNext = false

        val dispatcher = KeyDispatcher(
            scope = scope,
            send = { key ->
                sent += key
                if (throwNext) {
                    throwNext = false
                    throw IllegalStateException("boom")
                }
                answers.receive()
            },
            clock = { now },
            onDropped = { key, reason -> dropped += key to reason },
            onSent = { key, result -> completed += key to result },
        )

        fun answer(result: HubResult = HubResult.Ok("{}")) = answers.trySend(result)
    }

    private fun test(block: suspend Harness.() -> Unit) = runBlocking {
        val h = Harness(this)
        try {
            h.block()
        } finally {
            h.dispatcher.close()
        }
    }

    private suspend fun settle() = repeat(20) { yield() }

    @Test
    fun `keys arrive in order, one at a time`() = test {
        listOf("UP", "RIGHT", "ENTER").forEach { assertTrue(dispatcher.offer(it)) }
        settle()
        assertEquals(listOf("UP"), sent)
        assertEquals("UP", dispatcher.inFlight)
        answer(); settle()
        assertEquals(listOf("UP", "RIGHT"), sent)
        answer(); answer(); settle()
        assertEquals(listOf("UP", "RIGHT", "ENTER"), sent)
        assertEquals(0, dispatcher.pendingCount)
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun `hold-to-repeat cannot pile up behind a slow request`() = test {
        // Finger held on DOWN for 2 s while the first request hangs: 18 repeats.
        repeat(18) { dispatcher.offer("DOWN"); settle() }
        assertEquals(1, sent.size)
        // In flight + 2 waiting = repeat cap of 3; the rest was refused at once.
        assertEquals(2, dispatcher.pendingCount)
        assertEquals(15, dropped.count { it.second == KeyDispatcher.DropReason.REPEAT_CAP })
        repeat(3) { answer() }
        settle()
        assertEquals(3, sent.size)
    }

    @Test
    fun `lifting the finger drops what still waits`() = test {
        repeat(3) { dispatcher.offer("LEFT"); settle() }
        assertEquals(2, dispatcher.cancelPending())
        answer(); settle()
        assertEquals(listOf("LEFT"), sent)
        assertEquals(2, dropped.count { it.second == KeyDispatcher.DropReason.CANCELLED })
    }

    @Test
    fun `different keys are not capped by the repeat rule, only by size`() = test {
        val keys = List(14) { if (it % 2 == 0) "UP" else "DOWN" }
        val accepted = keys.map { dispatcher.offer(it).also { settle() } }
        // First one goes on the wire, then maxPending may wait.
        assertEquals(1 + dispatcher.maxPending, accepted.count { it })
        assertEquals(dispatcher.maxPending, dispatcher.pendingCount)
        assertEquals(14 - 1 - dispatcher.maxPending, dropped.count { it.second == KeyDispatcher.DropReason.QUEUE_FULL })
    }

    @Test
    fun `a key that waited too long is never sent`() = test {
        dispatcher.offer("UP"); settle()
        dispatcher.offer("BACK")
        now += dispatcher.maxAgeMs + 1
        dispatcher.offer("HOME") // fresh
        answer(); settle()
        assertEquals(listOf("UP", "HOME"), sent)
        assertEquals(listOf("BACK" to KeyDispatcher.DropReason.STALE), dropped)
        answer(); settle()
    }

    @Test
    fun `unreachable hub flushes the backlog instead of replaying it later`() = test {
        dispatcher.offer("UP"); settle()
        dispatcher.offer("RIGHT"); dispatcher.offer("ENTER")
        answer(HubResult.Unreachable); settle()
        assertEquals(listOf("UP"), sent)
        assertEquals(
            listOf("RIGHT", "ENTER"),
            dropped.filter { it.second == KeyDispatcher.DropReason.FLUSHED_AFTER_FAILURE }.map { it.first },
        )
        // The queue keeps working afterwards.
        dispatcher.offer("BACK"); settle()
        answer(); settle()
        assertEquals(listOf("UP", "BACK"), sent)
    }

    @Test
    fun `a rejected key does not flush the queue`() = test {
        dispatcher.offer("UP"); settle()
        dispatcher.offer("DOWN")
        answer(HubResult.Rejected(400, "bad key")); settle()
        answer(); settle()
        assertEquals(listOf("UP", "DOWN"), sent)
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun `volume steps may queue deeper but stay bounded`() = test {
        repeat(30) { dispatcher.offer("VOLUMEUP"); settle() }
        assertEquals(dispatcher.maxVolumeRepeats - 1, dispatcher.pendingCount)
        assertEquals(30 - dispatcher.maxVolumeRepeats, dropped.size)
        assertTrue(dropped.all { !it.second.afterQueued })
        repeat(dispatcher.maxVolumeRepeats) { answer() }
        settle()
        assertEquals(dispatcher.maxVolumeRepeats, completed.size)
    }

    @Test
    fun `an exception in the transport does not kill the worker`() = test {
        throwNext = true
        dispatcher.offer("UP"); settle()
        assertEquals(listOf("UP" to HubResult.Unreachable), completed)
        dispatcher.offer("DOWN"); settle()
        answer(); settle()
        assertEquals(listOf("UP", "DOWN"), sent)
    }

    @Test
    fun `offer refuses after the run is broken by another key`() = test {
        // UP in flight, UP, UP waiting -> another UP refused, but DOWN then UP accepted again.
        repeat(3) { dispatcher.offer("UP"); settle() }
        assertFalse(dispatcher.offer("UP"))
        assertTrue(dispatcher.offer("DOWN"))
        assertTrue(dispatcher.offer("UP"))
        repeat(5) { answer() }
        settle()
        assertEquals(listOf("UP", "UP", "UP", "DOWN", "UP"), sent)
    }
}
