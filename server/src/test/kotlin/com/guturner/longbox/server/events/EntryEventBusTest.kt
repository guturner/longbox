package com.guturner.longbox.server.events

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class EntryEventBusTest {

    private val id = UUID.randomUUID()
    private val at = Instant.EPOCH

    @Test
    fun testPublishIsReceived() = runTest {
        val bus = AppEventBus()
        val collected = mutableListOf<EntryEvent>()
        // UnconfinedTestDispatcher starts collecting immediately so the subscriber
        // is registered before publish() is called.
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.entryEvents.toList(collected)
        }
        bus.publish(EntryEvent.Scanned(id, at, "75960609558200111"))
        job.cancel()
        assertEquals(1, collected.size)
        val event = assertIs<EntryEvent.Scanned>(collected[0])
        assertEquals(id, event.entryId)
        assertEquals("75960609558200111", event.code)
    }

    @Test
    fun testContainerEventIsReceived() = runTest {
        val bus = AppEventBus()
        val collected = mutableListOf<ContainerEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            bus.containerEvents.toList(collected)
        }
        val containerId = UUID.randomUUID()
        bus.publish(ContainerEvent.Created(containerId, at, "My Stack", "STACK"))
        job.cancel()
        assertEquals(1, collected.size)
        val event = assertIs<ContainerEvent.Created>(collected[0])
        assertEquals(containerId, event.containerId)
        assertEquals("My Stack", event.name)
    }

    @Test
    fun testRecordingPublisherCaptures() = runTest {
        val publisher = RecordingEventPublisher()
        publisher.publish(EntryEvent.Scanned(id, at, "75960609558200111"))
        publisher.publish(EntryEvent.Removed(id, at))
        assertEquals(2, publisher.recorded.size)
        assertIs<EntryEvent.Scanned>(publisher.recorded[0])
        assertIs<EntryEvent.Removed>(publisher.recorded[1])
    }

    @Test
    fun testRecordingContainerPublisherCaptures() = runTest {
        val publisher = RecordingContainerEventPublisher()
        val containerId = UUID.randomUUID()
        publisher.publish(ContainerEvent.Created(containerId, at, "Comics Box A", "BOX"))
        publisher.publish(ContainerEvent.Deleted(containerId, at))
        assertEquals(2, publisher.recorded.size)
        assertIs<ContainerEvent.Created>(publisher.recorded[0])
        assertIs<ContainerEvent.Deleted>(publisher.recorded[1])
    }
}
