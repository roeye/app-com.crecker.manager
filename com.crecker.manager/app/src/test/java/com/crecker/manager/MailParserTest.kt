package com.crecker.manager

import com.crecker.manager.systems.mail.MailParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage

class MailParserTest {
    private fun parse(raw: String) =
        MailParser.parse(MimeMessage(Session.getInstance(Properties()), raw.replace("\n", "\r\n").byteInputStream()))

    // נושא בעברית ב-base64, טקסט + HTML, קובץ מצורף ותמונה מוטמעת (cid)
    private val full = """
        From: =?UTF-8?B?16rXnteZ16jXlA==?= <support@example.com>
        To: info@example.com
        Cc: a@example.com, b@example.com
        Subject: =?UTF-8?B?16nXnNeV150=?=
        Date: Mon, 26 Oct 2026 10:00:00 +0000
        MIME-Version: 1.0
        Content-Type: multipart/mixed; boundary="mixed"

        --mixed
        Content-Type: multipart/related; boundary="related"

        --related
        Content-Type: multipart/alternative; boundary="alt"

        --alt
        Content-Type: text/plain; charset=utf-8

        Plain body
        --alt
        Content-Type: text/html; charset=utf-8

        <p>Hi <img src="cid:logo@x"></p>
        --alt--
        --related
        Content-Type: image/png
        Content-ID: <logo@x>
        Content-Disposition: inline
        Content-Transfer-Encoding: base64

        iVBORw0KGgo=
        --related--
        --mixed
        Content-Type: application/pdf; name="invoice.pdf"
        Content-Disposition: attachment; filename="invoice.pdf"
        Content-Transfer-Encoding: base64

        JVBERi0xLjQK
        --mixed--
    """.trimIndent()

    @Test
    fun parsesHeaders() {
        val content = parse(full)
        assertEquals("שלום", content.subject)
        assertEquals("תמירה <support@example.com>", content.from)
        assertEquals(listOf("info@example.com"), content.to)
        assertEquals(listOf("a@example.com", "b@example.com"), content.cc)
        assertEquals(1_793_008_800_000L, content.date)
    }

    @Test
    fun parsesBodies() {
        val content = parse(full)
        assertEquals("Plain body", content.text?.trim())
        assertTrue(content.html!!.contains("<p>Hi"))
    }

    @Test
    fun inlineImageBecomesDataUri() {
        val html = parse(full).html!!
        assertFalse(html.contains("cid:"))
        assertTrue(html.contains("src=\"data:image/png;base64,iVBORw0KGgo=\""))
    }

    @Test
    fun attachmentListedWithPath() {
        val attachments = parse(full).attachments
        assertEquals(1, attachments.size)
        assertEquals("invoice.pdf", attachments[0].name)
        assertEquals("application/pdf", attachments[0].mimeType)
        assertEquals(listOf(1), attachments[0].path)
    }

    @Test
    fun plainTextOnly() {
        val content = parse(
            """
            From: a@example.com
            Subject: Hello
            Content-Type: text/plain; charset=utf-8

            Just text
            """.trimIndent(),
        )
        assertEquals("Just text", content.text?.trim())
        assertNull(content.html)
        assertTrue(content.attachments.isEmpty())
    }

    @Test
    fun detectsRemoteContent() {
        assertTrue(MailParser.hasRemoteContent("""<img src="https://track.example.com/p.gif">"""))
        assertTrue(MailParser.hasRemoteContent("""<img SRC='//cdn.example.com/a.png'>"""))
        assertTrue(MailParser.hasRemoteContent("""<td background="http://x.com/bg.jpg">"""))
        assertTrue(MailParser.hasRemoteContent("""<div style="background:url('https://x.com/a.png')">"""))
        assertTrue(MailParser.hasRemoteContent("""<link rel="stylesheet" href="https://x.com/a.css">"""))
        assertTrue(MailParser.hasRemoteContent("""<style>@import "https://x.com/a.css";</style>"""))
    }

    @Test
    fun ignoresLinksAndLocalImages() {
        assertFalse(MailParser.hasRemoteContent("""<a href="https://example.com">site</a>"""))
        assertFalse(MailParser.hasRemoteContent("""<img src="data:image/png;base64,AAAA">"""))
        assertFalse(MailParser.hasRemoteContent("""<p>Visit https://example.com</p>"""))
    }

    @Test
    fun baseType() {
        assertEquals("text/html", MailParser.baseType("TEXT/HTML; charset=utf-8"))
        assertEquals("application/octet-stream", MailParser.baseType(null))
    }
}
