package su.kidoz.jetaprog.plugins.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import su.kidoz.jetaprog.common.text.CamelHumpMatcher
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import java.io.File

/**
 * Index of Kotlin symbols for a project.
 */
public class KotlinSymbolIndex {
    private val mutex = Mutex()
    private val symbolsByFile = mutableMapOf<String, List<KotlinSymbol>>()
    private val symbolsByName = mutableMapOf<String, MutableList<KotlinSymbol>>()
    private val symbolsByFqName = mutableMapOf<String, KotlinSymbol>()

    /**
     * Indexes all Kotlin files in a directory.
     */
    public suspend fun indexDirectory(directory: String): Unit =
        withContext(Dispatchers.IO) {
            val dir = File(directory)
            if (!dir.isDirectory) return@withContext

            dir
                .walkTopDown()
                // Generated sources under build/ and the like used to be indexed too, so Go to
                // Class offered copies of every symbol and definitions could land in build output.
                .onEnter { it == dir || !(it.name.startsWith(".") || it.name in EXCLUDED_DIRECTORIES) }
                .filter { it.isFile && it.extension in KOTLIN_EXTENSIONS }
                .forEach { file ->
                    indexFile(file.absolutePath)
                }
        }

    /**
     * Indexes a single Kotlin file.
     */
    public suspend fun indexFile(filePath: String): Unit =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists() || file.extension !in KOTLIN_EXTENSIONS) return@withContext

            val content = file.readText()
            val symbols = parseSymbols(content, filePath)

            mutex.withLock {
                // Remove old symbols from this file
                symbolsByFile[filePath]?.forEach { symbol ->
                    symbolsByName[symbol.name]?.remove(symbol)
                    if (symbolsByFqName[symbol.fqName] == symbol) symbolsByFqName.remove(symbol.fqName)
                }

                // Add new symbols
                symbolsByFile[filePath] = symbols
                symbols.forEach { symbol ->
                    symbolsByName.getOrPut(symbol.name) { mutableListOf() }.add(symbol)
                    symbolsByFqName[symbol.fqName] = symbol
                }
            }
        }

    /**
     * Removes all symbols of [filePath] (when the file was deleted or moved).
     */
    public suspend fun removeFile(filePath: String): Unit =
        mutex.withLock {
            val removed = symbolsByFile.remove(filePath) ?: return@withLock
            removed.forEach { symbol ->
                symbolsByName[symbol.name]?.let { list ->
                    list.remove(symbol)
                    if (list.isEmpty()) symbolsByName.remove(symbol.name)
                }
                if (symbolsByFqName[symbol.fqName] == symbol) symbolsByFqName.remove(symbol.fqName)
            }
        }

    /**
     * Finds symbols by name.
     */
    public suspend fun findByName(name: String): List<KotlinSymbol> =
        mutex.withLock {
            symbolsByName[name]?.toList() ?: emptyList()
        }

    /**
     * Finds a symbol by fully qualified name.
     */
    public suspend fun findByFqName(fqName: String): KotlinSymbol? =
        mutex.withLock {
            symbolsByFqName[fqName]
        }

    /**
     * Searches symbols matching a query.
     */
    public suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<KotlinSymbol> =
        mutex.withLock {
            val matcher = CamelHumpMatcher(query)
            symbolsByName.entries
                .filter { matcher.matches(it.key) }
                .flatMap { it.value }
                .take(limit)
        }

    /**
     * Gets all symbols in a file.
     */
    public suspend fun getFileSymbols(filePath: String): List<KotlinSymbol> =
        mutex.withLock {
            symbolsByFile[filePath]?.toList() ?: emptyList()
        }

    /**
     * Gets the symbol at a position in a file.
     */
    public suspend fun getSymbolAt(
        filePath: String,
        position: TextPosition,
    ): KotlinSymbol? =
        mutex.withLock {
            symbolsByFile[filePath]?.find { symbol ->
                position.line >= symbol.nameRange.start.line &&
                    position.line <= symbol.nameRange.end.line &&
                    (
                        position.line != symbol.nameRange.start.line ||
                            position.column >= symbol.nameRange.start.column
                    ) &&
                    (
                        position.line != symbol.nameRange.end.line ||
                            position.column <= symbol.nameRange.end.column
                    )
            }
        }

    private fun parseSymbols(
        content: String,
        filePath: String,
    ): List<KotlinSymbol> {
        val symbols = mutableListOf<KotlinSymbol>()
        var packageName = ""
        val scopes = ArrayDeque<Scope>()
        var pending: PendingClass? = null
        var braceDepth = 0

        fun classPath(): List<String> = scopes.filter { it.isClass }.map { it.name }

        fun fqNameOf(
            name: String,
            parents: List<String>,
        ): String =
            buildString {
                if (packageName.isNotEmpty()) append("$packageName.")
                parents.forEach { append("$it.") }
                append(name)
            }

        fun addClass(
            name: String,
            kind: SymbolKind,
            visibility: Visibility,
            nameRange: TextRange,
        ) {
            symbols +=
                KotlinSymbol(
                    name = name,
                    fqName = fqNameOf(name, classPath()),
                    kind = kind,
                    filePath = filePath,
                    range = nameRange,
                    nameRange = nameRange,
                    parent = scopes.lastOrNull { it.isClass }?.name,
                    visibility = visibility,
                )
        }

        content.split('\n').forEachIndexed { lineIndex, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                return@forEachIndexed
            }
            val code = codeOnly(line)
            val depthBefore = braceDepth
            braceDepth = maxOf(0, braceDepth + code.count { it == '{' } - code.count { it == '}' })
            while (scopes.isNotEmpty() && braceDepth < scopes.last().depthInside) scopes.removeLast()
            if (pending?.let { braceDepth < it.depth } == true) pending = null

            val indent = line.length - trimmed.length
            val insideFunction = scopes.lastOrNull()?.isClass == false

            packagePattern.find(code)?.let { packageName = it.groupValues[1] }

            val companion = companionPattern.find(code)
            val declaration = companion ?: classPattern.find(code)
            if (declaration != null) {
                val header = pending
                if (header != null && indent <= header.indent) pending = null
                val modifiers = declaration.groupValues[2]
                val keyword = declaration.groupValues[3]
                val nameGroup = declaration.groups[4]
                val name = nameGroup?.value ?: DEFAULT_COMPANION_NAME
                val nameRange =
                    nameGroup?.range?.let { lineRange(lineIndex, it) } ?: lineRange(lineIndex, declaration.range)
                val kind =
                    when {
                        companion != null -> SymbolKind.COMPANION_OBJECT
                        "enum " in modifiers -> SymbolKind.ENUM
                        "annotation " in modifiers -> SymbolKind.ANNOTATION
                        keyword == "interface" -> SymbolKind.INTERFACE
                        keyword == "object" -> SymbolKind.OBJECT
                        else -> SymbolKind.CLASS
                    }
                addClass(name, kind, parseVisibility(modifiers), nameRange)
                // `class Point(val x: Int, val y: Int)`: constructor properties on the header line.
                constructorPropPattern.findAll(code, declaration.range.last + 1).forEach { property ->
                    val propertyName = property.groups[2] ?: return@forEach
                    val propertyRange = lineRange(lineIndex, propertyName.range)
                    symbols +=
                        KotlinSymbol(
                            name = propertyName.value,
                            fqName = fqNameOf(propertyName.value, classPath() + name),
                            kind = SymbolKind.PROPERTY,
                            filePath = filePath,
                            range = propertyRange,
                            nameRange = propertyRange,
                            parent = name,
                            visibility = parseVisibility(property.groupValues[1]),
                        )
                }
                when {
                    braceDepth > depthBefore -> scopes.addLast(Scope(name, depthBefore + 1, isClass = true))
                    braceDepth == depthBefore && '{' !in code -> pending = PendingClass(name, indent, braceDepth)
                }
                return@forEachIndexed
            }

            val header = pending
            if (header != null) {
                when {
                    // A line back at the header's indentation that is not its closing `)`
                    // means the class had no body (or the body was on one line).
                    indent <= header.indent && trimmed.isNotEmpty() && !trimmed.startsWith(')') -> {
                        pending = null
                    }

                    // The header that started a few lines up has just opened its body.
                    braceDepth > depthBefore -> {
                        scopes.addLast(Scope(header.name, depthBefore + 1, isClass = true))
                        pending = null
                    }
                }
            }

            initPattern.find(code)?.let {
                if (braceDepth > depthBefore) scopes.addLast(Scope("init", depthBefore + 1, isClass = false))
                return@forEachIndexed
            }

            if (insideFunction) return@forEachIndexed

            funPattern.find(code)?.let { match ->
                val modifiers = match.groupValues[2]
                val receiverType = match.groupValues[3]
                val nameGroup = match.groups[4] ?: return@let
                val name = nameGroup.value.trim('`')
                val parents = classPath() + listOfNotNull(pending?.name)
                val nameRange = lineRange(lineIndex, nameGroup.range)
                symbols +=
                    KotlinSymbol(
                        name = name,
                        fqName = fqNameOf(name, parents),
                        kind = SymbolKind.FUNCTION,
                        filePath = filePath,
                        range = nameRange,
                        nameRange = nameRange,
                        parent = parents.lastOrNull() ?: receiverType.takeIf { it.isNotEmpty() },
                        visibility = parseVisibility(modifiers),
                        isExtension = receiverType.isNotEmpty(),
                    )
                if (braceDepth > depthBefore) scopes.addLast(Scope(name, depthBefore + 1, isClass = false))
                return@forEachIndexed
            }

            propPattern.find(code)?.let { match ->
                val modifiers = match.groupValues[2]
                val receiverType = match.groupValues[3]
                val nameGroup = match.groups[4] ?: return@let
                val name = nameGroup.value.trim('`')
                // Properties declared in a pending class header are its constructor properties.
                val parents = classPath() + listOfNotNull(pending?.name)
                val nameRange = lineRange(lineIndex, nameGroup.range)
                symbols +=
                    KotlinSymbol(
                        name = name,
                        fqName = fqNameOf(name, parents),
                        kind = SymbolKind.PROPERTY,
                        filePath = filePath,
                        range = nameRange,
                        nameRange = nameRange,
                        parent = parents.lastOrNull() ?: receiverType.takeIf { it.isNotEmpty() },
                        visibility = parseVisibility(modifiers),
                        isExtension = receiverType.isNotEmpty(),
                    )
            }
        }

        return symbols
    }

    private fun lineRange(
        lineIndex: Int,
        range: IntRange,
    ): TextRange = TextRange(TextPosition(lineIndex, range.first), TextPosition(lineIndex, range.last + 1))

    /** [line] with string and character literals blanked and the line comment removed. */
    private fun codeOnly(line: String): String =
        line
            .replace(stringLiteral, "\"\"")
            .replace(charLiteral, "''")
            .substringBefore("//")

    private fun parseVisibility(modifiers: String): Visibility =
        when {
            "private " in modifiers -> Visibility.PRIVATE
            "protected " in modifiers -> Visibility.PROTECTED
            "internal " in modifiers -> Visibility.INTERNAL
            else -> Visibility.PUBLIC
        }

    /** A brace-delimited scope; members declared inside a non-class scope are locals and skipped. */
    private data class Scope(
        val name: String,
        val depthInside: Int,
        val isClass: Boolean,
    )

    /** A class header without a body yet: `class Foo(` spanning several lines. */
    private data class PendingClass(
        val name: String,
        val indent: Int,
        val depth: Int,
    )

    public companion object {
        /** Kotlin sources and scripts (`build.gradle.kts`, `*.main.kts`) are both indexed. */
        public val KOTLIN_EXTENSIONS: Set<String> = setOf("kt", "kts")

        /** Directories never walked: build output and dependency caches. */
        public val EXCLUDED_DIRECTORIES: Set<String> =
            setOf("build", "out", "dist", "node_modules", "target", "bin", "obj")

        private const val DEFAULT_COMPANION_NAME = "Companion"

        private const val ANNOTATIONS = """((?:@[\w.]+(?:\([^)]*\))?\s+)*)"""

        /**
         * Every declaration modifier Kotlin allows, in any order. The old patterns
         * accepted only visibility plus a handful of others, so `override fun`,
         * `@Composable fun`, `const val`, `expect class` and friends were invisible
         * to Go to Symbol, structure, auto-import and semantic context nomination.
         */
        private const val MODIFIERS =
            """((?:(?:public|private|internal|protected|abstract|open|final|sealed|data|inline|value|inner|enum|""" +
                """annotation|expect|actual|override|suspend|operator|infix|tailrec|external|const|lateinit|fun|""" +
                """vararg|noinline|crossinline)\s+)*)"""

        private const val RECEIVER = """(?:([\w.]+(?:<[^(]*>)?\??)\.)?"""
        private const val NAME = """(\w+|`[^`]+`)"""

        private val packagePattern = Regex("""^package\s+([\w.]+)""")
        private val companionPattern = Regex("""^\s*$ANNOTATIONS$MODIFIERS(companion\s+object)\b(?:\s+(\w+))?""")
        private val classPattern = Regex("""^\s*$ANNOTATIONS$MODIFIERS(class|interface|object)\s+(\w+)""")
        private val funPattern = Regex("""^\s*$ANNOTATIONS${MODIFIERS}fun\s+(?:<[^>]*>\s+)?$RECEIVER$NAME\s*\(""")
        private val propPattern =
            Regex("""^\s*$ANNOTATIONS$MODIFIERS(?:val|var)\s+(?:<[^>]*>\s+)?$RECEIVER$NAME\s*(?::|=|by\s|$)""")
        private val initPattern = Regex("""^\s*init\s*\{""")
        private val constructorPropPattern = Regex("""$MODIFIERS(?:val|var)\s+(\w+)\s*:""")
        private val stringLiteral = Regex(""""(?:[^"\\]|\\.)*"""")
        private val charLiteral = Regex("""'(?:[^'\\]|\\.)'""")
    }
}
