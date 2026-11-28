package com.guturner.longbox.data.repository

import com.guturner.longbox.contract.API_V1
import com.guturner.longbox.contract.ContainerDto
import com.guturner.longbox.contract.ContainerListResponse
import com.guturner.longbox.contract.CollectionEntryDto
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.contract.CreateContainerRequest
import com.guturner.longbox.contract.MoveToContainerRequest
import com.guturner.longbox.contract.RenameContainerRequest
import com.guturner.longbox.data.local.CachedContainers
import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.data.local.StoreKeys
import com.guturner.longbox.data.remote.LongboxApi
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.ContainerRepository
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Offline-first container repository.
 *
 * Container creation is offline-capable; rename, delete, move, and remove-from-container
 * require the server to be reachable (they're low-frequency operations during shelf organisation).
 */
class OfflineFirstContainerRepository(
    private val api: LongboxApi,
    private val store: KeyValueStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) : ContainerRepository {

    private val json = api.json
    private val cache = MutableStateFlow<CachedContainers?>(null)
    private val cacheMutex = Mutex()

    init {
        scope.launch {
            cache.value = store.read(StoreKeys.CONTAINERS)
                ?.let { runCatching { json.decodeFromString(CachedContainers.serializer(), it) }.getOrNull() }
        }
    }

    override fun observeContainers(): Flow<List<Container>> =
        cache.asStateFlow().map { cached ->
            cached?.containers.orEmpty().mapNotNull(ContractMapper::asContainer)
        }

    override suspend fun refresh(): Outcome<Unit> {
        val result = api.get<ContainerListResponse> { url.takeFrom("$API_V1/containers") }
        return when (result) {
            is Outcome.Success -> {
                persistCache(CachedContainers(result.value.containers, nowMillis()))
                Outcome.Success(Unit)
            }
            is Outcome.Failure -> result
        }
    }

    override suspend fun createContainer(
        name: String,
        kind: ContainerKind,
        clientMutationId: String?,
    ): Outcome<Container> {
        val result = api.send {
            method = HttpMethod.Post
            url.takeFrom("$API_V1/containers")
            contentType(ContentType.Application.Json)
            setBody(CreateContainerRequest(name, kind.name, clientMutationId))
        }
        return when (result) {
            is Outcome.Success -> {
                val dto = try { result.value.body<ContainerDto>() }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        return Outcome.Failure(DomainError.Unexpected("Unexpected server response"))
                    }
                cacheMutex.withLock {
                    cache.value?.let { persistCache(it.copy(containers = it.containers + dto)) }
                        ?: persistCache(CachedContainers(listOf(dto), nowMillis()))
                }
                ContractMapper.asContainer(dto)?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(DomainError.Unexpected("invalid container kind: ${dto.kind}"))
            }
            is Outcome.Failure -> result
        }
    }

    override suspend fun renameContainer(id: ContainerId, newName: String): Outcome<Container> {
        val result = api.send {
            method = HttpMethod.Patch
            url.takeFrom("$API_V1/containers/${id.value}")
            contentType(ContentType.Application.Json)
            setBody(RenameContainerRequest(newName))
        }
        return when (result) {
            is Outcome.Success -> {
                val dto = try { result.value.body<ContainerDto>() }
                    catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        return Outcome.Failure(DomainError.Unexpected("Unexpected server response"))
                    }
                cacheMutex.withLock {
                    cache.value?.let { cached ->
                        persistCache(cached.copy(containers = cached.containers.map { if (it.id == id.value) dto else it }))
                    }
                }
                ContractMapper.asContainer(dto)?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(DomainError.Unexpected("invalid container kind: ${dto.kind}"))
            }
            is Outcome.Failure -> result
        }
    }

    override suspend fun deleteContainer(id: ContainerId): Outcome<Unit> {
        val result = api.send {
            method = HttpMethod.Delete
            url.takeFrom("$API_V1/containers/${id.value}")
        }
        return when (result) {
            is Outcome.Success -> {
                cacheMutex.withLock {
                    cache.value?.let { persistCache(it.copy(containers = it.containers.filterNot { c -> c.id == id.value })) }
                }
                Outcome.Success(Unit)
            }
            is Outcome.Failure -> result
        }
    }

    override suspend fun moveToContainer(entryId: EntryId, containerId: ContainerId): Outcome<CollectionEntry> {
        val result = api.send {
            method = HttpMethod.Post
            url.takeFrom("$API_V1/containers/${containerId.value}/entries")
            contentType(ContentType.Application.Json)
            setBody(MoveToContainerRequest(entryId.value))
        }
        return when (result) {
            is Outcome.Success -> {
                val dto = result.value.body<CollectionEntryDto>()
                ContractMapper.asEntry(dto)?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(DomainError.Unexpected("invalid entry response"))
            }
            is Outcome.Failure -> result
        }
    }

    override suspend fun removeFromContainer(entryId: EntryId): Outcome<CollectionEntry> {
        // Server route: DELETE /containers/{containerId}/entries/{id}
        // The containerId segment is required by the router but ignored by the service (only entryId matters).
        // We use "_" as a placeholder since we don't track it on the client side.
        val result = api.send {
            method = HttpMethod.Delete
            url.takeFrom("$API_V1/containers/_/entries/${entryId.value}")
        }
        return when (result) {
            is Outcome.Success -> {
                // 204 No Content: the entry is now unorganised. Build a minimal success entry.
                // The collection repo will serve the full updated entry on the next sync.
                Outcome.Success(
                    com.guturner.longbox.domain.model.CollectionEntry(
                        id = entryId,
                        scannedCode = "",
                        state = com.guturner.longbox.domain.model.EntryState.IN_STACK,
                        comic = null,
                        ownedCoverName = null,
                        addedAtEpochMillis = nowMillis(),
                    ),
                )
            }
            is Outcome.Failure -> result
        }
    }

    private suspend fun persistCache(value: CachedContainers) {
        store.write(StoreKeys.CONTAINERS, json.encodeToString(CachedContainers.serializer(), value))
        cache.value = value
    }
}
