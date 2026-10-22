package com.crecker.manager

import com.crecker.manager.systems.paisresults.PAIS_FILES
import com.crecker.manager.systems.paisresults.validate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaisFilesTest {
    private val nextRaffle = PAIS_FILES.first { it.name == "nextraffle-chance.xml" }
    private val rss = PAIS_FILES.first { it.name == "rssfeed-lotto.xml" }
    private val manui = PAIS_FILES.first { it.name == "rssfeed-manui.xml" }

    @Test
    fun tenUniqueFiles() {
        assertEquals(10, PAIS_FILES.size)
        assertEquals(10, PAIS_FILES.map { it.name }.toSet().size)
    }

    @Test
    fun nextRaffleJsonIsValid() {
        val body = """[{"nextLottoryDate":"Oct 22, 2026 09:00:00","displayDate":"22/10/2026","displayTime":"09:00"}]"""
        assertNull(nextRaffle.validate(200, body.toByteArray()))
    }

    @Test
    fun rssWithItemsIsValid() {
        val body = "<?xml version=\"1.0\"?><rss><channel><item>x</item></channel></rss>".padEnd(2000)
        assertNull(rss.validate(200, body.toByteArray()))
    }

    @Test
    fun manuiHtmlIsValid() {
        val body = "<div class=\"lot_results_item\">1</div>".padEnd(2000)
        assertNull(manui.validate(200, body.toByteArray()))
    }

    @Test
    fun rejectsHttpError() = assertNotNull(rss.validate(500, ByteArray(5000)))

    @Test
    fun rejectsSmallFile() = assertNotNull(rss.validate(200, "<item>".toByteArray()))

    @Test
    fun rejectsErrorPage() {
        val body = "<html><body>Access denied</body></html>".padEnd(5000)
        assertNotNull(rss.validate(200, body.toByteArray()))
    }
}
