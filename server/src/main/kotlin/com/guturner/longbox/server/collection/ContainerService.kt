package com.guturner.longbox.server.collection

import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.server.events.ContainerEvent
import com.guturner.longbox.server.events.ContainerEventPublisher
import com.guturner.longbox.server.events.EntryEvent
import com.guturner.longbox.server.events.EntryEventPublisher
import com.guturner.longbox.server.persistence.ContainerStore
import java.time.Instant
import java.util.UUID

class ContainerService(
    private val store: ContainerStore,
    private val entryEvents: EntryEventPublisher,
    private val containerEvents: ContainerEventPublisher,
    private val now: () -> Instant = Instant::now,
) {

    suspend fun list(userId: UUID): List<Container> = store.list(userId)

    suspend fun create(
        userId: UUID,
        name: String,
        kind: ContainerKind,
        clientMutationId: String? = null,
    ): Outcome<Container> {
        if (name.isBlank() || name.length > 100) {
            return Outcome.Failure(DomainError.Unexpected("name must be 1-100 chars"))
        }
        val id = UUID.randomUUID()
        return runCatching { store.insert(userId, id, name.trim(), kind) }
            .fold(
                onSuccess = { container ->
                    containerEvents.publish(ContainerEvent.Created(id, now(), container.name, kind.name))
                    Outcome.Success(container)
                },
                onFailure = { Outcome.Failure(DomainError.Unexpected(it.message ?: "insert failed")) },
            )
    }

    suspend fun rename(userId: UUID, containerId: String, newName: String): Outcome<Container> {
        val id = runCatching { UUID.fromString(containerId) }.getOrNull()
            ?: return Outcome.Failure(DomainError.NotFound)
        if (newName.isBlank() || newName.length > 100) {
            return Outcome.Failure(DomainError.Unexpected("name must be 1-100 chars"))
        }
        val existing = store.find(userId, id) ?: return Outcome.Failure(DomainError.NotFound)
        return runCatching { store.rename(userId, id, newName.trim()) }
            .fold(
                onSuccess = { updated ->
                    if (updated == null) return Outcome.Failure(DomainError.NotFound)
                    containerEvents.publish(ContainerEvent.Renamed(id, now(), existing.name, newName.trim()))
                    Outcome.Success(updated)
                },
                onFailure = { Outcome.Failure(DomainError.Unexpected(it.message ?: "rename failed")) },
            )
    }

    suspend fun delete(userId: UUID, containerId: String): Outcome<Unit> {
        val id = runCatching { UUID.fromString(containerId) }.getOrNull()
            ?: return Outcome.Failure(DomainError.NotFound)
        return if (store.delete(userId, id)) {
            containerEvents.publish(ContainerEvent.Deleted(id, now()))
            Outcome.Success(Unit)
        } else Outcome.Failure(DomainError.NotFound)
    }

    suspend fun moveEntry(userId: UUID, entryId: String, containerId: String): Outcome<CollectionEntry> {
        val eId = runCatching { UUID.fromString(entryId) }.getOrNull()
            ?: return Outcome.Failure(DomainError.NotFound)
        val cId = runCatching { UUID.fromString(containerId) }.getOrNull()
            ?: return Outcome.Failure(DomainError.NotFound)
        val entry = store.moveEntry(userId, eId, cId) ?: return Outcome.Failure(DomainError.NotFound)
        entryEvents.publish(
            EntryEvent.MovedToContainer(
                entryId = eId,
                occurredAt = now(),
                containerId = cId,
                containerKind = entry.state.name,
            ),
        )
        return Outcome.Success(entry)
    }

    suspend fun removeFromContainer(userId: UUID, entryId: String): Outcome<CollectionEntry> {
        val eId = runCatching { UUID.fromString(entryId) }.getOrNull()
            ?: return Outcome.Failure(DomainError.NotFound)
        val entry = store.removeFromContainer(userId, eId) ?: return Outcome.Failure(DomainError.NotFound)
        entryEvents.publish(EntryEvent.RemovedFromContainer(entryId = eId, occurredAt = now()))
        return Outcome.Success(entry)
    }
}
