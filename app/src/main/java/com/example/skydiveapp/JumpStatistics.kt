package com.example.skydiveapp

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal data class JumpStatistics(
    val total: Int,
    val thisMonth: Int,
    val thisYear: Int,
    val totalFreefallSeconds: Long?,
    val favoriteLocation: String?
)

internal fun parseJumpDate(value: String): Calendar? {
    val input = value.trim()
    if (!input.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) return null
    val position = ParsePosition(0)
    val date = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).apply { isLenient = false }.parse(input, position) ?: return null
    if (position.index != input.length) return null
    return Calendar.getInstance().apply { time = date }
}

internal fun calculateJumpStatistics(jumps: List<Jump>, now: Calendar = Calendar.getInstance()): JumpStatistics {
    var month = 0
    var year = 0
    val locations = linkedMapOf<String, Pair<String, Int>>()
    var seconds: Long? = null
    jumps.forEach { jump ->
        parseJumpDate(jump.date)?.let { date ->
            if (date.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                year++
                if (date.get(Calendar.MONTH) == now.get(Calendar.MONTH)) month++
            }
        }
        jump.freefallSeconds?.takeIf { it >= 0 }?.let { seconds = (seconds ?: 0L) + it }
        val location = jump.location.trim()
        if (location.isNotEmpty()) {
            val key = location.lowercase(Locale.ROOT)
            val old = locations[key]
            locations[key] = (old?.first ?: location) to ((old?.second ?: 0) + 1)
        }
    }
    return JumpStatistics(jumps.size, month, year, seconds, locations.values.maxByOrNull { it.second }?.first)
}

internal fun formatFreefall(seconds: Long?): String {
    if (seconds == null) return "Нет данных"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "$hours ч $minutes мин $remaining с" else if (minutes > 0) "$minutes мин $remaining с" else "$remaining с"
}
