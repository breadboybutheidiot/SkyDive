package com.example.skydiveapp

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class Jump(val id: String, val date: String, val location: String, val altitude: String, val discipline: String, val notes: String, val freefallSeconds: Int? = null, val isFavorite: Boolean = false)
internal data class Profile(val name: String, val photo: String?)
internal data class ChatMessage(val id: String, val authorId: String?, val body: String, val createdAt: String, val editedAt: String?)
internal data class ChatAuthor(val id: String, val name: String, val photo: String?)
internal data class ChatPermissions(val userId: String, val isAdmin: Boolean)
internal data class PublicProfile(val name: String, val photo: String?, val jumpCount: Long, val freefallSeconds: Long?)
internal class ChatCooldownException(val seconds: Int) : Exception()

internal class JumpStore(private val auth: SupabaseAuth) {
    private fun request(method: String, query: String, body: JSONObject? = null, table: String = "jumps", upsert: Boolean = false): String {
        val session = auth.currentSession()
        val connection = (URL("https://owfsmpsqbwsguejtnyng.supabase.co/rest/v1/$table$query").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("apikey", "sb_publishable_mkFFIi3vQfyPngWlQiLwCg_IiaqJaLt")
            setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", if (upsert) "resolution=merge-duplicates,return=representation" else "return=representation")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val result = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val code = try { JSONObject(result).optString("code") } catch (_: Exception) { "" }
                val message = when {
                    status == 401 -> "Сессия закончилась, войди заново"
                    table == "rpc/sync_my_achievements" && code in listOf("PGRST202", "PGRST205") -> "Достижения пока не настроены"
                    table == "rpc/sync_my_achievements" -> "Не получилось загрузить достижения"
                    table == "jumps" && code == "42703" -> "Нужно обновить настройки избранного в Supabase"
                    table == "rpc/set_jump_favorite" && code in listOf("PGRST202", "PGRST205") -> "Нужно обновить настройки избранного в Supabase"
                    table == "rpc/set_jump_favorite" -> "Не получилось изменить избранное"
                    table == "skydiver_profiles" && code == "PGRST205" -> "Профиль пока не настроен. Обратись к разработчику"
                    table == "skydiver_profiles" && status == 403 -> "Нет доступа к профилю"
                    table == "skydiver_profiles" -> "Не получилось открыть профиль (код $status)"
                    table == "chat_messages" && code == "42703" -> "Нужно обновить настройки чата в Supabase"
                    table in listOf("rpc/chat_profiles", "rpc/chat_public_profile", "rpc/chat_permissions", "rpc/send_chat_message", "rpc/edit_chat_message", "rpc/delete_chat_message") && code in listOf("PGRST202", "PGRST205") -> "Нужно обновить настройки чата в Supabase"
                    table == "chat_messages" || table.startsWith("rpc/chat_") || table in listOf("rpc/send_chat_message", "rpc/edit_chat_message", "rpc/delete_chat_message") -> if (status == 403) "Нет доступа или сообщение уже удалено" else "Не получилось подключиться к чату (код $status)"
                    else -> "Не получилось загрузить журнал (код $status). Проверь таблицу в Supabase"
                }
                throw AuthException(message, invalidSession = status == 401)
            }
            return result
        } finally { connection.disconnect() }
    }
    private fun parse(json: JSONObject) = Jump(json.getString("id"), json.getString("jump_date"), json.getString("location"), json.optString("altitude"), json.optString("discipline"), json.optString("notes"), if (json.isNull("freefall_seconds")) null else json.getInt("freefall_seconds"), json.optBoolean("is_favorite", false))
    fun list(): List<Jump> {
        val result = mutableListOf<Jump>()
        val pageSize = 500
        do {
            val array = JSONArray(request("GET", "?select=id,jump_date,location,altitude,discipline,notes,freefall_seconds,is_favorite&order=created_at.desc,id.desc&limit=$pageSize&offset=${result.size}"))
            for (i in 0 until array.length()) result.add(parse(array.getJSONObject(i)))
            val count = array.length()
        } while (count == pageSize)
        return result
    }
    fun add(jump: Jump): Jump {
        val body = JSONObject().put("id", jump.id).put("jump_date", jump.date).put("location", jump.location).put("altitude", jump.altitude).put("discipline", jump.discipline).put("notes", jump.notes).put("freefall_seconds", jump.freefallSeconds ?: JSONObject.NULL)
        return parse(JSONArray(request("POST", "", body)).getJSONObject(0))
    }
    fun update(jump: Jump): Jump {
        val body = JSONObject().put("jump_id", jump.id).put("new_date", jump.date).put("new_location", jump.location)
            .put("new_altitude", jump.altitude).put("new_discipline", jump.discipline).put("new_notes", jump.notes)
            .put("new_freefall_seconds", jump.freefallSeconds ?: JSONObject.NULL)
        return parse(JSONObject(request("POST", "", body, table = "rpc/update_own_jump")))
    }
    fun delete(jump: Jump) { val rows = JSONArray(request("DELETE", "?id=eq.${jump.id}&select=id")); if (rows.length() != 1) throw AuthException("Не получилось удалить запись. Обнови журнал и попробуй снова") }
    fun setFavorite(id: String, value: Boolean): Jump {
        val body = JSONObject().put("jump_id", id).put("favorite", value)
        return parse(JSONObject(request("POST", "", body, table = "rpc/set_jump_favorite")))
    }
    fun syncAchievements(): Set<String> {
        val rows = JSONArray(request("POST", "", JSONObject(), table = "rpc/sync_my_achievements"))
        return (0 until rows.length()).map { rows.getString(it) }.toSet()
    }
    fun loadProfile(): Profile? {
        val rows = JSONArray(request("GET", "?select=display_name,photo_base64", table = "skydiver_profiles"))
        if (rows.length() == 0) return null
        val item = rows.getJSONObject(0)
        return Profile(item.optString("display_name"), item.optString("photo_base64").takeIf { !item.isNull("photo_base64") && it.isNotBlank() })
    }
    fun saveProfile(profile: Profile) {
        val body = JSONObject().put("display_name", profile.name).put("photo_base64", profile.photo ?: JSONObject.NULL)
        val rows = JSONArray(request("POST", "?on_conflict=user_id", body, "skydiver_profiles", true))
        if (rows.length() != 1) throw AuthException("Не получилось сохранить профиль")
    }
    fun loadMessages(): List<ChatMessage> {
        val rows = JSONArray(request("GET", "?select=id,author_id,body,created_at,edited_at&order=created_at.desc,id.desc&limit=50", table = "chat_messages"))
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            ChatMessage(item.getString("id"), if (item.isNull("author_id")) null else item.optString("author_id"), item.getString("body"), item.getString("created_at"), if (item.isNull("edited_at")) null else item.optString("edited_at"))
        }.reversed()
    }
    fun loadChatAuthors(ids: Set<String>): Map<String, ChatAuthor> {
        if (ids.isEmpty()) return emptyMap()
        val rows = JSONArray(request("POST", "", JSONObject().put("author_ids", JSONArray(ids.toList())), table = "rpc/chat_profiles"))
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            ChatAuthor(item.getString("user_id"), item.optString("display_name", "Участник"), if (item.isNull("photo_base64")) null else item.optString("photo_base64"))
        }.associateBy { it.id }
    }
    fun sendMessage(body: String) {
        val result = JSONObject(request("POST", "", JSONObject().put("message_body", body), table = "rpc/send_chat_message"))
        if (!result.optBoolean("ok", false)) throw ChatCooldownException(result.optInt("wait_seconds", 10).coerceIn(1, 10))
    }
    fun chatPermissions(): ChatPermissions {
        val result = JSONObject(request("POST", "", JSONObject(), table = "rpc/chat_permissions"))
        val userId = result.optString("user_id")
        if (userId.isBlank() || userId == "null") throw AuthException("Войди в аккаунт ещё раз", invalidSession = true)
        return ChatPermissions(userId, result.optBoolean("is_admin", false))
    }
    fun editMessage(id: String, body: String) {
        val result = request("POST", "", JSONObject().put("message_id", id).put("message_body", body), table = "rpc/edit_chat_message")
        if (result.trim() != "true") throw AuthException("Не получилось изменить сообщение")
    }
    fun deleteMessage(id: String) {
        val result = request("POST", "", JSONObject().put("message_id", id), table = "rpc/delete_chat_message")
        if (result.trim() != "true") throw AuthException("Не получилось удалить сообщение")
    }
    fun loadPublicProfile(authorId: String): PublicProfile {
        val rows = JSONArray(request("POST", "", JSONObject().put("profile_user_id", authorId), table = "rpc/chat_public_profile"))
        if (rows.length() == 0) throw AuthException("Профиль недоступен")
        val item = rows.getJSONObject(0)
        return PublicProfile(item.optString("display_name", "Участник"), if (item.isNull("photo_base64")) null else item.optString("photo_base64"), item.getLong("jump_count"), if (item.isNull("freefall_seconds")) null else item.getLong("freefall_seconds"))
    }
    fun loadChecks(count: Int): List<Boolean> {
        val rows = JSONArray(request("GET", "?select=checked", table = "equipment_checklists"))
        if (rows.length() == 0) return List(count) { false }
        val values = rows.getJSONObject(0).getJSONArray("checked")
        return List(count) { i -> values.optBoolean(i, false) }
    }
    fun saveChecks(values: List<Boolean>) {
        val rows = JSONArray(request("POST", "?on_conflict=user_id", JSONObject().put("checked", JSONArray(values)), "equipment_checklists", true))
        if (rows.length() != 1) throw AuthException("Не получилось сохранить чек-лист")
    }

}
