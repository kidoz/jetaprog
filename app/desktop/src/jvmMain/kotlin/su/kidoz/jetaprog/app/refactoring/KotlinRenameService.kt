package su.kidoz.jetaprog.app.refactoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.search.ProjectTextSearcher
import su.kidoz.jetaprog.editor.search.TextSearchQuery
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.plugins.kotlin.KotlinSymbolIndex
import su.kidoz.jetaprog.plugins.kotlin.analysis.KotlinSemanticAnalyzer

/** A single occurrence to replace, as half-open character offsets within its file. */
public data class RenameOccurrence(
    /** Inclusive start offset. */
    val startOffset: Int,
    /** Exclusive end offset. */
    val endOffset: Int,
)

/**
 * A validated set of occurrences to rename.
 *
 * [originContent] is the content the occurrences were resolved against for
 * [originPath] — typically the live editor buffer rather than the file on disk.
 */
public data class RenamePlan(
    /** The current name of the symbol. */
    val symbolName: String,
    /** File the rename was invoked from. */
    val originPath: String,
    /** Content of [originPath] the plan was computed against. */
    val originContent: String,
    /** Occurrences per file, each sorted by offset. */
    val occurrences: Map<String, List<RenameOccurrence>>,
) {
    /** Total number of occurrences that will be replaced. */
    public val occurrenceCount: Int get() = occurrences.values.sumOf { it.size }

    /** Files the rename will modify. */
    public val affectedFiles: Set<String> get() = occurrences.keys
}

/** Outcome of preparing a rename. */
public sealed interface RenamePreparation {
    /** The rename can proceed. */
    public data class Ready(
        val plan: RenamePlan,
    ) : RenamePreparation

    /** The rename cannot proceed; [reason] is user-facing. */
    public data class Unavailable(
        val reason: String,
    ) : RenamePreparation
}

/** Outcome of applying a rename. */
public sealed interface RenameOutcome {
    /**
     * All files were rewritten.
     *
     * [updatedOriginContent] is the new content of the origin file, for the
     * caller to push into the open editor buffer.
     */
    public data class Applied(
        val filesChanged: Int,
        val occurrencesReplaced: Int,
        val updatedOriginContent: String,
    ) : RenameOutcome

    /** Nothing was written; [reason] is user-facing. */
    public data class Failed(
        val reason: String,
    ) : RenameOutcome
}

/**
 * Renames Kotlin symbols across the project.
 *
 * Occurrences come from binding resolution (see [KotlinSemanticAnalyzer.references]),
 * never from textual matching: a rename driven by text search would silently
 * rewrite comments, string literals and unrelated symbols that share the name.
 * When resolution is unavailable the rename is refused rather than approximated.
 *
 * Before writing, every occurrence is re-checked against the current file
 * content, so a plan that has gone stale (the file changed after it was
 * prepared) aborts without modifying anything.
 */
public class KotlinRenameService(
    private val fileSystem: FileSystem,
    private val symbolIndex: KotlinSymbolIndex,
    private val semanticAnalyzer: KotlinSemanticAnalyzer,
    private val workspacePath: String,
) {
    private val textSearcher = ProjectTextSearcher(fileSystem)

    /**
     * Resolves the symbol at [position] and collects every occurrence to rename.
     *
     * @param content live content of [filePath], which may differ from disk.
     */
    public suspend fun prepare(
        filePath: String,
        position: TextPosition,
        content: String,
    ): RenamePreparation {
        if (!isKotlinFile(filePath)) {
            return RenamePreparation.Unavailable("Rename is available for Kotlin files only.")
        }
        if (!semanticAnalyzer.isReady(filePath)) {
            return RenamePreparation.Unavailable(
                "Rename needs the project classpath, which is still being imported.",
            )
        }
        val identifier =
            identifierAt(content, position)
                ?: return RenamePreparation.Unavailable("Place the caret on a symbol to rename it.")

        val candidates = candidateFiles(identifier, filePath)
        if (candidates.size > MAX_CONTEXT_FILES) {
            return RenamePreparation.Unavailable(
                "\"$identifier\" appears in ${candidates.size} files, more than rename can analyze at once.",
            )
        }

        val offset = position.toOffset(content)
        val references =
            withContext(Dispatchers.Default) {
                runCatching {
                    semanticAnalyzer.references(
                        text = content,
                        offset = offset,
                        contextFiles = candidates,
                        filePath = filePath,
                    )
                }.getOrNull()
            } ?: return RenamePreparation.Unavailable("Could not analyze \"$identifier\" for rename.")

        // Library symbols resolve to a descriptor with no source, so resolution
        // yields nothing; distinguish that from a genuine failure by asking the
        // index whether the project declares this name at all.
        val declaredInProject = symbolIndex.findByName(identifier).isNotEmpty()
        if (references.isEmpty() || references.none { it.isDeclaration }) {
            return RenamePreparation.Unavailable(
                if (declaredInProject) {
                    "Could not resolve \"$identifier\"."
                } else {
                    "\"$identifier\" is declared outside this project and cannot be renamed here."
                },
            )
        }

        val occurrences =
            references
                .groupBy { it.filePath ?: filePath }
                .mapValues { (_, refs) ->
                    refs
                        .map { RenameOccurrence(it.startOffset, it.endOffset) }
                        .distinct()
                        .sortedBy { it.startOffset }
                }

        return RenamePreparation.Ready(
            RenamePlan(
                symbolName = identifier,
                originPath = filePath,
                originContent = content,
                occurrences = occurrences,
            ),
        )
    }

    /**
     * Rewrites every planned occurrence to [newName].
     *
     * Files are written only after all of them have been read and verified, so
     * a stale or unreadable file leaves the project untouched.
     */
    public suspend fun apply(
        plan: RenamePlan,
        newName: String,
    ): RenameOutcome {
        validateName(newName, plan.symbolName)?.let { return RenameOutcome.Failed(it) }

        // Stage every rewrite first; only write once all files verify.
        val staged = mutableMapOf<String, String>()
        for ((path, occurrences) in plan.occurrences) {
            val current =
                if (path == plan.originPath) {
                    plan.originContent
                } else {
                    fileSystem.readText(path).getOrNull()
                        ?: return RenameOutcome.Failed("Could not read ${path.substringAfterLast('/')}.")
                }
            staged[path] =
                rewrite(current, occurrences, plan.symbolName, newName)
                    ?: return RenameOutcome.Failed(
                        "${path.substringAfterLast('/')} changed since the rename was prepared.",
                    )
        }

        for ((path, content) in staged) {
            fileSystem.writeText(path, content).getOrElse {
                return RenameOutcome.Failed("Could not write ${path.substringAfterLast('/')}: ${it.message}")
            }
            symbolIndex.indexFile(path)
        }

        return RenameOutcome.Applied(
            filesChanged = staged.size,
            occurrencesReplaced = plan.occurrenceCount,
            updatedOriginContent = staged.getValue(plan.originPath),
        )
    }

    /**
     * Replaces [occurrences] with [newName], or returns null when any occurrence
     * no longer spells [oldName] — meaning the content moved under the plan.
     */
    private fun rewrite(
        content: String,
        occurrences: List<RenameOccurrence>,
        oldName: String,
        newName: String,
    ): String? {
        val builder = StringBuilder(content)
        // Apply back-to-front so earlier offsets stay valid.
        for (occurrence in occurrences.sortedByDescending { it.startOffset }) {
            if (occurrence.startOffset < 0 || occurrence.endOffset > builder.length) return null
            if (builder.substring(occurrence.startOffset, occurrence.endOffset) != oldName) return null
            builder.replace(occurrence.startOffset, occurrence.endOffset, newName)
        }
        return builder.toString()
    }

    private fun validateName(
        newName: String,
        oldName: String,
    ): String? =
        when {
            newName.isBlank() -> "Enter a new name."
            newName == oldName -> "The new name matches the current one."
            !IDENTIFIER.matches(newName) -> "\"$newName\" is not a valid Kotlin identifier."
            newName in KEYWORDS -> "\"$newName\" is a Kotlin keyword."
            else -> null
        }

    private suspend fun candidateFiles(
        identifier: String,
        originPath: String,
    ): List<String> =
        textSearcher
            .search(
                workspacePath,
                TextSearchQuery(query = identifier, caseSensitive = true, wholeWord = true),
                MAX_SEARCH_RESULTS,
            ).map { it.filePath }
            .filter { isKotlinFile(it) && it != originPath }
            .distinct()

    private fun isKotlinFile(filePath: String): Boolean = filePath.endsWith(".kt") || filePath.endsWith(".kts")

    private fun identifierAt(
        content: String,
        position: TextPosition,
    ): String? {
        val line = content.lines().getOrNull(position.line) ?: return null
        if (position.column > line.length) return null

        var start = position.column.coerceAtMost(line.length)
        var end = start
        while (start > 0 && line[start - 1].isIdentifierChar()) start--
        while (end < line.length && line[end].isIdentifierChar()) end++
        return line.substring(start, end).takeIf { it.isNotEmpty() }
    }

    private fun Char.isIdentifierChar(): Boolean = isLetterOrDigit() || this == '_'

    private fun TextPosition.toOffset(text: String): Int {
        val lines = text.lines()
        if (line >= lines.size) return text.length
        val before = lines.take(line).sumOf { it.length + 1 }
        return (before + column).coerceIn(0, text.length)
    }

    private companion object {
        /** Cap on files analyzed together, mirroring semantic find-usages. */
        const val MAX_CONTEXT_FILES = 24
        const val MAX_SEARCH_RESULTS = 500

        val IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

        /** Hard keywords that can never be used as a plain identifier. */
        val KEYWORDS =
            setOf(
                "as",
                "break",
                "class",
                "continue",
                "do",
                "else",
                "false",
                "for",
                "fun",
                "if",
                "in",
                "interface",
                "is",
                "null",
                "object",
                "package",
                "return",
                "super",
                "this",
                "throw",
                "true",
                "try",
                "typealias",
                "typeof",
                "val",
                "var",
                "when",
                "while",
            )
    }
}
