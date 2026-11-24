package com.guturner.longbox.server.api

import com.guturner.longbox.contract.CSRF_HEADER
import com.guturner.longbox.contract.ErrorDto
import com.guturner.longbox.server.auth.TokenService
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.hsts.HSTS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.response.respond
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

const val SESSION_AUTH = "session"

data class UserPrincipal(val userId: UUID)

fun Application.configureSecurity(tokens: TokenService) {
    install(Authentication) {
        bearer(SESSION_AUTH) {
            realm = "longbox"
            // Native clients send Authorization: Bearer; the web client sends the HttpOnly cookie.
            authHeader { call ->
                call.request.parseAuthorizationHeader()
                    ?: call.request.cookies[ACCESS_COOKIE]?.let { HttpAuthHeader.Single("Bearer", it) }
            }
            authenticate { credential -> tokens.authenticate(credential.token)?.let(::UserPrincipal) }
        }
    }

    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
        register(API_RATE_LIMIT) {
            rateLimiter(limit = 600, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<UserPrincipal>()?.userId?.toString() ?: call.request.origin.remoteAddress }
        }
    }

    // Cookie-authenticated, state-changing requests must carry a custom header. Browsers can't add
    // custom headers cross-site without a CORS preflight, which we don't allow (OWASP CSRF cheat sheet).
    install(
        createApplicationPlugin("CsrfGuard") {
            onCall { call ->
                val unsafe = call.request.local.method !in setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)
                val cookieAuth = call.request.cookies[ACCESS_COOKIE] != null || call.request.cookies[REFRESH_COOKIE] != null
                if (unsafe && cookieAuth && call.request.headers[CSRF_HEADER] == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorDto("csrf", "Missing $CSRF_HEADER header"))
                }
            }
        },
    )

    install(HSTS) {
        maxAgeInSeconds = 31_536_000
        includeSubDomains = true
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("Referrer-Policy", "no-referrer")
        header("X-Frame-Options", "DENY")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
    }
}

/**
 * For the Compose/Wasm web client served from this origin. 'wasm-unsafe-eval' is required to
 * instantiate WebAssembly; the accounts.google.com entries are what Google Identity Services needs.
 */
private val CONTENT_SECURITY_POLICY = listOf(
    "default-src 'self'",
    "script-src 'self' 'wasm-unsafe-eval' https://accounts.google.com/gsi/client",
    "style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style",
    "frame-src https://accounts.google.com/gsi/",
    "connect-src 'self' https://accounts.google.com/gsi/",
    "img-src 'self' data: blob:",
    "font-src 'self' data:",
    "object-src 'none'",
    "base-uri 'none'",
    "frame-ancestors 'none'",
).joinToString("; ")
