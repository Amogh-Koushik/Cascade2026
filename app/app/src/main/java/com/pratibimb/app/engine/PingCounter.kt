package com.pratibimb.app.engine

// Counts repeated pings per sender for the repeated-ping override (CONTRACTS.md section 3). A
// ping is a burst: a message less than BURST_GAP_MILLIS after the sender's previous one
// continues the same ping, because that is someone typing several lines, not trying again.
// Plain Kotlin, so it runs in JVM tests; agents/feature_builder.py counts the same way.
class PingCounter(private val burstGapMillis: Long = BURST_GAP_MILLIS) {
    private val pingStarts = HashMap<String, ArrayDeque<Long>>()
    private val lastMessage = HashMap<String, Long>()

    // Earlier pings from this sender that started inside the window, not counting the one
    // this message belongs to; then records the message.
    @Synchronized
    fun record(senderHash: String, postedAtMillis: Long, windowMillis: Long): Int {
        val starts = pingStarts.getOrPut(senderHash) { ArrayDeque() }
        val last = lastMessage[senderHash]
        lastMessage[senderHash] = postedAtMillis
        val continuesBurst = last != null && postedAtMillis - last < burstGapMillis
        if (!continuesBurst) starts.addLast(postedAtMillis)
        while (starts.isNotEmpty() && postedAtMillis - starts.first() > windowMillis) starts.removeFirst()
        return (starts.size - 1).coerceAtLeast(0)
    }

    // Earlier pings inside the window without recording anything, for a message that does not
    // count as a ping (a reel or a forward when the text-only rule is on).
    @Synchronized
    fun peek(senderHash: String, postedAtMillis: Long, windowMillis: Long): Int =
        pingStarts[senderHash].orEmpty().count { postedAtMillis - it <= windowMillis }

    @Synchronized
    fun clear() {
        pingStarts.clear()
        lastMessage.clear()
    }

    companion object {
        const val BURST_GAP_MILLIS = 20_000L
    }
}
