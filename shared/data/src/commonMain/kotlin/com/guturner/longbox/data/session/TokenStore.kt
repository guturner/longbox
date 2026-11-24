package com.guturner.longbox.data.session

/** Tokens issued by the Longbox server to native clients. */
data class StoredTokens(val accessToken: String, val refreshToken: String)

/**
 * Platform secure storage (OWASP MASVS-STORAGE): Android Keystore, Windows Credential Manager.
 * The web client never sees tokens; it relies on HttpOnly cookies, so it uses [CookieSession].
 */
interface TokenStore {
    suspend fun load(): StoredTokens?
    suspend fun save(tokens: StoredTokens)
    suspend fun clear()
}

/** How this client authenticates to the server. */
sealed interface SessionTransport {
    data class Bearer(val store: TokenStore) : SessionTransport
    data object CookieSession : SessionTransport
}
