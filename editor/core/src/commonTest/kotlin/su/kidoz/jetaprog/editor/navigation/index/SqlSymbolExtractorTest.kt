package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlSymbolExtractorTest {
    @Test
    fun `extracts portable PostgreSQL and StarRocks schema objects`() {
        val source =
            """
            CREATE DATABASE analytics;
            CREATE SCHEMA IF NOT EXISTS reporting;
            CREATE TABLE reporting.events (id BIGINT);
            CREATE MATERIALIZED VIEW reporting.daily_events AS SELECT 1;
            CREATE OR REPLACE FUNCTION reporting.refresh_events() RETURNS void AS ${'$'}${'$'} SELECT 1 ${'$'}${'$'};
            CREATE TYPE reporting.event_state AS ENUM ('new');
            CREATE UNIQUE INDEX events_id_idx ON reporting.events (id);
            CREATE ROUTINE LOAD analytics.kafka_events ON events PROPERTIES ("format"="json");
            """.trimIndent()

        val symbols = SqlSymbolExtractor().extractSymbols(source, "/workspace/schema.starrocks.sql")
        val symbolsByName = symbols.associateBy { it.name }

        assertEquals(NavigationSymbolKind.MODULE, symbolsByName.getValue("analytics").kind)
        assertEquals(NavigationSymbolKind.NAMESPACE, symbolsByName.getValue("reporting").kind)
        assertEquals(NavigationSymbolKind.STRUCT, symbolsByName.getValue("reporting.events").kind)
        assertEquals(NavigationSymbolKind.OBJECT, symbolsByName.getValue("reporting.daily_events").kind)
        assertEquals(NavigationSymbolKind.FUNCTION, symbolsByName.getValue("reporting.refresh_events").kind)
        assertEquals(NavigationSymbolKind.TYPE_ALIAS, symbolsByName.getValue("reporting.event_state").kind)
        assertEquals(NavigationSymbolKind.OBJECT, symbolsByName.getValue("events_id_idx").kind)
        assertEquals(NavigationSymbolKind.OBJECT, symbolsByName.getValue("analytics.kafka_events").kind)
        assertTrue(symbols.all { it.languageId == "sql" })
    }
}
