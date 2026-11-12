package su.kidoz.jetaprog.build.go

import kotlin.test.Test
import kotlin.test.assertEquals

class GoWorkParserTest {
    @Test
    fun parsesUseDirectoriesAndReplacements() {
        val workspace =
            GoWorkParser.parse(
                """
                go 1.25.0

                toolchain go1.25.1

                use (
                    ./app
                    ./lib
                )

                use ./tools

                replace example.com/dep => ../dep
                """.trimIndent(),
            )

        assertEquals("1.25.0", workspace.goVersion)
        assertEquals("go1.25.1", workspace.toolchain)
        assertEquals(listOf("./app", "./lib", "./tools"), workspace.use)
        assertEquals(
            GoReplacement(oldPath = "example.com/dep", newPath = "../dep"),
            workspace.replacements.single(),
        )
    }
}
