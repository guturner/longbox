package com.guturner.longbox.server.config

/**
 * All secrets come from the environment (or a secrets manager that injects env vars),
 * never from source control. See docs/SECURITY.md, "Secrets".
 */
data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val metron: MetronConfig,
    val auth: AuthConfig,
    val allowedWebOrigins: List<String>,
    /** Behind Cloud Run / Fly.io TLS terminates at the proxy; trust X-Forwarded-* only then. */
    val behindProxy: Boolean,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            // Docker secrets convention: FOO_FILE=/run/secrets/foo takes precedence over FOO.
            fun secretOrEnv(name: String): String? =
                env["${name}_FILE"]?.let { java.io.File(it).readText().trim() } ?: env[name]

            fun required(name: String) = secretOrEnv(name)?.takeIf { it.isNotBlank() }
                ?: error("Missing required environment variable $name (or ${name}_FILE)")

            fun list(name: String) = env[name].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                database = DatabaseConfig(
                    jdbcUrl = required("DATABASE_URL"),
                    username = required("DATABASE_USER"),
                    password = required("DATABASE_PASSWORD"),
                ),
                metron = MetronConfig(
                    username = required("METRON_USERNAME"),
                    password = required("METRON_PASSWORD"),
                    userAgent = env["METRON_USER_AGENT"] ?: "Longbox/0.1 (github.com/guturner)",
                    // Metron's published limits are 20/min and 5,000/day. Stay under both.
                    requestsPerMinute = env["METRON_REQUESTS_PER_MINUTE"]?.toIntOrNull() ?: 18,
                    requestsPerDay = env["METRON_REQUESTS_PER_DAY"]?.toIntOrNull() ?: 4_500,
                ),
                auth = AuthConfig(
                    googleClientIds = list("GOOGLE_CLIENT_IDS").ifEmpty { error("GOOGLE_CLIENT_IDS is empty") },
                    allowedEmails = list("ALLOWED_GOOGLE_EMAILS").map { it.lowercase() }
                        .ifEmpty { error("ALLOWED_GOOGLE_EMAILS is empty; refusing to start an open server") },
                ),
                allowedWebOrigins = list("WEB_ORIGINS"),
                behindProxy = env["BEHIND_PROXY"]?.toBooleanStrictOrNull() ?: true,
            )
        }
    }
}

data class DatabaseConfig(val jdbcUrl: String, val username: String, val password: String) {
    override fun toString() = "DatabaseConfig(jdbcUrl=$jdbcUrl, username=$username, password=***)"
}

data class MetronConfig(
    val username: String,
    val password: String,
    val userAgent: String,
    val requestsPerMinute: Int,
    val requestsPerDay: Int,
) {
    override fun toString() = "MetronConfig(username=$username, password=***, userAgent=$userAgent)"
}

data class AuthConfig(
    /** Every OAuth client id (Android, desktop, web) whose ID tokens we accept as `aud`. */
    val googleClientIds: List<String>,
    /** Single-user deployment: only these verified Google accounts may sign in. */
    val allowedEmails: List<String>,
    val accessTokenTtlSeconds: Long = 15 * 60,
    val refreshTokenTtlSeconds: Long = 60L * 60 * 24 * 60,
)
