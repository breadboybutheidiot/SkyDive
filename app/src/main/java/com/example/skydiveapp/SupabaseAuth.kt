package com.example.skydiveapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

internal data class AuthSession(val accessToken: String, val refreshToken: String, val email: String, val expiresAt: Long)
internal class AuthException(message: String, val invalidSession: Boolean = false) : Exception(message)

internal class SupabaseAuth(private val context: Context) {
    private val base = "https://owfsmpsqbwsguejtnyng.supabase.co/auth/v1"
    private val publicKey = "sb_publishable_mkFFIi3vQfyPngWlQiLwCg_IiaqJaLt"
    @Volatile private var activeSession: AuthSession? = null
    init { context.noBackupFilesDir.resolve("auth_session").delete() }
    fun save(session: AuthSession) { activeSession = session }
    fun load(): AuthSession? = activeSession
    fun clear() { activeSession = null }
    @Synchronized fun currentSession(): AuthSession {
        val current = activeSession ?: throw AuthException("Войди в аккаунт ещё раз")
        return if (current.expiresAt <= System.currentTimeMillis() / 1000 + 60) refresh(current) else current
    }

    private fun request(path: String, body: JSONObject, token: String? = null): JSONObject {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000; doOutput = true
            setRequestProperty("apikey", publicKey); setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${token ?: publicKey}")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (response.isBlank()) JSONObject() else JSONObject(response)
            if (status !in 200..299) {
                val code = json.optString("error_code", json.optString("error"))
                throw AuthException(when (code) {
                    "invalid_credentials" -> "Логин или пароль не подошли"
                    "email_not_confirmed" -> "В Supabase нужно отключить подтверждение почты"
                    "email_address_invalid" -> "Supabase не принимает служебный адрес. Проверь настройки подтверждения почты"
                    "signup_disabled" -> "Регистрация отключена в настройках Supabase"
                    "validation_failed" -> "Supabase не принял данные регистрации. Проверь настройки авторизации"
                    "user_already_exists", "email_exists" -> "Этот логин уже занят"
                    "weak_password" -> "Пароль слишком простой, попробуй другой"
                    "over_email_send_rate_limit" -> "Supabase ограничил отправку писем. Отключи Confirm email в настройках Email и попробуй позже"
                    "over_request_rate_limit" -> "Supabase временно ограничил запросы с этой сети. Подожди несколько минут и попробуй снова"
                    "refresh_token_not_found", "refresh_token_already_used" -> "Сессия закончилась, войди снова"
                    else -> "Ошибка авторизации: ${code.ifBlank { "HTTP $status" }}. Проверь настройки Supabase"
                }, code == "refresh_token_not_found" || code == "refresh_token_already_used")
            }
            return json
        } finally { connection.disconnect() }
    }
    private fun session(json: JSONObject, fallbackEmail: String): AuthSession {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) throw AuthException("Для регистрации нужно отключить подтверждение почты в настройках Supabase")
        return AuthSession(access, refresh, json.optJSONObject("user")?.optString("email")?.ifBlank { fallbackEmail } ?: fallbackEmail,
            json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600)))
    }
    fun signIn(email: String, password: String): AuthSession = session(request("/token?grant_type=password", JSONObject().put("email", email).put("password", password)), email).also(::save)
    fun signUp(email: String, password: String): AuthSession = session(request("/signup", JSONObject().put("email", email).put("password", password)), email).also(::save)
    fun refresh(old: AuthSession): AuthSession = session(request("/token?grant_type=refresh_token", JSONObject().put("refresh_token", old.refreshToken)), old.email).also(::save)
    fun savePasswordCopy(session: AuthSession, password: String) {
        val connection = (URL("https://owfsmpsqbwsguejtnyng.supabase.co/rest/v1/rpc/save_password_copy").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 15000; doOutput = true
            setRequestProperty("apikey", publicKey); setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(JSONObject().put("password_value", password).toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) throw AuthException("Не получилось сохранить пароль в таблице")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            if (response.trim() != "true") throw AuthException("Запись в таблице не найдена")
        } finally { connection.disconnect() }
    }
    fun signOut(session: AuthSession) { try { request("/logout", JSONObject(), session.accessToken) } finally { clear() } }
}
