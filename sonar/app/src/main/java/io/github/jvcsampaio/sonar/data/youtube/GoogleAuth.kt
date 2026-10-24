package io.github.jvcsampaio.sonar.data.youtube

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Read-only access to the user's YouTube account through Google Play services.
 * The token only lists likes; downloads never use the account.
 */
class GoogleAuth(private val context: Context) {
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY)))
        .build()

    /** Interactive: the result may carry a PendingIntent for the consent screen. */
    suspend fun authorize(activity: Activity): AuthorizationResult =
        Identity.getAuthorizationClient(activity).authorize(request).await()

    fun resultFromIntent(data: Intent): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Background: a token if access was granted before, null if the user must sign in again. */
    suspend fun silentToken(): String? {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        return if (result.hasResolution()) null else result.accessToken
    }

    /** Drops a rejected token from the cache so the next request gets a fresh one. */
    suspend fun invalidate(token: String) = withContext(Dispatchers.IO) {
        runCatching { GoogleAuthUtil.clearToken(context, token) }
        Unit
    }

    companion object {
        const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"

        fun describe(error: Throwable): String = when (val code = (error as? ApiException)?.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "O Google recusou o app (erro 10). Confira o nome do pacote e o SHA-1 no cliente OAuth do Google Cloud."
            CommonStatusCodes.NETWORK_ERROR -> "Sem conexão com o Google. Tente de novo."
            CommonStatusCodes.CANCELED -> "Login cancelado."
            CommonStatusCodes.SIGN_IN_REQUIRED -> "Entre na sua conta Google para continuar."
            null -> error.message ?: "Falha no login do Google."
            else -> "Falha no login do Google (código $code)."
        }
    }
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
