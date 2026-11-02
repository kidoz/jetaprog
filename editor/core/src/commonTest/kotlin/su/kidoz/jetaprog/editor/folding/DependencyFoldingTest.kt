package su.kidoz.jetaprog.editor.folding

import su.kidoz.jetaprog.editor.document.LanguageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyFoldingTest {
    @Test
    fun kotlinHeaderRetainsPackageCommentsAndFollowingCode() {
        val text = "/* license */\npackage test\n\nimport a.B\nimport c.D\n\nfun main() {}\n"
        val fold = DependencyFolding.detect(text, LanguageId.KOTLIN).single()
        assertEquals("import a.B\nimport c.D", text.substring(fold.start, fold.end))
        assertEquals(3, fold.startLine)
        assertEquals(4, fold.endLine)
        assertEquals(2, fold.declarationCount)
    }

    @Test
    fun multilineImportsAndGoBlocksRemainComplete() {
        val js = "import {\n  foo,\n  bar\n} from 'lib';\nconst x = 1;\n"
        assertEquals(3, DependencyFolding.detect(js, LanguageId.TYPESCRIPT).single().endLine)
        val go = "package main\nimport (\n \"fmt\"\n \"os\"\n)\nfunc main() {}"
        assertEquals(4, DependencyFolding.detect(go, LanguageId.GO).single().endLine)
    }

    @Test
    fun conditionalIncludesAreSeparateAndKeepDirectivesVisible() {
        val text = "#include <a>\n#include <b>\n#if FOO\n#include <c>\n#include <d>\n#endif\nint main() {}"
        val folds = DependencyFolding.detect(text, LanguageId.CPP)
        assertEquals(listOf(0, 3), folds.map { it.startLine })
        assertEquals(listOf(1, 4), folds.map { it.endLine })
        assertTrue(folds.all { !text.substring(it.start, it.end).contains("#if") })
    }

    @Test
    fun commentsAndDocstringsNeverBecomeImportBlocks() {
        val python = "\"\"\"\nimport fake\nimport fake2\n\"\"\"\nimport os\nimport sys\nprint('hello')"
        assertEquals(4, DependencyFolding.detect(python, LanguageId.PYTHON).single().startLine)
        val kotlin = "/*\nimport fake\nimport fake2\n*/\nfun main() {}"
        assertTrue(DependencyFolding.detect(kotlin, LanguageId.KOTLIN).isEmpty())
    }

    @Test
    fun executableStatementsAndDynamicImportsStayVisible() {
        for (text in listOf(
            "import('lib');\nimport a from 'a';\nimport b from 'b';",
            "const x = 1;\nimport a from 'a';\nimport b from 'b';",
            "import a from 'a'; run();\nimport b from 'b';",
        )) {
            assertTrue(DependencyFolding.detect(text, LanguageId.TYPESCRIPT).isEmpty())
        }
        assertTrue(DependencyFolding.detect("import a\nimport b", LanguageId.PLAIN_TEXT).isEmpty())
    }

    @Test
    fun commentsSeparateBlocksAndCrLfIsPreserved() {
        val text = "import a\r\nimport b\r\n// explanation\r\nimport c\r\nimport d\r\n"
        val folds = DependencyFolding.detect(text, LanguageId.KOTLIN)
        assertEquals(2, folds.size)
        assertEquals("import a\r\nimport b", text.substring(folds[0].start, folds[0].end))
        assertEquals("\r\n// explanation\r\n", text.substring(folds[0].end, folds[1].start))
    }

    @Test
    fun singleLineImportsAndIncompleteDeclarationsStayExpanded() {
        assertTrue(DependencyFolding.detect("import a\nfun f() {}", LanguageId.KOTLIN).isEmpty())
        assertTrue(DependencyFolding.detect("import {\nfoo,\n", LanguageId.TYPESCRIPT).isEmpty())
    }
}
