package com.guturner.longbox.server.api

import com.guturner.longbox.contract.API_V1
import com.guturner.longbox.contract.ContainerListResponse
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.contract.CreateContainerRequest
import com.guturner.longbox.contract.MoveToContainerRequest
import com.guturner.longbox.contract.RenameContainerRequest
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.server.collection.ContainerService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.containerRoutes(containers: ContainerService) {
    authenticate(SESSION_AUTH) {
        rateLimit(API_RATE_LIMIT) {
            route("$API_V1/containers") {
                get {
                    val list = containers.list(call.userId())
                    call.respond(ContainerListResponse(list.map(ContractMapper::asContainerDto)))
                }

                post {
                    val request = call.receive<CreateContainerRequest>()
                    val kind = ContainerKind.entries.firstOrNull { it.name == request.kind }
                        ?: return@post call.respondError(DomainError.Unexpected("invalid kind"))
                    when (val result = containers.create(call.userId(), request.name, kind, request.clientMutationId)) {
                        is Outcome.Success -> call.respond(HttpStatusCode.Created, ContractMapper.asContainerDto(result.value))
                        is Outcome.Failure -> call.respondError(result.error)
                    }
                }

                patch("{id}") {
                    val request = call.receive<RenameContainerRequest>()
                    when (val result = containers.rename(call.userId(), call.parameters["id"].orEmpty(), request.name)) {
                        is Outcome.Success -> call.respond(ContractMapper.asContainerDto(result.value))
                        is Outcome.Failure -> call.respondError(result.error)
                    }
                }

                delete("{id}") {
                    when (val result = containers.delete(call.userId(), call.parameters["id"].orEmpty())) {
                        is Outcome.Success -> call.respond(HttpStatusCode.NoContent)
                        is Outcome.Failure -> call.respondError(result.error)
                    }
                }

                post("{containerId}/entries") {
                    val request = call.receive<MoveToContainerRequest>()
                    when (val result = containers.moveEntry(call.userId(), request.entryId, call.parameters["containerId"].orEmpty())) {
                        is Outcome.Success -> call.respond(ContractMapper.asEntryDto(result.value))
                        is Outcome.Failure -> call.respondError(result.error)
                    }
                }

                delete("{containerId}/entries/{id}") {
                    when (val result = containers.removeFromContainer(call.userId(), call.parameters["id"].orEmpty())) {
                        is Outcome.Success -> call.respond(HttpStatusCode.NoContent)
                        is Outcome.Failure -> call.respondError(result.error)
                    }
                }
            }
        }
    }
}
