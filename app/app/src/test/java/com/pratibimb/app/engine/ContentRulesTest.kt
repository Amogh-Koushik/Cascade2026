package com.pratibimb.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ContentRulesTest {
    // 26 Oct 2026, 14:00 local time.
    private val twoPm: Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 26, 14, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val hour = 3_600_000L

    private fun classify(text: String, title: String = "Rohan", hasPicture: Boolean = false) =
        ContentRules.classify(title, text, hasPicture, twoPm)

    @Test
    fun attachmentPlaceholdersAreMediaOnly() {
        listOf("📷 Photo", "Photo", "Video", "🎤 Voice message", "Sticker", "sent a photo", "Rohan sent you a GIF")
            .forEach { assertEquals(it, ContentKind.MEDIA, classify(it).kind) }
        assertEquals(ContentKind.MEDIA, classify("", hasPicture = true).kind)
    }

    @Test
    fun reelsPostsAndForwardsAreForwards() {
        listOf("Forwarded", "sent you a reel", "shared a post", "sent a reel by memes.daily", "Forwarded: lol", "sent you a Snap")
            .forEach { assertEquals(it, ContentKind.FORWARD, classify(it).kind) }
    }

    @Test
    fun anythingElseIsPlainText() {
        listOf("hey, free tonight?", "Photo of the board pls", "video call at 5?", "ok")
            .forEach { assertEquals(it, ContentKind.TEXT, classify(it).kind) }
    }

    @Test
    fun linksAreFoundWithOrWithoutAScheme() {
        assertTrue(classify("Register at https://forms.gle/abc").hasUrl)
        assertTrue(classify("Update KYC at bit.ly/kyc-upd").hasUrl)
        assertTrue(classify("see www.example.org").hasUrl)
        assertFalse(classify("meet at 5.30 near gate").hasUrl)
    }

    @Test
    fun questionMarksAreNoticed() {
        assertTrue(classify("are you coming?").hasQuestion)
        assertFalse(classify("coming").hasQuestion)
    }

    @Test
    fun clockDeadlinesResolveToTheNextMatchingTime() {
        val bySix = classify("Register by 6 pm")
        assertEquals("by 6 pm", bySix.deadlinePhrase)
        assertEquals(twoPm + 4 * hour, bySix.deadlineAtMillis)

        assertEquals(twoPm + 4 * hour + 30 * 60_000L, classify("portal closes at 18:30").deadlineAtMillis)
        // No am/pm: the next 5 o'clock after 2 pm is 5 pm.
        assertEquals(twoPm + 3 * hour, classify("submit before 5").deadlineAtMillis)
    }

    @Test
    fun relativeDeadlinesCountFromThePost() {
        assertEquals(twoPm + 30 * 60_000L, classify("form closes in 30 min").deadlineAtMillis)
        assertEquals(twoPm + 2 * hour, classify("reply within 2 hours").deadlineAtMillis)
    }

    @Test
    fun deadlineWordsWithoutATimeHaveNoInstant() {
        val lastDate = classify("Last date for registration is Friday")
        assertEquals("last date", lastDate.deadlinePhrase)
        assertNull(lastDate.deadlineAtMillis)
        assertNull(classify("see you later").deadlinePhrase)
        // "at 5" alone is too vague to be a deadline.
        assertNull(classify("meet at 5").deadlineAtMillis)
    }

    @Test
    fun mentionsAreEveryoneTagsOrYourNames() {
        assertTrue(ContentRules.mentionsYou("@everyone submit the form", emptyList()))
        assertTrue(ContentRules.mentionsYou("@Amogh can you check", listOf("amogh")))
        assertFalse(ContentRules.mentionsYou("@Rahul can you check", listOf("amogh")))
        assertFalse(ContentRules.mentionsYou("anyone has notes", emptyList()))
    }

    @Test
    fun whatsAppTagMarkupStillCountsAsAMention() {
        // WhatsApp wraps a tag in direction isolates, and puts "~" before names not in contacts.
        assertTrue(ContentRules.mentionsYou("Rohan: ⁨@Satvik⁩ send the file", listOf("Satvik")))
        assertTrue(ContentRules.mentionsYou("@~Satvik are you coming", listOf("satvik")))
        assertFalse(ContentRules.mentionsYou("Satvik said hi", listOf("Satvik")))
    }

    @Test
    fun whatsAppsMentionedYouTitleIsAMentionWithoutAnyNames() {
        // As logged on the phone: the title carries WhatsApp's own label for a tag.
        val posted = "Cascade 2026 (3 messages): 📣 Mentioned you\nSingle Meerut Css: @Satvik Rastogi h"
        assertTrue(ContentRules.mentionsYou(posted, emptyList()))
        assertTrue(ContentRules.mentionsYou("Single Meerut Css: @Satvik Rastogi h", listOf("Satvik")))
    }

    @Test
    fun groupTitleDropsWhatWhatsAppAddsPerNotification() {
        assertEquals("Cascade 2026", ContentRules.groupTitle("Cascade 2026 (3 messages): 📣 Mentioned you"))
        assertEquals("Cascade 2026", ContentRules.groupTitle("Cascade 2026 (12 new messages)"))
        assertEquals("Cascade 2026", ContentRules.groupTitle("Cascade 2026"))
        assertEquals("Room 1 (A)", ContentRules.groupTitle("Room 1 (A)"))
    }

    @Test
    fun mentionNamesJoinEnteredAndAppNamesButNotSelfLabels() {
        assertEquals(
            setOf("Satvik", "Sat", "Satvik R"),
            ContentRules.mentionNames(listOf("Satvik", " Sat "), listOf("Satvik R", "You", null, "me", "")),
        )
    }
}
