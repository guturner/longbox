package com.guturner.longbox.server.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VerifiedGoogleUser(val subject: String, val email: String, val name: String?)

/** Port, so routes can be tested without calling Google. */
fun interface GoogleIdentityVerifier {
    suspend fun verify(idToken: String): VerifiedGoogleUser?
}

/**
 * Google's own verifier checks the JWT signature against Google's published keys and the
 * `aud`, `iss` and `exp` claims (developers.google.com/identity/sign-in/web/backend-auth).
 * We additionally require `email_verified`.
 */
class GoogleApiIdentityVerifier(clientIds: List<String>) : GoogleIdentityVerifier {

    private val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(clientIds)
        .build()

    override suspend fun verify(idToken: String): VerifiedGoogleUser? = withContext(Dispatchers.IO) {
        val token = runCatching { verifier.verify(idToken) }.getOrNull() ?: return@withContext null
        val payload = token.payload
        if (payload.emailVerified != true) return@withContext null
        VerifiedGoogleUser(
            subject = payload.subject,
            email = payload.email.lowercase(),
            name = payload["name"] as? String,
        )
    }
}
