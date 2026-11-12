package su.kidoz.jetaprog.build.go

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoModuleParserTest {
    @Test
    fun parsesModuleToolchainRequirementsReplacementsAndRetractions() {
        val module =
            GoModuleParser.parse(
                """
                module example.com/acme

                go 1.25.0

                toolchain go1.25.1

                require (
                    example.com/dep v1.2.3
                    example.com/other v0.1.0 // indirect
                )

                require example.com/direct v1.4.0

                exclude example.com/old v1.0.0

                replace example.com/dep v1.2.3 => example.com/dep v1.2.4

                replace example.com/local => ../local

                retract (
                    v1.0.0 // published by mistake
                    [v1.1.0, v1.1.2]
                )
                """.trimIndent(),
            )

        assertEquals("example.com/acme", module.modulePath)
        assertEquals("1.25.0", module.goVersion)
        assertEquals("go1.25.1", module.toolchain)
        assertEquals(
            listOf(
                GoRequirement("example.com/dep", "v1.2.3"),
                GoRequirement("example.com/other", "v0.1.0", indirect = true),
                GoRequirement("example.com/direct", "v1.4.0"),
            ),
            module.requirements,
        )
        assertEquals(listOf(GoModuleVersion("example.com/old", "v1.0.0")), module.excludes)
        assertEquals(
            listOf(
                GoReplacement("example.com/dep", "v1.2.3", "example.com/dep", "v1.2.4"),
                GoReplacement("example.com/local", null, "../local", null),
            ),
            module.replacements,
        )
        assertEquals("published by mistake", module.retractions.first().rationale)
        assertEquals("v1.0.0", module.retractions.first().low)
        assertNull(module.retractions.first().high)
        assertEquals("v1.1.0", module.retractions[1].low)
        assertEquals("v1.1.2", module.retractions[1].high)
    }

    @Test
    fun ignoresBlockCommentsAndQuotedModulePaths() {
        val module =
            GoModuleParser.parse(
                """
                /* header
                   still a comment */
                module "example.com/quoted"
                """.trimIndent(),
            )

        assertEquals("example.com/quoted", module.modulePath)
        assertTrue(module.requirements.isEmpty())
    }
}
