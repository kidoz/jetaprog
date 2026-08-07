package su.kidoz.jetaprog.build.gradle.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/** JVM loader for Gradle's JUnit-compatible XML test result files. */
public class JvmGradleTestReportLoader : GradleTestReportLoader {
    override suspend fun load(
        projectRoot: String,
        taskPath: String,
        startedAtMillis: Long,
    ): Result<GradleTestRun> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(projectRoot).canonicalFile
                val reports = discoverReports(root, taskPath, startedAtMillis)
                GradleTestRun(reports.map { report -> parseSuite(root, report) }.sortedBy(GradleTestSuite::taskPath))
            }
        }

    private fun discoverReports(
        root: File,
        taskPath: String,
        startedAtMillis: Long,
    ): List<File> {
        val matching =
            root
                .walkTopDown()
                .onEnter { directory -> directory == root || directory.name !in EXCLUDED_DIRECTORIES }
                .filter { file ->
                    file.isFile &&
                        file.name.startsWith("TEST-") &&
                        file.extension == "xml" &&
                        TEST_RESULTS_SEGMENT in file.invariantSeparatorsPath &&
                        matchesTask(root, file, taskPath)
                }.toList()
        val recent = matching.filter { it.lastModified() >= startedAtMillis }
        return recent.ifEmpty { matching }
    }

    private fun matchesTask(
        root: File,
        report: File,
        requestedTaskPath: String,
    ): Boolean {
        val identity = reportIdentity(root, report)
        val requestedTask = requestedTaskPath.substringAfterLast(':')
        val requestedModule = requestedTaskPath.substringBeforeLast(':', missingDelimiterValue = "")
        val broadTask = requestedTask in BROAD_TEST_TASKS || requestedTask.contains("test", ignoreCase = true).not()
        val moduleMatches = requestedModule.isEmpty() || identity.modulePath == requestedModule
        val taskMatches = broadTask || identity.testTask == requestedTask
        return moduleMatches && taskMatches
    }

    private fun parseSuite(
        root: File,
        report: File,
    ): GradleTestSuite {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        documentBuilderFactory.isExpandEntityReferences = false
        documentBuilderFactory.isXIncludeAware = false
        val suiteElement = documentBuilderFactory.newDocumentBuilder().parse(report).documentElement
        val identity = reportIdentity(root, report)
        val cases =
            buildList {
                val nodes = suiteElement.getElementsByTagName("testcase")
                repeat(nodes.length) { index ->
                    add(parseCase(nodes.item(index) as Element, suiteElement.getAttribute("name")))
                }
            }
        return GradleTestSuite(
            name = suiteElement.getAttribute("name"),
            modulePath = identity.modulePath,
            taskPath = identity.taskPath,
            reportPath = report.absolutePath,
            cases = cases,
        )
    }

    private fun parseCase(
        element: Element,
        fallbackSuiteName: String,
    ): GradleTestCase {
        val failure = element.firstChildElement("failure") ?: element.firstChildElement("error")
        val skipped = element.firstChildElement("skipped")
        val status =
            when {
                failure != null -> GradleTestStatus.FAILED
                skipped != null -> GradleTestStatus.SKIPPED
                else -> GradleTestStatus.PASSED
            }
        return GradleTestCase(
            suiteName = element.getAttribute("classname").ifBlank { fallbackSuiteName },
            name = element.getAttribute("name"),
            status = status,
            durationMs = (element.getAttribute("time").toDoubleOrNull().orZero() * MILLIS_PER_SECOND).roundToLong(),
            failureMessage = failure?.getAttribute("message")?.ifBlank { null },
            failureDetails = failure?.textContent?.trim()?.ifBlank { null },
        )
    }

    private fun Element.firstChildElement(tagName: String): Element? {
        val nodes = getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0) as? Element else null
    }

    private fun reportIdentity(
        root: File,
        report: File,
    ): ReportIdentity {
        val relative = report.relativeTo(root).invariantSeparatorsPath
        val moduleDirectory = relative.substringBefore(TEST_RESULTS_SEGMENT).removeSuffix("/")
        val modulePath = moduleDirectory.takeIf(String::isNotEmpty)?.let { ":${it.replace('/', ':')}" }.orEmpty()
        val testTask = relative.substringAfter(TEST_RESULTS_SEGMENT).substringBefore('/')
        val taskPath = if (modulePath.isEmpty()) testTask else "$modulePath:$testTask"
        return ReportIdentity(modulePath, testTask, taskPath)
    }

    private fun Double?.orZero(): Double = this ?: 0.0

    private data class ReportIdentity(
        val modulePath: String,
        val testTask: String,
        val taskPath: String,
    )

    private companion object {
        private const val TEST_RESULTS_SEGMENT = "build/test-results/"
        private const val MILLIS_PER_SECOND = 1_000
        private val EXCLUDED_DIRECTORIES = setOf(".git", ".gradle", ".idea", ".jetaprog", "node_modules")
        private val BROAD_TEST_TASKS = setOf("test", "allTests", "check", "build", "verify")
    }
}
