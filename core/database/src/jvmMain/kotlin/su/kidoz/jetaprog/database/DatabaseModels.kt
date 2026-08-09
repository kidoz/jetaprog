package su.kidoz.jetaprog.database

import kotlinx.serialization.Serializable
import java.util.UUID

/** Database engines supported by the IDE database tools. */
@Serializable
public enum class DatabaseDialect(
    /** Human-readable engine name. */
    public val displayName: String,
    /** Default server port. */
    public val defaultPort: Int,
) {
    /** PostgreSQL database server. */
    POSTGRESQL("PostgreSQL", 5432),

    /** StarRocks analytics database server. */
    STARROCKS("StarRocks", 9030),
}

/** TLS policy for a database connection. */
@Serializable
public enum class DatabaseSslMode {
    /** Do not negotiate TLS. */
    DISABLE,

    /** Require a TLS-protected connection. */
    REQUIRE,
}

/**
 * Persistable connection settings. Passwords are deliberately absent and must be supplied separately.
 */
@Serializable
public data class DatabaseConnectionProfile(
    /** Stable profile identifier. */
    val id: String,
    /** Name shown in the Database tool window. */
    val name: String,
    /** Database engine. */
    val dialect: DatabaseDialect,
    /** Server hostname or IP address. */
    val host: String,
    /** Server port. */
    val port: Int,
    /** StarRocks catalog; ignored by PostgreSQL. */
    val catalog: String = "default_catalog",
    /** Initial database name. */
    val database: String,
    /** Login username. */
    val username: String,
    /** TLS policy. */
    val sslMode: DatabaseSslMode = DatabaseSslMode.DISABLE,
    /** Connection timeout in seconds. */
    val connectTimeoutSeconds: Int = 10,
) {
    /** Returns validation messages, or an empty list when the profile is usable. */
    public fun validationErrors(): List<String> =
        buildList {
            if (name.isBlank()) add("Connection name is required")
            if (host.isBlank()) add("Host is required")
            if (host.any { it.isWhitespace() || it == '/' || it == '?' || it == '#' }) {
                add("Host contains unsupported URL characters")
            }
            if (port !in MIN_PORT..MAX_PORT) add("Port must be between $MIN_PORT and $MAX_PORT")
            if (database.isBlank()) add("Database is required")
            if (database.hasUnsupportedPathCharacters()) add("Database contains unsupported URL characters")
            if (dialect == DatabaseDialect.STARROCKS && catalog.isBlank()) add("Catalog is required")
            if (catalog.hasUnsupportedPathCharacters()) add("Catalog contains unsupported URL characters")
            if (username.isBlank()) add("Username is required")
            if (connectTimeoutSeconds !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
                add("Connection timeout must be between $MIN_TIMEOUT_SECONDS and $MAX_TIMEOUT_SECONDS seconds")
            }
        }

    /** Creates a new profile populated with defaults for [dialect]. */
    public companion object {
        /** Creates a profile suitable for editing in the connection dialog. */
        public fun create(dialect: DatabaseDialect = DatabaseDialect.POSTGRESQL): DatabaseConnectionProfile =
            DatabaseConnectionProfile(
                id = UUID.randomUUID().toString(),
                name = dialect.displayName,
                dialect = dialect,
                host = "localhost",
                port = dialect.defaultPort,
                database = "",
                username = "",
            )

        private const val MIN_PORT = 1
        private const val MAX_PORT = 65_535
        private const val MIN_TIMEOUT_SECONDS = 1
        private const val MAX_TIMEOUT_SECONDS = 300
    }
}

/** A password value whose string representation never exposes the secret. */
public class DatabaseCredential private constructor(
    internal val value: String,
) {
    override fun toString(): String = "DatabaseCredential(***)"

    /** Creates a transient credential from [password]. */
    public companion object {
        /** Wraps [password] in a redacting value object. */
        public fun fromPassword(password: String): DatabaseCredential = DatabaseCredential(password)

        /** Credential for servers that accept an empty password. */
        public val Empty: DatabaseCredential = DatabaseCredential("")
    }
}

/** Basic server information returned after a successful connection test. */
public data class DatabaseConnectionInfo(
    /** Database product reported by JDBC. */
    val productName: String,
    /** Database product version reported by JDBC. */
    val productVersion: String,
    /** JDBC driver name. */
    val driverName: String,
)

/** A database column discovered through JDBC metadata. */
public data class DatabaseColumn(
    /** Column name. */
    val name: String,
    /** Vendor-specific database type name. */
    val typeName: String,
    /** Whether the column accepts null values. */
    val nullable: Boolean,
    /** One-based ordinal position within the table. */
    val ordinal: Int,
)

/** A table or view discovered through JDBC metadata. */
public data class DatabaseTable(
    /** Table name. */
    val name: String,
    /** JDBC table type, such as TABLE or VIEW. */
    val type: String,
    /** Columns ordered by their ordinal position. */
    val columns: List<DatabaseColumn>,
)

/** A schema and its tables discovered through JDBC metadata. */
public data class DatabaseSchema(
    /** Catalog containing the schema, when reported by the driver. */
    val catalog: String?,
    /** Schema name. */
    val name: String,
    /** Tables and views in the schema. */
    val tables: List<DatabaseTable>,
)

/** A result-set column displayed in the query results grid. */
public data class DatabaseResultColumn(
    /** Column label reported by JDBC. */
    val label: String,
    /** Vendor-specific database type name. */
    val typeName: String,
)

/** Result of a SQL statement. */
public data class DatabaseQueryResult(
    /** Result-set columns, empty for update statements. */
    val columns: List<DatabaseResultColumn>,
    /** String-rendered row values aligned with [columns]. */
    val rows: List<List<String?>>,
    /** Affected row count for update statements, otherwise null. */
    val affectedRows: Long?,
    /** Elapsed execution time in milliseconds. */
    val elapsedMillis: Long,
    /** Whether more rows existed beyond the configured limit. */
    val truncated: Boolean,
    /** Non-fatal JDBC warnings. */
    val warnings: List<String>,
)

private fun String.hasUnsupportedPathCharacters(): Boolean =
    any { it.isWhitespace() || it == '/' || it == '?' || it == '#' || it == '\\' }
