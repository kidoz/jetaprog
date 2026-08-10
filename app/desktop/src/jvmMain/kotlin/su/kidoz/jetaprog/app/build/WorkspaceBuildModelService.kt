package su.kidoz.jetaprog.app.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.build.dotnet.ProjectFileParser
import su.kidoz.jetaprog.build.gradle.importer.GradleImportModel
import su.kidoz.jetaprog.plugins.api.services.BuildDependency
import su.kidoz.jetaprog.plugins.api.services.BuildModelService
import java.io.File
import java.io.IOException

/**
 * [BuildModelService] over the workspace's build tooling.
 *
 * Structured sources: the imported Gradle model (Maven coordinates per module) and
 * MSBuild project files (`PackageReference` items). When neither is available yet —
 * e.g. before the Gradle import finishes — [hasDependency] falls back to scanning build
 * files for the literal prefix so framework detection still works at plugin activation.
 */
public class WorkspaceBuildModelService(
    private val workspacePath: String,
    private val gradleModelProvider: () -> GradleImportModel?,
) : BuildModelService {
    override suspend fun dependencies(): List<BuildDependency> =
        withContext(Dispatchers.IO) {
            gradleDependencies() + nugetDependencies()
        }

    override suspend fun hasDependency(prefix: String): Boolean =
        withContext(Dispatchers.IO) {
            val structured = gradleDependencies() + nugetDependencies()
            if (structured.isNotEmpty()) {
                structured.any { it.matches(prefix) } || buildFilesMention(prefix)
            } else {
                buildFilesMention(prefix)
            }
        }

    override suspend fun classpathJars(): List<String> =
        withContext(Dispatchers.IO) {
            gradleModelProvider()
                ?.modules
                ?.flatMap { it.classpath }
                ?.distinct()
                .orEmpty()
        }

    private fun BuildDependency.matches(prefix: String): Boolean =
        group == prefix || group.startsWith("$prefix.") || name.startsWith(prefix)

    private fun gradleDependencies(): List<BuildDependency> =
        gradleModelProvider()
            ?.modules
            ?.flatMap { module -> module.externalDependencies }
            ?.map { BuildDependency(group = it.group, name = it.name, version = it.version) }
            ?.distinct()
            .orEmpty()

    private fun nugetDependencies(): List<BuildDependency> =
        buildFiles { it.extension in MSBUILD_PROJECT_EXTENSIONS }
            .flatMap { file ->
                ProjectFileParser.parsePackageReferences(file.readTextOrEmpty())
            }.map { BuildDependency(group = it.id, name = it.id, version = it.version) }
            .distinct()

    private fun buildFilesMention(prefix: String): Boolean =
        buildFiles { it.name in JVM_BUILD_FILE_NAMES }
            .any { file -> file.readTextOrEmpty().contains(prefix) }

    private fun buildFiles(filter: (File) -> Boolean): List<File> {
        val root = File(workspacePath)
        if (!root.isDirectory) return emptyList()
        return root
            .walkTopDown()
            .maxDepth(BUILD_FILE_SCAN_DEPTH)
            .onEnter { dir -> !dir.name.startsWith(".") && dir.name !in EXCLUDED_DIRECTORIES }
            .filter { it.isFile && filter(it) }
            .toList()
    }

    private fun File.readTextOrEmpty(): String =
        try {
            readText()
        } catch (_: IOException) {
            ""
        }

    private companion object {
        val MSBUILD_PROJECT_EXTENSIONS = setOf("csproj", "fsproj", "vbproj")
        val JVM_BUILD_FILE_NAMES = setOf("build.gradle", "build.gradle.kts", "pom.xml")
        val EXCLUDED_DIRECTORIES = setOf("build", "out", "dist", "node_modules", "target", "bin", "obj")
        const val BUILD_FILE_SCAN_DEPTH = 4
    }
}
