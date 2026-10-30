package com.example.skydiveapp

import android.os.Bundle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF1C1B20)
private val Muted = Color(0xFF56545F)
private val Teal = Color(0xFF454A65)
private val Paper = Color(0xFFF9F7FF)
private val Line = Color(0xFFD3D3DE)
private val Orange = Color(0xFF777D9B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Teal, background = Paper, surface = Color(0xFFE2E2EC), onSurface = Ink)) { SkyDiveApp() } }
    }
}

private val checks = listOf("Основной парашют", "Запасной парашют", "Прибор безопасности (AAD)", "Высотомер", "Шлем и очки", "Комбинезон и обувь", "Проверка снаряжения с напарником")

@Composable
private fun SkyDiveApp() {
    val context = LocalContext.current
    val auth = remember { SupabaseAuth(context) }
    var session by remember { mutableStateOf<AuthSession?>(null) }
    var passwordCopyError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (session == null) {
        AuthScreen(auth) { result, warning -> session = result; passwordCopyError = warning }
    } else {
        MainContent(session!!.email, auth) {
            val current = session!!
            scope.launch { withContext(Dispatchers.IO) { try { auth.signOut(current) } catch (_: Exception) { } }; session = null }
        }
    }
    if (passwordCopyError != null) AlertDialog(onDismissRequest = { passwordCopyError = null }, title = { Text("Не получилось к сожаланию") }, text = { Text(passwordCopyError!!) }, confirmButton = { TextButton(onClick = { passwordCopyError = null }) { Text("Понятно") } })
}

@Composable
private fun MainContent(email: String, auth: SupabaseAuth, onSignOut: () -> Unit) {
    val store = remember { JumpStore(auth) }
    val jumps = remember { mutableStateListOf<Jump>() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val checked = remember { mutableStateListOf<Boolean>().apply { checks.indices.forEach { add(false) } } }
    LaunchedEffect(reload) {
        loading = true
        try { val data = withContext(Dispatchers.IO) { store.list() to store.loadChecks(checks.size) }; jumps.clear(); jumps.addAll(data.first); data.second.forEachIndexed { i, value -> checked[i] = value }; cloudError = null }
        catch (e: Exception) { cloudError = if (e is AuthException) e.message else "шо то не грузит, интернет ты свой проверь, а то оно не функционирует" }
        finally { loading = false }
    }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteJump by remember { mutableStateOf<Jump?>(null) }
    fun saveChecklist(values: List<Boolean>) {
        if (saving || loading) return
        saving = true
        scope.launch {
            try { withContext(Dispatchers.IO) { store.saveChecks(values) }; values.forEachIndexed { i, value -> checked[i] = value }; cloudError = null }
            catch (e: Exception) { cloudError = if (e is AuthException) e.message else "Чеклист не сохраняется, вайфай сеть всемирная паутина не работает" }
            finally { saving = false }
        }
    }
    Scaffold(containerColor = Paper, bottomBar = {
        Surface(color = Color(0xFFF0EFF6), shadowElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("⌂" to "Главная", "▤" to "Журнал", "✓" to "Чек-лист", "◇" to "Инструменты").forEachIndexed { index, item ->
                    Column(Modifier.weight(1f).clickable { tab = index }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(8.dp)).background(if (tab == index) Color(0xFFDCE2FA) else Color.Transparent))
                        Text(item.second, color = if (tab == index) Teal else Muted, fontSize = 10.sp, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
    }) { padding ->
        when (tab) {
            0 -> HomeScreen(jumps, checked.count { it }, { showAdd = true }, { tab = it }, email, onSignOut, Modifier.padding(padding))
            1 -> JournalScreen(jumps, { showAdd = true }, { deleteJump = it }, Modifier.padding(padding))
            2 -> ChecklistScreen(checked, { i, value -> saveChecklist(checked.toList().mapIndexed { index, old -> if (index == i) value else old }) }, { saveChecklist(List(checks.size) { false }) }, Modifier.padding(padding))
            3 -> ToolsScreen(Modifier.padding(padding))
        }
    }
    if (cloudError != null && !showAdd && deleteJump == null) AlertDialog(onDismissRequest = { cloudError = null }, title = { Text("Не получилось") }, text = { Text(cloudError!!) }, confirmButton = { TextButton(onClick = { cloudError = null; reload++ }) { Text("Попробовать снова") } }, dismissButton = { TextButton(onClick = { cloudError = null }) { Text("Закрыть") } })
    if (loading) Box(Modifier.fillMaxSize().background(Paper.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    if (showAdd) JumpDialog(onDismiss = { if (!saving) showAdd = false }, saving = saving, error = cloudError, onSave = { date, location, altitude, discipline, notes ->
        if (!saving) { saving = true; cloudError = null; scope.launch {
            try { val jump = Jump(java.util.UUID.randomUUID().toString(), date, location, altitude, discipline, notes); val saved = withContext(Dispatchers.IO) { store.add(jump) }; jumps.add(0, saved); showAdd = false; tab = 1 }
            catch (e: Exception) { cloudError = if (e is AuthException) e.message else "прыжок не сохранен, интернет сеть свои проверь" }
            finally { saving = false }
        } }
    })
    deleteJump?.let { jump -> AlertDialog(onDismissRequest = { deleteJump = null }, title = { Text("Реально удалить прыжок?") }, text = { Text("Эта запись удалится из аккаунта вот это да") }, confirmButton = { TextButton(enabled = !saving, onClick = { saving = true; scope.launch { try { withContext(Dispatchers.IO) { store.delete(jump) }; jumps.remove(jump); deleteJump = null; cloudError = null } catch (e: Exception) { deleteJump = null; cloudError = if (e is AuthException) e.message else "Не получилось удалить прыжок нужно проверить паутину сетевую свою" } finally { saving = false } } }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleteJump = null }) { Text("Отмена") } }) }
}

@Composable
private fun HomeScreen(jumps: List<Jump>, done: Int, add: () -> Unit, navigate: (Int) -> Unit, email: String, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("BLUE SKY", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp); Spacer(Modifier.height(5.dp)); Text("Главная", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Normal) }
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFDFE4FA)), contentAlignment = Alignment.Center) { Text("✳", color = Teal, fontSize = 23.sp) }
        }
        Spacer(Modifier.height(20.dp))
        HeroCard(jumps.size, add)
        Spacer(Modifier.height(25.dp))
        SectionTitle("Статистика", "")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("ПРЫЖКОВ", jumps.size.toString(), "в журнале", Modifier.weight(1f))
            StatCard("ПРОВЕРЕНО", "$done / ${checks.size}", "пунктов из списка", Modifier.weight(1f))
        }
        Spacer(Modifier.height(25.dp))
        SectionTitle("Снаряжение и инструменты", "БЫСТРЫЙ ДОСТУП")
        Spacer(Modifier.height(12.dp))
        ActionCard("✓", "Чек лист снаряжения", "Список для проверки", "ОТКРЫТЬ", { navigate(2) })
        Spacer(Modifier.height(10.dp))
        ActionCard("↗", "Инструменты", "Быстро перевести единицы", "ОТКРЫТЬ", { navigate(3) })
        Spacer(Modifier.height(25.dp))
        SectionTitle("Крайний прыжок", "ЖУРНАЛ", { navigate(1) })
        Spacer(Modifier.height(12.dp))
        if (jumps.isEmpty()) EmptyCard("Прыжков пока нет", "Добавь запись о прыжке") else JumpCard(jumps.first(), null)
        Spacer(Modifier.height(24.dp))
        Text("Аккаунт: ${email.removeSuffix("@owfsmpsqbwsguejtnyng.supabase.co").removeSuffix("@users.skydive.example")}", color = Muted, fontSize = 12.sp)
        TextButton(onClick = onSignOut) { Text("Выйти из аккаунта") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HeroCard(count: Int, add: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC)).padding(16.dp)) {
        Text(if (count == 0) "Первый прыжок" else "Журнал прыжков", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text("Добавляй сюда свои прыжки", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = add, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDFE4FA), contentColor = Ink), shape = RoundedCornerShape(20.dp)) {
            Text("+  Добавить прыжок", fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

@Composable private fun SectionTitle(title: String, label: String, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Medium); Text(label + if (onClick != null) "  →" else "", modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier, color = if (onClick != null) Teal else Muted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun StatCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) { Column(modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC)).padding(16.dp)) { Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(10.dp)); Text(value, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(sub, color = Muted, fontSize = 11.sp) } }
@Composable private fun ActionCard(icon: String, title: String, sub: String, label: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC)).clickable { click() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(43.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFDFE4FA)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 21.sp, color = Teal, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(sub, color = Muted, fontSize = 11.sp) }; Text("→", color = Teal, fontSize = 21.sp) } }
@Composable private fun EmptyCard(title: String, sub: String) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("✦", color = Orange, fontSize = 22.sp); Spacer(Modifier.height(6.dp)); Text(title, color = Ink, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(sub, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center) } }
@Composable private fun PageHeader(kicker: String, title: String, subtitle: String) { Spacer(Modifier.height(24.dp)); Text(kicker, color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp); Spacer(Modifier.height(7.dp)); Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Normal); Spacer(Modifier.height(5.dp)); Text(subtitle, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(24.dp)) }

@Composable private fun JournalScreen(jumps: List<Jump>, add: () -> Unit, remove: (Jump) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageHeader("ПРЫЖКИ", "Журнал прыжков", "Твои записи"); Button(onClick = add, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("+  Добавить прыжок", fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(12.dp)); SectionTitle("Записи", "${jumps.size} ВСЕГО") }
        if (jumps.isEmpty()) item { EmptyCard("Прыжков пока нет", "Нажми кнопку чтобы добавить запись") }
        items(jumps, key = { it.id }) { JumpCard(it) { remove(it) } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
@Composable private fun JumpCard(jump: Jump, remove: (() -> Unit)?) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC)).padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFDFE4FA)), contentAlignment = Alignment.Center) { Text("↗", color = Teal, fontSize = 22.sp) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(jump.location, fontWeight = FontWeight.Bold, color = Ink); Text(jump.date, fontSize = 12.sp, color = Muted) }; if (remove != null) Text("Удалить", modifier = Modifier.clickable { remove() }.padding(6.dp), color = Muted, fontSize = 11.sp) }; Spacer(Modifier.height(12.dp)); Text(listOfNotNull(jump.discipline.takeIf { it.isNotBlank() }, jump.altitude.takeIf { it.isNotBlank() }?.let { "$it м" }).joinToString("  ·  ").ifBlank { "Прыжок" }, color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); if (jump.notes.isNotBlank()) { Spacer(Modifier.height(7.dp)); Text(jump.notes, color = Muted, fontSize = 12.sp) } } }

@Composable private fun ChecklistScreen(checked: List<Boolean>, update: (Int, Boolean) -> Unit, reset: () -> Unit, modifier: Modifier = Modifier) { Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) { PageHeader("СНАРЯЖЕНИЕ", "Чек-лист", "Отметь что уже проверил"); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC)).padding(18.dp)) { Text("УЖЕ ПРОВЕРЕНО", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(6.dp)); Text("${checked.count { it }} из ${checks.size} проверено", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { checked.count { it }.toFloat() / checks.size }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(10.dp)), color = Teal, trackColor = Color.White) }; Spacer(Modifier.height(22.dp)); SectionTitle("Снаряжение", "ПЕРЕД ВЫЛЕТОМ"); Spacer(Modifier.height(12.dp)); Column(Modifier.clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC))) { checks.forEachIndexed { i, item -> Row(Modifier.fillMaxWidth().clickable { update(i, !checked[i]) }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = checked[i], onCheckedChange = { update(i, it) }); Text(item, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp)) }; if (i < checks.lastIndex) HorizontalDivider(Modifier.padding(start = 62.dp), color = Line) } }; Spacer(Modifier.height(12.dp)); TextButton(onClick = reset, modifier = Modifier.align(Alignment.End)) { Text("Начать проверку заново") }; Text("Это просто памятка. Перед прыжком обязательно проверь снаряжение по правилам своей дропзоны и вместе с инструктором.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp); Spacer(Modifier.height(24.dp)) } }

@Composable private fun ToolsScreen(modifier: Modifier = Modifier) { var value by remember { mutableStateOf("") }; var selected by remember { mutableIntStateOf(0) }; var reverse by remember { mutableStateOf(false) }; val conversions = listOf(Triple("Высота", "м", "фт"), Triple("Скорость", "м/с", "км/ч"), Triple("Расстояние", "км", "мили")); val factors = listOf(3.280839895, 3.6, 0.621371192); val item = conversions[selected]; val number = value.replace(',', '.').toDoubleOrNull(); val result = number?.let { if (reverse) it / factors[selected] else it * factors[selected] }; Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) { PageHeader("РАСЧЁТЫ", "Инструменты", "Перевод единиц измерения"); SectionTitle("Конвертер", "ПЕРЕВОД ЕДИНИЦ"); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { conversions.forEachIndexed { i, conversion -> FilterChip(selected = selected == i, onClick = { selected = i; reverse = false }, label = { Text(conversion.first, fontSize = 12.sp) }, modifier = Modifier.weight(1f)) } }; Spacer(Modifier.height(12.dp)); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFFE2E2EC)).padding(18.dp)) { Text("ИЗ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(if (reverse) item.third else item.second) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)); TextButton(onClick = { reverse = !reverse }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("⇅  Поменять местами") }; Text("РЕЗУЛЬТАТ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); Text(if (result != null && result.isFinite()) "${String.format(Locale.getDefault(), "%.2f", result)} ${if (reverse) item.second else item.third}" else "Введи число (${if (reverse) item.second else item.third})", color = Teal, fontWeight = FontWeight.Bold, fontSize = 27.sp) }; Spacer(Modifier.height(18.dp)); Text("Для планирования прыжка сверяйся с приборами, погодой и правилами своей дропзоны.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) } }

@Composable private fun JumpDialog(onDismiss: () -> Unit, saving: Boolean, error: String?, onSave: (String, String, String, String, String) -> Unit) { var date by remember { mutableStateOf(SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())) }; var location by remember { mutableStateOf("") }; var altitude by remember { mutableStateOf("") }; var discipline by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var attempted by remember { mutableStateOf(false) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Новый прыжок", fontWeight = FontWeight.Bold) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(date, { date = it }, label = { Text("Дата") }, singleLine = true, isError = attempted && date.isBlank()); OutlinedTextField(location, { location = it }, label = { Text("Место / дропзона") }, singleLine = true, isError = attempted && location.isBlank()); OutlinedTextField(altitude, { altitude = it }, label = { Text("Высота выхода, м (необязательно)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, isError = attempted && altitude.isNotBlank() && (altitude.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it > 0 } != true)); OutlinedTextField(discipline, { discipline = it }, label = { Text("Дисциплина (необязательно)") }, singleLine = true); OutlinedTextField(notes, { notes = it }, label = { Text("Заметки (необязательно)") }, minLines = 2); if (attempted) Text("Укажи дату и место, высота должна быть больше нуля", color = MaterialTheme.colorScheme.error, fontSize = 11.sp); if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) } }, confirmButton = { TextButton(enabled = !saving, onClick = { attempted = true; if (date.isNotBlank() && location.isNotBlank() && (altitude.isBlank() || altitude.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true)) onSave(date.trim(), location.trim(), altitude.trim(), discipline.trim(), notes.trim()) }) { Text(if (saving) "Сохраняем..." else "Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }) }


@Composable
private fun AuthScreen(auth: SupabaseAuth, onSuccess: (AuthSession, String?) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Text("BLUE SKY", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text(if (register) "Регистрация" else "Вход", color = Ink, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(if (register) "Придумай логин и пароль" else "Введи логин и пароль", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(username, { username = it; error = null }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), supportingText = { Text("От 3 до 24 символов: латинские буквы, цифры и подчёркивание") }, shape = RoundedCornerShape(11.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it; error = null }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(11.dp))
        if (register) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(confirmPassword, { confirmPassword = it; error = null }, label = { Text("Повтори пароль") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(11.dp))
        }
        if (error != null) { Spacer(Modifier.height(10.dp)); Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            val login = username.trim().lowercase(Locale.ROOT)
            val address = "$login@owfsmpsqbwsguejtnyng.supabase.co"
            when {
                !login.matches(Regex("[a-z0-9_]{3,24}")) -> error = "В логине нужно от 3 до 24 латинских букв, цифр или подчёркиваний"
                password.isBlank() -> error = "Введи пароль"
                register && password.length < 6 -> error = "Пароль должен быть хотя бы из 6 символов"
                register && password != confirmPassword -> error = "Пароли не совпадают"
                else -> { busy = true; error = null; scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { if (register) auth.signUp(address, password) else auth.signIn(address, password) }
                        val warning = withContext(Dispatchers.IO) { try { auth.savePasswordCopy(result, password); null } catch (_: Exception) { "Пароль не сохранился в таблице. Проверь что обновлённый SQL выполнен и войди в аккаунт ещё раз" } }
                        password = ""; onSuccess(result, warning)
                    }
                    catch (e: AuthException) { error = e.message }
                    catch (_: Exception) { error = "Не получилось подключиться. Проверь интернет и попробуй ещё раз" }
                    finally { busy = false }
                } }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(11.dp)) { Text(if (busy) "Загрузка..." else if (register) "Зарегистрироваться" else "Войти") }
        TextButton(onClick = { register = !register; confirmPassword = ""; error = null }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (register) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться") }
    }
}
