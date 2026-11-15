package com.example.skydiveapp

import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementsTest {
    @Test fun progressUsesJumpCountDistinctPlacesAndKnownFreefall() {
        val jumps = listOf(
            Jump("1", "01.01.2026", " Якутск ", "", "", "", 120),
            Jump("2", "02.01.2026", "якутск", "", "", "", 180),
            Jump("3", "03.01.2026", "Москва", "", "", "", null),
            Jump("4", "04.01.2026", " ", "", "", "", -3)
        )
        val items = achievementsFor(jumps).associateBy { it.key }
        assertEquals(4L, items.getValue("jumps_10").current)
        assertEquals(2L, items.getValue("places_3").current)
        assertEquals(300L, items.getValue("freefall_300").current)
    }

    @Test fun emptyJournalStartsAtZero() {
        val items = achievementsFor(emptyList())
        assertEquals(6, items.size)
        assertEquals(true, items.all { it.current == 0L })
    }
}
