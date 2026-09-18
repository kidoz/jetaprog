package su.kidoz.jetaprog.editor.navigation

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.lsp.protocol.LspDocumentSymbol
import su.kidoz.jetaprog.lsp.protocol.LspHover
import su.kidoz.jetaprog.lsp.protocol.LspLocation
import su.kidoz.jetaprog.lsp.protocol.LspSymbolKind

/**
 * Adapter to convert LSP protocol types to navigation domain types.
 */
public class LspNavigationAdapter {
    /**
     * Convert an LSP location to a NavigationTarget.
     *
     * Returns null for locations the editor cannot open: library schemes such as
     * `jdt://` or `jar:` used to be passed through as if they were paths and ended
     * in a "Failed to open file" error.
     */
    public fun toNavigationTarget(
        location: LspLocation,
        name: String? = null,
        kind: NavigationSymbolKind = NavigationSymbolKind.UNKNOWN,
    ): NavigationTarget? {
        val filePath = uriToPath(location.uri) ?: return null
        val fileName = filePath.substringAfterLast('/')

        return NavigationTarget(
            name = name ?: fileName,
            qualifiedName = filePath,
            kind = kind,
            filePath = filePath,
            position =
                TextPosition(
                    line = location.range.start.line,
                    column = location.range.start.character,
                ),
            endPosition =
                TextPosition(
                    line = location.range.end.line,
                    column = location.range.end.character,
                ),
        )
    }

    /**
     * Convert a list of LSP locations to NavigationTargets, dropping those without a file path.
     */
    public fun toNavigationTargets(locations: List<LspLocation>): List<NavigationTarget> =
        locations.mapNotNull { toNavigationTarget(it) }

    /**
     * Convert LSP document symbols to StructureItems.
     */
    public fun toStructureItems(
        symbols: List<LspDocumentSymbol>,
        filePath: String,
        depth: Int = 0,
    ): List<StructureItem> =
        symbols.map { symbol ->
            StructureItem(
                target =
                    NavigationTarget(
                        name = symbol.name,
                        qualifiedName = symbol.name,
                        kind = mapSymbolKind(symbol.kind),
                        filePath = filePath,
                        position =
                            TextPosition(
                                line = symbol.selectionRange.start.line,
                                column = symbol.selectionRange.start.character,
                            ),
                        endPosition =
                            TextPosition(
                                line = symbol.selectionRange.end.line,
                                column = symbol.selectionRange.end.character,
                            ),
                        detail = symbol.detail,
                    ),
                visibility = SymbolVisibility.PUBLIC,
                children =
                    symbol.children?.let { toStructureItems(it, filePath, depth + 1) }
                        ?: emptyList(),
                depth = depth,
            )
        }

    /**
     * Convert LSP locations to FindUsagesResult.
     */
    public fun toFindUsagesResult(
        symbol: NavigationTarget,
        locations: List<LspLocation>,
    ): FindUsagesResult {
        val usagesByFile =
            locations
                .mapNotNull { location -> uriToPath(location.uri)?.let { path -> path to location } }
                .groupBy({ it.first }, { it.second })

        val groups =
            usagesByFile.map { (filePath, fileLocations) ->
                UsageGroup(
                    filePath = filePath,
                    fileName = filePath.substringAfterLast('/'),
                    usages =
                        fileLocations.mapNotNull { location ->
                            val target = toNavigationTarget(location) ?: return@mapNotNull null
                            UsageInfo(
                                target = target,
                                usageKind = UsageKind.UNKNOWN,
                                contextLine = "", // Would need file content to populate
                                lineNumber = location.range.start.line + 1,
                                columnRange =
                                    MatchRange(
                                        start = location.range.start.character,
                                        endInclusive = location.range.end.character,
                                    ),
                            )
                        },
                )
            }

        return FindUsagesResult(
            symbol = symbol,
            groups = groups,
            totalCount = locations.size,
        )
    }

    /**
     * Convert LSP hover to QuickInfo.
     */
    public fun toQuickInfo(
        symbol: NavigationTarget,
        hover: LspHover,
    ): QuickInfo =
        QuickInfo(
            symbol = symbol,
            documentation = hover.contents.value,
            definitionPreview = null,
            signature = null,
        )

    /**
     * Map LSP symbol kind to navigation symbol kind.
     */
    public fun mapSymbolKind(kind: LspSymbolKind): NavigationSymbolKind =
        when (kind) {
            LspSymbolKind.File -> NavigationSymbolKind.FILE
            LspSymbolKind.Module -> NavigationSymbolKind.MODULE
            LspSymbolKind.Namespace -> NavigationSymbolKind.NAMESPACE
            LspSymbolKind.Package -> NavigationSymbolKind.PACKAGE
            LspSymbolKind.Class -> NavigationSymbolKind.CLASS
            LspSymbolKind.Method -> NavigationSymbolKind.METHOD
            LspSymbolKind.Property -> NavigationSymbolKind.PROPERTY
            LspSymbolKind.Field -> NavigationSymbolKind.FIELD
            LspSymbolKind.Constructor -> NavigationSymbolKind.CONSTRUCTOR
            LspSymbolKind.Enum -> NavigationSymbolKind.ENUM
            LspSymbolKind.Interface -> NavigationSymbolKind.INTERFACE
            LspSymbolKind.Function -> NavigationSymbolKind.FUNCTION
            LspSymbolKind.Variable -> NavigationSymbolKind.VARIABLE
            LspSymbolKind.Constant -> NavigationSymbolKind.CONSTANT
            LspSymbolKind.String -> NavigationSymbolKind.CONSTANT
            LspSymbolKind.Number -> NavigationSymbolKind.CONSTANT
            LspSymbolKind.Boolean -> NavigationSymbolKind.CONSTANT
            LspSymbolKind.Array -> NavigationSymbolKind.VARIABLE
            LspSymbolKind.Object -> NavigationSymbolKind.OBJECT
            LspSymbolKind.Key -> NavigationSymbolKind.PROPERTY
            LspSymbolKind.Null -> NavigationSymbolKind.CONSTANT
            LspSymbolKind.EnumMember -> NavigationSymbolKind.ENUM_MEMBER
            LspSymbolKind.Struct -> NavigationSymbolKind.STRUCT
            LspSymbolKind.Event -> NavigationSymbolKind.FUNCTION
            LspSymbolKind.Operator -> NavigationSymbolKind.FUNCTION
            LspSymbolKind.TypeParameter -> NavigationSymbolKind.TYPE_PARAMETER
        }

    /**
     * Map LSP symbol kind to navigation symbol kind (alias for mapSymbolKind).
     */
    public fun toLspSymbolKind(kind: LspSymbolKind): NavigationSymbolKind = mapSymbolKind(kind)

    /**
     * Path for a `file://` URI; null for any other scheme (`jdt://`, `jar:`, `zipfile:`).
     */
    private fun uriToPath(uri: String): String? = uri.takeIf { it.startsWith("file://") }?.removePrefix("file://")
}
