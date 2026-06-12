package su.kidoz.jetaprog.plugins.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
                .filter { it.isFile && it.extension == "kt" }
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
            if (!file.exists() || file.extension != "kt") return@withContext

            val content = file.readText()
            val symbols = parseSymbols(content, filePath)

            mutex.withLock {
                // Remove old symbols from this file
                symbolsByFile[filePath]?.forEach { symbol ->
                    symbolsByName[symbol.name]?.remove(symbol)
                    symbolsByFqName.remove(symbol.fqName)
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
            val lowerQuery = query.lowercase()
            symbolsByName.entries
                .filter { it.key.lowercase().contains(lowerQuery) }
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
        val lines = content.lines()

        var packageName = ""
        var currentClass: String? = null
        var braceDepth = 0

        // Patterns for parsing
        val packagePattern = Regex("""^package\s+([\w.]+)""")
        val classPattern =
            Regex(
                """^(\s*)(public\s+|private\s+|internal\s+|protected\s+)?(abstract\s+|open\s+|sealed\s+|data\s+|inline\s+|value\s+)*(class|interface|object|enum\s+class|annotation\s+class)\s+(\w+)""",
            )
        val funPattern =
            Regex(
                """^(\s*)(public\s+|private\s+|internal\s+|protected\s+)?(suspend\s+|inline\s+|operator\s+|infix\s+)*(fun\s+)(<[^>]+>\s+)?(\w+\.)?(\w+)\s*\(""",
            )
        val propPattern =
            Regex(
                """^(\s*)(public\s+|private\s+|internal\s+|protected\s+)?(val|var)\s+(<[^>]+>\s+)?(\w+\.)?(\w+)\s*[:=]""",
            )

        lines.forEachIndexed { lineIndex, line ->
            // Track brace depth for class scope
            braceDepth += line.count { it == '{' } - line.count { it == '}' }
            if (braceDepth <= 0) {
                currentClass = null
                braceDepth = 0
            }

            // Package
            packagePattern.find(line)?.let { match ->
                packageName = match.groupValues[1]
            }

            // Class/Interface/Object
            classPattern.find(line)?.let { match ->
                val visibilityStr = match.groupValues[2].trim()
                val kindStr = match.groupValues[4].trim()
                val name = match.groupValues[5]

                val visibility = parseVisibility(visibilityStr)
                val kind =
                    when {
                        kindStr == "interface" -> SymbolKind.INTERFACE
                        kindStr == "object" -> SymbolKind.OBJECT
                        kindStr.startsWith("enum") -> SymbolKind.ENUM
                        kindStr.startsWith("annotation") -> SymbolKind.ANNOTATION
                        else -> SymbolKind.CLASS
                    }

                val fqName = if (packageName.isNotEmpty()) "$packageName.$name" else name
                val nameStart = line.indexOf(name, match.range.first)
                val position = TextPosition(lineIndex, nameStart)
                val endPosition = TextPosition(lineIndex, nameStart + name.length)

                symbols.add(
                    KotlinSymbol(
                        name = name,
                        fqName = fqName,
                        kind = kind,
                        filePath = filePath,
                        range = TextRange(position, endPosition),
                        nameRange = TextRange(position, endPosition),
                        visibility = visibility,
                    ),
                )

                currentClass = name
            }

            // Function
            funPattern.find(line)?.let { match ->
                val visibilityStr = match.groupValues[2].trim()
                val receiverType = match.groupValues[6].trimEnd('.')
                val name = match.groupValues[7]

                val visibility = parseVisibility(visibilityStr)
                val isExtension = receiverType.isNotEmpty()
                val parent = currentClass ?: receiverType.takeIf { it.isNotEmpty() }

                val fqName =
                    buildString {
                        if (packageName.isNotEmpty()) append("$packageName.")
                        if (currentClass != null) append("$currentClass.")
                        append(name)
                    }

                val nameStart = line.indexOf(name, match.range.first)
                val position = TextPosition(lineIndex, nameStart)
                val endPosition = TextPosition(lineIndex, nameStart + name.length)

                symbols.add(
                    KotlinSymbol(
                        name = name,
                        fqName = fqName,
                        kind = SymbolKind.FUNCTION,
                        filePath = filePath,
                        range = TextRange(position, endPosition),
                        nameRange = TextRange(position, endPosition),
                        parent = parent,
                        visibility = visibility,
                        isExtension = isExtension,
                    ),
                )
            }

            // Property
            propPattern.find(line)?.let { match ->
                val visibilityStr = match.groupValues[2].trim()
                val receiverType = match.groupValues[5].trimEnd('.')
                val name = match.groupValues[6]

                val visibility = parseVisibility(visibilityStr)
                val isExtension = receiverType.isNotEmpty()
                val parent = currentClass ?: receiverType.takeIf { it.isNotEmpty() }

                val fqName =
                    buildString {
                        if (packageName.isNotEmpty()) append("$packageName.")
                        if (currentClass != null) append("$currentClass.")
                        append(name)
                    }

                val nameStart = line.indexOf(name, match.range.first)
                val position = TextPosition(lineIndex, nameStart)
                val endPosition = TextPosition(lineIndex, nameStart + name.length)

                symbols.add(
                    KotlinSymbol(
                        name = name,
                        fqName = fqName,
                        kind = SymbolKind.PROPERTY,
                        filePath = filePath,
                        range = TextRange(position, endPosition),
                        nameRange = TextRange(position, endPosition),
                        parent = parent,
                        visibility = visibility,
                        isExtension = isExtension,
                    ),
                )
            }
        }

        return symbols
    }

    private fun parseVisibility(str: String): Visibility =
        when (str) {
            "private" -> Visibility.PRIVATE
            "protected" -> Visibility.PROTECTED
            "internal" -> Visibility.INTERNAL
            else -> Visibility.PUBLIC
        }
}
