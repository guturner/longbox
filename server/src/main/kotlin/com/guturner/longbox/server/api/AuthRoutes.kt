package com.guturner.longbox.server.api

import com.guturner.longbox.contract.API_V1
import com.guturner.longbox.contract.ErrorDto
import com.guturner.longbox.contract.GoogleSignInRequest
import com.guturner.longbox.contract.MeResponse
import com.guturner.longbox.contract.RefreshRequest
import com.guturner.longbox.contract.TokenResponse
import com.guturner.longbox.server.auth.IssuedTokens
import com.guturner.longbox.server.auth.SignInResult
import com.guturner.longbox.server.auth.TokenService
import com.guturner.longbox.server.persistence.AuthStore
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.http.auth.HttpAuthHeader

const val ACCESS_COOKIE = "lb_access"
const val REFRESH_COOKIE = "lb_refresh"
val AUTH_RATE_LIMIT = RateLimitName("auth")

fun Route.authRoutes(tokens: TokenService, users: AuthStore) {
    route("$API_V1/auth") {
        rateLimit(AUTH_RATE_LIMIT) {
            post("google") {
                val request = call.receive<GoogleSignInRequest>()
                if (request.idToken.length > MAX_ID_TOKEN_LENGTH) {
                    return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("bad_request", "Token too large"))
                }
                when (val result = tokens.signInWithGoogle(request.idToken)) {
                    SignInResult.InvalidToken ->
                        call.respond(HttpStatusCode.Unauthorized, ErrorDto("invalid_token", "Google sign-in could not be verified"))
                    SignInResult.NotAllowed ->
                        call.respond(HttpStatusCode.Forbidden, ErrorDto("not_allowed", "This account is not allowed on this server"))
                    is SignInResult.Success ->
                        call.respondTokens(result.tokens, asCookies = request.client == "web")
                }
            }

            post("refresh") {
                val fromCookie = call.request.cookies[REFRESH_COOKIE]
                val token = fromCookie ?: call.receive<RefreshRequest>().refreshToken
                val issued = tokens.refresh(token)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorDto("invalid_token", "Please sign in again"))
                call.respondTokens(issued, asCookies = fromCookie != null)
            }
        }

        post("logout") {
            val token = call.request.cookies[REFRESH_COOKIE]
                ?: call.request.cookies[ACCESS_COOKIE]
                ?: (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)?.blob
            token?.let { tokens.signOut(it) }
            call.clearAuthCookies()
            call.respond(HttpStatusCode.NoContent)
        }

        authenticate(SESSION_AUTH) {
            get("me") {
                val user = users.user(call.principal<UserPrincipal>()!!.userId)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(MeResponse(user.email, user.displayName))
            }
        }
    }
}

private suspend fun ApplicationCall.respondTokens(tokens: IssuedTokens, asCookies: Boolean) {
    if (asCookies) {
        // OWASP Session Management: Secure + HttpOnly + SameSite=Strict, narrowest useful Path.
        response.cookies.append(authCookie(ACCESS_COOKIE, tokens.accessToken, tokens.accessExpiresInSeconds, "/api"))
        response.cookies.append(authCookie(REFRESH_COOKIE, tokens.refreshToken, tokens.refreshExpiresInSeconds, "$API_V1/auth"))
        respond(HttpStatusCode.NoContent)
    } else {
        respond(TokenResponse(tokens.accessToken, tokens.accessExpiresInSeconds, tokens.refreshToken))
    }
}

private fun ApplicationCall.clearAuthCookies() {
    response.cookies.append(authCookie(ACCESS_COOKIE, "", 0, "/api"))
    response.cookies.append(authCookie(REFRESH_COOKIE, "", 0, "$API_V1/auth"))
}

private fun authCookie(name: String, value: String, maxAgeSeconds: Long, path: String) = Cookie(
    name = name,
    value = value,
    maxAge = maxAgeSeconds.toInt(),
    path = path,
    secure = true,
    httpOnly = true,
    extensions = mapOf("SameSite" to "Strict"),
)

private const val MAX_ID_TOKEN_LENGTH = 4_096
