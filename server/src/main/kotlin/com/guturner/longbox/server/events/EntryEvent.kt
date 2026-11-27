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
    // MovedToContainer added when FEAT-15 wires containers
}

/** Swappable: production = SharedFlowEntryEventBus, tests = RecordingEventPublisher. */
fun interface EntryEventPublisher {
    suspend fun publish(event: EntryEvent)
}

class SharedFlowEntryEventBus : EntryEventPublisher {
    private val _events = MutableSharedFlow<EntryEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<EntryEvent> = _events.asSharedFlow()
    override suspend fun publish(event: EntryEvent) { _events.emit(event) }
}

class RecordingEventPublisher : EntryEventPublisher {
    val recorded = mutableListOf<EntryEvent>()
    override suspend fun publish(event: EntryEvent) { recorded += event }
}
