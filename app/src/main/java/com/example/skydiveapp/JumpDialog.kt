package com.example.skydiveapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
internal fun JumpDialog(initial: Jump?, draft: JumpDraft, onDismiss: () -> Unit, saving: Boolean, error: String?, onSave: (Jump) -> Unit) {
    var date by draft::date
    var location by draft::location
    var altitude by draft::altitude
    var discipline by draft::discipline
    var notes by draft::notes
    var freefall by draft::freefall
    var attempted by draft::attempted
    var info by remember { mutableStateOf<InfoContent?>(null) }
    val validDate = parseJumpDate(date) != null
    val validAltitude = altitude.isBlank() || altitude.trim().replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
    val validFreefall = freefall.isBlank() || freefall.toIntOrNull()?.let { it in 0..86400 } == true
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (initial == null) "Новый прыжок" else "Изменить прыжок", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("Дата: дд.мм.гггг") }, trailingIcon = { InfoButton("Дата") { info = InfoContent("Дата", "Когда был прыжок. Например, 15.11.2026") } }, singleLine = true, isError = attempted && !validDate)
                OutlinedTextField(location, { location = it }, label = { Text("Место / дропзона") }, trailingIcon = { InfoButton("Место") { info = InfoContent("Место / дропзона", "Напиши название места, где прыгал. Это поле нужно заполнить") } }, singleLine = true, isError = attempted && location.isBlank())
                OutlinedTextField(altitude, { altitude = it }, label = { Text("Высота выхода, м") }, trailingIcon = { InfoButton("Высота выхода") { info = InfoContent("Высота выхода", "Высота в метрах, на которой ты выпрыгнул из самолёта. Можно оставить пустым") } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = attempted && !validAltitude)
                OutlinedTextField(freefall, { freefall = it }, label = { Text("Свободное падение, с") }, trailingIcon = { InfoButton("Свободное падение") { info = InfoContent("Свободное падение", "Сколько секунд длилось свободное падение до раскрытия парашюта. Можно оставить пустым") } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = attempted && !validFreefall, supportingText = { Text("Необязательно, от 0 до 86400 секунд") })
                OutlinedTextField(discipline, { discipline = it }, label = { Text("Дисциплина") }, trailingIcon = { InfoButton("Дисциплина") { info = InfoContent("Дисциплина", "Например, тандем или групповая акробатика. Если не хочешь указывать, оставь пустым") } }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Заметки") }, trailingIcon = { InfoButton("Заметки") { info = InfoContent("Заметки", "Можешь записать сюда свои впечатления или что-нибудь о прыжке") } }, minLines = 2)
                if (attempted && (!validDate || location.isBlank() || !validAltitude || !validFreefall)) Text("Проверь дату, место, высоту и время падения", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            attempted = true
            if (validDate && location.isNotBlank() && validAltitude && validFreefall) {
                onSave(Jump(draft.id, date.trim(), location.trim(), altitude.trim(), discipline.trim(), notes.trim(), freefall.takeIf { it.isNotBlank() }?.toInt()))
            }
        }) { LoadingLabel(saving, "Сохранить", "Сохраняем...") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") } }
    )
    InfoDialog(info) { info = null }
}
