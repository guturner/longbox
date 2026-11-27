package com.guturner.longbox.server

import com.guturner.longbox.contract.CSRF_HEADER
import com.guturner.longbox.contract.ErrorDto
import com.guturner.longbox.domain.usecase.ResolveBarcodeUseCase
import com.guturner.longbox.server.api.collectionRoutes
import com.guturner.longbox.server.api.containerRoutes
import com.guturner.longbox.server.api.configureSecurity
import com.guturner.longbox.server.api.authRoutes
import com.guturner.longbox.server.api.imageProxyRoutes
import com.guturner.longbox.server.auth.GoogleApiIdentityVerifier
import com.guturner.longbox.server.auth.TokenService
import com.guturner.longbox.server.catalog.CachedMetronCatalog
import com.guturner.longbox.server.collection.CollectionService
import com.guturner.longbox.server.collection.ContainerService
import com.guturner.longbox.server.collection.ScanWorker
import com.guturner.longbox.server.config.AppConfig
import com.guturner.longbox.server.metron.MetronClient
import com.guturner.longbox.server.metron.MetronRateLimiter
import com.guturner.longbox.server.events.AppEventBus
import com.guturner.longbox.server.persistence.AuthStore
import com.guturner.longbox.server.persistence.JdbcCollectionStore
import com.guturner.longbox.server.persistence.JdbcContainerStore
import com.guturner.longbox.server.persistence.Database
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.hours

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

/**
 * Composition root. Wiring is explicit constructor injection (as in queuehaven's controllers),
 * so the dependency graph is readable in one place and the inner rings never see a DI framework.
 */
fun Application.module(config: AppConfig) {
    val log = LoggerFactory.getLogger("Longbox")
    log.info("Starting with {}", config.metron) // toString() masks secrets

    val db = Database.connect(config.database)
    val metron = MetronClient(config.metron, MetronRateLimiter(config.metron.requestsPerMinute, config.metron.requestsPerDay))
    val catalog = CachedMetronCatalog(db, metron)
    val authStore = AuthStore(db)
    val tokens = TokenService(authStore, GoogleApiIdentityVerifier(config.auth.googleClientIds), config.auth)
    val collectionStore = JdbcCollectionStore(db)
    val containerStore = JdbcContainerStore(db)
    val eventBus = AppEventBus()
    val worker = ScanWorker(collectionStore, ResolveBarcodeUseCase(catalog), eventBus)
    val collection = CollectionService(collectionStore, worker, eventBus)
    val containerService = ContainerService(containerStore, eventBus, eventBus)

    val imageClient = HttpClient(OkHttp) {
        followRedirects = false // a redirect could point the proxy at an internal host
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
    }

    val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    worker.start(background)
    background.launch {
        while (true) {
            runCatching { authStore.deleteExpired(Instant.now()) }
            kotlinx.coroutines.delay(6.hours)
        }
    }
    monitor.subscribe(ApplicationStopped) {
        background.cancel()
        imageClient.close()
    }

    if (config.behindProxy) install(XForwardedHeaders)

    install(CallId) {
        generate { UUID.randomUUID().toString() }
        verify { it.length <= 64 }
    }
    install(CallLogging) {
        level = Level.INFO
        callIdMdc("call-id")
        // Log method, path and status only: never headers, cookies, or bodies (tokens live there).
        format { call -> "${call.request.local.method.value} ${call.request.path()} -> ${call.response.status()?.value}" }
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
    }
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto("bad_request", "Malformed request"))
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal", "Something went wrong"))
        }
    }
    if (config.allowedWebOrigins.isNotEmpty()) {
        // Only needed when the web app is served from a different origin (e.g. local dev server).
        install(CORS) {
            config.allowedWebOrigins.forEach { allowHost(it.substringAfter("://"), schemes = listOf(it.substringBefore("://"))) }
            allowCredentials = true
            allowMethod(HttpMethod.Delete)
            allowHeader(HttpHeaders.ContentType)
            allowHeader(CSRF_HEADER)
        }
    }

    configureSecurity(tokens)

    routing {
        // Clients probe this to decide between online and offline (outbox) mode, so it checks the DB too.
        get("/healthz") {
            if (collectionStore.ping()) call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
            else call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "database unavailable"))
        }
        authRoutes(tokens, authStore)
        collectionRoutes(collection, catalog)
        containerRoutes(containerService)
        imageProxyRoutes(imageClient, allowedHosts = IMAGE_HOSTS)

        // Serve the built Compose/Wasm web app from the same origin, so cookies can be SameSite=Strict.
        System.getenv("WEB_DIST_DIR")?.let { dir -> staticFiles("/", File(dir)) { default("index.html") } }
    }
}

/** Hosts the web client's image proxy may fetch from. Extend via IMAGE_HOSTS if Metron's CDN changes. */
private val IMAGE_HOSTS: Set<String> =
    (System.getenv("IMAGE_HOSTS") ?: "static.metron.cloud,metron.cloud")
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
