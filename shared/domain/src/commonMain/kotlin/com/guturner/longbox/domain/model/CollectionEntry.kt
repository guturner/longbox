package com.guturner.longbox.domain.model

data class EntryId(val value: String)

/** Where a scan is in its lookup. Lookups are queued server-side to respect Metron's rate limit. */
enum class LookupStatus {
    PENDING,
    RESOLVED,
    NOT_FOUND,
}

/**
 * One physical comic in the collection. Two copies of the same issue are two entries
 * (e.g. Cover A and a 1:25 variant), which is why the owned cover is tracked here.
 */
data class CollectionEntry(
    val id: EntryId,
    val scannedCode: String,
    val status: LookupStatus,
    val comic: Comic?,
    /** Name of the cover this copy has, when the barcode identifies a variant. */
    val ownedCoverName: String?,
    val addedAtEpochMillis: Long,
    /** Scanned while away from home and still waiting in the device outbox. */
    val isQueuedOnDevice: Boolean = false,
    val copyCount: Int = 1,
)
