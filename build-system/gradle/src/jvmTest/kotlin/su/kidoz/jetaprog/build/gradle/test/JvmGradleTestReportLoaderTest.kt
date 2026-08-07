package su.kidoz.jetaprog.build.gradle.test

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmGradleTestReportLoaderTest {
    @Test
    fun `loads module test reports with failures and skipped cases`() {
        val root = Files.createTempDirectory("jetaprog-test-reports")
        val report = root.resolve("plugins/kotlin/build/test-results/jvmTest/TEST-SampleTest.xml")
        report.parent.createDirectories()
        report.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="SampleTest" tests="3" failures="1" skipped="1" time="0.15">
              <testcase name="passes" classname="sample.SampleTest" time="0.05"/>
              <testcase name="fails" classname="sample.SampleTest" time="0.1">
                <failure message="expected true">stack trace</failure>
              </testcase>
              <testcase name="skips" classname="sample.SampleTest" time="0"><skipped/></testcase>
            </testsuite>
            """.trimIndent(),
        )

        val result =
            runBlocking {
                JvmGradleTestReportLoader().load(
                    projectRoot = root.toString(),
                    taskPath = ":plugins:kotlin:jvmTest",
                    startedAtMillis = 0,
                )
            }.getOrThrow()

        assertEquals(1, result.suites.size)
        assertEquals(3, result.totalCount)
        assertEquals(1, result.passedCount)
        assertEquals(1, result.failedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(150, result.durationMs)
        assertEquals(":plugins:kotlin:jvmTest", result.suites.single().taskPath)
        assertTrue(
            result.suites
                .single()
                .cases[1]
                .failureDetails
                .orEmpty()
                .contains("stack trace"),
        )
    }

    @Test
    fun `filters reports to the requested module and test task`() {
        val root = Files.createTempDirectory("jetaprog-test-filter")
        writePassingReport(root.resolve("first/build/test-results/jvmTest/TEST-First.xml"), "First")
        writePassingReport(root.resolve("second/build/test-results/jvmTest/TEST-Second.xml"), "Second")
        writePassingReport(root.resolve("first/build/test-results/jsTest/TEST-FirstJs.xml"), "FirstJs")

        val result =
            runBlocking {
                JvmGradleTestReportLoader().load(root.toString(), ":first:jvmTest", 0)
            }.getOrThrow()

        assertEquals(listOf(":first:jvmTest"), result.suites.map { it.taskPath })
    }

    private fun writePassingReport(
        path: java.nio.file.Path,
        suiteName: String,
    ) {
        path.parent.createDirectories()
        path.writeText(
            """
            <testsuite name="$suiteName" tests="1" failures="0" skipped="0" time="0.01">
              <testcase name="passes" classname="$suiteName" time="0.01"/>
            </testsuite>
            """.trimIndent(),
        )
    }
}
