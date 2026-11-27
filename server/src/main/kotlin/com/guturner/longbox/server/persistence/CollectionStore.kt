package com.guturner.longbox.server.persistence

import com.guturner.longbox.contract.ComicDto
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.LookupStatus
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class QueuedScan(val entryId: UUID, val code: String, val attempts: Int, val status: LookupStatus)

/**
 * Every query that touches a user's data takes the authenticated user id and filters on it,
 * which is the server-side object-level authorization check (OWASP API1:2023).
 */
class CollectionStore(
    private val db: Database,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun list(userId: UUID): List<CollectionEntry> = db.transaction {
        prepare(
            """
            select e.id, e.scanned_code, e.status, e.owned_cover_name, e.added_at, e.copy_count, i.payload::text as payload
            from collection_entry e left join catalog_issue i on i.id = e.issue_id
            where e.user_id = ?
            order by e.added_at
            """.trimIndent(),
            userId,
        ).queryList(::asEntry)
    }

    suspend fun find(userId: UUID, entryId: UUID): CollectionEntry? = db.transaction {
        prepare(
            """
            select e.id, e.scanned_code, e.status, e.owned_cover_name, e.added_at, e.copy_count, i.payload::text as payload
            from collection_entry e left join catalog_issue i on i.id = e.issue_id
            where e.user_id = ? and e.id = ?
            """.trimIndent(),
            userId, entryId,
        ).querySingle(::asEntry)
    }

    /**
     * Idempotent on (user, clientMutationId): a replayed offline scan returns the id of the entry
     * created the first time instead of inserting a duplicate.
     * De-dupe on (user, scanned_code): re-scanning the same code increments copy_count on the existing row.
     */
    suspend fun insertPending(userId: UUID, code: String, addedAt: Instant, clientMutationId: String?): UUID = db.transaction {
        if (clientMutationId != null) {
            prepare("select id from collection_entry where user_id = ? and client_mutation_id = ?", userId, clientMutationId)
                .querySingle { it.getObject("id", UUID::class.java) }
                ?.let { return@transaction it }
        }
        val existing = prepare("select id from collection_entry where user_id = ? and scanned_code = ?", userId, code)
            .querySingle { it.getObject("id", UUID::class.java) }
        if (existing != null) {
            prepare("update collection_entry set copy_count = copy_count + 1 where id = ?", existing).update()
            return@transaction existing
        }
        val id = UUID.randomUUID()
        prepare(
            """
            insert into collection_entry (id, user_id, scanned_code, status, added_at, next_attempt_at, client_mutation_id)
            values (?, ?, ?, 'PENDING', ?, now(), ?)
            """.trimIndent(),
            id, userId, code, addedAt, clientMutationId,
        ).update()
        id
    }

    suspend fun ping(): Boolean = runCatching {
        db.transaction { prepare("select 1").querySingle { it.getInt(1) } == 1 }
    }.getOrDefault(false)

    /** Returns false when the entry doesn't exist *for this user* (no information leak about other users). */
    suspend fun delete(userId: UUID, entryId: UUID): Boolean = db.transaction {
        prepare("delete from collection_entry where user_id = ? and id = ?", userId, entryId).update() == 1
    }

    suspend fun countToday(userId: UUID, since: Instant): Int = db.transaction {
        prepare("select count(*) from collection_entry where user_id = ? and added_at >= ?", userId, since)
            .querySingle { it.getInt(1) } ?: 0
    }

    /** Claims the next scan due for lookup. SKIP LOCKED lets several workers run safely. */
    suspend fun claimNextDue(now: Instant, maxNotFoundRetries: Int): QueuedScan? = db.transaction {
        prepare(
            """
            select id, scanned_code, attempts, status from collection_entry
            where next_attempt_at <= ?
              and (status = 'PENDING' or (status = 'NOT_FOUND' and attempts < ?))
            order by next_attempt_at
            limit 1
            for update skip locked
            """.trimIndent(),
            now, maxNotFoundRetries,
        ).querySingle { rs ->
            QueuedScan(
                entryId = rs.getObject("id", UUID::class.java),
                code = rs.getString("scanned_code"),
                attempts = rs.getInt("attempts"),
                status = LookupStatus.valueOf(rs.getString("status")),
            )
        }?.also { scan ->
            // Push it out a little so a crashed worker doesn't leave it claimed forever.
            prepare("update collection_entry set next_attempt_at = ? where id = ?", now.plusSeconds(120), scan.entryId).update()
        }
    }

    suspend fun markResolved(entryId: UUID, issueId: Long, ownedCoverName: String?) = db.transaction {
        prepare(
            "update collection_entry set status = 'RESOLVED', issue_id = ?, owned_cover_name = ?, attempts = attempts + 1 where id = ?",
            issueId, ownedCoverName, entryId,
        ).update()
    }

    suspend fun markNotFound(entryId: UUID, retryAt: Instant) = db.transaction {
        prepare(
            "update collection_entry set status = 'NOT_FOUND', attempts = attempts + 1, next_attempt_at = ? where id = ?",
            retryAt, entryId,
        ).update()
    }

    suspend fun reschedule(entryId: UUID, retryAt: Instant, countAttempt: Boolean) = db.transaction {
        prepare(
            "update collection_entry set next_attempt_at = ?, attempts = attempts + ? where id = ?",
            retryAt, if (countAttempt) 1 else 0, entryId,
        ).update()
    }

    private fun asEntry(rs: ResultSet): CollectionEntry = CollectionEntry(
        id = EntryId(rs.getObject("id", UUID::class.java).toString()),
        scannedCode = rs.getString("scanned_code"),
        status = LookupStatus.valueOf(rs.getString("status")),
        comic = rs.getString("payload")?.let { ContractMapper.asComic(json.decodeFromString(ComicDto.serializer(), it)) },
        ownedCoverName = rs.getString("owned_cover_name"),
        addedAtEpochMillis = rs.instant("added_at")!!.toEpochMilli(),
        copyCount = rs.getInt("copy_count"),
    )
}
