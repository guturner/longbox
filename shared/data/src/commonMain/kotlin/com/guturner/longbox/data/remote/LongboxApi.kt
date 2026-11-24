package com.guturner.longbox.data.remote

import com.guturner.longbox.contract.API_V1
import com.guturner.longbox.contract.CSRF_HEADER
import com.guturner.longbox.contract.ErrorDto
import com.guturner.longbox.contract.GoogleSignInRequest
import com.guturner.longbox.contract.RefreshRequest
import com.guturner.longbox.contract.TokenResponse
import com.guturner.longbox.data.session.SessionTransport
import com.guturner.longbox.data.session.StoredTokens
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The one place that knows HTTP. Native clients use bearer tokens with automatic refresh
 * (Ktor Auth plugin); the web client relies on same-origin HttpOnly cookies and refreshes on 401.
 */
class LongboxApi(
    engine: HttpClientEngine,
    private val baseUrl: String,
    private val transport: SessionTransport,
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Set by the session repository; invoked when the server rejects the session for good. */
    var onSessionExpired: (() -> Unit)? = null

    /** Unauthenticated client used for sign-in and refresh, so refresh can't recurse. */
    private val plain = HttpClient(engine) { common() }

    val http: HttpClient = HttpClient(engine) {
        common()
        if (transport is SessionTransport.Bearer) {
            install(Auth) {
                bearer {
                    loadTokens { transport.store.load()?.let { BearerTokens(it.accessToken, it.refreshToken) } }
                    refreshTokens {
                        val refresh = oldTokens?.refreshToken ?: return@refreshTokens null
                        val response = plain.post("$API_V1/auth/refresh") {
                            markAsRefreshTokenRequest()
                            contentType(ContentType.Application.Json)
                            setBody(RefreshRequest(refresh))
                        }
                        if (!response.status.isSuccess()) {
                            transport.store.clear()
                            return@refreshTokens null
                        }
                        val tokens = response.body<TokenResponse>()
                        transport.store.save(StoredTokens(tokens.accessToken, tokens.refreshToken))
                        BearerTokens(tokens.accessToken, tokens.refreshToken)
                    }
                    sendWithoutRequest { true }
                }
            }
        }
    }

    private fun HttpClientConfig<*>.common() {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 20_000
        }
        defaultRequest {
            url(baseUrl)
            header(CSRF_HEADER, "1")
        }
    }

    /**
     * True when the home server *and its database* answer. A quick timeout keeps the app snappy
     * when roaming, where the NAS's LAN address simply doesn't route.
     */
    suspend fun isServerReachable(): Boolean = try {
        plain.get("/healthz") { timeout { requestTimeoutMillis = PROBE_TIMEOUT_MS; connectTimeoutMillis = PROBE_TIMEOUT_MS } }
            .status == HttpStatusCode.OK
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        false
    }

    /** Sends a request, refreshing the cookie session once on 401 for the web client. */
    suspend fun send(block: HttpRequestBuilder.() -> Unit): Outcome<HttpResponse> = try {
        var response = http.request(block)
        if (response.status == HttpStatusCode.Unauthorized && transport is SessionTransport.CookieSession) {
            val refreshed = plain.post("$API_V1/auth/refresh")
            if (refreshed.status.isSuccess()) response = http.request(block)
        }
        if (response.status == HttpStatusCode.Unauthorized) onSessionExpired?.invoke()
        if (response.status.isSuccess()) Outcome.Success(response) else Outcome.Failure(asError(response))
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Outcome.Failure(DomainError.Offline)
    }

    suspend inline fun <reified T> get(crossinline block: HttpRequestBuilder.() -> Unit): Outcome<T> =
        when (val result = send { block() }) {
            is Outcome.Success -> Outcome.Success(result.value.body<T>())
            is Outcome.Failure -> result
        }

    suspend fun signIn(request: GoogleSignInRequest): HttpResponse =
        plain.post("$API_V1/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun logout(tokens: StoredTokens?) {
        runCatching {
            plain.post("$API_V1/auth/logout") {
                tokens?.let { header(HttpHeaders.Authorization, "Bearer ${it.refreshToken}") }
            }
        }
    }

    private suspend fun asError(response: HttpResponse): DomainError {
        val body = runCatching { response.body<ErrorDto>() }.getOrNull()
        return when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> DomainError.Unauthorized
            HttpStatusCode.NotFound -> DomainError.NotFound
            HttpStatusCode.TooManyRequests -> DomainError.RateLimited(body?.retryAfterSeconds)
            HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout -> DomainError.Offline
            else -> DomainError.Unexpected(body?.message ?: "HTTP ${response.status.value}")
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 3_000L
    }
}
