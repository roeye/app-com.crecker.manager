package com.crecker.manager

import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.elapsedParts
import com.crecker.manager.core.remote.RemoteFileInfo
import com.crecker.manager.systems.paisresults.Freshness
import com.crecker.manager.systems.paisresults.PaisResultsSystem
import com.crecker.manager.core.model.formatDateTime
import com.crecker.manager.systems.paisresults.freshnessOf
import com.crecker.manager.systems.paisresults.overallStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CheckLastUpdateTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val day = Duration.ofHours(24)

    private fun info(ago: Duration) = RemoteFileInfo(now.minus(ago), 100)

    @Test
    fun freshWithinThreshold() = assertEquals(Freshness.FRESH, freshnessOf(info(Duration.ofHours(23)), now, day))

    @Test
    fun exactlyThresholdIsFresh() = assertEquals(Freshness.FRESH, freshnessOf(info(day), now, day))

    @Test
    fun staleAfterThreshold() = assertEquals(Freshness.STALE, freshnessOf(info(day.plusMinutes(1)), now, day))

    @Test
    fun missingFile() = assertEquals(Freshness.MISSING, freshnessOf(null, now, day))

    @Test
    fun futureTimestampIsFresh() = assertEquals(Freshness.FRESH, freshnessOf(info(Duration.ofMinutes(-5)), now, day))

    @Test
    fun overall() {
        assertEquals(RunStatus.SUCCESS, overallStatus(listOf(Freshness.FRESH, Freshness.FRESH)))
        assertEquals(RunStatus.PARTIAL, overallStatus(listOf(Freshness.FRESH, Freshness.STALE)))
        assertEquals(RunStatus.FAILED, overallStatus(listOf(Freshness.STALE, Freshness.MISSING)))
        assertEquals(RunStatus.FAILED, overallStatus(listOf(Freshness.FRESH, Freshness.ERROR)))
    }

    @Test
    fun elapsedTwoLargestUnits() {
        assertEquals(
            listOf(R.string.time_days to 2L, R.string.time_hours to 3L),
            elapsedParts(Duration.ofDays(2).plusHours(3).plusMinutes(15)),
        )
        assertEquals(
            listOf(R.string.time_hours to 2L, R.string.time_minutes to 15L),
            elapsedParts(Duration.ofHours(2).plusMinutes(15).plusSeconds(40)),
        )
        assertEquals(listOf(R.string.time_minutes to 5L), elapsedParts(Duration.ofMinutes(5)))
    }

    @Test
    fun elapsedSkipsZeroSecondUnit() =
        assertEquals(listOf(R.string.time_days to 1L), elapsedParts(Duration.ofDays(1).plusMinutes(30)))

    @Test
    fun elapsedUnderMinuteIsEmpty() = assertEquals(emptyList<Pair<Int, Long>>(), elapsedParts(Duration.ofSeconds(59)))

    @Test
    fun elapsedIgnoresSign() = assertEquals(listOf(R.string.time_minutes to 5L), elapsedParts(Duration.ofMinutes(-5)))

    @Test
    fun israelTimeIncludesDaylightSaving() {
        // קיץ: UTC+3, חורף: UTC+2
        assertEquals("01/07/2026 15:00:00", formatDateTime(Instant.parse("2026-07-01T12:00:00Z"), PaisResultsSystem.ISRAEL_ZONE))
        assertEquals("01/12/2026 14:00:00", formatDateTime(Instant.parse("2026-12-01T12:00:00Z"), PaisResultsSystem.ISRAEL_ZONE))
    }
}
