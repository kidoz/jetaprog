package su.kidoz.jetaprog.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DatabaseModelsTest {
    @Test
    fun `builds dialect-specific JDBC URLs`() {
        val postgresql = profile(DatabaseDialect.POSTGRESQL)
        val starRocks =
            profile(DatabaseDialect.STARROCKS).copy(
                port = DatabaseDialect.STARROCKS.defaultPort,
                catalog = "analytics",
            )

        assertEquals("jdbc:postgresql://localhost:5432/demo", postgresql.jdbcUrl())
        assertEquals("jdbc:starrocks://localhost:9030/analytics.demo", starRocks.jdbcUrl())
    }

    @Test
    fun `rejects JDBC URL delimiter injection`() {
        val errors = profile(DatabaseDialect.POSTGRESQL).copy(host = "localhost?sslmode=disable").validationErrors()

        assertTrue(errors.any { it.contains("Host") })
    }

    @Test
    fun `credential string is always redacted`() {
        val credential = DatabaseCredential.fromPassword("never-print-this")

        assertEquals("DatabaseCredential(***)", credential.toString())
    }

    @Test
    fun `runtime includes both JDBC drivers`() {
        assertEquals("org.postgresql.Driver", Class.forName("org.postgresql.Driver").name)
        assertEquals("com.starrocks.cj.jdbc.Driver", Class.forName("com.starrocks.cj.jdbc.Driver").name)
    }
}

internal fun profile(dialect: DatabaseDialect): DatabaseConnectionProfile =
    DatabaseConnectionProfile(
        id = "profile-1",
        name = "Demo",
        dialect = dialect,
        host = "localhost",
        port = dialect.defaultPort,
        database = "demo",
        username = "developer",
    )
