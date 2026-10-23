package su.kidoz.jetaprog.app.inspection

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.app.mcp.formatInspection
import su.kidoz.jetaprog.lint.config.LintConfiguration
import su.kidoz.jetaprog.lint.engine.DefaultLintEngine
import su.kidoz.jetaprog.lint.model.LintSeverity
import su.kidoz.jetaprog.lint.provider.LintProviderRegistry
import su.kidoz.jetaprog.lint.providers.StyleLintProvider
import su.kidoz.jetaprog.plugins.runtime.services.LintServiceImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Inspections run the registered lint rules for one file and leave disabled or
 * excluded paths unreported as problems.
 */
class FileInspectionRunnerTest {
    @Test
    fun reportsAStyleFindingAndSkipsACleanFile() =
        runTest {
            val runner = runner()
            val path = "/proj/src/A.kt"

            val dirty = runner.inspect(path, "val x = 1 \n", "kotlin")
            val findings = assertIs<FileInspection.Findings>(dirty)
            val finding = findings.results.single()
            assertEquals("style/trailing-whitespace", finding.ruleId.value)
            assertEquals(LintSeverity.WARNING, finding.severity)
            assertEquals(0, finding.range.start.line)
            assertEquals(
                "[WARNING] style/trailing-whitespace src/A.kt:1: Line has trailing whitespace",
                formatInspection("/proj", path, dirty),
            )

            val clean = runner.inspect(path, "val x = 1\n", "kotlin")
            assertEquals("No problems.", formatInspection("/proj", path, clean))
        }

    @Test
    fun honorsDisabledAndExcludedConfiguration() =
        runTest {
            val service = service()
            val runner = FileInspectionRunner(service)
            val path = "/proj/src/A.kt"

            service.setConfiguration(LintConfiguration(enabled = false))
            assertEquals(FileInspection.Disabled, runner.inspect(path, "val x = 1 \n", "kotlin"))
            assertEquals("Inspections are disabled.", formatInspection("/proj", path, FileInspection.Disabled))

            service.setConfiguration(LintConfiguration(excludePatterns = listOf(path)))
            assertEquals(FileInspection.Excluded, runner.inspect(path, "val x = 1 \n", "kotlin"))
            assertEquals(
                "This path is excluded from inspections.",
                formatInspection("/proj", path, FileInspection.Excluded),
            )
        }

    private fun runner(): FileInspectionRunner = FileInspectionRunner(service())

    private fun service(): LintServiceImpl {
        val engine = DefaultLintEngine()
        val registry = LintProviderRegistry(engine)
        return LintServiceImpl(engine, registry).also { it.registerProvider(StyleLintProvider()) }
    }
}
