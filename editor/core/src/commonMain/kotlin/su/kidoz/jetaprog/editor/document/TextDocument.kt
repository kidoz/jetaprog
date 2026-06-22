package su.kidoz.jetaprog.editor.document

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange

/**
 * Unique identifier for a document.
 */
@Serializable
@JvmInline
public value class DocumentUri(
    public val value: String,
) {
    override fun toString(): String = value

    public companion object {
        /**
         * Creates a DocumentUri from a file path.
         */
        public fun fromPath(path: String): DocumentUri = DocumentUri("file://$path")

        /**
         * Creates a DocumentUri from a file path.
         */
        public fun file(path: String): DocumentUri = DocumentUri("file://$path")

        /**
         * Creates a DocumentUri for an untitled document.
         */
        public fun untitled(id: Int): DocumentUri = DocumentUri("untitled:$id")
    }
}

/**
 * Language identifier for a document.
 */
@Serializable
@JvmInline
public value class LanguageId(
    public val value: String,
) {
    override fun toString(): String = value

    public companion object {
        public val PLAIN_TEXT: LanguageId = LanguageId("plaintext")
        public val KOTLIN: LanguageId = LanguageId("kotlin")
        public val RUST: LanguageId = LanguageId("rust")
        public val CPP: LanguageId = LanguageId("cpp")
        public val JAVA: LanguageId = LanguageId("java")
        public val JAVASCRIPT: LanguageId = LanguageId("javascript")
        public val TYPESCRIPT: LanguageId = LanguageId("typescript")
        public val PYTHON: LanguageId = LanguageId("python")
        public val CSHARP: LanguageId = LanguageId("csharp")
        public val MSBUILD: LanguageId = LanguageId("msbuild")
        public val MARKDOWN: LanguageId = LanguageId("markdown")
        public val JSON: LanguageId = LanguageId("json")
        public val YAML: LanguageId = LanguageId("yaml")
        public val TOML: LanguageId = LanguageId("toml")
        public val XML: LanguageId = LanguageId("xml")
        public val HTML: LanguageId = LanguageId("html")
        public val CSS: LanguageId = LanguageId("css")
        public val VALA: LanguageId = LanguageId("vala")
        public val VAPI: LanguageId = LanguageId("vapi")
        public val MESON: LanguageId = LanguageId("meson")
        public val GO: LanguageId = LanguageId("go")

        private val displayNames: Map<String, String> =
            mapOf(
                "plaintext" to "Plain Text",
                "kotlin" to "Kotlin",
                "rust" to "Rust",
                "cpp" to "C++",
                "java" to "Java",
                "javascript" to "JavaScript",
                "typescript" to "TypeScript",
                "python" to "Python",
                "csharp" to "C#",
                "msbuild" to "MSBuild",
                "markdown" to "Markdown",
                "json" to "JSON",
                "yaml" to "YAML",
                "toml" to "TOML",
                "xml" to "XML",
                "html" to "HTML",
                "css" to "CSS",
                "vala" to "Vala",
                "vapi" to "Vala API",
                "meson" to "Meson",
                "go" to "Go",
            )
    }

    /**
     * Human-readable display name for this language.
     */
    public val displayName: String
        get() = Companion.displayNames[value] ?: value.replaceFirstChar { it.uppercase() }
}

/**
 * Represents an immutable text document.
 */
@Serializable
public data class TextDocument(
    /**
     * The unique identifier for this document.
     */
    val uri: DocumentUri,
    /**
     * The language identifier for this document.
     */
    val languageId: LanguageId,
    /**
     * The version of this document (incremented on each change).
     */
    val version: Int,
    /**
     * The text content of this document.
     */
    val content: String,
    /**
     * The file name (without path).
     */
    val fileName: String,
    /**
     * Whether this document has been modified since last save.
     */
    val isDirty: Boolean = false,
    /**
     * The line ending used in this document.
     */
    val lineEnding: LineEnding = LineEnding.LF,
) {
    /**
     * The number of lines in this document.
     */
    val lineCount: Int get() = content.lines().size

    /**
     * The total number of characters.
     */
    val length: Int get() = content.length

    /**
     * Gets a specific line (0-based).
     */
    public fun getLine(lineNumber: Int): String {
        val lines = content.lines()
        return if (lineNumber in lines.indices) lines[lineNumber] else ""
    }

    /**
     * Gets text within a range.
     */
    public fun getText(range: TextRange): String {
        val startOffset = positionToOffset(range.start)
        val endOffset = positionToOffset(range.end)
        return content.substring(startOffset.coerceAtMost(length), endOffset.coerceAtMost(length))
    }

    /**
     * Converts a position to an offset.
     */
    public fun positionToOffset(position: TextPosition): Int {
        val lines = content.lines()
        var offset = 0
        for (i in 0 until position.line.coerceAtMost(lines.size - 1)) {
            offset += lines[i].length + 1 // +1 for newline
        }
        val lineLength = if (position.line < lines.size) lines[position.line].length else 0
        return offset + position.column.coerceAtMost(lineLength)
    }

    /**
     * Converts an offset to a position.
     */
    public fun offsetToPosition(offset: Int): TextPosition {
        var remaining = offset.coerceIn(0, length)
        val lines = content.lines()
        for ((lineNum, line) in lines.withIndex()) {
            if (remaining <= line.length) {
                return TextPosition(lineNum, remaining)
            }
            remaining -= line.length + 1 // +1 for newline
        }
        return TextPosition(lines.size - 1, lines.lastOrNull()?.length ?: 0)
    }

    /**
     * Creates a new document with updated content.
     */
    public fun withContent(newContent: String): TextDocument =
        copy(
            content = newContent,
            version = version + 1,
            isDirty = true,
        )

    /**
     * Creates a new document marked as saved.
     */
    public fun markSaved(): TextDocument = copy(isDirty = false)
}

/**
 * Line ending types.
 */
public enum class LineEnding(
    public val value: String,
) {
    LF("\n"),
    CRLF("\r\n"),
    CR("\r"),
}
