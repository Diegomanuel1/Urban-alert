package com.example.feature.auth

import android.util.Log
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.profile.ProfileRepository
import com.example.data.profile.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Ready(val profile: UserProfile?) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

sealed interface ProfileActionStatus {
    data object Idle : ProfileActionStatus
    data object Saving : ProfileActionStatus
    data object Deleting : ProfileActionStatus
    data class Message(val text: String, val isError: Boolean = false) : ProfileActionStatus
}

class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val currentUserId: String,
) : ViewModel() {

    private val _actionStatus = MutableStateFlow<ProfileActionStatus>(ProfileActionStatus.Idle)
    val actionStatus: StateFlow<ProfileActionStatus> = _actionStatus.asStateFlow()

    val profileState: StateFlow<ProfileUiState> = profileRepository.observeProfile(currentUserId)
        .map<UserProfile?, ProfileUiState> { ProfileUiState.Ready(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Erro ao observar perfil", rootError)
            emit(
                ProfileUiState.Error(
                    rootError.message ?: "Não foi possível carregar o perfil.",
                ),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ProfileUiState.Loading,
        )

    init {
        ensureProfileInitialized()
    }

    private fun ensureProfileInitialized() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null && currentUser.uid == currentUserId) {
            viewModelScope.launch {
                profileRepository.ensureCitizenProfile(currentUser).onFailure { error ->
                    _actionStatus.value = ProfileActionStatus.Message(
                        text = error.localizedMessage ?: "Falha ao sincronizar perfil inicial.",
                        isError = true,
                    )
                }
            }
        }
    }

    fun updateProfile(displayName: String, homeCityCode: String) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty() || trimmed.length > 80) {
            _actionStatus.value = ProfileActionStatus.Message(
                text = "Informe um nome de exibição entre 1 e 80 caracteres.",
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            _actionStatus.value = ProfileActionStatus.Saving
            val result = profileRepository.updateProfile(
                userId = currentUserId,
                displayName = trimmed,
                homeCityCode = homeCityCode,
            )
            _actionStatus.value = result.fold(
                onSuccess = {
                    ProfileActionStatus.Message("Perfil atualizado com segurança.")
                },
                onFailure = { error ->
                    ProfileActionStatus.Message(
                        text = error.localizedMessage ?: "Não foi possível atualizar o perfil.",
                        isError = true,
                    )
                },
            )
        }
    }

    fun signOut(credentialManager: CredentialManager) {
        viewModelScope.launch {
            authRepository.signOut(credentialManager)
        }
    }

    fun deleteAccountAndData(credentialManager: CredentialManager) {
        viewModelScope.launch {
            _actionStatus.value = ProfileActionStatus.Deleting
            val profileDeleteResult = profileRepository.deleteProfile(currentUserId)
            if (profileDeleteResult.isFailure) {
                _actionStatus.value = ProfileActionStatus.Message(
                    text = profileDeleteResult.exceptionOrNull()?.localizedMessage
                        ?: "Erro ao remover dados do perfil no banco.",
                    isError = true,
                )
                return@launch
            }
            val authDeleteResult = authRepository.deleteCurrentAuthUser(credentialManager)
            if (authDeleteResult.isFailure) {
                val err = authDeleteResult.exceptionOrNull()
                _actionStatus.value = ProfileActionStatus.Message(
                    text = err?.localizedMessage
                        ?: "Dados do perfil removidos. Para excluir a conta Google do login, entre novamente e repita a ação.",
                    isError = true,
                )
            }
        }
    }

    fun clearMessage() {
        if (_actionStatus.value is ProfileActionStatus.Message) {
            _actionStatus.value = ProfileActionStatus.Idle
        }
    }

    private companion object {
        const val TAG = "ProfileVM"
    }
}
