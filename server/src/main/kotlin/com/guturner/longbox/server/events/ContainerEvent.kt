package com.guturner.longbox.server.events

import java.time.Instant
import java.util.UUID

sealed interface ContainerEvent {
    val containerId: UUID
    val occurredAt: Instant

    data class Created(
        override val containerId: UUID,
        override val occurredAt: Instant,
        val name: String,
        val kind: String,
    ) : ContainerEvent

    data class Renamed(
        override val containerId: UUID,
        override val occurredAt: Instant,
        val oldName: String,
        val newName: String,
    ) : ContainerEvent

    data class Deleted(
        override val containerId: UUID,
        override val occurredAt: Instant,
    ) : ContainerEvent
}

fun interface ContainerEventPublisher {
    suspend fun publish(event: ContainerEvent)
}

class RecordingContainerEventPublisher : ContainerEventPublisher {
    val recorded = mutableListOf<ContainerEvent>()
    override suspend fun publish(event: ContainerEvent) { recorded += event }
}
