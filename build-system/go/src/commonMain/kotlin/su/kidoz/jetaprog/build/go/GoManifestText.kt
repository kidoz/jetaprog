package su.kidoz.jetaprog.build.go

/**
 * One logical line of a Go manifest after comments are split from code.
 *
 * @property tokens Code tokens, with quotes removed and `=>` kept as one token.
 * @property comment Text after `//`, without the slashes.
 */
internal data class GoManifestLine(
    val tokens: List<String>,
    val comment: String?,
)

/**
 * Splits a `go.mod` or `go.work` file into logical lines.
 *
 * Block comments are removed. A `//` line comment ends the code on that line and is
 * returned separately so callers can see markers such as `indirect`.
 */
internal fun readGoManifestLines(text: String): List<GoManifestLine> =
    stripBlockComments(text)
        .lineSequence()
        .map { line ->
            val commentAt = indexOfLineComment(line)
            val comment = if (commentAt >= 0) line.substring(commentAt + 2).trim().ifEmpty { null } else null
            val code = if (commentAt >= 0) line.substring(0, commentAt) else line
            GoManifestLine(tokenizeManifest(code), comment)
        }.filter { it.tokens.isNotEmpty() }
        .toList()

internal fun parseModuleVersion(args: List<String>): GoModuleVersion? {
    if (args.size < 2) return null
    return GoModuleVersion(path = args[0], version = args[1])
}

internal fun parseReplacement(args: List<String>): GoReplacement? {
    val arrow = args.indexOf("=>")
    if (arrow <= 0 || arrow == args.lastIndex) return null
    val oldSide = args.subList(0, arrow)
    val newSide = args.subList(arrow + 1, args.size)
    val oldPath = oldSide.firstOrNull() ?: return null
    val newPath = newSide.firstOrNull() ?: return null
    return GoReplacement(
        oldPath = oldPath,
        oldVersion = oldSide.getOrNull(1),
        newPath = newPath,
        newVersion = newSide.getOrNull(1),
    )
}

internal fun parseRetraction(
    args: List<String>,
    comment: String?,
): GoRetraction? {
    val rationale = comment?.takeIf { it.isNotBlank() }
    if (args.firstOrNull() == "[" && args.size >= 4) {
        return GoRetraction(low = args[1], high = args[3], rationale = rationale)
    }
    val version = args.firstOrNull() ?: return null
    return GoRetraction(low = version, rationale = rationale)
}

private fun stripBlockComments(text: String): String =
    buildString(text.length) {
        var index = 0
        var inBlock = false
        while (index < text.length) {
            if (inBlock) {
                if (text.startsWith("*/", index)) {
                    inBlock = false
                    index += 2
                } else {
                    if (text[index] == '\n') append('\n')
                    index++
                }
            } else if (text.startsWith("/*", index) && !insideQuotes(text, index)) {
                inBlock = true
                index += 2
            } else {
                append(text[index])
                index++
            }
        }
    }

private fun insideQuotes(
    text: String,
    index: Int,
): Boolean {
    var quotes = 0
    var cursor = 0
    while (cursor < index) {
        if (text[cursor] == '"' && (cursor == 0 || text[cursor - 1] != '\\')) quotes++
        cursor++
    }
    return quotes % 2 == 1
}

private fun indexOfLineComment(line: String): Int {
    var index = 0
    var inQuotes = false
    while (index < line.length) {
        val character = line[index]
        if (character == '"' && (index == 0 || line[index - 1] != '\\')) {
            inQuotes = !inQuotes
        } else if (!inQuotes && line.startsWith("//", index)) {
            return index
        }
        index++
    }
    return -1
}

private fun tokenizeManifest(code: String): List<String> {
    val tokens = mutableListOf<String>()
    var index = 0
    while (index < code.length) {
        val character = code[index]
        when {
            character.isWhitespace() -> {
                index++
            }

            character == '"' -> {
                val end = code.indexOf('"', index + 1).let { if (it < 0) code.lastIndex else it }
                tokens += code.substring(index + 1, end)
                index = end + 1
            }

            code.startsWith("=>", index) -> {
                tokens += "=>"
                index += 2
            }

            character in "()[]," -> {
                tokens += character.toString()
                index++
            }

            else -> {
                val start = index
                while (index < code.length && !isTokenBoundary(code, index)) index++
                if (index > start) tokens += code.substring(start, index)
            }
        }
    }
    return tokens
}

private fun isTokenBoundary(
    code: String,
    index: Int,
): Boolean {
    val character = code[index]
    return character.isWhitespace() || character in "()[],\"" || code.startsWith("=>", index)
}
