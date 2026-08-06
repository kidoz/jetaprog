package su.kidoz.jetaprog.editor.completion.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpectedTypeInferenceTest {
    private fun inferAtEnd(code: String): ExpectedTypeContext = ExpectedTypeInference.inferAt(code, code.length)

    @Test
    fun infersACPlainDeclaration() {
        val context = inferAtEnd("int main() {\n    int total = ")

        assertEquals("int", context.expectedType?.name)
        assertEquals(TypeContextKind.Assignment, context.contextKind)
    }

    @Test
    fun infersAQualifiedCppType() {
        val context = inferAtEnd("void f() {\n    std::string name = ")

        assertEquals("std::string", context.expectedType?.name)
    }

    @Test
    fun infersAGenericCppType() {
        val context = inferAtEnd("void f() {\n    std::vector<int> values = ")

        assertEquals("std::vector", context.expectedType?.name)
        assertEquals(listOf("int"), context.expectedType?.typeParameters?.map { it.name })
    }

    @Test
    fun infersThroughConstAndPointers() {
        assertEquals("char", inferAtEnd("void f() {\n    const char *s = ").expectedType?.name)
        assertEquals("Widget", inferAtEnd("void f() {\n    Widget& w = ").expectedType?.name)
    }

    @Test
    fun infersAKotlinDeclaration() {
        val context = inferAtEnd("fun f() {\n    val count: Int = ")

        assertEquals("Int", context.expectedType?.name)
        assertEquals("kotlin.Int", context.expectedType?.qualifiedName)
    }

    @Test
    fun infersANullableKotlinDeclaration() {
        val context = inferAtEnd("fun f() {\n    var name: String? = ")

        assertEquals("String", context.expectedType?.name)
        assertEquals(true, context.expectedType?.isNullable)
    }

    @Test
    fun reportsAssignmentWithoutATypeWhenTheVariableAlreadyExists() {
        val context = inferAtEnd("void f() {\n    total = ")

        assertNull(context.expectedType)
        assertEquals(TypeContextKind.Assignment, context.contextKind)
    }

    @Test
    fun autoCarriesNoTypeInformation() {
        val context = inferAtEnd("void f() {\n    auto value = ")

        assertNull(context.expectedType, "auto tells us nothing, so nothing should be inferred")
    }

    @Test
    fun givesUpOnAFunctionArgument() {
        // The parameter type needs the language server; guessing would filter wrongly.
        val context = inferAtEnd("void f() {\n    compute(")

        assertNull(context.expectedType)
        assertEquals(TypeContextKind.Unknown, context.contextKind)
    }

    @Test
    fun doesNotReachBackToAnEarlierStatement() {
        // The declaration on the previous line must not be matched against this caret.
        val context = inferAtEnd("void f() {\n    int total = 0;\n    compute(")

        assertNull(context.expectedType)
    }

    @Test
    fun doesNotReachAcrossALineBreak() {
        val context = inferAtEnd("void f() {\n    int total =\n        ")

        assertNull(context.expectedType)
    }

    @Test
    fun emptyInputIsSafe() {
        assertEquals(ExpectedTypeContext.None, ExpectedTypeInference.inferAt("", 0))
        assertEquals(ExpectedTypeContext.None, ExpectedTypeInference.inferAt("int x = 1;", 0))
    }

    @Test
    fun offsetOutOfRangeIsClamped() {
        val code = "int total = "
        assertEquals("int", ExpectedTypeInference.inferAt(code, 9999).expectedType?.name)
    }
}
