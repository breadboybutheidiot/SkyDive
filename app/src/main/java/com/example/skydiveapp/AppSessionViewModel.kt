package com.example.skydiveapp

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal class AuthFormState {
    var register by mutableStateOf(false)
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var passwordVisible by mutableStateOf(false)
    var confirmationVisible by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    fun clearSecrets() { password = ""; confirmPassword = ""; passwordVisible = false; confirmationVisible = false }
}

internal class JumpDraft(initial: Jump?) {
    val id = initial?.id ?: UUID.randomUUID().toString()
    var date by mutableStateOf(initial?.date ?: SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()))
    var location by mutableStateOf(initial?.location ?: "")
    var altitude by mutableStateOf(initial?.altitude ?: "")
    var discipline by mutableStateOf(initial?.discipline ?: "")
    var notes by mutableStateOf(initial?.notes ?: "")
    var freefall by mutableStateOf(initial?.freefallSeconds?.toString() ?: "")
    var attempted by mutableStateOf(false)
}

internal class ProfileDraft(login: String) {
    var name by mutableStateOf(login)
    var photo by mutableStateOf<String?>(null)
    var dirty by mutableStateOf(false)
}

internal class AppSessionViewModel(application: Application) : AndroidViewModel(application) {
    val auth = SupabaseAuth(application)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val authForm = AuthFormState()
    var session by mutableStateOf<AuthSession?>(null)
    var passwordCopyError by mutableStateOf<String?>(null)
    val jumps = mutableStateListOf<Jump>()
    var tab by mutableIntStateOf(0)
    var showAdd by mutableStateOf(false)
    var editingJump by mutableStateOf<Jump?>(null)
    var deleteJump by mutableStateOf<Jump?>(null)
    var jumpDraft by mutableStateOf<JumpDraft?>(null)
    var profileDraft by mutableStateOf<ProfileDraft?>(null)
    var chatDraft by mutableStateOf("")
    var chatCooldownEnd by mutableLongStateOf(0L)
    var chatSending by mutableStateOf(false)
    var chatSendError by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    var jumpsAvailable by mutableStateOf(false)
    var cloudError by mutableStateOf<String?>(null)
    var reload by mutableIntStateOf(0)

    fun resetAfterLogout() {
        session = null
        jumps.clear()
        tab = 0; showAdd = false; editingJump = null; deleteJump = null; jumpDraft = null
        profileDraft = null; chatDraft = ""; chatCooldownEnd = 0L; chatSending = false; chatSendError = null
        saving = false; jumpsAvailable = false; cloudError = null; reload = 0
        authForm.clearSecrets()
    }
    override fun onCleared() { auth.clear(); authForm.clearSecrets(); scope.cancel() }
}
