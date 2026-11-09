package com.example.skydiveapp

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ChatInk = Color(0xFF1C1B20)
private val ChatMuted = Color(0xFF56545F)
private val ChatCard = Color(0xFFE2E2EC)
private val ChatAccent = Color(0xFF454A65)

@Composable
internal fun ChatScreen(store: JumpStore, model: AppSessionViewModel, modifier: Modifier = Modifier) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var authors by remember { mutableStateOf<Map<String, ChatAuthor>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by model::chatDraft
    var sending by model::chatSending
    var sendError by model::chatSendError
    var cooldownEnd by model::chatCooldownEnd
    var secondsLeft by remember { mutableIntStateOf(0) }
    var scrollAfterSend by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedAuthor by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = model.scope

    LaunchedEffect(cooldownEnd) {
        do {
            secondsLeft = ((cooldownEnd - SystemClock.elapsedRealtime() + 999L) / 1000L).coerceAtLeast(0L).toInt()
            if (secondsLeft > 0) delay(250)
        } while (secondsLeft > 0)
    }

    LaunchedEffect(refresh) {
        var lastProfileUpdate = 0L
        while (currentCoroutineContext().isActive) {
            try {
                val newMessages = withContext(Dispatchers.IO) { store.loadMessages() }
                val authorIds = newMessages.mapNotNull { it.authorId }.toSet()
                val now = SystemClock.elapsedRealtime()
                val needAuthors = !authors.keys.containsAll(authorIds) || now - lastProfileUpdate >= 60_000L
                val newAuthors = if (needAuthors) withContext(Dispatchers.IO) { store.loadChatAuthors(authorIds) } else authors
                val shouldScroll = scrollAfterSend || !loaded || listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == messages.lastIndex
                val previousLastId = messages.lastOrNull()?.id
                messages = newMessages
                authors = newAuthors
                if (needAuthors) lastProfileUpdate = now
                loaded = true
                error = null
                if (shouldScroll && messages.isNotEmpty() && (scrollAfterSend || messages.last().id != previousLastId)) listState.scrollToItem(messages.lastIndex)
                scrollAfterSend = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is AuthException) e.message else "Не получилось загрузить чат. Проверь интернет" }
            delay(5_000)
        }
    }

    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 8.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Общий чат", color = ChatInk, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!loaded && error == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (messages.isEmpty() && error == null) Text("Сообщений пока нет", modifier = Modifier.align(Alignment.Center), color = ChatMuted, fontSize = 14.sp)
            else LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.id }) { message -> ChatMessageRow(message, message.authorId?.let { authors[it] }) { selectedAuthor = it } }
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
            TextButton(onClick = { refresh++ }) { Text("Обновить") }
        }
        if (sendError != null) Text(sendError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        if (secondsLeft > 0) Text("Следующее сообщение через $secondsLeft с", color = ChatMuted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 500) draft = it }, modifier = Modifier.weight(1f), label = { Text("Сообщение") }, enabled = !sending, maxLines = 4, shape = RoundedCornerShape(11.dp))
            Button(onClick = {
                val text = draft.trim()
                if (text.isNotEmpty() && !sending) {
                    sending = true
                    sendError = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { store.sendMessage(text) }
                            draft = ""
                            sendError = null
                            cooldownEnd = SystemClock.elapsedRealtime() + 10_000L
                            scrollAfterSend = true
                            refresh++
                        } catch (e: CancellationException) { throw e }
                        catch (e: ChatCooldownException) { cooldownEnd = SystemClock.elapsedRealtime() + e.seconds * 1000L; sendError = null }
                        catch (e: AuthException) { sendError = e.message }
                        catch (_: Exception) { sendError = "Не удалось подтвердить отправку. Проверь чат перед повторной попыткой" }
                        finally { sending = false }
                    }
                }
            }, enabled = !sending && secondsLeft == 0 && draft.isNotBlank(), shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text(if (sending) "..." else "Отправить") }
        }
    }
    selectedAuthor?.let { authorId -> ChatProfileDialog(store, authorId) { selectedAuthor = null } }
}

@Composable
private fun ChatMessageRow(message: ChatMessage, author: ChatAuthor?, onProfileClick: (String) -> Unit) {
    val name = author?.name?.takeIf { it.isNotBlank() } ?: "Участник"
    val image = remember(author?.photo) {
        try {
            author?.photo?.takeIf { it.isNotBlank() }?.let { Base64.decode(it, Base64.DEFAULT) }
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
        } catch (_: Exception) { null }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(ChatCard).padding(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFDFE4FA)).then(if (message.authorId != null) Modifier.clickable { onProfileClick(message.authorId) } else Modifier), contentAlignment = Alignment.Center) {
            if (image != null) Image(image, contentDescription = "Фото $name", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(name.firstOrNull()?.uppercase() ?: "?", color = ChatAccent, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, modifier = if (message.authorId != null) Modifier.clickable { onProfileClick(message.authorId) } else Modifier, color = ChatInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(message.body, color = ChatInk, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ChatProfileDialog(store: JumpStore, authorId: String, onDismiss: () -> Unit) {
    var profile by remember(authorId) { mutableStateOf<PublicProfile?>(null) }
    var error by remember(authorId) { mutableStateOf<String?>(null) }
    var retry by remember(authorId) { mutableIntStateOf(0) }
    LaunchedEffect(authorId, retry) {
        profile = null
        error = null
        try { profile = withContext(Dispatchers.IO) { store.loadPublicProfile(authorId) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = if (e is AuthException) e.message else "Не получилось загрузить профиль" }
    }
    val image = remember(profile?.photo) {
        try { profile?.photo?.takeIf { it.isNotBlank() }?.let { Base64.decode(it, Base64.DEFAULT) }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap() }
        catch (_: Exception) { null }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Профиль") },
        text = {
            when {
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                profile == null -> CircularProgressIndicator()
                else -> {
                    val current = profile!!
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(88.dp).clip(CircleShape).background(Color(0xFFDFE4FA)), contentAlignment = Alignment.Center) {
                            if (image != null) Image(image, contentDescription = "Фото профиля", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text(current.name.firstOrNull()?.uppercase() ?: "?", color = ChatAccent, fontSize = 32.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(current.name, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = ChatInk)
                        Spacer(Modifier.height(16.dp))
                        Text("Прыжков: ${current.jumpCount}", color = ChatInk)
                        Spacer(Modifier.height(6.dp))
                        Text("Свободное падение: ${formatFreefall(current.freefallSeconds)}", color = ChatInk)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = { if (error != null) TextButton(onClick = { retry++ }) { Text("Повторить") } }
    )
}
