package com.guturner.longbox.server.api

import com.guturner.longbox.contract.API_V1
import com.guturner.longbox.contract.CollectionResponse
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.contract.ErrorDto
import com.guturner.longbox.contract.ReleasesResponse
import com.guturner.longbox.contract.ScanRequest
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.repository.ComicCatalog
import com.guturner.longbox.server.collection.CollectionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

val API_RATE_LIMIT = RateLimitName("api")

fun Route.collectionRoutes(collection: CollectionService, catalog: ComicCatalog) {
    authenticate(SESSION_AUTH) {
        rateLimit(API_RATE_LIMIT) {
            route("$API_V1/collection") {
                get {
                    val entries = collection.list(call.userId())
                    call.respond(CollectionResponse(entries.map(ContractMapper::asEntryDto)))
                }

                post("scans") {
                    val request = call.receive<ScanRequest>()
                    when (val result = collection.addScan(call.userId(), request.code, request.addOn, request.clientMutationId, request.scannedAtEpochMillis)) {
                        is Outcome.Success -> call.respond(HttpStatusCode.Accepted, ContractMapper.asEntryDto(result.value))
                        is Outcome.Failure -> call.respondError(result.error)
                    }
                }

                delete("{entryId}") {
                    when (val result = collection.remove(call.userId(), call.parameters["entryId"].orEmpty())) {
                        is Outcome.Success -> call.respond(HttpStatusCode.NoContent)
                        is Outcome.Failure -> call.respondError(result.error)
                    }
                }
            }

            route(API_V1) {
                get("comics/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respondError(DomainError.NotFound)
                    call.respondOutcome(catalog.comic(ComicId(id))) { ContractMapper.asComicDto(it) }
                }
                get("creators/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respondError(DomainError.NotFound)
                    call.respondOutcome(catalog.creator(CreatorId(id))) { ContractMapper.asCreatorDto(it) }
                }
                get("arcs/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respondError(DomainError.NotFound)
                    call.respondOutcome(catalog.storyArc(StoryArcId(id))) { ContractMapper.asStoryArcDto(it) }
                }
                get("releases") {
                    val from = call.request.queryParameters["from"]?.let(CalendarDate::parse)
                    val to = call.request.queryParameters["to"]?.let(CalendarDate::parse)
                    if (from == null || to == null || to < from || to.epochDay - from.epochDay > 13) {
                        return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("bad_request", "from/to must be ISO dates at most 2 weeks apart"))
                    }
                    call.respondOutcome(catalog.releases(from, to)) { comics ->
                        ReleasesResponse(from.toString(), to.toString(), comics.map(ContractMapper::asComicDto))
                    }
                }
            }
        }
    }
}

fun ApplicationCall.userId(): UUID = principal<UserPrincipal>()!!.userId

suspend inline fun <T, reified R : Any> ApplicationCall.respondOutcome(outcome: Outcome<T>, map: (T) -> R) =
    when (outcome) {
        is Outcome.Success -> respond(map(outcome.value))
        is Outcome.Failure -> respondError(outcome.error)
    }

suspend fun ApplicationCall.respondError(error: DomainError) {
    val (status, body) = when (error) {
        is DomainError.InvalidBarcode -> HttpStatusCode.UnprocessableEntity to ErrorDto("invalid_barcode", error.reason.message)
        DomainError.NotFound -> HttpStatusCode.NotFound to ErrorDto("not_found", "Not found")
        DomainError.Unauthorized -> HttpStatusCode.Unauthorized to ErrorDto("unauthorized", "Please sign in")
        is DomainError.RateLimited -> HttpStatusCode.TooManyRequests to
            ErrorDto("rate_limited", "Catalog is busy, try again shortly", error.retryAfterSeconds)
        DomainError.Offline -> HttpStatusCode.BadGateway to ErrorDto("upstream_unavailable", "Catalog is unreachable")
        // Never echo internal detail to clients (OWASP Error Handling Cheat Sheet).
        is DomainError.Unexpected -> HttpStatusCode.BadGateway to ErrorDto("upstream_error", "Catalog error")
    }
    respond(status, body)
}
