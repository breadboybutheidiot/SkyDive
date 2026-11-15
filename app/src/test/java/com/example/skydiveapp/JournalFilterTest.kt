package com.example.skydiveapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class JournalFilterTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 15) }
    private val jumps = listOf(
        Jump("1", "01.11.2026", "Якутск", "3000", "Тандем", "Первый прыжок", isFavorite = true),
        Jump("2", "20.10.2026", "Москва", "4000", "ТАНДЕМ", "Облачно"),
        Jump("3", "15.11.2025", "Якутск", "", "FS", "", isFavorite = true),
        Jump("4", "не дата", "Тест", "", "", "")
    )

    @Test fun searchMatchesNotesAndIgnoresCase() {
        assertEquals(listOf("1"), filterJumps(jumps, "пЕРВЫЙ", false, null, JournalPeriod.ALL, now).map { it.id })
    }

    @Test fun favoritesDisciplineAndPeriodCombine() {
        assertEquals(listOf("1"), filterJumps(jumps, "", true, "тандем", JournalPeriod.MONTH, now).map { it.id })
        assertEquals(listOf("1", "2"), filterJumps(jumps, "", false, "тандем", JournalPeriod.YEAR, now).map { it.id })
    }

    @Test fun invalidDatesOnlyAppearWhenPeriodIsUnrestricted() {
        assertEquals(4, filterJumps(jumps, "", false, null, JournalPeriod.ALL, now).size)
        assertEquals(listOf("1"), filterJumps(jumps, "", false, null, JournalPeriod.MONTH, now).map { it.id })
    }
}
