package com.guturner.longbox.server.catalog

import com.guturner.longbox.contract.ComicDto
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.contract.CreatorDto
import com.guturner.longbox.contract.StoryArcDto
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.map
import com.guturner.longbox.domain.model.Publisher
import com.guturner.longbox.domain.model.PublisherId
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.repository.BarcodeLookup
import com.guturner.longbox.domain.repository.ComicCatalog
import com.guturner.longbox.domain.usecase.FOLLOWED_PUBLISHERS
import com.guturner.longbox.server.metron.MetronClient
import com.guturner.longbox.server.metron.MetronMapper
import com.guturner.longbox.server.persistence.Database
import com.guturner.longbox.server.persistence.instant
import com.guturner.longbox.server.persistence.prepare
import com.guturner.longbox.server.persistence.querySingle
import com.guturner.longbox.server.persistence.update
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-through cache in front of Metron. Metron asks API users to cache and to avoid
 * re-requesting unchanged data; this keeps repeat views of the library at zero API calls.
 */
class CachedMetronCatalog(
    private val db: Database,
    private val metron: MetronClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val now: () -> Instant = Instant::now,
) : ComicCatalog, BarcodeLookup {

    private val releasesCache = ConcurrentHashMap<String, Pair<Instant, List<Comic>>>()

    override suspend fun findByCode(code: String): Outcome<Comic?> {
        val cached = db.transaction {
            prepare("select issue_id, fetched_at from catalog_upc where code = ?", code)
                .querySingle { rs -> (rs.getObject("issue_id") as Long?) to rs.instant("fetched_at")!! }
        }
        if (cached != null) {
            val (issueId, fetchedAt) = cached
            if (issueId != null) return comic(ComicId(issueId)).asNullable()
            if (fetchedAt.isAfter(now().minus(NEGATIVE_TTL))) return Outcome.Success(null)
        }

        val matches = when (val result = metron.issuesByUpc(code)) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val match = matches.firstOrNull()
        if (match == null) {
            db.transaction { upsertUpc(code, null) }
            return Outcome.Success(null)
        }
        return when (val detail = fetchAndStoreIssue(match.id)) {
            is Outcome.Failure -> detail
            is Outcome.Success -> {
                db.transaction { upsertUpc(code, match.id) }
                Outcome.Success(detail.value)
            }
        }
    }

    override suspend fun comic(id: ComicId): Outcome<Comic> {
        val cached = readPayload("catalog_issue", id.value)
        val comic = cached?.let { ContractMapper.asComic(json.decodeFromString(ComicDto.serializer(), it.first)) }
        if (cached != null && comic != null && cached.second.isAfter(now().minus(issueTtl(comic)))) return Outcome.Success(comic)

        return when (val fresh = fetchAndStoreIssue(id.value)) {
            is Outcome.Success -> fresh.value?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.NotFound)
            // Serve stale data rather than failing when Metron is unavailable or we are throttled.
            is Outcome.Failure -> comic?.let { Outcome.Success(it) } ?: fresh
        }
    }

    override suspend fun creator(id: CreatorId): Outcome<Creator> =
        cachedOrFetch(
            table = "catalog_creator",
            id = id.value,
            ttl = REFERENCE_TTL,
            decode = { ContractMapper.asCreator(json.decodeFromString(CreatorDto.serializer(), it)) },
            encode = { json.encodeToString(CreatorDto.serializer(), ContractMapper.asCreatorDto(it)) },
            fetch = { metron.creator(id.value).map { dto -> dto?.let(MetronMapper::asCreator) } },
        )

    override suspend fun storyArc(id: StoryArcId): Outcome<StoryArc> =
        cachedOrFetch(
            table = "catalog_arc",
            id = id.value,
            ttl = REFERENCE_TTL,
            decode = { ContractMapper.asStoryArc(json.decodeFromString(StoryArcDto.serializer(), it)) },
            encode = { json.encodeToString(StoryArcDto.serializer(), ContractMapper.asStoryArcDto(it)) },
            fetch = { metron.arc(id.value).map { dto -> dto?.let(MetronMapper::asStoryArc) } },
        )

    override suspend fun releases(from: CalendarDate, to: CalendarDate): Outcome<List<Comic>> {
        val key = "$from..$to"
        releasesCache[key]?.let { (at, comics) -> if (at.isAfter(now().minus(RELEASES_TTL))) return Outcome.Success(comics) }

        val all = mutableListOf<Comic>()
        for (publisherName in FOLLOWED_PUBLISHERS) {
            when (val page = metron.releases(from.toString(), to.toString(), publisherName)) {
                is Outcome.Failure -> return page
                is Outcome.Success -> {
                    // Metron list results omit the publisher; the id is unknown here, the name is what we filtered on.
                    val publisher = Publisher(PublisherId(0), publisherName)
                    all += page.value.map { MetronMapper.asReleaseComic(it, publisher) }
                }
            }
        }
        val distinct = all.distinctBy { it.id }
        releasesCache[key] = now() to distinct
        return Outcome.Success(distinct)
    }

    private suspend fun fetchAndStoreIssue(id: Long): Outcome<Comic?> =
        when (val result = metron.issue(id)) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                val comic = result.value?.let(MetronMapper::asComic)
                if (comic != null) {
                    val payload = json.encodeToString(ComicDto.serializer(), ContractMapper.asComicDto(comic))
                    db.transaction { upsertPayload("catalog_issue", id, payload) }
                }
                Outcome.Success(comic)
            }
        }

    private suspend fun <T : Any> cachedOrFetch(
        table: String,
        id: Long,
        ttl: Duration,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> Outcome<T?>,
    ): Outcome<T> {
        val cached = readPayload(table, id)
        val value = cached?.let { decode(it.first) }
        if (cached != null && value != null && cached.second.isAfter(now().minus(ttl))) return Outcome.Success(value)

        return when (val fresh = fetch()) {
            is Outcome.Failure -> value?.let { Outcome.Success(it) } ?: fresh
            is Outcome.Success -> {
                val item = fresh.value ?: return Outcome.Failure(DomainError.NotFound)
                db.transaction { upsertPayload(table, id, encode(item)) }
                Outcome.Success(item)
            }
        }
    }

    private suspend fun readPayload(table: String, id: Long): Pair<String, Instant>? {
        require(table in CACHE_TABLES) // table names are never user input; guard anyway
        return db.transaction {
            prepare("select payload::text as payload, fetched_at from $table where id = ?", id)
                .querySingle { rs -> rs.getString("payload") to rs.instant("fetched_at")!! }
        }
    }

    private fun java.sql.Connection.upsertPayload(table: String, id: Long, payload: String) {
        require(table in CACHE_TABLES)
        prepare(
            """
            insert into $table (id, payload, fetched_at) values (?, cast(? as jsonb), ?)
            on conflict (id) do update set payload = excluded.payload, fetched_at = excluded.fetched_at
            """.trimIndent(),
            id, payload, now(),
        ).update()
    }

    private fun java.sql.Connection.upsertUpc(code: String, issueId: Long?) {
        prepare(
            """
            insert into catalog_upc (code, issue_id, fetched_at) values (?, ?, ?)
            on conflict (code) do update set issue_id = excluded.issue_id, fetched_at = excluded.fetched_at
            """.trimIndent(),
            code, issueId, now(),
        ).update()
    }

    /** Recent books gain credits/arcs in the weeks after release, so refresh them sooner. */
    private fun issueTtl(comic: Comic): Duration {
        val released = comic.storeDate ?: comic.coverDate ?: return RECENT_ISSUE_TTL
        val ageDays = Duration.between(
            Instant.ofEpochSecond(released.epochDay * 86_400), now(),
        ).toDays()
        return if (ageDays < 60) RECENT_ISSUE_TTL else ISSUE_TTL
    }

    private companion object {
        val CACHE_TABLES = setOf("catalog_issue", "catalog_creator", "catalog_arc")
        val NEGATIVE_TTL: Duration = Duration.ofHours(12)
        val RECENT_ISSUE_TTL: Duration = Duration.ofDays(2)
        val ISSUE_TTL: Duration = Duration.ofDays(30)
        val REFERENCE_TTL: Duration = Duration.ofDays(30)
        val RELEASES_TTL: Duration = Duration.ofHours(6)
    }
}

private fun Outcome<Comic>.asNullable(): Outcome<Comic?> = when (this) {
    is Outcome.Success -> this
    is Outcome.Failure -> if (error == DomainError.NotFound) Outcome.Success(null) else this
}

