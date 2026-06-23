package su.kidoz.jetaprog.app.terminal

/**
 * Snapshot of the terminal screen grid.
 */
internal data class TerminalScreenSnapshot(
    val lines: List<String>,
    val cursorRow: Int,
    val cursorColumn: Int,
)

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
    private var parserState: ParserState = ParserState.Ground
    private val csiBuffer: StringBuilder = StringBuilder()
    private val scrollback: ArrayDeque<String> = ArrayDeque()
    private val screen: MutableList<CharArray> = MutableList(this.rows) { blankLine() }

    /**
     * Apply terminal output to the screen grid and return the updated snapshot.
     */
    internal fun accept(text: String): TerminalScreenSnapshot {
        text.forEach { character -> accept(character) }
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

        val oldLines = screen.map { row -> row.concatToString().trimEnd() }
        this.columns = newColumns
        this.rows = newRows
        screen.clear()

        val visibleLines = oldLines.takeLast(newRows)
        repeat(newRows - visibleLines.size) {
            screen += blankLine()
        }
        visibleLines.forEach { line ->
            val row = blankLine()
            line.take(newColumns).forEachIndexed { index, character ->
                row[index] = character
            }
            screen += row
        }

        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorColumn = cursorColumn.coerceIn(0, newColumns - 1)
        return snapshot()
    }

    /**
     * Current screen and scrollback lines.
     */
    internal fun snapshot(): TerminalScreenSnapshot =
        TerminalScreenSnapshot(
            lines = scrollback.toList() + screen.map { row -> row.concatToString().trimEnd() },
            cursorRow = cursorRow,
            cursorColumn = cursorColumn,
        )

    private fun accept(character: Char) {
        when (parserState) {
            ParserState.Ground -> acceptGround(character)
            ParserState.Escape -> acceptEscape(character)
            ParserState.Csi -> acceptCsi(character)
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
        }
        if (parserState == ParserState.Escape) {
            parserState = ParserState.Ground
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

            'm', 'h', 'l', 'n', 'r' -> {}
        }
    }

    private fun putPrintable(character: Char) {
        if (cursorColumn >= columns) {
            cursorColumn = 0
            lineFeed()
        }
        screen[cursorRow][cursorColumn] = character
        cursorColumn += 1
        if (cursorColumn >= columns) {
            cursorColumn = columns
        }
    }

    private fun lineFeed() {
        if (cursorRow >= rows - 1) {
            scrollUp()
        } else {
            cursorRow += 1
        }
    }

    private fun reverseIndex() {
        if (cursorRow == 0) {
            screen.add(0, blankLine())
            screen.removeAt(screen.lastIndex)
        } else {
            cursorRow -= 1
        }
    }

    private fun scrollUp() {
        scrollback.addLast(screen.removeAt(0).concatToString().trimEnd())
        while (scrollback.size > maxScrollbackLines) {
            scrollback.removeFirst()
        }
        screen += blankLine()
    }

    private fun scrollDown() {
        screen.add(0, blankLine())
        screen.removeAt(screen.lastIndex)
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
        val safeCount = count.coerceIn(0, rows - cursorRow)
        repeat(safeCount) {
            screen.add(cursorRow, blankLine())
            screen.removeAt(screen.lastIndex)
        }
    }

    private fun deleteLines(count: Int) {
        val safeCount = count.coerceIn(0, rows - cursorRow)
        repeat(safeCount) {
            screen.removeAt(cursorRow)
            screen += blankLine()
        }
    }

    private fun fillRow(
        row: Int,
        fromColumn: Int,
        toColumnExclusive: Int,
        value: Char,
    ) {
        for (column in fromColumn.coerceAtLeast(0) until toColumnExclusive.coerceAtMost(columns)) {
            screen[row][column] = value
        }
    }

    private fun blankLine(): CharArray = CharArray(columns) { ' ' }

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
        private val C0_CONTROLS: CharRange = '\u0000'..'\u001f'
        private val CSI_FINAL_RANGE: IntRange = 0x40..0x7e
    }
}
