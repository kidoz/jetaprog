package su.kidoz.jetaprog.database

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

internal class DatabaseExecutionServiceTest {
    @Test
    fun `materializes bounded query results`() =
        runTest {
            val connection = mockk<Connection>()
            val statement = mockk<Statement>()
            val resultSet = mockk<ResultSet>()
            val metadata = mockk<ResultSetMetaData>()
            stubClosable(connection, statement, resultSet)
            every { connection.createStatement() } returns statement
            every { statement.maxRows = any() } just runs
            every { statement.execute("select id, name from demo") } returns true
            every { statement.resultSet } returns resultSet
            every { statement.warnings } returns null
            every { resultSet.metaData } returns metadata
            every { metadata.columnCount } returns 2
            every { metadata.getColumnLabel(1) } returns "id"
            every { metadata.getColumnLabel(2) } returns "name"
            every { metadata.getColumnTypeName(1) } returns "int4"
            every { metadata.getColumnTypeName(2) } returns "text"
            every { resultSet.next() } returnsMany listOf(true, true, false)
            every { resultSet.getObject(1) } returnsMany listOf(1, 2)
            every { resultSet.getObject(2) } returnsMany listOf("alpha", "beta")
            val service = JvmDatabaseExecutionService { _, _ -> connection }

            val result =
                service.execute(
                    profile(DatabaseDialect.POSTGRESQL),
                    DatabaseCredential.Empty,
                    "select id, name from demo",
                    rowLimit = 10,
                )

            assertEquals(listOf("id", "name"), result.columns.map { it.label })
            assertEquals(listOf(listOf("1", "alpha"), listOf("2", "beta")), result.rows)
            assertFalse(result.truncated)
            verify { statement.maxRows = 11 }
        }

    @Test
    fun `loads schema table and column metadata`() =
        runTest {
            val connection = mockk<Connection>()
            val metadata = mockk<DatabaseMetaData>()
            val schemas = mockk<ResultSet>()
            val tables = mockk<ResultSet>()
            val columns = mockk<ResultSet>()
            every { connection.metaData } returns metadata
            every { connection.close() } just runs
            every { metadata.schemas } returns schemas
            every { schemas.close() } just runs
            every { schemas.next() } returnsMany listOf(true, false)
            every { schemas.getString("TABLE_CATALOG") } returns "demo"
            every { schemas.getString("TABLE_SCHEM") } returns "public"
            every { metadata.getTables("demo", "public", "%", any()) } returns tables
            every { tables.close() } just runs
            every { tables.next() } returnsMany listOf(true, false)
            every { tables.getString("TABLE_NAME") } returns "customers"
            every { tables.getString("TABLE_TYPE") } returns "TABLE"
            every { metadata.getColumns("demo", "public", "customers", "%") } returns columns
            every { columns.close() } just runs
            every { columns.next() } returnsMany listOf(true, false)
            every { columns.getString("COLUMN_NAME") } returns "id"
            every { columns.getString("TYPE_NAME") } returns "int8"
            every { columns.getInt("NULLABLE") } returns DatabaseMetaData.columnNoNulls
            every { columns.getInt("ORDINAL_POSITION") } returns 1
            val service = JvmDatabaseExecutionService { _, _ -> connection }

            val result = service.introspect(profile(DatabaseDialect.POSTGRESQL), DatabaseCredential.Empty)

            assertEquals("public", result.single().name)
            assertEquals(
                "customers",
                result
                    .single()
                    .tables
                    .single()
                    .name,
            )
            assertEquals(
                "id",
                result
                    .single()
                    .tables
                    .single()
                    .columns
                    .single()
                    .name,
            )
        }

    @Test
    fun `cancel reaches the active JDBC statement and connection`() {
        runBlocking<Unit> {
            val connection = mockk<Connection>()
            val statement = mockk<Statement>()
            val started = CompletableDeferred<Unit>()
            val released = CompletableDeferred<Unit>()
            every { connection.createStatement() } returns statement
            every { connection.close() } just runs
            every { statement.close() } just runs
            every { statement.maxRows = any() } just runs
            every { statement.execute(any()) } answers {
                started.complete(Unit)
                runBlocking { released.await() }
                false
            }
            every { statement.cancel() } just runs
            every { statement.largeUpdateCount } returns 0L
            every { statement.warnings } returns null
            val service = JvmDatabaseExecutionService { _, _ -> connection }
            val execution =
                async(Dispatchers.Default) {
                    service.execute(
                        profile(DatabaseDialect.POSTGRESQL),
                        DatabaseCredential.Empty,
                        "select pg_sleep(10)",
                    )
                }

            started.await()
            service.cancel()
            released.complete(Unit)
            execution.await()

            verify { statement.cancel() }
            verify(atLeast = 1) { connection.close() }
        }
    }

    private fun stubClosable(
        connection: Connection,
        statement: Statement,
        resultSet: ResultSet,
    ) {
        every { connection.close() } just runs
        every { statement.close() } just runs
        every { resultSet.close() } just runs
    }
}
