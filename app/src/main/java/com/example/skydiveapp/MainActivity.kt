package com.example.skydiveapp

import android.os.Bundle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val Teal: Color @Composable get() = MaterialTheme.colorScheme.primary
private val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
private val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val Orange: Color @Composable get() = MaterialTheme.colorScheme.secondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[AppSessionViewModel::class.java]
        setContent {
            AppTheme(model.darkTheme) {
                val background = MaterialTheme.colorScheme.background
                val dark = model.darkTheme
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                    @Suppress("DEPRECATION")
                    window.statusBarColor = background.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = background.toArgb()
                }
                SkyDiveApp(model)
            }
        }
    }
}

private val checks = listOf("Основной парашют", "Запасной парашют", "Прибор безопасности (AAD)", "Высотомер", "Шлем и очки", "Комбинезон и обувь", "Проверка снаряжения с напарником")

@Composable
private fun SkyDiveApp(model: AppSessionViewModel) {
    val auth = model.auth
    var session by model::session
    var passwordCopyError by model::passwordCopyError
    val scope = model.scope
    if (model.restoringSession) {
        Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (session == null) {
        AuthScreen(auth, model.authForm, model.scope) { result, warning -> session = result; passwordCopyError = warning }
    } else {
        MainContent(session!!.email, auth, model) {
            val current = session!!
            scope.launch { withContext(Dispatchers.IO) { try { auth.signOut(current) } catch (_: Exception) { } }; model.resetAfterLogout() }
        }
    }
    if (passwordCopyError != null) AlertDialog(onDismissRequest = { passwordCopyError = null }, title = { Text("Не получилось к сожаланию") }, text = { Text(passwordCopyError!!) }, confirmButton = { TextButton(onClick = { passwordCopyError = null }) { Text("Понятно") } })
}

@Composable
private fun MainContent(email: String, auth: SupabaseAuth, model: AppSessionViewModel, onSignOut: () -> Unit) {
    val store = remember { JumpStore(auth) }
    val jumps = model.jumps
    val scope = model.scope
    var loading by remember { mutableStateOf(true) }
    var saving by model::saving
    var jumpsAvailable by model::jumpsAvailable
    var cloudError by model::cloudError
    var reload by model::reload
    LaunchedEffect(reload) {
        loading = true
        try { val data = withContext(Dispatchers.IO) { store.list() }; jumps.clear(); jumps.addAll(data); jumpsAvailable = true; cloudError = null }
        catch (e: Exception) {
            jumpsAvailable = false
            if (e is AuthException && e.invalidSession) {
                auth.clear(); model.resetAfterLogout(); model.authForm.error = e.message
            } else cloudError = if (e is AuthException) e.message else "шо то не грузит, интернет ты свой проверь, а то оно не функционирует"
        }
        finally { loading = false }
    }
    var tab by model::tab
    var showAdd by model::showAdd
    var editingJump by model::editingJump
    var deleteJump by model::deleteJump
    Scaffold(containerColor = Paper, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 0.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("⌂" to "Главная", "▤" to "Журнал", "◇" to "Инструменты", "✉" to "Чат", "○" to "Профиль").forEachIndexed { index, item ->
                    Column(Modifier.weight(1f).clickable { tab = index }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(8.dp)).background(if (tab == index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent))
                        Text(item.second, color = if (tab == index) Teal else Muted, fontSize = 10.sp, fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
    }) { padding ->
        when (tab) {
            0 -> HomeScreen(jumps, jumpsAvailable, { model.jumpDraft = JumpDraft(null); showAdd = true }, { tab = it }, Modifier.padding(padding))
            1 -> JournalScreen(jumps, model.favoritePending,
                add = { cloudError = null; editingJump = null; model.jumpDraft = JumpDraft(null); showAdd = true },
                edit = { cloudError = null; editingJump = it; model.jumpDraft = JumpDraft(it) },
                remove = { deleteJump = it },
                favorite = { jump ->
                    if (jump.id !in model.favoritePending) {
                        model.favoritePending = model.favoritePending + jump.id
                        scope.launch {
                            try {
                                val updated = withContext(Dispatchers.IO) { store.setFavorite(jump.id, !jump.isFavorite) }
                                val index = jumps.indexOfFirst { it.id == jump.id }
                                if (index >= 0) jumps[index] = jumps[index].copy(isFavorite = updated.isFavorite)
                            } catch (e: Exception) {
                                cloudError = if (e is AuthException) e.message else "Не получилось изменить избранное. Проверь интернет"
                            } finally { model.favoritePending = model.favoritePending - jump.id }
                        }
                    }
                }, modifier = Modifier.padding(padding))
            2 -> ToolsScreen(Modifier.padding(padding))
            3 -> ChatScreen(store, model, Modifier.padding(padding))
            4 -> ProfileScreen(store, jumps, jumpsAvailable, email.substringBefore('@'), model, onSignOut, Modifier.padding(padding))
        }
    }
    if (cloudError != null && !showAdd && editingJump == null && deleteJump == null) AlertDialog(onDismissRequest = { cloudError = null }, title = { Text("Не получилось") }, text = { Text(cloudError!!) }, confirmButton = { TextButton(onClick = { cloudError = null; reload++ }) { Text("Попробовать снова") } }, dismissButton = { TextButton(onClick = { cloudError = null }) { Text("Закрыть") } })
    if (loading) Box(Modifier.fillMaxSize().background(Paper.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    if ((showAdd || editingJump != null) && model.jumpDraft != null) JumpDialog(initial = editingJump, draft = model.jumpDraft!!, onDismiss = { if (!saving) { showAdd = false; editingJump = null; model.jumpDraft = null; cloudError = null } }, saving = saving, error = cloudError, onSave = { jump ->
        if (!saving) { saving = true; cloudError = null; scope.launch {
            try {
                val isEdit = editingJump != null
                val saved = withContext(Dispatchers.IO) { if (isEdit) store.update(jump) else store.add(jump) }
                if (isEdit) { val index = jumps.indexOfFirst { it.id == saved.id }; if (index >= 0) jumps[index] = saved }
                else jumps.add(0, saved)
                showAdd = false; editingJump = null; model.jumpDraft = null; tab = 1
            }
            catch (e: Exception) { cloudError = if (e is AuthException) e.message else "Не получилось сохранить прыжок. Проверь интернет" }
            finally { saving = false }
        } }
    })
    deleteJump?.let { jump -> AlertDialog(onDismissRequest = { if (!saving) deleteJump = null }, title = { Text("Реально удалить прыжок?") }, text = { Text("Эта запись удалится из аккаунта вот это да") }, confirmButton = { TextButton(enabled = !saving, onClick = { saving = true; scope.launch { try { withContext(Dispatchers.IO) { store.delete(jump) }; jumps.remove(jump); deleteJump = null; cloudError = null } catch (e: Exception) { deleteJump = null; cloudError = if (e is AuthException) e.message else "Не получилось удалить прыжок нужно проверить паутину сетевую свою" } finally { saving = false } } }) { LoadingLabel(saving, "Удалить", "Удаляем...") } }, dismissButton = { TextButton(enabled = !saving, onClick = { deleteJump = null }) { Text("Отмена") } }) }
}

@Composable
private fun HomeScreen(jumps: List<Jump>, statsAvailable: Boolean, add: () -> Unit, navigate: (Int) -> Unit, modifier: Modifier = Modifier) {
    var info by remember { mutableStateOf<InfoContent?>(null) }
    val places = jumps.map { it.location.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("SkyDiveApp", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp); Spacer(Modifier.height(5.dp)); Text("Главная", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Normal) }
            Image(painterResource(R.drawable.skydive_icon), contentDescription = null, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        HeroCard(jumps.size, add)
        Spacer(Modifier.height(25.dp))
        SectionTitle("Статистика", "")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("ПРЫЖКОВ", if (statsAvailable) jumps.size.toString() else "—", "в журнале", Modifier.weight(1f)) {
                info = InfoContent("Прыжки", if (statsAvailable) "Всего записей в журнале: ${jumps.size}" else "Не получилось загрузить журнал. Попробуй ещё раз")
            }
            StatCard("МЕСТ", if (statsAvailable) places.size.toString() else "—", "дропзон", Modifier.weight(1f)) {
                info = InfoContent("Места", if (!statsAvailable) "Не получилось загрузить журнал. Попробуй ещё раз" else if (places.isEmpty()) "Пока нет записей о местах прыжков" else "Мест: ${places.size}\n\n${places.take(10).joinToString("\n")}${if (places.size > 10) "\n..." else ""}")
            }
        }
        Spacer(Modifier.height(25.dp))
        SectionTitle("Разделы", "БЫСТРЫЙ ДОСТУП")
        Spacer(Modifier.height(12.dp))
        ActionCard("○", "Профиль", "Фото и личная статистика", "ОТКРЫТЬ", { navigate(4) })
        Spacer(Modifier.height(10.dp))
        ActionCard("↗", "Инструменты", "Быстро перевести единицы", "ОТКРЫТЬ", { navigate(2) })
        Spacer(Modifier.height(25.dp))
        SectionTitle("Крайний прыжок", "ЖУРНАЛ", { navigate(1) })
        Spacer(Modifier.height(12.dp))
        if (jumps.isEmpty()) EmptyCard("Прыжков пока нет", "Добавь запись о прыжке") else JumpCard(jumps.first(), null, null)
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(16.dp))
    }
    InfoDialog(info) { info = null }
}

@Composable
private fun HeroCard(count: Int, add: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(if (count == 0) "Первый прыжок" else "Журнал прыжков", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text("Добавляй сюда свои прыжки", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = add, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = Ink), shape = RoundedCornerShape(20.dp)) {
            Text("+  Добавить прыжок", fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

@Composable private fun SectionTitle(title: String, label: String, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Medium); Text(label + if (onClick != null) "  →" else "", modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier, color = if (onClick != null) Teal else Muted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold) } }
@Composable private fun StatCard(label: String, value: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) { Column(modifier.clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClickLabel = "Подробнее: $label", onClick = onClick).padding(16.dp)) { Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(10.dp)); Text(value, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text(sub, color = Muted, fontSize = 11.sp); Spacer(Modifier.height(6.dp)); Text("Подробнее", color = Teal, fontSize = 10.sp) } }
@Composable private fun ActionCard(icon: String, title: String, sub: String, label: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).clickable { click() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(43.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text(icon, fontSize = 21.sp, color = Teal, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(sub, color = Muted, fontSize = 11.sp) }; Text("→", color = Teal, fontSize = 21.sp) } }
@Composable private fun EmptyCard(title: String, sub: String) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("✦", color = Orange, fontSize = 22.sp); Spacer(Modifier.height(6.dp)); Text(title, color = Ink, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(sub, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center) } }
@Composable private fun PageHeader(kicker: String, title: String, subtitle: String) { Spacer(Modifier.height(24.dp)); Text(kicker, color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp); Spacer(Modifier.height(7.dp)); Text(title, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Normal); Spacer(Modifier.height(5.dp)); Text(subtitle, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(24.dp)) }

@Composable
internal fun JournalScreen(jumps: List<Jump>, pending: Set<String>, add: () -> Unit, edit: (Jump) -> Unit, remove: (Jump) -> Unit, favorite: (Jump) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var periodName by rememberSaveable { mutableStateOf(JournalPeriod.ALL.name) }
    var discipline by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val period = JournalPeriod.entries.firstOrNull { it.name == periodName } ?: JournalPeriod.ALL
    val disciplines = jumps.map { it.discipline.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }
    val filtered = filterJumps(jumps, query, favoritesOnly, discipline, period)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            PageHeader("ПРЫЖКИ", "Журнал прыжков", "Твои записи")
            Button(onClick = add, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("+  Добавить прыжок", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск по записям") }, singleLine = true, shape = RoundedCornerShape(11.dp),
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Очистить", fontSize = 11.sp) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("★ Избранное") })
                Box(Modifier.weight(1f)) {
                    FilterChip(selected = discipline != null, onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth(), label = { Text(discipline ?: "Дисциплина", maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Все дисциплины") }, onClick = { discipline = null; menuOpen = false })
                        disciplines.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { discipline = name; menuOpen = false }) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(JournalPeriod.ALL to "Все", JournalPeriod.MONTH to "За месяц", JournalPeriod.YEAR to "За год").forEach { (value, title) ->
                    FilterChip(selected = period == value, onClick = { periodName = value.name }, label = { Text(title, fontSize = 12.sp) })
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionTitle("Записи", "${filtered.size} ИЗ ${jumps.size}")
        }
        if (jumps.isEmpty()) item { EmptyCard("Прыжков пока нет", "Нажми кнопку чтобы добавить запись") }
        else if (filtered.isEmpty()) item {
            Column {
                EmptyCard("Ничего не нашлось", "Попробуй другой запрос или сбрось фильтры")
                TextButton(onClick = { query = ""; favoritesOnly = false; periodName = JournalPeriod.ALL.name; discipline = null }) { Text("Сбросить фильтры") }
            }
        }
        items(filtered, key = { it.id }) { jump ->
            val busy = jump.id in pending
            JumpCard(jump, if (busy) null else { { edit(jump) } }, if (busy) null else { { remove(jump) } }, { favorite(jump) }, busy)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun JumpCard(jump: Jump, edit: (() -> Unit)?, remove: (() -> Unit)?, favorite: (() -> Unit)? = null, favoriteBusy: Boolean = false) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { Text("↗", color = Teal, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(jump.location, fontWeight = FontWeight.Bold, color = Ink); Text(jump.date, fontSize = 12.sp, color = Muted) }
            if (favorite != null) {
                IconButton(onClick = favorite, enabled = !favoriteBusy, modifier = Modifier.semantics { contentDescription = if (jump.isFavorite) "Убрать из избранного" else "В избранное" }) {
                    if (favoriteBusy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(if (jump.isFavorite) "★" else "☆", color = Teal, fontSize = 26.sp)
                }
            } else if (jump.isFavorite) Text("★", color = Teal, fontSize = 20.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(listOfNotNull(jump.discipline.takeIf { it.isNotBlank() }, jump.altitude.takeIf { it.isNotBlank() }?.let { "$it м" }, jump.freefallSeconds?.let { "$it с падения" }).joinToString("  ·  ").ifBlank { "Прыжок" }, color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (jump.notes.isNotBlank()) { Spacer(Modifier.height(7.dp)); Text(jump.notes, color = Muted, fontSize = 12.sp) }
        if (edit != null || remove != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (edit != null) Text("Изменить", modifier = Modifier.clickable { edit() }.padding(vertical = 8.dp), color = Teal, fontSize = 12.sp)
                if (remove != null) Text("Удалить", modifier = Modifier.clickable { remove() }.padding(vertical = 8.dp), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun ChecklistScreen(checked: List<Boolean>, update: (Int, Boolean) -> Unit, reset: () -> Unit, modifier: Modifier = Modifier) { Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) { PageHeader("СНАРЯЖЕНИЕ", "Чек-лист", "Отметь что уже проверил"); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp)) { Text("УЖЕ ПРОВЕРЕНО", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(6.dp)); Text("${checked.count { it }} из ${checks.size} проверено", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { checked.count { it }.toFloat() / checks.size }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(10.dp)), color = Teal, trackColor = MaterialTheme.colorScheme.secondaryContainer) }; Spacer(Modifier.height(22.dp)); SectionTitle("Снаряжение", "ПЕРЕД ВЫЛЕТОМ"); Spacer(Modifier.height(12.dp)); Column(Modifier.clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface)) { checks.forEachIndexed { i, item -> Row(Modifier.fillMaxWidth().clickable { update(i, !checked[i]) }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = checked[i], onCheckedChange = { update(i, it) }); Text(item, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp)) }; if (i < checks.lastIndex) HorizontalDivider(Modifier.padding(start = 62.dp), color = Line) } }; Spacer(Modifier.height(12.dp)); TextButton(onClick = reset, modifier = Modifier.align(Alignment.End)) { Text("Начать проверку заново") }; Text("Это просто памятка. Перед прыжком обязательно проверь снаряжение по правилам своей дропзоны и вместе с инструктором.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp); Spacer(Modifier.height(24.dp)) } }

@Composable private fun ToolsScreen(modifier: Modifier = Modifier) { var value by remember { mutableStateOf("") }; var selected by remember { mutableIntStateOf(0) }; var reverse by remember { mutableStateOf(false) }; val conversions = listOf(Triple("Высота", "м", "фт"), Triple("Скорость", "м/с", "км/ч"), Triple("Расстояние", "км", "мили")); val factors = listOf(3.280839895, 3.6, 0.621371192); val item = conversions[selected]; val number = value.replace(',', '.').toDoubleOrNull(); val result = number?.let { if (reverse) it / factors[selected] else it * factors[selected] }; Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) { PageHeader("РАСЧЁТЫ", "Инструменты", "Перевод единиц измерения"); SectionTitle("Конвертер", "ПЕРЕВОД ЕДИНИЦ"); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { conversions.forEachIndexed { i, conversion -> FilterChip(selected = selected == i, onClick = { selected = i; reverse = false }, label = { Text(conversion.first, fontSize = 12.sp) }, modifier = Modifier.weight(1f)) } }; Spacer(Modifier.height(12.dp)); Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp)) { Text("ИЗ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(if (reverse) item.third else item.second) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)); TextButton(onClick = { reverse = !reverse }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("⇅  Поменять местами") }; Text("РЕЗУЛЬТАТ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); Text(if (result != null && result.isFinite()) "${String.format(Locale.getDefault(), "%.2f", result)} ${if (reverse) item.second else item.third}" else "Введи число (${if (reverse) item.second else item.third})", color = Teal, fontWeight = FontWeight.Bold, fontSize = 27.sp) }; Spacer(Modifier.height(18.dp)); Text("Для планирования прыжка сверяйся с приборами, погодой и правилами своей дропзоны.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) } }

@Composable
private fun AuthScreen(auth: SupabaseAuth, form: AuthFormState, scope: kotlinx.coroutines.CoroutineScope, onSuccess: (AuthSession, String?) -> Unit) {
    var register by form::register
    var passwordVisible by form::passwordVisible
    var confirmationVisible by form::confirmationVisible
    var username by form::username
    var password by form::password
    var confirmPassword by form::confirmPassword
    var busy by form::busy
    var error by form::error
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.skydive_icon), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Text("SkyDiveApp", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (register) "Регистрация" else "Вход", color = Ink, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(if (register) "Придумай логин и пароль" else "Введи логин и пароль", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(username, { username = it; error = null }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), supportingText = { Text("От 3 до 24 символов: латинские буквы, цифры и подчёркивание") }, shape = RoundedCornerShape(11.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it; error = null }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { PasswordVisibilityButton(passwordVisible) { passwordVisible = !passwordVisible } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(11.dp))
        if (register) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(confirmPassword, { confirmPassword = it; error = null }, label = { Text("Повтори пароль") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, visualTransformation = if (confirmationVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { PasswordVisibilityButton(confirmationVisible) { confirmationVisible = !confirmationVisible } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(11.dp))
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
                        form.clearSecrets(); onSuccess(result, warning)
                    }
                    catch (e: AuthException) { error = e.message }
                    catch (_: Exception) { error = "Не получилось подключиться. Проверь интернет и попробуй ещё раз" }
                    finally { busy = false }
                } }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(11.dp)) { LoadingLabel(busy, if (register) "Зарегистрироваться" else "Войти", "Загрузка...") }
        TextButton(onClick = { register = !register; confirmPassword = ""; passwordVisible = false; confirmationVisible = false; error = null }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (register) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться") }
    }
}

@Composable
private fun PasswordVisibilityButton(visible: Boolean, onClick: () -> Unit) {
    val description = if (visible) "Скрыть пароль" else "Показать пароль"
    val muted = Muted
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.size(24.dp)) {
            val eye = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.5f)
                cubicTo(size.width * 0.3f, size.height * 0.16f, size.width * 0.7f, size.height * 0.16f, size.width * 0.92f, size.height * 0.5f)
                cubicTo(size.width * 0.7f, size.height * 0.84f, size.width * 0.3f, size.height * 0.84f, size.width * 0.08f, size.height * 0.5f)
                close()
            }
            val stroke = 1.8.dp.toPx()
            drawPath(eye, color = muted, style = Stroke(width = stroke))
            drawCircle(muted, radius = size.width * 0.13f, style = Stroke(width = stroke))
            if (!visible) {
                drawLine(muted, Offset(size.width * 0.16f, size.height * 0.16f), Offset(size.width * 0.84f, size.height * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}
