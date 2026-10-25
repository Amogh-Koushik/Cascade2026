package com.pratibimb.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PingCounterTest {
    private val second = 1_000L
    private val window = 3 * 60_000L

    @Test
    fun linesTypedInOneBurstAreOnePing() {
        val counter = PingCounter()

        val counts = listOf(0L, 5 * second, 12 * second, 25 * second).map { counter.record("mom", it, window) }

        assertEquals(listOf(0, 0, 0, 0), counts)
    }

    @Test
    fun separateTriesCountUpAndTheThirdReachesTwoEarlier() {
        val counter = PingCounter()

        val counts = listOf(0L, 60 * second, 120 * second).map { counter.record("mom", it, window) }

        assertEquals(listOf(0, 1, 2), counts)
    }

    @Test
    fun pingsOutsideTheWindowDropOut() {
        val counter = PingCounter()
        counter.record("mom", 0L, window)
        counter.record("mom", 60 * second, window)

        assertEquals(1, counter.record("mom", 200 * second, window))
    }

    @Test
    fun sendersAreCountedSeparately() {
        val counter = PingCounter()
        counter.record("mom", 0L, window)

        assertEquals(0, counter.record("papa", 60 * second, window))
    }

    @Test
    fun peekCountsWithoutRecording() {
        val counter = PingCounter()
        counter.record("rohan", 0L, window)

        assertEquals(1, counter.peek("rohan", 60 * second, window))
        assertEquals(1, counter.record("rohan", 61 * second, window))
    }
}
