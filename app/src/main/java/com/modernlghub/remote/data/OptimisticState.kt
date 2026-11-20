package com.modernlghub.remote.data

/**
 * Reconciles optimistic UI changes with polled status.
 *
 * Problem it solves: the user turns the volume up, the UI shows 24 at once, but a poll that was
 * already on its way answers 23 – the number jumps back and forth. Each optimistic change
 * therefore *holds* its field until either
 *  - a poll that **started after** the last command for that field finished confirms the TV state
 *    (the TV's answer wins, even if it differs: the command may have been ignored), or
 *  - the hold expires ([holdMs]), as a safety net against a lost command, or
 *  - the command failed ([end] with ok = false): the hold is released at once so the next poll
 *    rolls the change back.
 *
 * Ordering uses [tick] tickets instead of wall time, so "started after" is exact.
 * Thread-safe; pure Kotlin for unit tests.
 */
class OptimisticState(
    private val clock: () -> Long = ::monotonicMillis,
    val holdMs: Long = 4_000L,
) {
    enum class Field { VOLUME, MUTE, SCREEN, APP, OUTPUT, POWER }

    private class Hold(var inFlight: Int, var doneTicket: Long, var expiresAt: Long)

    private val lock = Any()
    private var ticket = 0L
    private val holds = HashMap<Field, Hold>()

    /** A monotonically increasing ticket; take one when a poll starts. */
    fun tick(): Long = synchronized(lock) { ++ticket }

    /** An optimistic change to [field] was applied and its command is about to be sent. */
    fun begin(field: Field) = synchronized(lock) {
        val h = holds.getOrPut(field) { Hold(0, Long.MAX_VALUE, 0) }
        h.inFlight++
        h.doneTicket = Long.MAX_VALUE
        h.expiresAt = clock() + holdMs
    }

    /** The command for [field] finished; [ok] = the hub confirmed it. */
    fun end(field: Field, ok: Boolean) = synchronized(lock) {
        val h = holds[field] ?: return@synchronized
        if (!ok) {
            holds.remove(field)
            return@synchronized
        }
        h.inFlight = (h.inFlight - 1).coerceAtLeast(0)
        if (h.inFlight == 0) {
            h.doneTicket = ++ticket
            // Give the TV a moment to apply it before a poll may overrule the UI.
            h.expiresAt = maxOf(h.expiresAt, clock() + 1_000L)
        }
    }

    /** A [begin] whose command was never sent (e.g. refused by the key queue): undo its count only. */
    fun abandon(field: Field) = end(field, ok = true)

    /** Drops all holds (connection reset). */
    fun clear() = synchronized(lock) { holds.clear() }

    fun isHeld(field: Field): Boolean = synchronized(lock) { holds.containsKey(field) }

    /**
     * Merges a polled status into the current one: held fields keep their local value unless
     * the poll (started at [pollTicket]) is newer than the last command for that field.
     */
    fun merge(local: TvStatus, remote: TvStatus, pollTicket: Long): TvStatus = synchronized(lock) {
        val now = clock()
        val keep = HashSet<Field>()
        val it = holds.entries.iterator()
        while (it.hasNext()) {
            val (field, h) = it.next()
            val confirmed = h.inFlight == 0 && pollTicket > h.doneTicket
            if (confirmed || now >= h.expiresAt) it.remove() else keep += field
        }
        if (keep.isEmpty()) return@synchronized remote
        var audio = remote.audio
        if (Field.VOLUME in keep) audio = audio.copy(volume = local.audio.volume)
        if (Field.MUTE in keep) audio = audio.copy(muted = local.audio.muted)
        if (Field.OUTPUT in keep) audio = audio.copy(soundOutput = local.audio.soundOutput)
        remote.copy(
            audio = audio,
            screenOff = if (Field.SCREEN in keep) local.screenOff else remote.screenOff,
            currentAppId = if (Field.APP in keep) local.currentAppId else remote.currentAppId,
            power = if (Field.POWER in keep) local.power else remote.power,
        )
    }
}
