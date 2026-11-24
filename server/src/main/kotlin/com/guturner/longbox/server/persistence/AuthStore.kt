package com.guturner.longbox.server.persistence

import java.time.Instant
import java.util.UUID

enum class TokenKind { ACCESS, REFRESH }

data class StoredToken(
    val userId: UUID,
    val kind: TokenKind,
    val familyId: UUID,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val revokedAt: Instant?,
)

data class AppUser(val id: UUID, val email: String, val displayName: String?)

class AuthStore(private val db: Database) {

    suspend fun upsertUser(googleSub: String, email: String, displayName: String?): AppUser = db.transaction {
        prepare(
            """
            insert into app_user (id, google_sub, email, display_name) values (?, ?, ?, ?)
            on conflict (google_sub) do update set email = excluded.email, display_name = excluded.display_name
            returning id, email, display_name
            """.trimIndent(),
            UUID.randomUUID(), googleSub, email, displayName,
        ).querySingle { rs ->
            AppUser(rs.getObject("id", UUID::class.java), rs.getString("email"), rs.getString("display_name"))
        }!!
    }

    suspend fun user(id: UUID): AppUser? = db.transaction {
        prepare("select id, email, display_name from app_user where id = ?", id).querySingle { rs ->
            AppUser(rs.getObject("id", UUID::class.java), rs.getString("email"), rs.getString("display_name"))
        }
    }

    suspend fun insertToken(hash: ByteArray, userId: UUID, kind: TokenKind, familyId: UUID, expiresAt: Instant) =
        db.transaction {
            prepare(
                "insert into auth_token (token_hash, user_id, kind, family_id, expires_at) values (?, ?, ?, ?, ?)",
                hash, userId, kind.name, familyId, expiresAt,
            ).update()
        }

    suspend fun findToken(hash: ByteArray): StoredToken? = db.transaction {
        prepare(
            "select user_id, kind, family_id, expires_at, used_at, revoked_at from auth_token where token_hash = ?",
            hash,
        ).querySingle { rs ->
            StoredToken(
                userId = rs.getObject("user_id", UUID::class.java),
                kind = TokenKind.valueOf(rs.getString("kind")),
                familyId = rs.getObject("family_id", UUID::class.java),
                expiresAt = rs.instant("expires_at")!!,
                usedAt = rs.instant("used_at"),
                revokedAt = rs.instant("revoked_at"),
            )
        }
    }

    /** Atomically marks a refresh token used. Returns false if it was already used (replay). */
    suspend fun markUsed(hash: ByteArray, now: Instant): Boolean = db.transaction {
        prepare("update auth_token set used_at = ? where token_hash = ? and used_at is null", now, hash).update() == 1
    }

    suspend fun revokeFamily(familyId: UUID, now: Instant) = db.transaction {
        prepare("update auth_token set revoked_at = ? where family_id = ? and revoked_at is null", now, familyId).update()
    }

    suspend fun deleteExpired(now: Instant) = db.transaction {
        prepare("delete from auth_token where expires_at < ?", now).update()
    }
}
