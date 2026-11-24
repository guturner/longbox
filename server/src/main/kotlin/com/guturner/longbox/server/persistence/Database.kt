package com.guturner.longbox.server.persistence

import com.guturner.longbox.server.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

/**
 * Plain JDBC with parameterised statements only (no string-built SQL), per the OWASP
 * SQL Injection Prevention Cheat Sheet. Kept deliberately small; swap for Exposed later if wanted.
 */
class Database(private val dataSource: DataSource) {

    suspend fun <T> transaction(block: Connection.() -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.block().also { connection.commit() }
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            }
        }
    }

    companion object {
        fun connect(config: DatabaseConfig): Database {
            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = config.jdbcUrl
                    username = config.username
                    password = config.password
                    maximumPoolSize = 5
                    isAutoCommit = false
                },
            )
            Flyway.configure().dataSource(dataSource).load().migrate()
            return Database(dataSource)
        }
    }
}

fun Connection.prepare(sql: String, vararg params: Any?): PreparedStatement =
    prepareStatement(sql).apply {
        params.forEachIndexed { index, value ->
            // pgjdbc maps OffsetDateTime to timestamptz; java.time.Instant is not a JDBC 4.2 type.
            val bound = if (value is Instant) OffsetDateTime.ofInstant(value, ZoneOffset.UTC) else value
            setObject(index + 1, bound)
        }
    }

fun ResultSet.instant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()

fun <T> PreparedStatement.queryList(map: (ResultSet) -> T): List<T> = use { statement ->
    statement.executeQuery().use { rs ->
        buildList { while (rs.next()) add(map(rs)) }
    }
}

fun <T> PreparedStatement.querySingle(map: (ResultSet) -> T): T? = queryList(map).firstOrNull()

fun PreparedStatement.update(): Int = use { it.executeUpdate() }
