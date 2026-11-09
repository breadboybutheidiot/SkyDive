package com.example.skydiveapp

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class JumpStatisticsEdgeCasesTest {
    private fun entry(id: String, date: String = "01.01.2026", location: String = "", seconds: Int? = null) =
        Jump(id, date, location, "", "", "", seconds)
    private fun date(year: Int, month: Int, day: Int) = Calendar.getInstance().apply { clear(); set(year, month - 1, day, 12, 0) }

    @Test fun emptyJournalHasNoFavoriteOrTime() {
        val result = calculateJumpStatistics(emptyList(), date(2026, 11, 9))
        assertEquals(0, result.total)
        assertEquals(0, result.thisMonth)
        assertEquals(0, result.thisYear)
        assertNull(result.favoriteLocation)
        assertNull(result.totalFreefallSeconds)
    }

    @Test fun countsOnlyMatchingCalendarMonthAndYear() {
        val items = listOf(entry("a", "31.12.2025"), entry("b", "01.01.2026"), entry("c", "31.01.2026"), entry("d", "01.02.2026"))
        val result = calculateJumpStatistics(items, date(2026, 1, 15))
        assertEquals(2, result.thisMonth)
        assertEquals(3, result.thisYear)
    }

    @Test fun groupsLocationsIgnoringCaseAndOuterSpaces() {
        val result = calculateJumpStatistics(listOf(entry("1", location = " Якутск "), entry("2", location = "яКутск"), entry("3", location = "Другая")))
        assertEquals("Якутск", result.favoriteLocation)
    }

    @Test fun ignoresMalformedDatesWithoutLosingTotal() {
        val items = listOf(entry("1", "29.02.2024"), entry("2", "29.02.2025"), entry("3", ""), entry("4", "01.01.2026 extra"))
        val result = calculateJumpStatistics(items, date(2024, 2, 29))
        assertEquals(4, result.total)
        assertEquals(1, result.thisMonth)
        assertEquals(1, result.thisYear)
    }

    @Test fun handlesUnknownZeroAndLargeDurationsSeparately() {
        assertNull(calculateJumpStatistics(listOf(entry("1"))).totalFreefallSeconds)
        assertEquals(0L, calculateJumpStatistics(listOf(entry("2", seconds = 0))).totalFreefallSeconds)
        val sum = calculateJumpStatistics(listOf(entry("3", seconds = 2_000_000_000), entry("4", seconds = 2_000_000_000))).totalFreefallSeconds
        assertEquals(4_000_000_000L, sum)
        assertEquals("0 с", formatFreefall(0))
        assertEquals("1 мин 1 с", formatFreefall(61))
    }

    @Test fun acceptsWhitespaceAroundAValidDateButRejectsImpossibleDates() {
        assertNotNull(parseJumpDate("  29.02.2024  "))
        assertNull(parseJumpDate("31.04.2026"))
        assertNull(parseJumpDate("00.01.2026"))
        assertNull(parseJumpDate("01.13.2026"))
    }
}
