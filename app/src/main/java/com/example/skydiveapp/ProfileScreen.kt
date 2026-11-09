package com.example.skydiveapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val ProfileInk = Color(0xFF1C1B20)
private val ProfileMuted = Color(0xFF56545F)
private val ProfileCard = Color(0xFFE2E2EC)
private val ProfileAccent = Color(0xFF454A65)

@Composable
internal fun ProfileScreen(store: JumpStore, jumps: List<Jump>, statsAvailable: Boolean, login: String, model: AppSessionViewModel, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft = remember { model.profileDraft ?: ProfileDraft(login).also { model.profileDraft = it } }
    var name by draft::name
    var photo by draft::photo
    var initial by remember { mutableStateOf<Profile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            val profile = withContext(Dispatchers.IO) { store.loadProfile() } ?: Profile(login, null)
            if (!draft.dirty) { name = profile.name; photo = profile.photo }
            initial = profile
        } catch (e: Exception) {
            error = if (e is AuthException) e.message else "Не получилось загрузить профиль. Проверь интернет и попробуй снова"
        } finally { loading = false }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            picking = true
            error = null
            scope.launch {
                try { photo = withContext(Dispatchers.IO) { readProfilePhoto(context, uri) }; draft.dirty = true; message = null }
                catch (_: Exception) { error = "Не получилось открыть фото. Попробуй выбрать другое" }
                finally { picking = false }
            }
        }
    }
    val image = remember(photo) {
        try {
            photo?.let { Base64.decode(it, Base64.DEFAULT) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
        } catch (_: Exception) { null }
    }
    val maxAltitude = remember(jumps.toList()) {
        jumps.mapNotNull { it.altitude.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value.isFinite() && value > 0 } }.maxOrNull()
    }
    val statistics = calculateJumpStatistics(jumps.toList())
    val isBusy = loading || saving || picking
    val isDirty = initial != null && (name.trim() != initial!!.name || photo != initial!!.photo)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text("Профиль", color = ProfileInk, fontSize = 24.sp)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(ProfileCard).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(Color(0xFFDFE4FA)), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, contentDescription = "Фото профиля", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = ProfileAccent, fontSize = 36.sp)
            }
            TextButton(onClick = { picker.launch("image/*") }, enabled = !isBusy && initial != null) { Text(if (photo == null) "Добавить фото" else "Изменить фото") }
            if (photo != null) TextButton(onClick = { photo = null; draft.dirty = true; message = null }, enabled = !isBusy && initial != null) { Text("Удалить фото") }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) { name = it; draft.dirty = true; message = null } }, label = { Text("Название профиля") }, singleLine = true, enabled = !isBusy && initial != null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp))
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                val profileName = name.trim()
                if (profileName.isEmpty()) { error = "Введи название профиля"; return@Button }
                saving = true
                error = null
                scope.launch {
                    val updated = Profile(profileName, photo)
                    try { withContext(Dispatchers.IO) { store.saveProfile(updated) }; name = updated.name; initial = updated; draft.dirty = false; message = "Сохранено" }
                    catch (e: Exception) { error = if (e is AuthException) e.message else "Не получилось сохранить профиль. Проверь интернет и попробуй снова" }
                    finally { saving = false }
                }
            }, enabled = !isBusy && isDirty, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) {
                Text(if (saving) "Сохранение..." else "Сохранить")
            }
            if (picking) Text("Загрузка фото...", color = ProfileMuted, fontSize = 12.sp)
            if (message != null) Text(message!!, color = ProfileAccent, fontSize = 12.sp)
        }
        if (loading) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (initial == null) TextButton(onClick = { reload++ }) { Text("Попробовать снова") }
        }
        Spacer(Modifier.height(24.dp))
        Text("Личная статистика", color = ProfileInk, fontWeight = FontWeight.Medium, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStatistic("Прыжков", if (statsAvailable) jumps.size.toString() else "Нет данных", Modifier.weight(1f))
            ProfileStatistic("Макс. высота", if (statsAvailable) maxAltitude?.let { "${String.format(Locale.getDefault(), "%.0f", it)} м" } ?: "Нет данных" else "Нет данных", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStatistic("За этот месяц", if (statsAvailable) statistics.thisMonth.toString() else "Нет данных", Modifier.weight(1f))
            ProfileStatistic("За этот год", if (statsAvailable) statistics.thisYear.toString() else "Нет данных", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        ProfileStatistic("Свободное падение", if (statsAvailable) formatFreefall(statistics.totalFreefallSeconds) else "Нет данных", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        ProfileStatistic("Чаще всего", if (statsAvailable) statistics.favoriteLocation ?: "Нет данных" else "Нет данных", Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Статистика по записям в журнале. Время считается по прыжкам, где оно указано", color = ProfileMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSignOut) { Text("Выйти из аккаунта") }
    }
}

@Composable
private fun ProfileStatistic(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(11.dp)).background(ProfileCard).padding(16.dp)) {
        Text(label, color = ProfileMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = ProfileInk, fontWeight = FontWeight.Medium, fontSize = 20.sp)
    }
}

private fun readProfilePhoto(context: Context, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("Не удалось открыть изображение")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("Неверное изображение")
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (max(bounds.outWidth / inSampleSize, bounds.outHeight / inSampleSize) > 512) inSampleSize *= 2
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("Не удалось прочитать изображение")
    val scale = minOf(1f, 256f / max(bitmap.width, bitmap.height))
    val scaled = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
    return ByteArrayOutputStream().use { output ->
        check(scaled.compress(Bitmap.CompressFormat.JPEG, 80, output))
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}
