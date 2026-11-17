package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

/**
 * Gerencia autenticação exclusivamente com Google Sign-In via Jetpack Credential Manager
 * e Firebase Authentication.
 */
class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
) {
    fun currentUser(): FirebaseUser? = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Tentativa silenciosa no início do app usando exclusivamente GetGoogleIdOption.
     */
    suspend fun attemptSilentSignIn(
        context: Context,
        credentialManager: CredentialManager,
    ): FirebaseUser? {
        auth.currentUser?.let { return it }
        val clientId = resolveWebClientId(context) ?: return null
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credential = credentialManager.getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val result = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(idToken, null),
                ).await()
                result.user
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google Sign-In unavailable: ${e.message}")
            null
        }
    }

    /**
     * Login interativo usando exclusivamente GetSignInWithGoogleOption.
     */
    suspend fun signInWithGoogleInteractive(
        activity: Activity,
        credentialManager: CredentialManager,
    ): GoogleSignInOutcome {
        val clientId = resolveWebClientId(activity)
            ?: return GoogleSignInOutcome.Error(
                "Configuração de login Google ausente (default_web_client_id).",
            )

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(token, null),
                ).await()
                val user = authResult.user
                    ?: return GoogleSignInOutcome.Error("Sessão Google retornou usuário nulo.")
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Error("Tipo de credencial inesperado recebido.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInOutcome.Error(e.localizedMessage ?: "Falha ao entrar com Google.")
        }
    }

    /**
     * Encerra a sessão limpando o Credential Manager antes do FirebaseAuth.signOut()
     * dentro de NonCancellable para evitar vazamento de listeners.
     */
    suspend fun signOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    /**
     * Remove a conta no Firebase Auth (LGPD) após limpeza de credenciais.
     */
    suspend fun deleteCurrentAuthUser(credentialManager: CredentialManager): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(IllegalStateException("Nenhum usuário autenticado."))
            user.delete().await()
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state after delete: ${e.message}")
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete Firebase Auth user", e)
            Result.failure(e)
        }
    }

    private fun resolveWebClientId(context: Context): String? =
        try {
            context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }

    private companion object {
        const val TAG = "Auth"
    }
}
