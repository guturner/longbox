package com.guturner.longbox.server.persistence

import com.guturner.longbox.contract.ComicDto
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

interface ContainerStore {
    suspend fun list(userId: UUID): List<Container>
    suspend fun find(userId: UUID, containerId: UUID): Container?
    suspend fun insert(userId: UUID, containerId: UUID, name: String, kind: ContainerKind): Container
    suspend fun rename(userId: UUID, containerId: UUID, newName: String): Container?
    suspend fun delete(userId: UUID, containerId: UUID): Boolean
    suspend fun moveEntry(userId: UUID, entryId: UUID, containerId: UUID): CollectionEntry?
    suspend fun removeFromContainer(userId: UUID, entryId: UUID): CollectionEntry?
}

class JdbcContainerStore(
    private val db: Database,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ContainerStore {

    override suspend fun list(userId: UUID): List<Container> = db.transaction {
        prepare(
            """
            select c.id, c.name, c.kind, c.created_at,
                   count(e.id) as entry_count
            from container c
            left join collection_entry e on e.container_id = c.id
            where c.user_id = ?
            group by c.id, c.name, c.kind, c.created_at
            order by c.created_at
            """.trimIndent(),
            userId,
        ).queryList(::asContainer)
    }

    override suspend fun find(userId: UUID, containerId: UUID): Container? = db.transaction {
        prepare(
            """
            select c.id, c.name, c.kind, c.created_at,
                   count(e.id) as entry_count
            from container c
            left join collection_entry e on e.container_id = c.id
            where c.user_id = ? and c.id = ?
            group by c.id, c.name, c.kind, c.created_at
            """.trimIndent(),
            userId, containerId,
        ).querySingle(::asContainer)
    }

    override suspend fun insert(userId: UUID, containerId: UUID, name: String, kind: ContainerKind): Container = db.transaction {
        prepare(
            "insert into container (id, user_id, name, kind) values (?, ?, ?, ?)",
            containerId, userId, name, kind.name,
        ).update()
        Container(
            id = ContainerId(containerId.toString()),
            name = name,
            kind = kind,
            entryCount = 0,
            createdAtEpochMillis = Instant.now().toEpochMilli(),
        )
    }

    override suspend fun rename(userId: UUID, containerId: UUID, newName: String): Container? = db.transaction {
        val updated = prepare(
            "update container set name = ? where id = ? and user_id = ?",
            newName, containerId, userId,
        ).update()
        if (updated == 0) return@transaction null
        prepare(
            """
            select c.id, c.name, c.kind, c.created_at, count(e.id) as entry_count
            from container c left join collection_entry e on e.container_id = c.id
            where c.id = ?
            group by c.id, c.name, c.kind, c.created_at
            """.trimIndent(),
            containerId,
        ).querySingle(::asContainer)
    }

    override suspend fun delete(userId: UUID, containerId: UUID): Boolean = db.transaction {
        prepare("delete from container where id = ? and user_id = ?", containerId, userId).update() == 1
    }

    override suspend fun moveEntry(userId: UUID, entryId: UUID, containerId: UUID): CollectionEntry? = db.transaction {
        // Check ownership of both entry and container before mutating.
        val containerUserId = prepare("select user_id from container where id = ?", containerId)
            .querySingle { it.getObject("user_id", UUID::class.java) }
        if (containerUserId != userId) return@transaction null

        val kind = prepare("select kind from container where id = ?", containerId)
            .querySingle { it.getString("kind") } ?: return@transaction null
        val newStatus = if (kind == "BOX") "IN_BOX" else "IN_STACK"

        val updated = prepare(
            "update collection_entry set container_id = ?, status = ? where id = ? and user_id = ?",
            containerId, newStatus, entryId, userId,
        ).update()
        if (updated == 0) return@transaction null

        prepare(
            """
            select e.id, e.scanned_code, e.status, e.owned_cover_name, e.added_at, e.copy_count,
                   e.container_id, c.name as container_name, i.payload::text as payload
            from collection_entry e
            left join catalog_issue i on i.id = e.issue_id
            left join container c on c.id = e.container_id
            where e.id = ?
            """.trimIndent(),
            entryId,
        ).querySingle(::asEntry)
    }

    override suspend fun removeFromContainer(userId: UUID, entryId: UUID): CollectionEntry? = db.transaction {
        val updated = prepare(
            "update collection_entry set container_id = null, status = 'IN_STACK' where id = ? and user_id = ?",
            entryId, userId,
        ).update()
        if (updated == 0) return@transaction null

        prepare(
            """
            select e.id, e.scanned_code, e.status, e.owned_cover_name, e.added_at, e.copy_count,
                   e.container_id, null as container_name, i.payload::text as payload
            from collection_entry e
            left join catalog_issue i on i.id = e.issue_id
            where e.id = ?
            """.trimIndent(),
            entryId,
        ).querySingle(::asEntry)
    }

    private fun asContainer(rs: ResultSet) = Container(
        id = ContainerId(rs.getObject("id", UUID::class.java).toString()),
        name = rs.getString("name"),
        kind = ContainerKind.valueOf(rs.getString("kind")),
        entryCount = rs.getInt("entry_count"),
        createdAtEpochMillis = rs.instant("created_at")!!.toEpochMilli(),
    )

    private fun asEntry(rs: ResultSet): CollectionEntry = CollectionEntry(
        id = EntryId(rs.getObject("id", UUID::class.java).toString()),
        scannedCode = rs.getString("scanned_code"),
        state = EntryState.valueOf(rs.getString("status")),
        comic = rs.getString("payload")?.let { ContractMapper.asComic(json.decodeFromString(ComicDto.serializer(), it)) },
        ownedCoverName = rs.getString("owned_cover_name"),
        addedAtEpochMillis = rs.instant("added_at")!!.toEpochMilli(),
        copyCount = rs.getInt("copy_count"),
        containerId = rs.getString("container_id")?.let { ContainerId(it) },
        containerName = rs.getString("container_name"),
    )
}
