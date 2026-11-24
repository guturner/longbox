package com.guturner.longbox.server.metron

import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.server.config.MetronConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Thin, rate-limited client for https://metron.cloud/api/.
 *  - Credentials go in the Authorization header (HTTP Basic), never in URLs.
 *  - Pagination links are only followed if they stay on metron.cloud (OWASP API10:2023,
 *    unsafe consumption of APIs).
 */
class MetronClient(
    private val config: MetronConfig,
    private val limiter: MetronRateLimiter,
    private val http: HttpClient = defaultHttpClient(),
) {
    private val log = LoggerFactory.getLogger(MetronClient::class.java)

    suspend fun issuesByUpc(code: String): Outcome<List<MetronIssueListItem>> =
        getList("issue/", mapOf("upc" to code), maxPages = 1)

    suspend fun issue(id: Long): Outcome<MetronIssue?> = getOne("issue/$id/")

    suspend fun creator(id: Long): Outcome<MetronCreator?> = getOne("creator/$id/")

    suspend fun arc(id: Long): Outcome<MetronArc?> = getOne("arc/$id/")

    suspend fun releases(from: String, to: String, publisherName: String): Outcome<List<MetronIssueListItem>> =
        getList(
            "issue/",
            mapOf(
                "store_date_range_after" to from,
                "store_date_range_before" to to,
                "publisher_name" to publisherName,
            ),
            maxPages = 5,
        )

    private suspend inline fun <reified T> getOne(path: String): Outcome<T?> =
        when (val response = send(Url(BASE + path))) {
            is Outcome.Failure -> response
            is Outcome.Success -> when (response.value.status) {
                HttpStatusCode.NotFound -> Outcome.Success(null)
                else -> Outcome.Success(response.value.body<T>())
            }
        }

    private suspend fun getList(
        path: String,
        params: Map<String, String>,
        maxPages: Int,
    ): Outcome<List<MetronIssueListItem>> {
        var url: Url? = URLBuilder(BASE + path).apply {
            params.forEach { (k, v) -> parameters.append(k, v) }
        }.build()
        val results = mutableListOf<MetronIssueListItem>()
        var pages = 0
        while (url != null && pages < maxPages) {
            val response = send(url)
            if (response is Outcome.Failure) return response
            val page = (response as Outcome.Success).value.body<MetronPage<MetronIssueListItem>>()
            results += page.results
            url = page.next?.let(::Url)?.takeIf { it.host == METRON_HOST && it.protocol.name == "https" }
            pages++
        }
        return Outcome.Success(results)
    }

    private suspend fun send(url: Url): Outcome<HttpResponse> {
        if (!limiter.acquire()) return Outcome.Failure(DomainError.RateLimited(retryAfterSeconds = SECONDS_PER_HOUR))
        val response = try {
            http.get(url) {
                basicAuth(config.username, config.password)
                header(HttpHeaders.UserAgent, config.userAgent)
                header(HttpHeaders.Accept, "application/json")
            }
        } catch (e: IOException) {
            log.warn("Metron request failed: {}", e.javaClass.simpleName)
            return Outcome.Failure(DomainError.Offline)
        }

        // Respect the server's remaining budget even on success, so we don't burn through the
        // window and then get a 429. X-RateLimit-Reset is epoch seconds.
        val remaining = response.headers["X-RateLimit-Remaining"]?.toLongOrNull()
        val reset = response.headers["X-RateLimit-Reset"]?.toLongOrNull()
        if (remaining != null && remaining == 0L && reset != null) {
            val retryAfterSeconds = (reset - System.currentTimeMillis() / 1_000).coerceAtLeast(1L)
            limiter.backOff(retryAfterSeconds)
        }

        return when {
            response.status == HttpStatusCode.TooManyRequests -> {
                val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull() ?: 60
                limiter.backOff(retryAfter)
                Outcome.Failure(DomainError.RateLimited(retryAfter))
            }
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden -> {
                log.error("Metron rejected our credentials (HTTP {}); stopping retries", response.status.value)
                Outcome.Failure(DomainError.Unauthorized)
            }
            response.status.value in 200..299 || response.status == HttpStatusCode.NotFound ->
                Outcome.Success(response)
            else -> Outcome.Failure(DomainError.Unexpected("Metron HTTP ${response.status.value}"))
        }
    }

    companion object {
        private const val METRON_HOST = "metron.cloud"
        private const val BASE = "https://$METRON_HOST/api/"
        private const val SECONDS_PER_HOUR = 3_600L

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun defaultHttpClient() = HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                // OkHttp silently retries failed connections by default; disable so timeouts
                // and connection errors surface immediately rather than doubling requests.
                config { retryOnConnectionFailure(false) }
            }
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
        }
    }
}
