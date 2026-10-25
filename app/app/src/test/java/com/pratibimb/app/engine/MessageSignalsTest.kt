package com.pratibimb.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSignalsTest {
    private val payments = listOf("com.phonepe.app", "net.one97.paytm")

    @Test
    fun orderStatusFromAndroidFlagsOrOrderWords() {
        assertTrue(MessageSignals.isOrderStatus("progress", false, false, "Swiggy", "Your food"))
        assertTrue(MessageSignals.isOrderStatus(null, true, false, "Zomato", ""))
        assertTrue(MessageSignals.isOrderStatus(null, false, false, "Swiggy", "Your order is out for delivery"))
        assertFalse(MessageSignals.isOrderStatus("promo", false, false, "Swiggy", "Flat 60% off on biryani tonight!"))
    }

    @Test
    fun bankAlertsComeFromSmsOrPaymentApps() {
        assertTrue(MessageSignals.isBankAlert("com.google.android.apps.messaging", payments, "VM-HDFCBK", "Rs 500 debited from A/c XX1234"))
        assertTrue(MessageSignals.isBankAlert("com.phonepe.app", payments, "PhonePe", "Rs 120 sent via UPI"))
        assertFalse(MessageSignals.isBankAlert("in.swiggy.android", payments, "Swiggy", "Rs 50 cashback credited"))
        assertFalse(MessageSignals.isBankAlert("com.google.android.apps.messaging", payments, "AX-SWIGGY", "Order biryani now"))
    }

    @Test
    fun botsAreTelegramBotsAndBusinessSmsIds() {
        assertTrue(MessageSignals.isBot(AppFamily.TELEGRAM, "QuizBot"))
        assertTrue(MessageSignals.isBot(AppFamily.SMS, "AX-SWIGGY"))
        assertTrue(MessageSignals.isBot(AppFamily.SMS, "VM-AIRTEL-S"))
        assertFalse(MessageSignals.isBot(AppFamily.SMS, "Mom"))
        assertFalse(MessageSignals.isBot(AppFamily.TELEGRAM, "Rahul"))
        assertFalse(MessageSignals.isBot(AppFamily.WHATSAPP, "SomeBot"))
    }

    @Test
    fun familiesAndStreaks() {
        assertEquals(AppFamily.INSTAGRAM, MessageSignals.familyOf("com.instagram.android"))
        assertEquals(AppFamily.WHATSAPP, MessageSignals.familyOf("com.whatsapp"))
        assertEquals(AppFamily.OTHER, MessageSignals.familyOf("com.google.android.gm"))
        assertTrue(MessageSignals.isStreak("Your Streak with Rohan is about to expire ⌛"))
    }

    @Test
    fun fiveTextsInsideTwoMinutesCountUpAndOldOnesDropOut() {
        val counter = TextBurstCounter()
        val second = 1_000L

        val counts = listOf(0L, 20, 40, 60, 80).map { counter.record("rohan", it * second) }

        assertEquals(listOf(1, 2, 3, 4, 5), counts)
        assertEquals(1, counter.record("rohan", 500 * second))
        assertEquals(1, counter.record("priya", 500 * second))
    }
}
