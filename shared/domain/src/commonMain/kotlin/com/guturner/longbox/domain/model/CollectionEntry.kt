package com.guturner.longbox.domain.model

data class EntryId(val value: String)

/** Full lifecycle of a physical comic in the collection. */
enum class EntryState {
    PENDING,       // scanned, catalog lookup queued or in progress
    LOOKUP_FAILED, // not found in catalog (after retries exhausted)
    IN_STACK,      // catalog resolved, organized in a named stack
    IN_BOX,        // in a physical box (FEAT-15)
    DELETED,       // removed from collection
}

/**
 * One physical comic in the collection. Two copies of the same issue are two entries
 * (e.g. Cover A and a 1:25 variant), which is why the owned cover is tracked here.
 */
data class CollectionEntry(
    val id: EntryId,
    val scannedCode: String,
    val state: EntryState,
    val comic: Comic?,
    /** Name of the cover this copy has, when the barcode identifies a variant. */
    val ownedCoverName: String?,
    val addedAtEpochMillis: Long,
    /** Scanned while away from home and still waiting in the device outbox. */
    val isQueuedOnDevice: Boolean = false,
    val copyCount: Int = 1,
)
