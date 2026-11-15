package com.example.skydiveapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.AtomicFile
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class AuthSession(val accessToken: String, val refreshToken: String, val email: String, val expiresAt: Long)
internal class AuthException(message: String, val invalidSession: Boolean = false) : Exception(message)

internal class SupabaseAuth(private val context: Context) {
    private val base = "https://owfsmpsqbwsguejtnyng.supabase.co/auth/v1"
    private val publicKey = "sb_publishable_mkFFIi3vQfyPngWlQiLwCg_IiaqJaLt"
    @Volatile private var activeSession: AuthSession? = null
    private val sessionFile = AtomicFile(context.noBackupFilesDir.resolve("auth_session"))
    private val keyAlias = "skydive_session_key"

    private fun sessionKey(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    @Synchronized fun save(session: AuthSession) {
        val json = JSONObject().put("access_token", session.accessToken).put("refresh_token", session.refreshToken)
            .put("email", session.email).put("expires_at", session.expiresAt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey(true))
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val data = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString().toByteArray(Charsets.UTF_8)
        val stream = sessionFile.startWrite()
        try { stream.write(data); sessionFile.finishWrite(stream) }
        catch (e: Exception) { sessionFile.failWrite(stream); throw e }
        activeSession = session
    }

    @Synchronized fun load(): AuthSession? {
        activeSession?.let { return it }
        if (!sessionFile.baseFile.exists()) return null
        return try {
            val key = sessionKey(false) ?: throw IllegalStateException("Ключ сессии недоступен")
            val encrypted = JSONObject(sessionFile.openRead().bufferedReader().use { it.readText() })
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(encrypted.getString("iv"), Base64.NO_WRAP)))
            val data = JSONObject(String(cipher.doFinal(Base64.decode(encrypted.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
            AuthSession(data.getString("access_token"), data.getString("refresh_token"), data.getString("email"), data.getLong("expires_at"))
                .also { activeSession = it }
        } catch (_: Exception) { clear(); null }
    }
    @Synchronized fun clear() { activeSession = null; sessionFile.delete() }
    @Synchronized fun currentSession(): AuthSession {
        val current = activeSession ?: throw AuthException("Войди в аккаунт ещё раз", invalidSession = true)
        return if (current.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
            try { refresh(current) } catch (e: AuthException) { if (e.invalidSession) clear(); throw e }
        } else current
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
                    "refresh_token_not_found", "refresh_token_already_used", "session_not_found", "user_not_found" -> "Сессия закончилась, войди снова"
                    else -> "Ошибка авторизации: ${code.ifBlank { "HTTP $status" }}. Проверь настройки Supabase"
                }, code in setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "user_not_found"))
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
    fun signOut(session: AuthSession) { clear(); request("/logout", JSONObject(), session.accessToken) }
}
