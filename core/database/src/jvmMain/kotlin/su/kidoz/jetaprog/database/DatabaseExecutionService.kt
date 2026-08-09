package su.kidoz.jetaprog.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.common.Disposable
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

/** Cancellable JDBC operations used by connection tests, schema import, and SQL execution. */
public interface DatabaseExecutionService : Disposable {
    /** Opens and validates a connection, returning server and driver information. */
    public suspend fun testConnection(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
    ): DatabaseConnectionInfo

    /** Loads schemas, tables, views, and columns visible to the connection. */
    public suspend fun introspect(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
    ): List<DatabaseSchema>

    /** Executes one SQL statement and materializes at most [rowLimit] result rows. */
    public suspend fun execute(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
        sql: String,
        rowLimit: Int = DEFAULT_ROW_LIMIT,
    ): DatabaseQueryResult

    /** Cancels the current connection test, schema import, or SQL execution. */
    public fun cancel()

    public companion object {
        /** Default maximum number of rows materialized for the result grid. */
        public const val DEFAULT_ROW_LIMIT: Int = 500
    }
}

/** Standard JDBC implementation with a single cancellable operation lane. */
public class JvmDatabaseExecutionService internal constructor(
    private val connectionFactory: JdbcConnectionFactory,
) : DatabaseExecutionService {
    /** Creates a service backed by JDBC [DriverManager]. */
    public constructor() : this(DriverManagerJdbcConnectionFactory)

    private val operationMutex = Mutex()
    private val activeConnection = AtomicReference<Connection?>()
    private val activeStatement = AtomicReference<Statement?>()

    override suspend fun testConnection(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
    ): DatabaseConnectionInfo =
        withConnection(profile, credential) { connection ->
            val metadata = connection.metaData
            DatabaseConnectionInfo(
                productName = metadata.databaseProductName,
                productVersion = metadata.databaseProductVersion,
                driverName = metadata.driverName,
            )
        }

    override suspend fun introspect(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
    ): List<DatabaseSchema> =
        withConnection(profile, credential) { connection ->
            introspectMetadata(connection.metaData, profile)
        }

    override suspend fun execute(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
        sql: String,
        rowLimit: Int,
    ): DatabaseQueryResult {
        require(sql.isNotBlank()) { "SQL statement cannot be blank" }
        require(rowLimit > 0) { "Row limit must be positive" }
        return withConnection(profile, credential) { connection ->
            val startedAt = System.nanoTime()
            connection.createStatement().use { statement ->
                activeStatement.set(statement)
                try {
                    statement.maxRows = rowLimit + 1
                    val hasResultSet = statement.execute(sql)
                    if (hasResultSet) {
                        statement.resultSet.use { resultSet ->
                            materializeResult(statement, resultSet, rowLimit, startedAt)
                        }
                    } else {
                        DatabaseQueryResult(
                            columns = emptyList(),
                            rows = emptyList(),
                            affectedRows = statement.largeUpdateCount.takeIf { it >= 0L },
                            elapsedMillis = elapsedMillis(startedAt),
                            truncated = false,
                            warnings = statement.collectWarnings(),
                        )
                    }
                } finally {
                    activeStatement.compareAndSet(statement, null)
                }
            }
        }
    }

    override fun cancel() {
        activeStatement.getAndSet(null)?.closeAfterCancel()
        activeConnection.getAndSet(null)?.closeQuietly()
    }

    override fun dispose() {
        cancel()
    }

    private suspend fun <T> withConnection(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
        block: suspend (Connection) -> T,
    ): T {
        val validationErrors = profile.validationErrors()
        require(validationErrors.isEmpty()) { validationErrors.joinToString(". ") }
        return operationMutex.withLock {
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val connection = runInterruptible { connectionFactory.open(profile, credential) }
                activeConnection.set(connection)
                try {
                    block(connection)
                } catch (error: SQLException) {
                    if (!currentCoroutineContext().isActive) {
                        throw CancellationException("Database operation cancelled", error)
                    }
                    throw error
                } finally {
                    activeStatement.set(null)
                    activeConnection.compareAndSet(connection, null)
                    connection.closeQuietly()
                }
            }
        }
    }

    private suspend fun introspectMetadata(
        metadata: DatabaseMetaData,
        profile: DatabaseConnectionProfile,
    ): List<DatabaseSchema> {
        val schemas = readSchemas(metadata, profile)
        var remainingTableCount = MAX_TOTAL_TABLES
        return schemas.map { schema ->
            currentCoroutineContext().ensureActive()
            val tables = readTables(metadata, schema, remainingTableCount)
            remainingTableCount -= tables.size
            schema.copy(tables = tables)
        }
    }

    private fun readSchemas(
        metadata: DatabaseMetaData,
        profile: DatabaseConnectionProfile,
    ): List<DatabaseSchema> {
        val schemas = mutableListOf<DatabaseSchema>()
        metadata.schemas.use { resultSet ->
            while (resultSet.next() && schemas.size < MAX_SCHEMAS) {
                schemas +=
                    DatabaseSchema(
                        catalog = resultSet.getString("TABLE_CATALOG"),
                        name = resultSet.getString("TABLE_SCHEM") ?: profile.database,
                        tables = emptyList(),
                    )
            }
        }
        if (schemas.isEmpty()) {
            schemas +=
                DatabaseSchema(
                    catalog = if (profile.dialect == DatabaseDialect.STARROCKS) profile.catalog else profile.database,
                    name = profile.database,
                    tables = emptyList(),
                )
        }
        return schemas.distinctBy { it.catalog to it.name }.sortedBy { it.name.lowercase() }
    }

    private suspend fun readTables(
        metadata: DatabaseMetaData,
        schema: DatabaseSchema,
        remainingTableCount: Int,
    ): List<DatabaseTable> {
        val tables = mutableListOf<DatabaseTable>()
        val tableLimit = minOf(MAX_TABLES_PER_SCHEMA, remainingTableCount)
        metadata.getTables(schema.catalog, schema.name, "%", TABLE_TYPES).use { resultSet ->
            while (resultSet.next() && tables.size < tableLimit) {
                currentCoroutineContext().ensureActive()
                val tableName = resultSet.getString("TABLE_NAME") ?: continue
                tables +=
                    DatabaseTable(
                        name = tableName,
                        type = resultSet.getString("TABLE_TYPE") ?: "TABLE",
                        columns = readColumns(metadata, schema, tableName),
                    )
            }
        }
        return tables.sortedWith(compareBy<DatabaseTable> { it.type }.thenBy { it.name.lowercase() })
    }

    private fun readColumns(
        metadata: DatabaseMetaData,
        schema: DatabaseSchema,
        tableName: String,
    ): List<DatabaseColumn> {
        val columns = mutableListOf<DatabaseColumn>()
        metadata.getColumns(schema.catalog, schema.name, tableName, "%").use { resultSet ->
            while (resultSet.next() && columns.size < MAX_COLUMNS_PER_TABLE) {
                columns +=
                    DatabaseColumn(
                        name = resultSet.getString("COLUMN_NAME") ?: continue,
                        typeName = resultSet.getString("TYPE_NAME") ?: "unknown",
                        nullable = resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                        ordinal = resultSet.getInt("ORDINAL_POSITION"),
                    )
            }
        }
        return columns.sortedBy { it.ordinal }
    }

    private suspend fun materializeResult(
        statement: Statement,
        resultSet: ResultSet,
        rowLimit: Int,
        startedAt: Long,
    ): DatabaseQueryResult {
        val metadata = resultSet.metaData
        val columns =
            (1..metadata.columnCount).map { index ->
                DatabaseResultColumn(
                    label = metadata.getColumnLabel(index),
                    typeName = metadata.getColumnTypeName(index),
                )
            }
        val rows = mutableListOf<List<String?>>()
        var truncated = false
        while (resultSet.next()) {
            currentCoroutineContext().ensureActive()
            if (rows.size == rowLimit) {
                truncated = true
                break
            }
            rows += (1..metadata.columnCount).map { index -> resultSet.getObject(index).renderCellValue() }
        }
        return DatabaseQueryResult(
            columns = columns,
            rows = rows,
            affectedRows = null,
            elapsedMillis = elapsedMillis(startedAt),
            truncated = truncated,
            warnings = statement.collectWarnings(),
        )
    }

    private companion object {
        val TABLE_TYPES = arrayOf("TABLE", "VIEW", "MATERIALIZED VIEW")
        const val MAX_SCHEMAS = 100
        const val MAX_TABLES_PER_SCHEMA = 500
        const val MAX_TOTAL_TABLES = 2_000
        const val MAX_COLUMNS_PER_TABLE = 500
        const val MAX_CELL_LENGTH = 10_000

        fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000L

        fun Any?.renderCellValue(): String? =
            when (this) {
                null -> null
                is ByteArray -> joinToString(prefix = "0x", separator = "") { byte -> "%02x".format(byte) }
                else -> toString()
            }?.let { value ->
                if (value.length <= MAX_CELL_LENGTH) value else value.take(MAX_CELL_LENGTH) + "…"
            }

        fun Statement.collectWarnings(): List<String> =
            buildList {
                var warning = warnings
                while (warning != null) {
                    add(warning.message ?: warning.sqlState ?: "Database warning")
                    warning = warning.nextWarning
                }
            }

        fun Statement.closeAfterCancel() {
            runCatching { cancel() }
            runCatching { close() }
        }

        fun Connection.closeQuietly() {
            runCatching { close() }
        }
    }
}

internal fun interface JdbcConnectionFactory {
    fun open(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
    ): Connection
}

private object DriverManagerJdbcConnectionFactory : JdbcConnectionFactory {
    override fun open(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential,
    ): Connection {
        val properties =
            Properties().apply {
                setProperty("user", profile.username)
                setProperty("password", credential.value)
                when (profile.dialect) {
                    DatabaseDialect.POSTGRESQL -> {
                        setProperty("connectTimeout", profile.connectTimeoutSeconds.toString())
                        setProperty("sslmode", profile.sslMode.name.lowercase())
                    }

                    DatabaseDialect.STARROCKS -> {
                        setProperty("connectTimeout", (profile.connectTimeoutSeconds * MILLIS_PER_SECOND).toString())
                    }
                }
            }
        return DriverManager.getConnection(profile.jdbcUrl(), properties)
    }

    private const val MILLIS_PER_SECOND = 1_000
}

internal fun DatabaseConnectionProfile.jdbcUrl(): String =
    when (dialect) {
        DatabaseDialect.POSTGRESQL -> "jdbc:postgresql://$host:$port/$database"
        DatabaseDialect.STARROCKS -> "jdbc:starrocks://$host:$port/$catalog.$database"
    }
