package su.kidoz.jetaprog.app.terminal

/**
 * Snapshot of the terminal screen grid.
 */
internal data class TerminalScreenSnapshot(
    val lines: List<String>,
    val styledLines: List<TerminalStyledLine>,
    val cursorRow: Int,
    val cursorColumn: Int,
    val cursorLineIndex: Int,
    val inputMode: TerminalInputMode = TerminalInputMode(),
    val isCursorVisible: Boolean = true,
    val title: String? = null,
)

internal fun terminalCellWidth(codePoint: Int): Int =
    when {
        codePoint == ZERO_WIDTH_JOINER -> 0
        ZERO_WIDTH_RANGES.any { codePoint in it } -> 0
        Character.getType(codePoint).isZeroWidthCharacterType() -> 0
        WIDE_CHARACTER_RANGES.any { codePoint in it } -> 2
        else -> 1
    }

internal fun terminalCellWidth(character: Char): Int = terminalCellWidth(character.code)

/**
 * VT-style terminal emulator used by the desktop terminal panel.
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
    private val sequenceBuffer: StringBuilder = StringBuilder()
    private var controlStringType: Char = ' '
    private val scrollback: ArrayDeque<TerminalStyledLine> = ArrayDeque()
    private var currentStyle: TerminalCellStyle = TerminalCellStyle()
    private var currentHyperlink: String? = null
    private var primaryScreen: MutableList<MutableList<MutableTerminalCell>> = blankScreen()
    private var alternateScreen: MutableList<MutableList<MutableTerminalCell>>? = null
    private var screen: MutableList<MutableList<MutableTerminalCell>> = primaryScreen
    private var applicationCursorKeys: Boolean = false
    private var bracketedPaste: Boolean = false
    private var focusReporting: Boolean = false
    private var applicationKeypad: Boolean = false
    private var joinNextCodePoint: Boolean = false
    private var title: String? = null
    private val pendingResponses: ArrayDeque<String> = ArrayDeque()

    /**
     * Apply terminal output to the screen grid and return the updated snapshot.
     */
    internal fun accept(text: String): TerminalScreenSnapshot {
        text.codePoints().forEach { codePoint -> acceptCodePoint(codePoint) }
        return snapshot()
    }

    /**
     * Remove and return replies generated for terminal device queries.
     */
    internal fun drainResponses(): List<String> =
        buildList {
            while (pendingResponses.isNotEmpty()) {
                add(pendingResponses.removeFirst())
            }
        }

    /**
     * Clear visible output and scrollback.
     */
    internal fun clear(): TerminalScreenSnapshot {
        setAlternateScreen(false)
        scrollback.clear()
        primaryScreen = blankScreen()
        screen = primaryScreen
        cursorRow = 0
        cursorColumn = 0
        savedCursorRow = 0
        savedCursorColumn = 0
        parserState = ParserState.Ground
        currentStyle = TerminalCellStyle()
        currentHyperlink = null
        joinNextCodePoint = false
        resetScrollRegion()
        sequenceBuffer.clear()
        return snapshot()
    }

    /**
     * Resize both screen buffers while preserving visible content.
     */
    internal fun resize(
        columns: Int,
        rows: Int,
    ): TerminalScreenSnapshot {
        val newColumns = columns.coerceAtLeast(MIN_COLUMNS)
        val newRows = rows.coerceAtLeast(MIN_ROWS)
        if (newColumns == this.columns && newRows == this.rows) return snapshot()

        val oldColumns = this.columns
        val primaryAnchor = if (isAlternateScreenActive()) primaryCursorRow else cursorRow
        val resizedPrimary = resizeScreen(primaryScreen, oldColumns, newColumns, newRows, primaryAnchor)
        val resizedAlternate =
            alternateScreen?.let { resizeScreen(it, oldColumns, newColumns, newRows, cursorRow) }
        primaryScreen = resizedPrimary.first
        alternateScreen = resizedAlternate?.first
        this.columns = newColumns
        this.rows = newRows
        screen = alternateScreen ?: primaryScreen
        val activeRowOffset = resizedAlternate?.second ?: resizedPrimary.second
        cursorRow = (cursorRow - activeRowOffset).coerceIn(0, newRows - 1)
        cursorColumn = cursorColumn.coerceIn(0, newColumns)
        primaryCursorRow = (primaryCursorRow - resizedPrimary.second).coerceIn(0, newRows - 1)
        primaryCursorColumn = primaryCursorColumn.coerceIn(0, newColumns)
        savedCursorRow = (savedCursorRow - activeRowOffset).coerceIn(0, newRows - 1)
        savedCursorColumn = savedCursorColumn.coerceIn(0, newColumns)
        resetScrollRegion()
        return snapshot()
    }

    /**
     * Current screen and scrollback lines.
     */
    internal fun snapshot(): TerminalScreenSnapshot {
        val visible = visibleScreenLines()
        val allLines = scrollback.toList() + visible
        return TerminalScreenSnapshot(
            lines = allLines.map(TerminalStyledLine::plainText),
            styledLines = allLines,
            cursorRow = cursorRow,
            cursorColumn = cursorColumn,
            cursorLineIndex = scrollback.size + cursorRow,
            inputMode =
                TerminalInputMode(
                    applicationCursorKeys = applicationCursorKeys,
                    bracketedPaste = bracketedPaste,
                    focusReporting = focusReporting,
                    applicationKeypad = applicationKeypad,
                ),
            isCursorVisible = isCursorVisible,
            title = title,
        )
    }

    private fun visibleScreenLines(): List<TerminalStyledLine> {
        val lines = screen.map { row -> row.toStyledLine(columns) }
        val lastContentRow = lines.indexOfLast { row -> row.plainText.isNotEmpty() }
        return lines.take(maxOf(lastContentRow, cursorRow) + 1)
    }

    private fun acceptCodePoint(codePoint: Int) {
        if (codePoint <= Char.MAX_VALUE.code) {
            acceptCharacter(codePoint.toChar())
        } else if (parserState == ParserState.Ground) {
            putPrintable(codePoint)
        }
    }

    private fun acceptCharacter(character: Char) {
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
            ESC -> {
                parserState = ParserState.Escape
            }

            '\r' -> {
                cursorColumn = 0
                joinNextCodePoint = false
            }

            '\n' -> {
                lineFeed()
                joinNextCodePoint = false
            }

            '\b' -> {
                cursorColumn = (cursorColumn - 1).coerceAtLeast(0)
            }

            '\t' -> {
                cursorColumn = (cursorColumn + TAB_WIDTH - cursorColumn % TAB_WIDTH).coerceAtMost(columns - 1)
            }

            BEL -> {
                return
            }

            in C0_CONTROLS -> {
                return
            }

            else -> {
                putPrintable(character.code)
            }
        }
    }

    private fun acceptEscape(character: Char) {
        when (character) {
            '[' -> {
                startSequence(ParserState.Csi)
            }

            ']', 'P', '^', '_' -> {
                controlStringType = character
                startSequence(ParserState.ControlString)
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
                applicationKeypad = true
            }

            '>' -> {
                applicationKeypad = false
            }
        }
        if (parserState == ParserState.Escape) parserState = ParserState.Ground
    }

    private fun startSequence(state: ParserState) {
        sequenceBuffer.clear()
        parserState = state
    }

    private fun acceptControlString(character: Char) {
        when (character) {
            BEL -> finishControlString()
            ESC -> parserState = ParserState.ControlStringEscape
            else -> sequenceBuffer.append(character)
        }
    }

    private fun acceptControlStringEscape(character: Char) {
        if (character == '\\') {
            finishControlString()
        } else {
            sequenceBuffer.append(ESC).append(character)
            parserState = ParserState.ControlString
        }
    }

    private fun finishControlString() {
        if (controlStringType == ']') executeOsc(sequenceBuffer.toString())
        sequenceBuffer.clear()
        parserState = ParserState.Ground
    }

    private fun executeOsc(sequence: String) {
        val command = sequence.substringBefore(';')
        val payload = sequence.substringAfter(';', missingDelimiterValue = "")
        when (command) {
            "0", "2" -> {
                title = payload.take(MAX_TITLE_LENGTH)
            }

            "8" -> {
                val destination = payload.substringAfter(';', missingDelimiterValue = "")
                currentHyperlink = destination.ifBlank { null }
            }

            "10" -> {
                if (payload == "?") pendingResponses += "\u001b]10;rgb:dddd/dddd/dddd\u001b\\"
            }

            "11" -> {
                if (payload == "?") pendingResponses += "\u001b]11;rgb:1111/1111/1111\u001b\\"
            }
        }
    }

    private fun acceptCsi(character: Char) {
        if (character.code in CSI_FINAL_RANGE) {
            executeCsi(character, sequenceBuffer.toString())
            sequenceBuffer.clear()
            parserState = ParserState.Ground
        } else {
            sequenceBuffer.append(character)
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
        if (final == 'u' && sequence.startsWith('?')) {
            return
        }
        if (final == 'u' && sequence.startsWith('>')) return

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

            'm' -> {
                applyGraphicRendition(params)
            }

            'n' -> {
                if (params.valueOrDefault(0, 0) == CURSOR_POSITION_REPORT) reportCursorPosition()
            }

            'c' -> {
                reportDeviceAttributes(sequence)
            }
        }
    }

    private fun executePrivateMode(
        sequence: String,
        enabled: Boolean,
    ) {
        sequence.removePrefix("?").split(';').mapNotNull(String::toIntOrNull).forEach { mode ->
            when (mode) {
                1 -> applicationCursorKeys = enabled
                25 -> isCursorVisible = enabled
                47, 1047, 1049 -> setAlternateScreen(enabled)
                1004 -> focusReporting = enabled
                2004 -> bracketedPaste = enabled
            }
        }
    }

    private fun reportCursorPosition() {
        pendingResponses += "\u001b[${cursorRow + 1};${cursorColumn.coerceAtMost(columns - 1) + 1}R"
    }

    private fun reportDeviceAttributes(sequence: String) {
        pendingResponses += if (sequence.startsWith('>')) "\u001b[>0;0;0c" else "\u001b[?1;2c"
    }

    private fun applyGraphicRendition(params: List<Int?>) {
        val values = if (params.isEmpty()) listOf(0) else params.map { it ?: 0 }
        var index = 0
        while (index < values.size) {
            when (val value = values[index]) {
                0 -> currentStyle = TerminalCellStyle()
                1 -> currentStyle = currentStyle.copy(isBold = true)
                2 -> currentStyle = currentStyle.copy(isDim = true)
                3 -> currentStyle = currentStyle.copy(isItalic = true)
                4 -> currentStyle = currentStyle.copy(isUnderlined = true)
                7 -> currentStyle = currentStyle.copy(isInverse = true)
                9 -> currentStyle = currentStyle.copy(isStrikethrough = true)
                22 -> currentStyle = currentStyle.copy(isBold = false, isDim = false)
                23 -> currentStyle = currentStyle.copy(isItalic = false)
                24 -> currentStyle = currentStyle.copy(isUnderlined = false)
                27 -> currentStyle = currentStyle.copy(isInverse = false)
                29 -> currentStyle = currentStyle.copy(isStrikethrough = false)
                in 30..37 -> currentStyle = currentStyle.copy(foreground = ANSI_COLORS[value - 30])
                38 -> index = applyExtendedColor(values, index, foreground = true)
                39 -> currentStyle = currentStyle.copy(foreground = null)
                in 40..47 -> currentStyle = currentStyle.copy(background = ANSI_COLORS[value - 40])
                48 -> index = applyExtendedColor(values, index, foreground = false)
                49 -> currentStyle = currentStyle.copy(background = null)
                in 90..97 -> currentStyle = currentStyle.copy(foreground = ANSI_BRIGHT_COLORS[value - 90])
                in 100..107 -> currentStyle = currentStyle.copy(background = ANSI_BRIGHT_COLORS[value - 100])
            }
            index += 1
        }
    }

    private fun applyExtendedColor(
        values: List<Int>,
        start: Int,
        foreground: Boolean,
    ): Int {
        val mode = values.getOrNull(start + 1)
        val color =
            when (mode) {
                5 -> {
                    values.getOrNull(start + 2)?.let(::indexedColor)
                }

                2 -> {
                    val red = values.getOrNull(start + 2)
                    val green = values.getOrNull(start + 3)
                    val blue = values.getOrNull(start + 4)
                    if (red != null && green != null && blue != null) {
                        TerminalColor(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
                    } else {
                        null
                    }
                }

                else -> {
                    null
                }
            }
        if (color != null) {
            currentStyle =
                if (foreground) currentStyle.copy(foreground = color) else currentStyle.copy(background = color)
        }
        return start +
            if (mode == 2) {
                4
            } else if (mode == 5) {
                2
            } else {
                0
            }
    }

    private fun putPrintable(codePoint: Int) {
        val value = String(Character.toChars(codePoint))
        val cellWidth = terminalCellWidth(codePoint)
        if (cellWidth == 0 || joinNextCodePoint || shouldJoinRegionalIndicator(codePoint)) {
            appendToPreviousCell(value)
            joinNextCodePoint = codePoint == ZERO_WIDTH_JOINER
            return
        }
        if (cursorColumn >= columns) {
            cursorColumn = 0
            lineFeed()
        }
        if (cellWidth == WIDE_CELL_WIDTH && cursorColumn == columns - 1) {
            cursorColumn = 0
            lineFeed()
        }
        clearCell(cursorRow, cursorColumn)
        screen[cursorRow][cursorColumn] =
            MutableTerminalCell(value, cellWidth, currentStyle, currentHyperlink)
        if (cellWidth == WIDE_CELL_WIDTH) {
            screen[cursorRow][cursorColumn + 1] = MutableTerminalCell.continuation()
        }
        cursorColumn = (cursorColumn + cellWidth).coerceAtMost(columns)
    }

    private fun appendToPreviousCell(value: String) {
        val targetColumn = previousBaseColumn() ?: return
        screen[cursorRow][targetColumn].text += value
    }

    private fun previousBaseColumn(): Int? {
        var column = (cursorColumn - 1).coerceAtMost(columns - 1)
        if (column < 0) return null
        while (column > 0 && screen[cursorRow][column].isContinuation) column -= 1
        return column.takeIf { screen[cursorRow][it].text.isNotEmpty() }
    }

    private fun shouldJoinRegionalIndicator(codePoint: Int): Boolean {
        if (codePoint !in REGIONAL_INDICATORS) return false
        val previous = previousBaseColumn()?.let { screen[cursorRow][it].text } ?: return false
        return previous
            .codePoints()
            .toArray()
            .singleOrNull()
            ?.let { it in REGIONAL_INDICATORS } == true
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) {
            scrollUp()
        } else if (cursorRow < rows - 1) {
            cursorRow += 1
        }
    }

    private fun reverseIndex() {
        if (cursorRow == scrollTop) scrollDown() else cursorRow = (cursorRow - 1).coerceAtLeast(0)
    }

    private fun scrollUp() {
        val removedLine = screen.removeAt(scrollTop).toStyledLine(columns)
        if (!isAlternateScreenActive() && scrollTop == 0) {
            scrollback.addLast(removedLine)
            while (scrollback.size > maxScrollbackLines) scrollback.removeFirst()
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
        if (line[column].isContinuation && column > 0) line[column - 1] = blankCell()
        if (column + 1 < columns && line[column + 1].isContinuation) line[column + 1] = blankCell()
        line[column] = blankCell()
    }

    private fun moveCursor(
        row: Int = cursorRow,
        column: Int = cursorColumn,
    ) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorColumn = column.coerceIn(0, columns - 1)
        joinNextCodePoint = false
    }

    private fun saveCursor() {
        savedCursorRow = cursorRow
        savedCursorColumn = cursorColumn
    }

    private fun restoreCursor() = moveCursor(savedCursorRow, savedCursorColumn)

    private fun setAlternateScreen(enabled: Boolean) {
        if (enabled) {
            if (isAlternateScreenActive()) return
            primaryCursorRow = cursorRow
            primaryCursorColumn = cursorColumn
            alternateScreen = blankScreen()
            screen = alternateScreen ?: primaryScreen
            cursorRow = 0
            cursorColumn = 0
        } else {
            if (!isAlternateScreenActive()) return
            alternateScreen = null
            screen = primaryScreen
            moveCursor(primaryCursorRow, primaryCursorColumn)
        }
        resetScrollRegion()
    }

    private fun isAlternateScreenActive(): Boolean = alternateScreen != null

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (row in cursorRow + 1 until rows) screen[row] = blankLine()
            }

            1 -> {
                for (row in 0 until cursorRow) screen[row] = blankLine()
                eraseLine(1)
            }

            2 -> {
                for (row in 0 until rows) screen[row] = blankLine()
            }

            3 -> {
                scrollback.clear()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> fillRow(cursorRow, cursorColumn, columns)
            1 -> fillRow(cursorRow, 0, cursorColumn + 1)
            2 -> fillRow(cursorRow, 0, columns)
        }
    }

    private fun eraseCharacters(count: Int) = fillRow(cursorRow, cursorColumn, cursorColumn + count)

    private fun deleteCharacters(count: Int) {
        val safeCount = count.coerceIn(0, columns - cursorColumn)
        val row = screen[cursorRow]
        for (column in cursorColumn until columns - safeCount) row[column] = row[column + safeCount].copy()
        for (column in columns - safeCount until columns) row[column] = blankCell()
    }

    private fun insertCharacters(count: Int) {
        val safeCount = count.coerceIn(0, columns - cursorColumn)
        val row = screen[cursorRow]
        for (column in columns - 1 downTo cursorColumn + safeCount) row[column] = row[column - safeCount].copy()
        for (column in cursorColumn until cursorColumn + safeCount) row[column] = blankCell()
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceIn(0, scrollBottom - cursorRow + 1)) {
            screen.add(cursorRow, blankLine())
            screen.removeAt(scrollBottom + 1)
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        repeat(count.coerceIn(0, scrollBottom - cursorRow + 1)) {
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
        } else {
            scrollTop = top
            scrollBottom = bottom
            moveCursor(row = 0, column = 0)
        }
    }

    private fun resetScrollRegion() {
        scrollTop = 0
        scrollBottom = rows - 1
    }

    private fun fillRow(
        row: Int,
        fromColumn: Int,
        toColumnExclusive: Int,
    ) {
        for (column in fromColumn.coerceAtLeast(0) until toColumnExclusive.coerceAtMost(columns)) {
            clearCell(row, column)
        }
    }

    private fun blankCell(): MutableTerminalCell = MutableTerminalCell(style = currentStyle)

    private fun blankLine(): MutableList<MutableTerminalCell> = MutableList(columns) { blankCell() }

    private fun blankScreen(): MutableList<MutableList<MutableTerminalCell>> = MutableList(rows) { blankLine() }

    private fun resizeScreen(
        source: List<List<MutableTerminalCell>>,
        oldColumns: Int,
        newColumns: Int,
        newRows: Int,
        anchorRow: Int,
    ): Pair<MutableList<MutableList<MutableTerminalCell>>, Int> {
        val removedTopRows =
            if (source.size > newRows) {
                (anchorRow - newRows + 1).coerceIn(0, source.size - newRows)
            } else {
                0
            }
        val visibleRows = source.drop(removedTopRows).take(newRows)
        val result = mutableListOf<MutableList<MutableTerminalCell>>()
        visibleRows.forEach { oldRow ->
            val newRow = MutableList(newColumns) { blankCell() }
            for (column in 0 until minOf(oldColumns, newColumns, oldRow.size)) newRow[column] = oldRow[column].copy()
            result += newRow
        }
        while (result.size < newRows) result += MutableList(newColumns) { blankCell() }
        return result to removedTopRows
    }

    private fun parseCsiParams(sequence: String): List<Int?> {
        val parameterText =
            sequence.dropWhile { it == '?' || it == '>' || it == '=' }.takeWhile { it == ';' || it.isDigit() }
        if (parameterText.isEmpty()) return emptyList()
        return parameterText.split(';').map(String::toIntOrNull)
    }

    private fun List<Int?>.valueOrDefault(
        index: Int,
        default: Int,
    ): Int = getOrNull(index)?.takeIf { it > 0 } ?: default

    private fun indexedColor(index: Int): TerminalColor {
        val safeIndex = index.coerceIn(0, 255)
        if (safeIndex < ANSI_COLORS.size) return ANSI_COLORS[safeIndex]
        if (safeIndex < 16) return ANSI_BRIGHT_COLORS[safeIndex - ANSI_COLORS.size]
        if (safeIndex >= COLOR_CUBE_END) {
            val gray = GRAYSCALE_START + (safeIndex - COLOR_CUBE_END) * GRAYSCALE_STEP
            return TerminalColor(gray, gray, gray)
        }
        val cube = safeIndex - ANSI_EXTENDED_START
        return TerminalColor(
            COLOR_CUBE_LEVELS[cube / 36],
            COLOR_CUBE_LEVELS[cube / 6 % 6],
            COLOR_CUBE_LEVELS[cube % 6],
        )
    }

    private enum class ParserState {
        Ground,
        Escape,
        Csi,
        ControlString,
        ControlStringEscape,
    }

    internal data class MutableTerminalCell(
        var text: String = "",
        val width: Int = 1,
        val style: TerminalCellStyle = TerminalCellStyle(),
        val hyperlink: String? = null,
        val isContinuation: Boolean = false,
    ) {
        companion object {
            fun continuation(): MutableTerminalCell = MutableTerminalCell(width = 0, isContinuation = true)
        }
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
        private const val WIDE_CELL_WIDTH = 2
        private const val CURSOR_POSITION_REPORT = 6
        private const val MAX_TITLE_LENGTH = 512
        private const val ANSI_EXTENDED_START = 16
        private const val COLOR_CUBE_END = 232
        private const val GRAYSCALE_START = 8
        private const val GRAYSCALE_STEP = 10
        private val C0_CONTROLS: CharRange = '\u0000'..'\u001f'
        private val CSI_FINAL_RANGE: IntRange = 0x40..0x7e
        private val COLOR_CUBE_LEVELS = intArrayOf(0, 95, 135, 175, 215, 255)
        private val ANSI_COLORS =
            listOf(
                TerminalColor(0, 0, 0),
                TerminalColor(205, 49, 49),
                TerminalColor(13, 188, 121),
                TerminalColor(229, 229, 16),
                TerminalColor(36, 114, 200),
                TerminalColor(188, 63, 188),
                TerminalColor(17, 168, 205),
                TerminalColor(229, 229, 229),
            )
        private val ANSI_BRIGHT_COLORS =
            listOf(
                TerminalColor(102, 102, 102),
                TerminalColor(241, 76, 76),
                TerminalColor(35, 209, 139),
                TerminalColor(245, 245, 67),
                TerminalColor(59, 142, 234),
                TerminalColor(214, 112, 214),
                TerminalColor(41, 184, 219),
                TerminalColor(255, 255, 255),
            )
    }
}

private fun List<TerminalEmulator.MutableTerminalCell>.toStyledLine(columns: Int): TerminalStyledLine {
    val lastContentColumn = indexOfLast { it.text.isNotEmpty() || it.isContinuation }
    if (lastContentColumn < 0) return TerminalStyledLine("", emptyList())
    val segments = mutableListOf<TerminalStyledSegment>()
    var segmentText = StringBuilder()
    var segmentStart = 0
    var segmentWidth = 0
    var segmentStyle: TerminalCellStyle? = null
    var segmentHyperlink: String? = null

    fun flush() {
        val style = segmentStyle ?: return
        segments += TerminalStyledSegment(segmentText.toString(), segmentStart, segmentWidth, style, segmentHyperlink)
        segmentText = StringBuilder()
        segmentWidth = 0
    }

    var column = 0
    while (column <= lastContentColumn && column < columns) {
        val cell = this[column]
        if (cell.isContinuation) {
            column += 1
            continue
        }
        if (segmentStyle != cell.style || segmentHyperlink != cell.hyperlink) {
            flush()
            segmentStart = column
            segmentStyle = cell.style
            segmentHyperlink = cell.hyperlink
        }
        segmentText.append(cell.text.ifEmpty { " " })
        segmentWidth += cell.width
        column += cell.width.coerceAtLeast(1)
    }
    flush()
    return TerminalStyledLine(segments.joinToString("") { it.text }.trimEnd(), segments)
}

private const val ZERO_WIDTH_JOINER = 0x200d
private val REGIONAL_INDICATORS: IntRange = 0x1f1e6..0x1f1ff
private val ZERO_WIDTH_RANGES: List<IntRange> =
    listOf(0x0300..0x036f, 0x1ab0..0x1aff, 0x1dc0..0x1dff, 0xfe00..0xfe0f, 0xe0100..0xe01ef)
private val WIDE_CHARACTER_RANGES: List<IntRange> =
    listOf(
        0x1100..0x115f,
        0x231a..0x231b,
        0x2329..0x232a,
        0x2600..0x27bf,
        0x2b00..0x2bff,
        0x2e80..0xa4cf,
        0xac00..0xd7a3,
        0xf900..0xfaff,
        0xfe10..0xfe19,
        0xfe30..0xfe6f,
        0xff00..0xff60,
        0xffe0..0xffe6,
        0x1f000..0x1faff,
        0x20000..0x3fffd,
    )

private fun Int.isZeroWidthCharacterType(): Boolean =
    this == Character.NON_SPACING_MARK.toInt() ||
        this == Character.COMBINING_SPACING_MARK.toInt() ||
        this == Character.ENCLOSING_MARK.toInt() ||
        this == Character.FORMAT.toInt()
