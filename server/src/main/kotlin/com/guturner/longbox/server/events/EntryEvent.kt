package com.guturner.longbox.server.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.util.UUID

sealed interface EntryEvent {
    val entryId: UUID
    val occurredAt: Instant

    data class Scanned(
        override val entryId: UUID,
        override val occurredAt: Instant,
        val code: String,
    ) : EntryEvent

    data class LookupSucceeded(
        override val entryId: UUID,
        override val occurredAt: Instant,
        val comicId: Long,
        val ownedCoverName: String?,
    ) : EntryEvent

    data class LookupFailed(
        override val entryId: UUID,
        override val occurredAt: Instant,
        val attempts: Int,
        /** false = will retry; true = exhausted retries or permanently unparseable code */
        val permanent: Boolean,
    ) : EntryEvent

    data class Removed(
        override val entryId: UUID,
        override val occurredAt: Instant,
    ) : EntryEvent

    data class MovedToContainer(
        override val entryId: UUID,
        override val occurredAt: Instant,
        val containerId: UUID,
        val containerKind: String,
    ) : EntryEvent

    data class RemovedFromContainer(
        override val entryId: UUID,
        override val occurredAt: Instant,
    ) : EntryEvent

    /** Fired when addScan hits the copy-count branch (not a new row). */
    data class CopyRescanned(
        override val entryId: UUID,
        override val occurredAt: Instant,
        val newCopyCount: Int,
    ) : EntryEvent
}

/** Swappable: production = AppEventBus, tests = RecordingEventPublisher. */
fun interface EntryEventPublisher {
    suspend fun publish(event: EntryEvent)
}

/** Combined event bus for entry and container events. */
class AppEventBus : EntryEventPublisher, ContainerEventPublisher {
    private val _entryEvents = MutableSharedFlow<EntryEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _containerEvents = MutableSharedFlow<ContainerEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val entryEvents: SharedFlow<EntryEvent> = _entryEvents.asSharedFlow()
    val containerEvents: SharedFlow<ContainerEvent> = _containerEvents.asSharedFlow()

    override suspend fun publish(event: EntryEvent) { _entryEvents.emit(event) }
    override suspend fun publish(event: ContainerEvent) { _containerEvents.emit(event) }
}

/** Kept for backward compatibility with existing tests; prefer AppEventBus in production. */
typealias SharedFlowEntryEventBus = AppEventBus

class RecordingEventPublisher : EntryEventPublisher {
    val recorded = mutableListOf<EntryEvent>()
    override suspend fun publish(event: EntryEvent) { recorded += event }
}
