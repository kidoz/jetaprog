package su.kidoz.jetaprog.build.gradle.wrapper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.tooling.GradleConnector
import java.io.File
import java.nio.file.Files

/** JVM wrapper generator backed by Gradle's Tooling API. */
public class JvmGradleWrapperGenerator : GradleWrapperGenerator {
    override suspend fun generate(
        projectRoot: String,
        spec: GradleWrapperSpec,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = File(projectRoot).canonicalFile
                require(root.isDirectory) { "Project directory does not exist: $root" }
                validateSpec(spec)

                val connector =
                    GradleConnector
                        .newConnector()
                        .forProjectDirectory(root)
                        .useGradleVersion(spec.gradleVersion)

                val initScript = Files.createTempFile("jetaprog-wrapper", ".gradle").toFile()
                val bootstrapSettings = createBootstrapSettingsIfNeeded(root)
                try {
                    initScript.writeText(wrapperInitScript(spec))
                    connector.connect().use { connection ->
                        connection
                            .newBuild()
                            .forTasks("wrapper")
                            .withArguments("--init-script", initScript.absolutePath)
                            .run()
                    }
                } finally {
                    initScript.delete()
                    bootstrapSettings?.delete()
                }

                verifyWrapper(root)
            }
        }

    private fun createBootstrapSettingsIfNeeded(root: File): File? {
        if (GRADLE_BUILD_FILES.any { File(root, it).isFile }) return null

        return File(root, "settings.gradle.kts").apply {
            writeText("")
        }
    }

    private fun validateSpec(spec: GradleWrapperSpec) {
        require(GRADLE_VERSION_PATTERN.matches(spec.gradleVersion)) { "Invalid Gradle version: ${spec.gradleVersion}" }
        require(spec.distributionSha256Sum == null || SHA256_PATTERN.matches(spec.distributionSha256Sum)) {
            "Invalid Gradle distribution checksum"
        }
    }

    private fun wrapperInitScript(spec: GradleWrapperSpec): String =
        buildString {
            appendLine("import org.gradle.api.tasks.wrapper.Wrapper")
            appendLine("allprojects {")
            appendLine("    tasks.withType(Wrapper).configureEach {")
            appendLine("        gradleVersion = '${spec.gradleVersion}'")
            appendLine("        distributionType = Wrapper.DistributionType.BIN")
            spec.distributionSha256Sum?.let { checksum ->
                appendLine("        distributionSha256Sum = '$checksum'")
            }
            appendLine("    }")
            appendLine("}")
        }

    private fun verifyWrapper(root: File) {
        val missing =
            REQUIRED_WRAPPER_PATHS.filterNot { relativePath ->
                File(root, relativePath).isFile
            }
        check(missing.isEmpty()) {
            "Gradle wrapper generation did not create: ${missing.joinToString()}"
        }
    }

    private companion object {
        val GRADLE_VERSION_PATTERN = Regex("[0-9][0-9A-Za-z.-]*")
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
        val REQUIRED_WRAPPER_PATHS =
            listOf(
                "gradlew",
                "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.properties",
            )
        val GRADLE_BUILD_FILES =
            listOf(
                "settings.gradle",
                "settings.gradle.kts",
                "settings.gradle.dcl",
                "build.gradle",
                "build.gradle.kts",
                "build.gradle.dcl",
            )
    }
}
