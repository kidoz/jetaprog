package su.kidoz.jetaprog.build.gradle.importer

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.tooling.model.idea.IdeaSourceDirectory
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Imports a project's module structure from Gradle using the Tooling API.
 *
 * The importer connects to the build (using the project's own wrapper
 * distribution), fetches the [IdeaProject] model, and maps it into a
 * [GradleImportModel] of modules, source/test/resource roots, generated roots
 * and inter-module dependencies — the editor-neutral source of truth that
 * replaces hand-maintained `.jetaprog` metadata.
 */
public class GradleProjectImporter {
    /**
     * Imports the model for the Gradle build rooted at [projectRoot].
     *
     * Runs on [Dispatchers.IO] because the Tooling API performs blocking I/O and
     * may start a Gradle daemon. Failures (no wrapper, configuration errors,
     * daemon problems) are captured in the returned [Result].
     */
    public suspend fun import(projectRoot: String): Result<GradleImportModel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rootDir = File(projectRoot)
                val connector =
                    GradleConnector
                        .newConnector()
                        .forProjectDirectory(rootDir)
                        .useBuildDistribution()

                connector.connect().use { connection ->
                    val ideaProject = connection.getModel(IdeaProject::class.java)
                    mapModel(ideaProject, rootDir)
                }
            }.onFailure { error ->
                logger.warn(error) { "Gradle import failed for $projectRoot" }
            }
        }

    private fun mapModel(
        ideaProject: IdeaProject,
        rootDir: File,
    ): GradleImportModel {
        val modules =
            ideaProject.modules
                .mapNotNull { module -> mapModule(module, rootDir) }
                .sortedBy { it.path }

        return GradleImportModel(
            rootName = ideaProject.name,
            modules = modules,
            jdkName = ideaProject.jdkName,
            languageLevel = ideaProject.languageLevel?.level,
        )
    }

    private fun mapModule(
        module: IdeaModule,
        rootDir: File,
    ): GradleModuleModel? {
        val relativePath =
            module.gradleProject.path
                .trim(':')
                .replace(':', '/')
        if (relativePath.isEmpty()) return null // skip the root aggregator project

        val moduleDir = File(rootDir, relativePath)
        val sourceRoots = mutableListOf<String>()
        val testRoots = mutableListOf<String>()
        val resourceRoots = mutableListOf<String>()
        val generatedRoots = mutableListOf<String>()

        for (contentRoot in module.contentRoots) {
            collectRoots(contentRoot, moduleDir, sourceRoots, testRoots, resourceRoots, generatedRoots)
        }

        val dependencies =
            module.dependencies
                .filterIsInstance<IdeaModuleDependency>()
                .mapNotNull { it.targetModuleName }
                .distinct()
                .sorted()

        val classpath =
            module.dependencies
                .filterIsInstance<IdeaSingleEntryLibraryDependency>()
                .mapNotNull { it.file?.absolutePath }
                .distinct()
                .sorted()

        return GradleModuleModel(
            path = relativePath,
            name = module.name,
            sourceRoots = sourceRoots.distinct().sorted(),
            testRoots = testRoots.distinct().sorted(),
            resourceRoots = resourceRoots.distinct().sorted(),
            generatedRoots = generatedRoots.distinct().sorted(),
            moduleDependencies = dependencies,
            classpath = classpath,
        )
    }

    private fun collectRoots(
        contentRoot: IdeaContentRoot,
        moduleDir: File,
        sourceRoots: MutableList<String>,
        testRoots: MutableList<String>,
        resourceRoots: MutableList<String>,
        generatedRoots: MutableList<String>,
    ) {
        for (dir in contentRoot.sourceDirectories) {
            if (dir.isGenerated) {
                generatedRoots += dir.relativeTo(moduleDir)
            } else {
                sourceRoots +=
                    dir.relativeTo(moduleDir)
            }
        }
        for (dir in contentRoot.testDirectories) {
            if (dir.isGenerated) generatedRoots += dir.relativeTo(moduleDir) else testRoots += dir.relativeTo(moduleDir)
        }
        for (dir in contentRoot.resourceDirectories) {
            resourceRoots += dir.relativeTo(moduleDir)
        }
    }

    private fun IdeaSourceDirectory.relativeTo(moduleDir: File): String =
        runCatching { directory.relativeTo(moduleDir).invariantSeparatorsPath }
            .getOrDefault(directory.invariantSeparatorsPath)
}
