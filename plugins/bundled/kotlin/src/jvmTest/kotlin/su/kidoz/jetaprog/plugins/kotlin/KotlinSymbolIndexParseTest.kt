package su.kidoz.jetaprog.plugins.kotlin

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The index regexes accepted only visibility modifiers, so `override fun`,
 * annotated declarations, `const val`, companion objects and nested classes were
 * invisible to navigation, and locals inside functions were indexed as members.
 */
class KotlinSymbolIndexParseTest {
    private val root = Files.createTempDirectory("jetaprog-kotlin-parse").toFile()
    private val index = KotlinSymbolIndex()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun indexSource(text: String): String =
        runBlocking {
            val file = File(root, "Sample.kt").apply { writeText(text) }
            index.indexFile(file.absolutePath)
            file.absolutePath
        }

    private fun symbol(name: String): KotlinSymbol? = runBlocking { index.findByName(name).firstOrNull() }

    @Test
    fun modifiersAndAnnotationsDoNotHideDeclarations() {
        indexSource(
            """
            package demo

            @Composable
            fun Screen() {}

            abstract class Base {
                abstract fun render()
                override fun toString(): String = "Base"
                const val LIMIT = 3
                lateinit var name: String
                val lazyValue by lazy { 1 }
                expect fun platform(): String
                suspend fun List<Int>.total(): Int = sum()
                fun `has spaces`() {}
            }
            """.trimIndent(),
        )

        listOf("Screen", "render", "toString", "LIMIT", "name", "lazyValue", "platform", "total", "has spaces")
            .forEach { assertNotNull(symbol(it), "expected $it to be indexed") }
        assertEquals("demo.Base.render", symbol("render")?.fqName)
        assertEquals(true, symbol("total")?.isExtension)
        assertEquals(Visibility.PUBLIC, symbol("render")?.visibility)
    }

    @Test
    fun nestedClassesAndCompanionsKeepTheirParents() {
        indexSource(
            """
            package demo

            class Outer {
                class Inner {
                    fun innerFun() {}
                }
                companion object {
                    fun create() = Outer()
                }
                fun outerFun() {}
            }
            """.trimIndent(),
        )

        assertEquals("demo.Outer.Inner.innerFun", symbol("innerFun")?.fqName)
        assertEquals("Inner", symbol("innerFun")?.parent)
        assertEquals(SymbolKind.COMPANION_OBJECT, symbol("Companion")?.kind)
        assertEquals("demo.Outer.Companion.create", symbol("create")?.fqName)
        assertEquals("Outer", symbol("outerFun")?.parent)
        assertEquals("Outer", symbol("Inner")?.parent)
    }

    @Test
    fun localsInsideFunctionBodiesAreNotMembers() {
        indexSource(
            """
            class Service {
                fun run() {
                    val local = 1
                    fun helper() {}
                }
                val member = 2
            }
            """.trimIndent(),
        )

        assertNull(symbol("local"))
        assertNull(symbol("helper"))
        assertEquals("Service", symbol("member")?.parent)
    }

    @Test
    fun multiLineHeadersAndBracesInStringsDoNotBreakScopes() {
        indexSource(
            """
            data class Config(
                val host: String,
                val port: Int,
            ) : Base() {
                fun url() = "{" + host + "}"
                fun next() {}
            }

            class Bodyless(val x: Int)

            fun topLevel() {}
            """.trimIndent(),
        )

        assertEquals("Config", symbol("host")?.parent)
        assertEquals("Config", symbol("url")?.parent)
        assertEquals("Config", symbol("next")?.parent)
        assertEquals("Bodyless", symbol("x")?.parent)
        assertNull(symbol("topLevel")?.parent)
    }

    @Test
    fun commentedDeclarationsAreSkipped() {
        indexSource(
            """
            // fun commented() {}
            /*
             * class Documented
             */
            fun real() {}
            """.trimIndent(),
        )

        assertNull(symbol("commented"))
        assertNull(symbol("Documented"))
        assertNotNull(symbol("real"))
    }
}
