package su.kidoz.jetaprog.editor.navigation.index

import su.kidoz.jetaprog.editor.navigation.NavigationSymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prolog files had no extractor at all, so Go to Symbol, structure and the
 * definition fallback found nothing in them.
 */
class PrologSymbolExtractorTest {
    @Test
    fun `indexes each predicate once with its arity`() {
        val source =
            """
            :- module(family, [parent/2, ancestor/2]).
            :- dynamic cached/1.

            % facts
            parent(tom, bob).
            parent(bob, ann).

            /* rules
               with a body */
            ancestor(X, Y) :- parent(X, Y).
            ancestor(X, Y) :-
                parent(X, Z),
                ancestor(Z, Y).

            greeting --> [hello], name.
            name --> [world].
            'quoted atom'(A) :- A = 1.
            halt.
            """.trimIndent()

        val symbols = PrologSymbolExtractor().extractSymbols(source, "/workspace/family.pl")
        val byName = symbols.associateBy { it.name }

        assertEquals(NavigationSymbolKind.MODULE, byName.getValue("family").kind)
        assertEquals(1, symbols.count { it.name == "parent" })
        assertEquals("parent/2", byName.getValue("parent").signature)
        assertEquals("family", byName.getValue("parent").containerName)
        assertEquals(4, byName.getValue("parent").line)
        assertEquals(1, symbols.count { it.name == "ancestor" })
        assertEquals("ancestor/2", byName.getValue("ancestor").signature)
        assertEquals("greeting//0", byName.getValue("greeting").signature)
        assertEquals("name//0", byName.getValue("name").signature)
        assertEquals("quoted atom/1", byName.getValue("quoted atom").signature)
        assertEquals("halt/0", byName.getValue("halt").signature)
        assertTrue(symbols.none { it.name == "cached" }, "directives are not predicates")
        assertTrue(symbols.all { it.languageId == "dotprolog" })
    }

    @Test
    fun `nested terms and strings do not confuse the arity`() {
        val source =
            """
            pair(point(X, Y), [A, B], "a, b") :- true.
            """.trimIndent()

        val symbol = PrologSymbolExtractor().extractSymbols(source, "/workspace/p.pl").single()

        assertEquals("pair/3", symbol.signature)
    }
}
