package com.example.skydiveapp

import java.util.Calendar

internal enum class JournalPeriod { ALL, MONTH, YEAR }

internal fun filterJumps(
    jumps: List<Jump>,
    query: String,
    favoritesOnly: Boolean,
    discipline: String?,
    period: JournalPeriod,
    now: Calendar = Calendar.getInstance()
): List<Jump> {
    val search = query.trim()
    return jumps.filter { jump ->
        val matchesSearch = search.isEmpty() || listOf(jump.date, jump.location, jump.altitude, jump.discipline, jump.notes)
            .any { it.contains(search, ignoreCase = true) }
        val matchesDiscipline = discipline == null || jump.discipline.trim().equals(discipline, ignoreCase = true)
        val date = if (period == JournalPeriod.ALL) null else parseJumpDate(jump.date)
        val matchesPeriod = when (period) {
            JournalPeriod.ALL -> true
            JournalPeriod.MONTH -> date != null && date.get(Calendar.YEAR) == now.get(Calendar.YEAR) && date.get(Calendar.MONTH) == now.get(Calendar.MONTH)
            JournalPeriod.YEAR -> date != null && date.get(Calendar.YEAR) == now.get(Calendar.YEAR)
        }
        matchesSearch && (!favoritesOnly || jump.isFavorite) && matchesDiscipline && matchesPeriod
    }
}
