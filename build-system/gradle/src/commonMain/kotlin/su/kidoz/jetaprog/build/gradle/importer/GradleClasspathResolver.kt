package su.kidoz.jetaprog.build.gradle.importer

/**
 * Resolves the Gradle module and analysis classpath that own a workspace file.
 *
 * Module paths are matched longest-first so nested modules win over their
 * parents. The returned classpath includes the owning module and its transitive
 * project dependencies, while files outside imported modules fall back to the
 * complete workspace classpath.
 */
public class GradleClasspathResolver(
    private val projectRoot: String,
    private val model: GradleImportModel,
) {
    private val normalizedRoot = projectRoot.normalizedPath().trimEnd('/')
    private val modulesByIdentity: Map<String, GradleModuleModel> =
        buildMap {
            model.modules.forEach { module ->
                put(module.name, module)
                put(module.path, module)
                put(module.path.replace('/', ':'), module)
                put(":" + module.path.replace('/', ':'), module)
            }
        }

    /** Finds the most specific imported module containing [filePath]. */
    public fun moduleFor(filePath: String): GradleModuleModel? {
        val relativePath = filePath.workspaceRelativePath()
        return model.modules
            .asSequence()
            .filter { module ->
                val modulePath = module.path.normalizedPath().trim('/')
                relativePath == modulePath || relativePath.startsWith("$modulePath/")
            }.maxByOrNull { it.path.length }
    }

    /**
     * Returns the external dependency classpath visible to [filePath].
     *
     * Project-module dependencies are traversed because some Gradle models only
     * place their external libraries on the dependency module itself.
     */
    public fun classpathFor(filePath: String): List<String> {
        val owner = moduleFor(filePath) ?: return workspaceClasspath()
        val visited = mutableSetOf<String>()
        val classpath = linkedSetOf<String>()

        fun visit(module: GradleModuleModel) {
            if (!visited.add(module.path)) return
            classpath.addAll(module.classpath)
            module.moduleDependencies
                .mapNotNull(modulesByIdentity::get)
                .forEach(::visit)
        }

        visit(owner)
        return classpath.toList()
    }

    /** Returns the union classpath used for files outside imported modules. */
    public fun workspaceClasspath(): List<String> = model.modules.flatMap { it.classpath }.distinct()

    private fun String.workspaceRelativePath(): String {
        val normalized = normalizedPath()
        return normalized
            .removePrefix("$normalizedRoot/")
            .trimStart('/')
    }
}

private fun String.normalizedPath(): String = replace('\\', '/').replace(Regex("/+"), "/")
