package com.guturner.longbox.server.auth

import com.guturner.longbox.server.config.AuthConfig
import com.guturner.longbox.server.persistence.AppUser
import com.guturner.longbox.server.persistence.AuthStore
import com.guturner.longbox.server.persistence.TokenKind
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class IssuedTokens(
    val accessToken: String,
    val accessExpiresInSeconds: Long,
    val refreshToken: String,
    val refreshExpiresInSeconds: Long,
)

sealed interface SignInResult {
    data class Success(val user: AppUser, val tokens: IssuedTokens) : SignInResult
    data object InvalidToken : SignInResult
    data object NotAllowed : SignInResult
}

/**
 * Opaque, random bearer tokens (256 bits from SecureRandom; OWASP asks for >= 64 bits of entropy)
 * stored as SHA-256 hashes. Short-lived access tokens plus rotating, single-use refresh tokens with
 * replay detection, as RFC 9700 §4.14 recommends for public clients.
 */
class TokenService(
    private val store: AuthStore,
    private val google: GoogleIdentityVerifier,
    private val config: AuthConfig,
    private val now: () -> Instant = Instant::now,
) {
    private val random = SecureRandom()

    suspend fun signInWithGoogle(idToken: String): SignInResult {
        val identity = google.verify(idToken) ?: return SignInResult.InvalidToken
        if (identity.email !in config.allowedEmails) return SignInResult.NotAllowed
        val user = store.upsertUser(identity.subject, identity.email, identity.name)
        // New token family per sign-in, i.e. a fresh session id after authentication (no fixation).
        return SignInResult.Success(user, issue(user.id, UUID.randomUUID()))
    }

    /** Returns null for unknown, expired, revoked or replayed refresh tokens. */
    suspend fun refresh(refreshToken: String): IssuedTokens? {
        val hash = hash(refreshToken)
        val stored = store.findToken(hash) ?: return null
        val at = now()
        if (stored.kind != TokenKind.REFRESH || stored.revokedAt != null || stored.expiresAt.isBefore(at)) return null

        if (!store.markUsed(hash, at)) {
            // A refresh token was presented twice: assume theft and kill the whole session family.
            store.revokeFamily(stored.familyId, at)
            return null
        }
        return issue(stored.userId, stored.familyId)
    }

    /** Resolves an access token to a user id. */
    suspend fun authenticate(accessToken: String): UUID? {
        if (accessToken.length > MAX_TOKEN_LENGTH) return null
        val stored = store.findToken(hash(accessToken)) ?: return null
        val valid = stored.kind == TokenKind.ACCESS && stored.revokedAt == null && stored.expiresAt.isAfter(now())
        return if (valid) stored.userId else null
    }

    suspend fun signOut(anyToken: String) {
        val stored = store.findToken(hash(anyToken)) ?: return
        store.revokeFamily(stored.familyId, now())
    }

    private suspend fun issue(userId: UUID, familyId: UUID): IssuedTokens {
        val access = newToken()
        val refresh = newToken()
        val at = now()
        store.insertToken(hash(access), userId, TokenKind.ACCESS, familyId, at.plusSeconds(config.accessTokenTtlSeconds))
        store.insertToken(hash(refresh), userId, TokenKind.REFRESH, familyId, at.plusSeconds(config.refreshTokenTtlSeconds))
        return IssuedTokens(access, config.accessTokenTtlSeconds, refresh, config.refreshTokenTtlSeconds)
    }

    private fun newToken(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))

    private companion object {
        const val MAX_TOKEN_LENGTH = 128
    }
}
