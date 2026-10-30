package com.example.skydiveapp

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class Jump(val id: String, val date: String, val location: String, val altitude: String, val discipline: String, val notes: String)

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
            if (status !in 200..299) throw AuthException(if (status == 401) "Сессия закончилась, войди заново" else "Не получилось загрузить журнал (код $status). Проверь таблицу в Supabase")
            return result
        } finally { connection.disconnect() }
    }
    private fun parse(json: JSONObject) = Jump(json.getString("id"), json.getString("jump_date"), json.getString("location"), json.optString("altitude"), json.optString("discipline"), json.optString("notes"))
    fun list(): List<Jump> { val array = JSONArray(request("GET", "?select=id,jump_date,location,altitude,discipline,notes&order=created_at.desc")); return (0 until array.length()).map { parse(array.getJSONObject(it)) } }
    fun add(jump: Jump): Jump {
        val body = JSONObject().put("id", jump.id).put("jump_date", jump.date).put("location", jump.location).put("altitude", jump.altitude).put("discipline", jump.discipline).put("notes", jump.notes)
        return parse(JSONArray(request("POST", "", body)).getJSONObject(0))
    }
    fun delete(jump: Jump) { val rows = JSONArray(request("DELETE", "?id=eq.${jump.id}&select=id")); if (rows.length() != 1) throw AuthException("Не получилось удалить запись. Обнови журнал и попробуй снова") }
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
