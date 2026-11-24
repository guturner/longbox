package com.guturner.longbox.desktop

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * OAuth 2.0 for native apps (RFC 8252): authorization code flow in the *system browser*
 * (no embedded web view, §8.12), redirect to a loopback IP literal on an ephemeral port (§7.3),
 * and PKCE with S256 (§6; RFC 9700 §2.1.1). Returns Google's ID token for the Longbox server.
 *
 * Google issues "Desktop app" clients a client_secret that must be sent but, per RFC 8252 §8.5,
 * is not treated as confidential.
 */
class GoogleLoopbackSignIn(
    private val clientId: String,
    private val clientSecret: String,
) {
    private val random = SecureRandom()

    suspend fun requestIdToken(): String? = withContext(Dispatchers.IO) {
        val verifier = randomUrlSafe(48)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val state = randomUrlSafe(24)
        val code = CompletableDeferred<String?>()

        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val params = exchange.requestURI.rawQuery.orEmpty().split('&')
                .mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }
                .associate { (k, v) -> k to java.net.URLDecoder.decode(v, Charsets.UTF_8) }
            val ok = params["state"] == state && params["code"] != null
            val body = (if (ok) "Signed in. You can close this tab and return to Longbox." else "Sign-in failed.").toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            code.complete(if (ok) params["code"] else null)
        }
        server.start()
        val redirectUri = "http://127.0.0.1:${server.address.port}"

        try {
            val authUrl = URLBuilder("https://accounts.google.com/o/oauth2/v2/auth").apply {
                parameters.append("client_id", clientId)
                parameters.append("redirect_uri", redirectUri)
                parameters.append("response_type", "code")
                parameters.append("scope", "openid email profile")
                parameters.append("code_challenge", challenge)
                parameters.append("code_challenge_method", "S256")
                parameters.append("state", state)
                parameters.append("prompt", "select_account")
            }.buildString()
            Desktop.getDesktop().browse(URI(authUrl))

            val authCode = withTimeoutOrNull(5 * 60_000L) { code.await() } ?: return@withContext null
            exchange(authCode, verifier, redirectUri)
        } finally {
            server.stop(0)
        }
    }

    private suspend fun exchange(code: String, verifier: String, redirectUri: String): String? =
        HttpClient(OkHttp).use { http ->
            val response = http.submitForm(
                url = "https://oauth2.googleapis.com/token",
                formParameters = Parameters.build {
                    append("code", code)
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("code_verifier", verifier)
                    append("redirect_uri", redirectUri)
                    append("grant_type", "authorization_code")
                },
            )
            if (!response.status.isSuccess()) return null
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["id_token"]?.jsonPrimitive?.content
        }

    private fun randomUrlSafe(bytes: Int) = base64Url(ByteArray(bytes).also(random::nextBytes))
    private fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
