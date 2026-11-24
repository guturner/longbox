package com.guturner.longbox.server.api

import com.guturner.longbox.contract.API_V1
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.copyTo

/**
 * The web client loads covers through this proxy because the catalog's image CDN may not send
 * CORS headers. Strict host allow-list to avoid SSRF (OWASP API7:2023); only images, size-capped.
 * Android and desktop load image URLs directly.
 */
fun Route.imageProxyRoutes(http: HttpClient, allowedHosts: Set<String>) {
    authenticate(SESSION_AUTH) {
        get("$API_V1/image") {
            val src = call.request.queryParameters["src"]?.let { runCatching { Url(it) }.getOrNull() }
            if (src == null || src.protocol.name != "https" || src.host !in allowedHosts || src.port != 443) {
                return@get call.respond(HttpStatusCode.BadRequest)
            }
            val upstream = http.get(src)
            val type = upstream.contentType()
            val length = upstream.contentLength()
            if (upstream.status != HttpStatusCode.OK || type == null || !type.match(ContentType.Image.Any) ||
                (length != null && length > MAX_IMAGE_BYTES)
            ) {
                return@get call.respond(HttpStatusCode.BadGateway)
            }
            call.response.header(HttpHeaders.CacheControl, "private, max-age=604800, immutable")
            call.respondBytesWriter(contentType = type, contentLength = length) {
                upstream.bodyAsChannel().copyTo(this, limit = MAX_IMAGE_BYTES)
            }
        }
    }
}

private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
