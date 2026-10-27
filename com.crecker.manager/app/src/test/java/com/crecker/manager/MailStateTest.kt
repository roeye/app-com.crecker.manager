package com.crecker.manager

import com.crecker.manager.systems.mail.FolderSnapshot
import com.crecker.manager.systems.mail.FolderState
import com.crecker.manager.systems.mail.MailHeader
import com.crecker.manager.systems.mail.MessageRef
import com.crecker.manager.systems.mail.formatShortDate
import com.crecker.manager.systems.mail.merge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MailStateTest {
    private fun header(uid: Long) = MailHeader(uid, "a", "a@example.com", "s$uid", uid * 1000, seen = false, hasAttachments = false)
    private fun snapshot(vararg uids: Long, validity: Long = 1) =
        FolderSnapshot(validity, uids.size, uids.size, uids.sortedDescending().map(::header))

    @Test
    fun firstSyncIsBaselineNothingNew() {
        val state = (null as FolderState?).merge(snapshot(1, 2, 3))
        assertTrue(state.newMessages.isEmpty())
        assertTrue(state.unnotified.isEmpty())
    }

    @Test
    fun messagesAfterBaselineAreNewAndUnnotified() {
        val state = (null as FolderState?).merge(snapshot(1, 2)).merge(snapshot(1, 2, 3, 4))
        assertEquals(listOf(4L, 3L), state.newMessages.map { it.uid })
        assertEquals(listOf(4L, 3L), state.unnotified.map { it.uid })
    }

    @Test
    fun notifiedOnlyOnce() {
        val notified = (null as FolderState?).merge(snapshot(1)).merge(snapshot(1, 2)).markNotified()
        assertTrue(notified.unnotified.isEmpty())
        // עדיין "חדש" במסך עד שנכנסים לתיבה
        assertEquals(listOf(2L), notified.newMessages.map { it.uid })
        assertEquals(listOf(3L), notified.merge(snapshot(1, 2, 3)).unnotified.map { it.uid })
    }

    @Test
    fun viewingClearsNewAndPendingNotifications() {
        val viewed = (null as FolderState?).merge(snapshot(1)).merge(snapshot(1, 2)).markViewed()
        assertTrue(viewed.newMessages.isEmpty())
        assertTrue(viewed.unnotified.isEmpty())
    }

    @Test
    fun uidValidityChangeResetsBaseline() {
        val state = (null as FolderState?).merge(snapshot(1)).merge(snapshot(5, 6, validity = 2))
        assertTrue(state.newMessages.isEmpty())
        assertTrue(state.unnotified.isEmpty())
    }

    @Test
    fun firstMessageInEmptyFolderIsNew() {
        val state = (null as FolderState?).merge(snapshot()).merge(snapshot(1))
        assertEquals(listOf(1L), state.newMessages.map { it.uid })
    }

    @Test
    fun messageRefRoundTrip() {
        val ref = MessageRef("abc", "INBOX.Sub|folder", 7, 42)
        assertEquals(ref, MessageRef.decode(ref.encode()))
        assertNull(MessageRef.decode("broken"))
    }

    @Test
    fun shortDate() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val now = 1_793_008_800_000L // 26/10/2026 12:00 (שעון ישראל)
        assertEquals("11:00", formatShortDate(now - 3_600_000, now, zone))
        assertEquals("01/02", formatShortDate(1_769_940_000_000L, now, zone))
        assertEquals("31/12/25", formatShortDate(1_767_175_200_000L, now, zone))
    }
}
