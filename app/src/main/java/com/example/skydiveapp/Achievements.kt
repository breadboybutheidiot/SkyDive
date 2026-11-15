package com.example.skydiveapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

internal data class Achievement(val key: String, val title: String, val goal: Long, val current: Long, val unit: String)

internal fun achievementsFor(jumps: List<Jump>): List<Achievement> {
    val count = jumps.size.toLong()
    val places = jumps.map { it.location.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }.distinct().size.toLong()
    val seconds = jumps.mapNotNull { it.freefallSeconds?.takeIf { seconds -> seconds >= 0 } }.sumOf { it.toLong() }
    return listOf(
        Achievement("first_jump", "Первый прыжок", 1, count, "прыжков"),
        Achievement("jumps_10", "Уже 10 прыжков", 10, count, "прыжков"),
        Achievement("jumps_25", "25 прыжков", 25, count, "прыжков"),
        Achievement("jumps_50", "50 прыжков", 50, count, "прыжков"),
        Achievement("places_3", "3 разных места", 3, places, "мест"),
        Achievement("freefall_300", "5 минут падения", 300, seconds, "секунд")
    )
}

@Composable
internal fun AchievementsSection(jumps: List<Jump>, statsAvailable: Boolean, earned: Set<String>?, error: String?, onRetry: () -> Unit) {
    val achievements = achievementsFor(jumps)
    Column {
        Text("Достижения", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        when {
            error != null -> {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = onRetry) { Text("Попробовать снова") }
            }
            earned == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> {
                Text("Получено ${achievements.count { it.key in earned }} из ${achievements.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                achievements.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { item ->
                            val unlocked = item.key in earned
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                                Text(if (unlocked) "★" else "☆", color = MaterialTheme.colorScheme.primary, fontSize = 21.sp)
                                Text(item.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium, minLines = 2)
                                val progress = if (unlocked) "Получено" else if (!statsAvailable) "Нет данных" else "${item.current.coerceAtMost(item.goal)} / ${item.goal} ${item.unit}"
                                Text(progress, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { if (unlocked) 1f else if (statsAvailable) (item.current.toFloat() / item.goal).coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
