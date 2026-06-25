package su.kidoz.jetaprog.app.terminal

/**
 * Snapshot of the terminal screen grid.
 */
internal data class TerminalScreenSnapshot(
    val lines: List<String>,
    val cursorRow: Int,
    val cursorColumn: Int,
    val cursorLineIndex: Int,
    val inputMode: TerminalInputMode = TerminalInputMode(),
    val isCursorVisible: Boolean = true,
)

internal fun terminalCellWidth(character: Char): Int =
    when {
        character == WIDE_CONTINUATION -> 0
        ZERO_WIDTH_RANGES.any { character in it } -> 0
        Character.getType(character).isZeroWidthCharacterType() -> 0
        WIDE_CHARACTER_RANGES.any { character in it } -> 2
        else -> 1
    }

/**
 * Small VT-style terminal emulator for the first grid-rendering phase.
 */
internal class TerminalEmulator(
    columns: Int = DEFAULT_COLUMNS,
    rows: Int = DEFAULT_ROWS,
    private val maxScrollbackLines: Int = DEFAULT_SCROLLBACK_LINES,
) {
    private var columns: Int = columns.coerceAtLeast(MIN_COLUMNS)
    private var rows: Int = rows.coerceAtLeast(MIN_ROWS)
    private var cursorRow: Int = 0
    private var cursorColumn: Int = 0
    private var savedCursorRow: Int = 0
    private var savedCursorColumn: Int = 0
    private var primaryCursorRow: Int = 0
    private var primaryCursorColumn: Int = 0
    private var scrollTop: Int = 0
    private var scrollBottom: Int = this.rows - 1
    private var isCursorVisible: Boolean = true
    private var parserState: ParserState = ParserState.Ground
    private val csiBuffer: StringBuilder = StringBuilder()
    private val scrollback: ArrayDeque<String> = ArrayDeque()
    private val primaryScreen: MutableList<CharArray> = MutableList(this.rows) { blankLine() }
    private var alternateScreen: MutableList<CharArray>? = null
    private var screen: MutableList<CharArray> = primaryScreen
    private var applicationCursorKeys: Boolean = false

    /**
     * Apply terminal output to the screen grid and return the updated snapshot.
     */
    internal fun accept(text: String): TerminalScreenSnapshot {
        text.codePoints().forEach { codePoint -> acceptCodePoint(codePoint) }
        return snapshot()
    }

    /**
     * Clear visible output and scrollback.
     */
    internal fun clear(): TerminalScreenSnapshot {
        scrollback.clear()
        screen.clear()
        repeat(rows) {
            screen += blankLine()
        }
        cursorRow = 0
        cursorColumn = 0
        parserState = ParserState.Ground
        resetScrollRegion()
        csiBuffer.clear()
        return snapshot()
    }

    /**
     * Resize the screen grid while preserving as much visible text as possible.
     */
    internal fun resize(
        columns: Int,
        rows: Int,
    ): TerminalScreenSnapshot {
        val newColumns = columns.coerceAtLeast(MIN_COLUMNS)
        val newRows = rows.coerceAtLeast(MIN_ROWS)
        if (newColumns == this.columns && newRows == this.rows) {
            return snapshot()
        }

        val oldLines = screen.map { row -> row.toTerminalString() }
        this.columns = newColumns
        this.rows = newRows

        val visibleLines = oldLines.takeLast(newRows)
        screen.clear()
        repeat(newRows - visibleLines.size) {
            screen += blankLine()
        }
        visibleLines.forEach { line ->
            screen += line.toTerminalRow(newColumns)
        }

        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorColumn = cursorColumn.coerceIn(0, newColumns - 1)
        resetScrollRegion()
        return snapshot()
    }

    /**
     * Current screen and scrollback lines.
     */
    internal fun snapshot(): TerminalScreenSnapshot =
        TerminalScreenSnapshot(
            lines = scrollback.toList() + visibleScreenLines(),
            cursorRow = cursorRow,
            cursorColumn = cursorColumn,
            cursorLineIndex = scrollback.size + cursorRow,
            inputMode = TerminalInputMode(applicationCursorKeys = applicationCursorKeys),
            isCursorVisible = isCursorVisible,
        )

    private fun visibleScreenLines(): List<String> {
        val rows = screen.map { row -> row.toTerminalString() }
        val lastContentRow = rows.indexOfLast { row -> row.isNotEmpty() }
        val lastVisibleRow = maxOf(lastContentRow, cursorRow)
        return rows.take(lastVisibleRow + 1)
    }

    private fun acceptCodePoint(codePoint: Int) {
        if (codePoint <= Char.MAX_VALUE.code) {
            accept(codePoint.toChar())
        } else {
            putPrintable(REPLACEMENT_CHARACTER)
        }
    }

    private fun accept(character: Char) {
        when (parserState) {
            ParserState.Ground -> acceptGround(character)
            ParserState.Escape -> acceptEscape(character)
            ParserState.Csi -> acceptCsi(character)
            ParserState.ControlString -> acceptControlString(character)
            ParserState.ControlStringEscape -> acceptControlStringEscape(character)
        }
    }

    private fun acceptGround(character: Char) {
        when (character) {
            ESC -> parserState = ParserState.Escape
            '\r' -> cursorColumn = 0
            '\n' -> lineFeed()
            '\b' -> cursorColumn = (cursorColumn - 1).coerceAtLeast(0)
            '\t' -> cursorColumn = (cursorColumn + TAB_WIDTH - cursorColumn % TAB_WIDTH).coerceAtMost(columns - 1)
            BEL -> Unit
            in C0_CONTROLS -> Unit
            else -> putPrintable(character)
        }
    }

    private fun acceptEscape(character: Char) {
        when (character) {
            '[' -> {
                csiBuffer.clear()
                parserState = ParserState.Csi
            }

            ']', 'P', '^', '_' -> {
                parserState = ParserState.ControlString
            }

            'c' -> {
                clear()
            }

            '7' -> {
                saveCursor()
            }

            '8' -> {
                restoreCursor()
            }

            'D' -> {
                lineFeed()
            }

            'E' -> {
                cursorColumn = 0
                lineFeed()
            }

            'M' -> {
                reverseIndex()
            }

            '=' -> {
                // Application keypad mode. Keypad support is not modeled yet.
            }

            '>' -> {
                // Numeric keypad mode. Keypad support is not modeled yet.
            }
        }
        if (parserState == ParserState.Escape) {
            parserState = ParserState.Ground
        }
    }

    private fun acceptControlString(character: Char) {
        when (character) {
            BEL -> parserState = ParserState.Ground
            ESC -> parserState = ParserState.ControlStringEscape
        }
    }

    private fun acceptControlStringEscape(character: Char) {
        parserState =
            if (character == '\\') {
                ParserState.Ground
            } else {
                ParserState.ControlString
            }
    }

    private fun acceptCsi(character: Char) {
        if (character.code in CSI_FINAL_RANGE) {
            executeCsi(character, csiBuffer.toString())
            csiBuffer.clear()
            parserState = ParserState.Ground
        } else {
            csiBuffer.append(character)
        }
    }

    private fun executeCsi(
        final: Char,
        sequence: String,
    ) {
        if ((final == 'h' || final == 'l') && sequence.startsWith('?')) {
            executePrivateMode(sequence, enabled = final == 'h')
            return
        }

        val params = parseCsiParams(sequence)
        when (final) {
            'A' -> {
                moveCursor(row = cursorRow - params.valueOrDefault(0, 1))
            }

            'B' -> {
                moveCursor(row = cursorRow + params.valueOrDefault(0, 1))
            }

            'C' -> {
                moveCursor(column = cursorColumn + params.valueOrDefault(0, 1))
            }

            'D' -> {
                moveCursor(column = cursorColumn - params.valueOrDefault(0, 1))
            }

            'E' -> {
                moveCursor(row = cursorRow + params.valueOrDefault(0, 1), column = 0)
            }

            'F' -> {
                moveCursor(row = cursorRow - params.valueOrDefault(0, 1), column = 0)
            }

            'G' -> {
                moveCursor(column = params.valueOrDefault(0, 1) - 1)
            }

            'H', 'f' -> {
                moveCursor(
                    row = params.valueOrDefault(0, 1) - 1,
                    column = params.valueOrDefault(1, 1) - 1,
                )
            }

            'J' -> {
                eraseDisplay(params.valueOrDefault(0, 0))
            }

            'K' -> {
                eraseLine(params.valueOrDefault(0, 0))
            }

            'X' -> {
                eraseCharacters(params.valueOrDefault(0, 1))
            }

            'P' -> {
                deleteCharacters(params.valueOrDefault(0, 1))
            }

            '@' -> {
                insertCharacters(params.valueOrDefault(0, 1))
            }

            'L' -> {
                insertLines(params.valueOrDefault(0, 1))
            }

            'M' -> {
                deleteLines(params.valueOrDefault(0, 1))
            }

            'S' -> {
                repeat(params.valueOrDefault(0, 1)) { scrollUp() }
            }

            'T' -> {
                repeat(params.valueOrDefault(0, 1)) { scrollDown() }
            }

            's' -> {
                saveCursor()
            }

            'u' -> {
                restoreCursor()
            }

            'r' -> {
                setScrollRegion(
                    top = params.valueOrDefault(0, 1) - 1,
                    bottom = params.valueOrDefault(1, rows) - 1,
                )
            }

            'm', 'h', 'l', 'n' -> {}
        }
    }

    private fun executePrivateMode(
        sequence: String,
        enabled: Boolean,
    ) {
        sequence
            .removePrefix("?")
            .split(';')
            .mapNotNull { mode -> mode.toIntOrNull() }
            .forEach { mode ->
                when (mode) {
                    1 -> applicationCursorKeys = enabled
                    25 -> isCursorVisible = enabled
                    47, 1047, 1049 -> setAlternateScreen(enabled)
                }
            }
    }

    private fun putPrintable(character: Char) {
        val cellWidth = terminalCellWidth(character)
        if (cellWidth == 0) return
        if (cursorColumn >= columns) {
            cursorColumn = 0
            lineFeed()
        }
        if (cellWidth == WIDE_CELL_WIDTH && cursorColumn == columns - 1) {
            cursorColumn = 0
            lineFeed()
        }
        clearCell(cursorRow, cursorColumn)
        screen[cursorRow][cursorColumn] = character
        if (cellWidth == WIDE_CELL_WIDTH && cursorColumn + 1 < columns) {
            screen[cursorRow][cursorColumn + 1] = WIDE_CONTINUATION
        }
        cursorColumn += cellWidth
        if (cursorColumn >= columns) {
            cursorColumn = columns
        }
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollUp()
        } else if (cursorRow < rows - 1) {
            cursorRow += 1
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) {
            scrollDown()
        } else {
            cursorRow -= 1
        }
    }

    private fun scrollUp() {
        val removedLine = screen.removeAt(scrollTop).toTerminalString()
        if (!isAlternateScreenActive() && scrollTop == 0) {
            scrollback.addLast(removedLine)
            while (scrollback.size > maxScrollbackLines) {
                scrollback.removeFirst()
            }
        }
        screen.add(scrollBottom, blankLine())
    }

    private fun scrollDown() {
        screen.add(scrollTop, blankLine())
        screen.removeAt(scrollBottom + 1)
    }

    private fun clearCell(
        row: Int,
        column: Int,
    ) {
        val line = screen[row]
        if (line[column] == WIDE_CONTINUATION && column > 0) {
            line[column - 1] = ' '
        }
        if (column + 1 < columns && line[column + 1] == WIDE_CONTINUATION) {
            line[column + 1] = ' '
        }
        line[column] = ' '
    }

    private fun moveCursor(
        row: Int = cursorRow,
        column: Int = cursorColumn,
    ) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorColumn = column.coerceIn(0, columns - 1)
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorColumn = cursorColumn
    }

    private fun restoreCursor() {
        moveCursor(savedCursorRow, savedCursorColumn)
    }

    private fun setAlternateScreen(enabled: Boolean) {
        if (enabled) {
            if (isAlternateScreenActive()) return
            primaryCursorRow = cursorRow
            primaryCursorColumn = cursorColumn
            alternateScreen = MutableList(rows) { blankLine() }
            screen = alternateScreen ?: primaryScreen
            cursorRow = 0
            cursorColumn = 0
            resetScrollRegion()
        } else {
            if (!isAlternateScreenActive()) return
            alternateScreen = null
            screen = primaryScreen
            moveCursor(primaryCursorRow, primaryCursorColumn)
            resetScrollRegion()
        }
    }

    private fun isAlternateScreenActive(): Boolean = alternateScreen != null

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (row in cursorRow + 1 until rows) {
                    screen[row] = blankLine()
                }
            }

            1 -> {
                for (row in 0 until cursorRow) {
                    screen[row] = blankLine()
                }
                eraseLine(1)
            }

            2 -> {
                for (row in 0 until rows) {
                    screen[row] = blankLine()
                }
            }

            3 -> {
                scrollback.clear()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> fillRow(cursorRow, cursorColumn, columns, ' ')
            1 -> fillRow(cursorRow, 0, cursorColumn + 1, ' ')
            2 -> fillRow(cursorRow, 0, columns, ' ')
        }
    }

    private fun eraseCharacters(count: Int) {
        fillRow(cursorRow, cursorColumn, (cursorColumn + count).coerceAtMost(columns), ' ')
    }

    private fun deleteCharacters(count: Int) {
        val safeCount = count.coerceIn(0, columns - cursorColumn)
        val row = screen[cursorRow]
        for (column in cursorColumn until columns - safeCount) {
            row[column] = row[column + safeCount]
        }
        for (column in columns - safeCount until columns) {
            row[column] = ' '
        }
    }

    private fun insertCharacters(count: Int) {
        val safeCount = count.coerceIn(0, columns - cursorColumn)
        val row = screen[cursorRow]
        for (column in columns - 1 downTo cursorColumn + safeCount) {
            row[column] = row[column - safeCount]
        }
        for (column in cursorColumn until cursorColumn + safeCount) {
            row[column] = ' '
        }
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val safeCount = count.coerceIn(0, scrollBottom - cursorRow + 1)
        repeat(safeCount) {
            screen.add(cursorRow, blankLine())
            screen.removeAt(scrollBottom + 1)
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val safeCount = count.coerceIn(0, scrollBottom - cursorRow + 1)
        repeat(safeCount) {
            screen.removeAt(cursorRow)
            screen.add(scrollBottom, blankLine())
        }
    }

    private fun setScrollRegion(
        top: Int,
        bottom: Int,
    ) {
        if (top !in 0 until rows || bottom !in 0 until rows || top >= bottom) {
            resetScrollRegion()
            return
        }

        scrollTop = top
        scrollBottom = bottom
        moveCursor(row = 0, column = 0)
    }

    private fun resetScrollRegion() {
        scrollTop = 0
        scrollBottom = rows - 1
    }

    private fun fillRow(
        row: Int,
        fromColumn: Int,
        toColumnExclusive: Int,
        value: Char,
    ) {
        for (column in fromColumn.coerceAtLeast(0) until toColumnExclusive.coerceAtMost(columns)) {
            clearCell(row, column)
            if (value != ' ') {
                screen[row][column] = value
            }
        }
    }

    private fun blankLine(): CharArray = CharArray(columns) { ' ' }

    private fun String.toTerminalRow(columns: Int): CharArray {
        val row = CharArray(columns) { ' ' }
        var column = 0
        for (character in this) {
            val cellWidth = terminalCellWidth(character)
            if (cellWidth == 0) continue
            if (column >= columns) break
            if (cellWidth == WIDE_CELL_WIDTH && column == columns - 1) break
            row[column] = character
            if (cellWidth == WIDE_CELL_WIDTH) {
                row[column + 1] = WIDE_CONTINUATION
            }
            column += cellWidth
        }
        return row
    }

    private fun CharArray.toTerminalString(): String =
        buildString {
            this@toTerminalString.forEach { character ->
                if (character != WIDE_CONTINUATION) {
                    append(character)
                }
            }
        }.trimEnd()

    private fun parseCsiParams(sequence: String): List<Int?> {
        val parameterText =
            sequence
                .dropWhile { character -> character == '?' || character == '>' || character == '=' }
                .takeWhile { character -> character == ';' || character.isDigit() }
        if (parameterText.isEmpty()) return emptyList()
        return parameterText.split(';').map { value -> value.toIntOrNull() }
    }

    private fun List<Int?>.valueOrDefault(
        index: Int,
        default: Int,
    ): Int = getOrNull(index)?.takeIf { it > 0 } ?: default

    private enum class ParserState {
        Ground,
        Escape,
        Csi,
        ControlString,
        ControlStringEscape,
    }

    private companion object {
        private const val DEFAULT_COLUMNS = 120
        private const val DEFAULT_ROWS = 30
        private const val DEFAULT_SCROLLBACK_LINES = 10_000
        private const val MIN_COLUMNS = 1
        private const val MIN_ROWS = 1
        private const val TAB_WIDTH = 8
        private const val ESC = '\u001b'
        private const val BEL = '\u0007'
        private const val REPLACEMENT_CHARACTER = '\u25a1'
        private const val WIDE_CELL_WIDTH = 2
        private val C0_CONTROLS: CharRange = '\u0000'..'\u001f'
        private val CSI_FINAL_RANGE: IntRange = 0x40..0x7e
    }
}

private const val WIDE_CONTINUATION = '\u0000'

private val ZERO_WIDTH_RANGES: List<CharRange> =
    listOf(
        '\u0300'..'\u036f',
        '\ufe00'..'\ufe0f',
        '\u200b'..'\u200f',
    )

private val WIDE_CHARACTER_RANGES: List<CharRange> =
    listOf(
        '\u1100'..'\u115f',
        '\u231a'..'\u231b',
        '\u2329'..'\u232a',
        '\u2600'..'\u27bf',
        '\u2b00'..'\u2bff',
        '\u2e80'..'\ua4cf',
        '\uac00'..'\ud7a3',
        '\uf900'..'\ufaff',
        '\ufe10'..'\ufe19',
        '\ufe30'..'\ufe6f',
        '\uff00'..'\uff60',
        '\uffe0'..'\uffe6',
    )

private fun Int.isZeroWidthCharacterType(): Boolean =
    this == Character.NON_SPACING_MARK.toInt() ||
        this == Character.ENCLOSING_MARK.toInt() ||
        this == Character.FORMAT.toInt()
