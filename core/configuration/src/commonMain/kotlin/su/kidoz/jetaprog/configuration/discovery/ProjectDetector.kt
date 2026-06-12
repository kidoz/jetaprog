package su.kidoz.jetaprog.configuration.discovery

import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * Detects project type based on files present in the project directory.
 */
public class ProjectDetector(
    private val fileSystem: FileSystem,
) {
    /**
     * Detect all project types in the given directory.
     *
     * @param projectPath The root path to scan.
     * @return List of detected projects (a directory may contain multiple project types).
     */
    public suspend fun detectProjects(projectPath: String): List<DetectedProject> {
        val detected = mutableListOf<DetectedProject>()

        // Check for Gradle project
        detectGradle(projectPath)?.let { detected.add(it) }

        // Check for Cargo/Rust project
        detectCargo(projectPath)?.let { detected.add(it) }

        // Check for Meson project
        detectMeson(projectPath)?.let { detected.add(it) }

        // Check for Python projects (Poetry, UV, pyproject.toml, setup.py)
        detectPython(projectPath)?.let { detected.add(it) }

        // Check for CMake project
        detectCMake(projectPath)?.let { detected.add(it) }

        // Check for Node.js project
        detectNodeJs(projectPath)?.let { detected.add(it) }

        // Check for Go project
        detectGo(projectPath)?.let { detected.add(it) }

        return detected
    }

    /**
     * Detect the primary project type (first detected).
     */
    public suspend fun detectPrimaryProject(projectPath: String): DetectedProject? =
        detectProjects(projectPath).firstOrNull()

    private suspend fun detectGradle(projectPath: String): DetectedProject? {
        val gradleKts = "$projectPath/build.gradle.kts"
        val gradleGroovy = "$projectPath/build.gradle"
        val settingsKts = "$projectPath/settings.gradle.kts"
        val settingsGroovy = "$projectPath/settings.gradle"

        val detectionFile =
            when {
                fileSystem.exists(gradleKts) -> gradleKts
                fileSystem.exists(gradleGroovy) -> gradleGroovy
                fileSystem.exists(settingsKts) -> settingsKts
                fileSystem.exists(settingsGroovy) -> settingsGroovy
                else -> return null
            }

        // Try to extract project name from settings file
        val projectName = extractGradleProjectName(projectPath)

        return DetectedProject(
            type = ProjectType.GRADLE,
            rootPath = projectPath,
            detectionFile = detectionFile,
            projectName = projectName,
        )
    }

    private suspend fun detectCargo(projectPath: String): DetectedProject? {
        val cargoToml = "$projectPath/Cargo.toml"
        if (!fileSystem.exists(cargoToml)) return null

        // Try to extract project name and binary target
        val (projectName, mainEntry) = extractCargoInfo(projectPath)

        return DetectedProject(
            type = ProjectType.CARGO,
            rootPath = projectPath,
            detectionFile = cargoToml,
            projectName = projectName,
            mainEntry = mainEntry,
        )
    }

    private suspend fun detectMeson(projectPath: String): DetectedProject? {
        val mesonBuild = "$projectPath/meson.build"
        if (!fileSystem.exists(mesonBuild)) return null

        val projectName = extractMesonProjectName(projectPath)

        return DetectedProject(
            type = ProjectType.MESON,
            rootPath = projectPath,
            detectionFile = mesonBuild,
            projectName = projectName,
        )
    }

    private suspend fun detectPython(projectPath: String): DetectedProject? {
        val pyprojectToml = "$projectPath/pyproject.toml"
        val setupPy = "$projectPath/setup.py"
        val poetryLock = "$projectPath/poetry.lock"
        val uvLock = "$projectPath/uv.lock"

        // Check for Poetry first (has poetry.lock or [tool.poetry] in pyproject.toml)
        if (fileSystem.exists(poetryLock)) {
            return DetectedProject(
                type = ProjectType.POETRY,
                rootPath = projectPath,
                detectionFile = poetryLock,
                projectName = extractPythonProjectName(projectPath),
            )
        }

        // Check for UV
        if (fileSystem.exists(uvLock)) {
            return DetectedProject(
                type = ProjectType.UV,
                rootPath = projectPath,
                detectionFile = uvLock,
                projectName = extractPythonProjectName(projectPath),
            )
        }

        // Check for pyproject.toml
        if (fileSystem.exists(pyprojectToml)) {
            val content = fileSystem.readText(pyprojectToml).getOrNull() ?: ""

            // Detect Poetry by [tool.poetry] section
            if (content.contains("[tool.poetry]")) {
                return DetectedProject(
                    type = ProjectType.POETRY,
                    rootPath = projectPath,
                    detectionFile = pyprojectToml,
                    projectName = extractPythonProjectName(projectPath),
                )
            }

            // Detect UV by [tool.uv] section
            if (content.contains("[tool.uv]")) {
                return DetectedProject(
                    type = ProjectType.UV,
                    rootPath = projectPath,
                    detectionFile = pyprojectToml,
                    projectName = extractPythonProjectName(projectPath),
                )
            }

            // Generic Python project with pyproject.toml
            val mainEntry = findPythonMainEntry(projectPath)
            return DetectedProject(
                type = ProjectType.PYTHON_PYPROJECT,
                rootPath = projectPath,
                detectionFile = pyprojectToml,
                projectName = extractPythonProjectName(projectPath),
                mainEntry = mainEntry,
            )
        }

        // Check for setup.py (legacy Python)
        if (fileSystem.exists(setupPy)) {
            val mainEntry = findPythonMainEntry(projectPath)
            return DetectedProject(
                type = ProjectType.PYTHON_SETUP,
                rootPath = projectPath,
                detectionFile = setupPy,
                mainEntry = mainEntry,
            )
        }

        return null
    }

    private suspend fun detectCMake(projectPath: String): DetectedProject? {
        val cmakeLists = "$projectPath/CMakeLists.txt"
        if (!fileSystem.exists(cmakeLists)) return null

        return DetectedProject(
            type = ProjectType.CMAKE,
            rootPath = projectPath,
            detectionFile = cmakeLists,
        )
    }

    private suspend fun detectNodeJs(projectPath: String): DetectedProject? {
        val packageJson = "$projectPath/package.json"
        if (!fileSystem.exists(packageJson)) return null

        val projectName = extractNodeProjectName(projectPath)

        return DetectedProject(
            type = ProjectType.NODEJS,
            rootPath = projectPath,
            detectionFile = packageJson,
            projectName = projectName,
        )
    }

    private suspend fun detectGo(projectPath: String): DetectedProject? {
        val goMod = "$projectPath/go.mod"
        if (!fileSystem.exists(goMod)) return null

        return DetectedProject(
            type = ProjectType.GO,
            rootPath = projectPath,
            detectionFile = goMod,
        )
    }

    private suspend fun extractGradleProjectName(projectPath: String): String? {
        val settingsKts = "$projectPath/settings.gradle.kts"
        val settingsGroovy = "$projectPath/settings.gradle"

        val settingsPath =
            when {
                fileSystem.exists(settingsKts) -> settingsKts
                fileSystem.exists(settingsGroovy) -> settingsGroovy
                else -> return null
            }

        val content = fileSystem.readText(settingsPath).getOrNull() ?: return null

        // Match rootProject.name = "..." or rootProject.name = '...'
        val pattern = """rootProject\.name\s*=\s*["']([^"']+)["']""".toRegex()
        return pattern.find(content)?.groupValues?.get(1)
    }

    private suspend fun extractCargoInfo(projectPath: String): Pair<String?, String?> {
        val cargoToml = "$projectPath/Cargo.toml"
        val content = fileSystem.readText(cargoToml).getOrNull() ?: return null to null

        // Extract package name
        val namePattern = """\[package\][^\[]*name\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val projectName = namePattern.find(content)?.groupValues?.get(1)

        // Check for [[bin]] targets
        val binPattern = """\[\[bin\]\][^\[]*name\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val binName = binPattern.find(content)?.groupValues?.get(1)

        // If no explicit bin target, check for src/main.rs
        val mainEntry =
            binName ?: if (fileSystem.exists("$projectPath/src/main.rs")) {
                projectName
            } else {
                null
            }

        return projectName to mainEntry
    }

    private suspend fun extractMesonProjectName(projectPath: String): String? {
        val mesonBuild = "$projectPath/meson.build"
        val content = fileSystem.readText(mesonBuild).getOrNull() ?: return null

        // Match project('name', ...) or project("name", ...)
        val pattern = """project\s*\(\s*['"]([^'"]+)['"]""".toRegex()
        return pattern.find(content)?.groupValues?.get(1)
    }

    private suspend fun extractPythonProjectName(projectPath: String): String? {
        val pyprojectToml = "$projectPath/pyproject.toml"
        val content = fileSystem.readText(pyprojectToml).getOrNull() ?: return null

        // Try [project] name first
        val projectPattern = """\[project\][^\[]*name\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.DOT_MATCHES_ALL)
        projectPattern
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.let { return it }

        // Try [tool.poetry] name
        val poetryPattern =
            """\[tool\.poetry\][^\[]*name\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.DOT_MATCHES_ALL)
        return poetryPattern.find(content)?.groupValues?.get(1)
    }

    private suspend fun findPythonMainEntry(projectPath: String): String? {
        // Look for common Python entry points
        val candidates =
            listOf(
                "$projectPath/main.py",
                "$projectPath/app.py",
                "$projectPath/__main__.py",
                "$projectPath/src/main.py",
                "$projectPath/src/__main__.py",
            )

        return candidates.firstOrNull { fileSystem.exists(it) }
    }

    private suspend fun extractNodeProjectName(projectPath: String): String? {
        val packageJson = "$projectPath/package.json"
        val content = fileSystem.readText(packageJson).getOrNull() ?: return null

        // Simple regex to extract name from package.json
        val pattern = """"name"\s*:\s*"([^"]+)"""".toRegex()
        return pattern.find(content)?.groupValues?.get(1)
    }
}
