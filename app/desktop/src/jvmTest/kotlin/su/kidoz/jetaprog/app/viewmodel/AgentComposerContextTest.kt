package su.kidoz.jetaprog.app.viewmodel

import su.kidoz.jetaprog.app.ui.agent.ComposerAttachment
import su.kidoz.jetaprog.app.ui.agent.mentionQuery
import su.kidoz.jetaprog.app.ui.agent.promptWithContext
import su.kidoz.jetaprog.app.ui.agent.withoutMentionToken
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.editor.cursor.Cursor
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.state.EditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure helpers for `@` mentions, prompt context, and the editor selection excerpt. */
class AgentComposerContextTest {
    @Test
    fun mentionQueryReadsTheTrailingAtToken() {
        assertNull(mentionQuery(""))
        assertNull(mentionQuery("see @file "))
        assertNull(mentionQuery("no mention"))
        assertEquals("", mentionQuery("@"))
        assertEquals("Rea", mentionQuery("see @Rea"))
        assertEquals("Reader.kt", mentionQuery("first @Other\n@Reader.kt"))
    }

    @Test
    fun choosingAMentionRemovesOnlyTheTrailingToken() {
        assertEquals("see", withoutMentionToken("see @Rea"))
        assertEquals("line\n", withoutMentionToken("line\n@Reader.kt"))
        assertEquals("", withoutMentionToken("@Reader.kt"))
        assertEquals("keep this", withoutMentionToken("keep this"))
    }

    @Test
    fun promptCarriesFilesAndSelectionsAheadOfTheUserText() {
        val prompt =
            promptWithContext(
                "Explain this",
                listOf(
                    ComposerAttachment(label = "Reader.kt", path = "src/Reader.kt"),
                    ComposerAttachment(label = "Reader.kt", path = "src/Reader.kt", excerpt = "val x = 1"),
                ),
            )

        assertEquals(
            "Context:\nFile: src/Reader.kt\n\nSelection in src/Reader.kt:\nval x = 1\n\nExplain this",
            prompt,
        )
        assertEquals("Explain this", promptWithContext("Explain this", emptyList()))
    }

    @Test
    fun editorSnippetUsesTheWorkspaceRelativePathAndTheSelection() {
        val snippet =
            editorSnippet(
                workspacePath = "/proj",
                state =
                    EditorState(
                        activeDocumentUri = DocumentUri.file("/proj/src/Reader.kt"),
                        content = "abc\ndef\nghi",
                        cursor = Cursor(position = TextPosition(1, 3), anchor = TextPosition(1, 1)),
                    ),
            )

        assertEquals("Reader.kt", snippet?.label)
        assertEquals("src/Reader.kt", snippet?.path)
        assertEquals("ef", snippet?.selection)
    }

    @Test
    fun editorSnippetOmitsUntitledBuffersAndBlankSelections() {
        assertNull(editorSnippet("/proj", EditorState(activeDocumentUri = DocumentUri.untitled(1))))
        assertNull(editorSnippet("/proj", EditorState()))
        val outside =
            editorSnippet(
                "/proj",
                EditorState(activeDocumentUri = DocumentUri.file("/other/A.kt"), content = "a"),
            )
        assertEquals("/other/A.kt", outside?.path)
        assertNull(outside?.selection)
        assertNull(selectionExcerpt("   \n", TextPosition(0, 0), TextPosition(0, 3)))
    }

    @Test
    fun selectionExcerptIsCapped() {
        val excerpt =
            selectionExcerpt("a".repeat(SELECTION_EXCERPT_MAX_CHARS + 25), TextPosition(0, 0), TextPosition(0, 4_000))
        assertTrue(excerpt!!.endsWith("…"))
        assertEquals(SELECTION_EXCERPT_MAX_CHARS + 1, excerpt.length)
    }
}
