package com.example.skydiveapp

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class JumpStatisticsTest {
    private fun jump(id: String, date: String, location: String, seconds: Int? = null) = Jump(id, date, location, "", "", "", seconds)

    @Test fun countsValidDatesAndCombinedLocations() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 9, 12, 0, 0) }
        val result = calculateJumpStatistics(listOf(
            jump("1", "01.11.2026", "Якутск", 45),
            jump("2", "02.10.2026", " якутск ", 60),
            jump("3", "31.12.2025", "Другое"),
            jump("4", "40.11.2026", "Другое", 0)
        ), now)
        assertEquals(4, result.total)
        assertEquals(1, result.thisMonth)
        assertEquals(2, result.thisYear)
        assertEquals(105L, result.totalFreefallSeconds)
        assertEquals("Якутск", result.favoriteLocation)
    }

    @Test fun unknownTimeIsDifferentFromZero() {
        assertNull(calculateJumpStatistics(listOf(jump("1", "01.01.2026", "A"))).totalFreefallSeconds)
        assertEquals(0L, calculateJumpStatistics(listOf(jump("2", "01.01.2026", "A", 0))).totalFreefallSeconds)
        assertEquals("Нет данных", formatFreefall(null))
        assertEquals("1 ч 1 мин 1 с", formatFreefall(3661))
    }

    @Test fun rejectsInvalidAndIncompleteDates() {
        assertNotNull(parseJumpDate("29.02.2024"))
        assertNull(parseJumpDate("29.02.2025"))
        assertNull(parseJumpDate("1.02.2025"))
        assertNull(parseJumpDate("01.02.2025xyz"))
    }
}
