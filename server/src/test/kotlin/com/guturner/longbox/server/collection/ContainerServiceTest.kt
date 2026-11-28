package com.guturner.longbox.server.collection

import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.server.events.ContainerEvent
import com.guturner.longbox.server.events.EntryEvent
import com.guturner.longbox.server.events.RecordingContainerEventPublisher
import com.guturner.longbox.server.events.RecordingEventPublisher
import com.guturner.longbox.server.persistence.ContainerStore
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContainerServiceTest {

    private val userId = UUID.randomUUID()

    private class FakeContainerStore : ContainerStore {
        val containers = mutableMapOf<UUID, Container>()
        val entries = mutableMapOf<UUID, CollectionEntry>()

        override suspend fun list(userId: UUID): List<Container> = containers.values.toList()

        override suspend fun find(userId: UUID, containerId: UUID): Container? = containers[containerId]

        override suspend fun insert(userId: UUID, containerId: UUID, name: String, kind: ContainerKind): Container {
            val c = Container(ContainerId(containerId.toString()), name, kind, 0, Instant.now().toEpochMilli())
            containers[containerId] = c
            return c
        }

        override suspend fun rename(userId: UUID, containerId: UUID, newName: String): Container? {
            val existing = containers[containerId] ?: return null
            val updated = existing.copy(name = newName)
            containers[containerId] = updated
            return updated
        }

        override suspend fun delete(userId: UUID, containerId: UUID): Boolean =
            containers.remove(containerId) != null

        override suspend fun moveEntry(userId: UUID, entryId: UUID, containerId: UUID): CollectionEntry? {
            val container = containers[containerId] ?: return null
            val entry = entries[entryId] ?: CollectionEntry(
                EntryId(entryId.toString()), "code", EntryState.IN_STACK, null, null,
                Instant.now().toEpochMilli(),
            )
            val newState = if (container.kind != ContainerKind.STACK) EntryState.IN_BOX else EntryState.IN_STACK
            val updated = entry.copy(
                state = newState,
                containerId = ContainerId(containerId.toString()),
                containerName = container.name,
            )
            entries[entryId] = updated
            return updated
        }

        override suspend fun removeFromContainer(userId: UUID, entryId: UUID): CollectionEntry? {
            val entry = entries[entryId] ?: CollectionEntry(
                EntryId(entryId.toString()), "code", EntryState.IN_STACK, null, null,
                Instant.now().toEpochMilli(),
            )
            val updated = entry.copy(state = EntryState.IN_STACK, containerId = null, containerName = null)
            entries[entryId] = updated
            return updated
        }
    }

    private fun buildService(
        store: FakeContainerStore,
        entryEvents: RecordingEventPublisher,
        containerEvents: RecordingContainerEventPublisher,
    ) = ContainerService(store, entryEvents, containerEvents)

    @Test
    fun testCreateContainerPublishesCreatedEvent() = runTest {
        val store = FakeContainerStore()
        val entryEvents = RecordingEventPublisher()
        val containerEvents = RecordingContainerEventPublisher()
        val service = buildService(store, entryEvents, containerEvents)

        val result = service.create(userId, "My Stack", ContainerKind.STACK)

        assertIs<Outcome.Success<*>>(result)
        assertEquals(1, containerEvents.recorded.size)
        val event = assertIs<ContainerEvent.Created>(containerEvents.recorded[0])
        assertEquals("My Stack", event.name)
        assertEquals("STACK", event.kind)
    }

    @Test
    fun testRenameContainerPublishesRenamedEvent() = runTest {
        val store = FakeContainerStore()
        val entryEvents = RecordingEventPublisher()
        val containerEvents = RecordingContainerEventPublisher()
        val service = buildService(store, entryEvents, containerEvents)

        val created = service.create(userId, "Old Name", ContainerKind.SHORT_BOX)
        assertIs<Outcome.Success<*>>(created)
        containerEvents.recorded.clear()

        val containerId = (created as Outcome.Success).value.id.value
        val result = service.rename(userId, containerId, "New Name")

        assertIs<Outcome.Success<*>>(result)
        assertEquals(1, containerEvents.recorded.size)
        val event = assertIs<ContainerEvent.Renamed>(containerEvents.recorded[0])
        assertEquals("Old Name", event.oldName)
        assertEquals("New Name", event.newName)
    }

    @Test
    fun testDeleteContainerPublishesDeletedEvent() = runTest {
        val store = FakeContainerStore()
        val entryEvents = RecordingEventPublisher()
        val containerEvents = RecordingContainerEventPublisher()
        val service = buildService(store, entryEvents, containerEvents)

        val created = service.create(userId, "To Delete", ContainerKind.STACK)
        assertIs<Outcome.Success<*>>(created)
        containerEvents.recorded.clear()

        val containerId = (created as Outcome.Success).value.id.value
        val result = service.delete(userId, containerId)

        assertIs<Outcome.Success<*>>(result)
        assertEquals(1, containerEvents.recorded.size)
        assertIs<ContainerEvent.Deleted>(containerEvents.recorded[0])
    }

    @Test
    fun testMoveEntryPublishesMovedToContainerEvent() = runTest {
        val store = FakeContainerStore()
        val entryEvents = RecordingEventPublisher()
        val containerEvents = RecordingContainerEventPublisher()
        val service = buildService(store, entryEvents, containerEvents)

        val created = service.create(userId, "Physical Box", ContainerKind.LONG_BOX)
        assertIs<Outcome.Success<*>>(created)
        val containerId = (created as Outcome.Success).value.id.value

        val entryId = UUID.randomUUID().toString()
        val result = service.moveEntry(userId, entryId, containerId)

        assertIs<Outcome.Success<*>>(result)
        assertEquals(1, entryEvents.recorded.size)
        val event = assertIs<EntryEvent.MovedToContainer>(entryEvents.recorded[0])
        assertEquals(UUID.fromString(containerId), event.containerId)
    }

    @Test
    fun testRemoveFromContainerPublishesRemovedFromContainerEvent() = runTest {
        val store = FakeContainerStore()
        val entryEvents = RecordingEventPublisher()
        val containerEvents = RecordingContainerEventPublisher()
        val service = buildService(store, entryEvents, containerEvents)

        val entryId = UUID.randomUUID().toString()
        val result = service.removeFromContainer(userId, entryId)

        assertIs<Outcome.Success<*>>(result)
        assertEquals(1, entryEvents.recorded.size)
        assertIs<EntryEvent.RemovedFromContainer>(entryEvents.recorded[0])
    }
}
