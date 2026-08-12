package su.kidoz.jetaprog.plugins.dotnet.prolog

import su.kidoz.jetaprog.build.dotnet.ProjectFileParser

/**
 * Text-level analysis of `.dplproj` project files for DotProlog markers.
 *
 * DotProlog projects are SDK-style MSBuild projects that reference the `DotProlog.Sdk`
 * either through a nested `<Sdk Name="DotProlog.Sdk" Version="..." />` element or
 * directly in the `<Project Sdk="...">` attribute. The analysis is regex-based so it
 * works without an MSBuild evaluation, mirroring [ProjectFileParser].
 */
internal object DotPrologProjectFile {
    /** Whether the project file references the DotProlog MSBuild SDK. */
    fun isDotPrologProject(content: String): Boolean =
        SDK_ELEMENT_PATTERN.containsMatchIn(content) ||
            ProjectFileParser.parseSdk(content)?.contains(SDK_NAME, ignoreCase = true) == true

    /**
     * The requested `DotProlog.Sdk` version, e.g. `0.5.0`, or null when the SDK element
     * is absent or carries no version.
     */
    fun sdkVersion(content: String): String? {
        val element = SDK_ELEMENT_PATTERN.find(content)?.value ?: return null
        return SDK_VERSION_PATTERN.find(element)?.groupValues?.get(1)
    }

    private const val SDK_NAME = "DotProlog.Sdk"

    private val SDK_ELEMENT_PATTERN =
        Regex("""<Sdk\s+[^>]*?Name\s*=\s*"DotProlog\.Sdk"[^>]*>""", RegexOption.IGNORE_CASE)

    private val SDK_VERSION_PATTERN =
        Regex("""Version\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
}
