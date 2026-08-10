package su.kidoz.jetaprog.build.dotnet

/**
 * A NuGet package reference parsed from a project file (.csproj/.fsproj/.vbproj).
 */
public data class PackageReference(
    /** Package id, e.g. `Microsoft.AspNetCore.OpenApi`. */
    val id: String,
    /** Requested version, when specified inline. */
    val version: String? = null,
)

/**
 * Lightweight regex-based parser for MSBuild project files.
 *
 * Extracts the information framework detection needs (SDK id and package references)
 * without a full MSBuild evaluation.
 */
public object ProjectFileParser {
    private val sdkPattern =
        Regex("""<Project\s+[^>]*?Sdk\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

    private val packageReferencePattern =
        Regex(
            """<PackageReference\s+[^>]*?Include\s*=\s*"([^"]+)"(?:[^>]*?Version\s*=\s*"([^"]+)")?""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Returns the `Sdk` attribute of the project element, e.g. `Microsoft.NET.Sdk.Web`.
     */
    public fun parseSdk(content: String): String? = sdkPattern.find(content)?.groupValues?.get(1)

    /**
     * Returns all `PackageReference` items declared in the project file.
     */
    public fun parsePackageReferences(content: String): List<PackageReference> =
        packageReferencePattern
            .findAll(content)
            .map { match ->
                PackageReference(
                    id = match.groupValues[1],
                    version = match.groupValues[2].ifEmpty { null },
                )
            }.distinct()
            .toList()
}
