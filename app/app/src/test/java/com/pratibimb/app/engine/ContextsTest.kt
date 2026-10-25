package com.pratibimb.app.engine

import com.pratibimb.twin.Category
import com.pratibimb.twin.NotificationFeatures
import com.pratibimb.twin.SimpleJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ContextsTest {
    @Suppress("UNCHECKED_CAST")
    private val params = SimpleJsonParser(
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "contracts/params/twin_params_student_coder.json") }
            .first { it.isFile }
            .readText(),
    ).parse() as Map<String, Any?>

    private val contexts = Contexts.fromParams(params)

    @Test
    fun thePersonaFilesCarryThePlacementsContext() {
        assertEquals(listOf("placements"), contexts.map { it.id })
    }

    @Test
    fun placementPhrasesMatchAsWholeWords() {
        assertEquals("Placements", Contexts.match("Placement Cell", "Infosys shortlist is out", Category.WORK_EMAIL, contexts)?.label)
        assertEquals("Placements", Contexts.match("+91 98765", "Drive registration closes today", Category.MESSAGING, contexts)?.label)
        assertNull(Contexts.match("Rahul", "overdrive tonight lol", Category.MESSAGING, contexts))
    }

    @Test
    fun feedsCannotBorrowAContext() {
        assertNull(Contexts.match("Myntra", "Register now for the placement sale", Category.SHOPPING_PROMO, contexts))
        assertNull(Contexts.match("reels", "placement tips", Category.SOCIAL, contexts))
    }

    @Test
    fun inactiveContextsNeverMatch() {
        val off = contexts.map { it.copy(active = false) }

        assertNull(Contexts.match("Placement Cell", "shortlist", Category.WORK_EMAIL, off))
    }

    @Test
    fun capsFollowTheKindOfNotification() {
        val caps = HoldCaps.fromParams(params)
        fun features(category: Category, conversation: Boolean = false, group: Boolean = false) = NotificationFeatures(
            key = "k", postedAtMillis = 0L, packageName = "p", category = category, androidCategory = null,
            channelImportance = 3, isOngoing = false, isConversation = conversation, priorityConversation = false,
            groupChat = group, senderHash = null, senderDisplayName = null, sameSenderRecentPings = 0,
            baitHit = false, urgencyHit = false, otpPattern = false,
        )

        assertEquals(15.0, caps.capFor(features(Category.MESSAGING, conversation = true)), 0.0)
        assertEquals(30.0, caps.capFor(features(Category.MESSAGING, conversation = true, group = true)), 0.0)
        assertEquals(30.0, caps.capFor(features(Category.WORK_EMAIL)), 0.0)
        assertEquals(45.0, caps.capFor(features(Category.SOCIAL)), 0.0)
        assertEquals(45.0, caps.capFor(features(Category.SHOPPING_PROMO)), 0.0)
        assertEquals(caps.defaultMin, caps.capFor(features(Category.NEWS)), 0.0)
    }
}
