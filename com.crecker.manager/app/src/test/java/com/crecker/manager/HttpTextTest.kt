package com.crecker.manager

import com.crecker.manager.core.net.HttpResult
import com.crecker.manager.core.net.PhpProblem
import com.crecker.manager.core.net.htmlToText
import com.crecker.manager.core.net.phpProblemIn
import com.crecker.manager.core.net.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpTextTest {
    @Test
    fun brAndBlocksBecomeLines() =
        assertEquals("Start\nUpdated 120 flights\nDone", htmlToText("Start<br>Updated <b>120</b> flights<br />\n<p>Done</p>"))

    @Test
    fun plainTextNewlinesKept() =
        assertEquals("line 1\nline 2\n\nline 4", htmlToText("line 1\r\nline 2  \n\n\n\nline 4\n"))

    @Test
    fun scriptsStylesAndCommentsDropped() =
        assertEquals("ok", htmlToText("<html><head><title>x</title><style>p{}</style></head><body><script>alert(1)</script><!-- c -->ok</body></html>"))

    @Test
    fun entitiesDecoded() =
        assertEquals("a < b & \"c\" ' é ✓ &lt;", htmlToText("a &lt; b &amp; &quot;c&quot; &#39; &#233; &#x2713; &amp;lt;"))

    @Test
    fun tableCellsSeparated() =
        assertEquals("a\tb", htmlToText("<table><tr><td>a</td><td>b</td></tr></table>").trimEnd('\t'))

    @Test
    fun phpFatal() = assertEquals(
        PhpProblem.FATAL,
        phpProblemIn(htmlToText("<br />\n<b>Fatal error</b>:  Uncaught Error in /home/x.php on line 3<br />")),
    )

    @Test
    fun phpWarning() = assertEquals(PhpProblem.WARNING, phpProblemIn("PHP Warning:  Undefined variable \$x"))

    @Test
    fun noProblem() = assertNull(phpProblemIn("Updated 120 flights. No errors."))

    @Test
    fun charsetFromContentType() {
        val bytes = "שלום".toByteArray(charset("windows-1255"))
        assertEquals("שלום", HttpResult(200, bytes, "text/html; charset=windows-1255").text())
        assertEquals("ok", HttpResult(200, (Char(0xFEFF) + "ok").toByteArray(), null).text())
    }
}
