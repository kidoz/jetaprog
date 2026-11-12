package su.kidoz.jetaprog.build.go

import kotlin.test.Test
import kotlin.test.assertEquals

class GoToolchainTest {
    @Test
    fun buildsCompileRunAndTestCommands() {
        assertEquals(
            listOf("go", "build", "-o", "bin/app", "-race", "-tags=integration,netgo", "./..."),
            GoToolchain.command(
                GoOperation.Build(
                    output = "bin/app",
                    race = true,
                    tags = listOf("integration", "netgo"),
                ),
            ),
        )
        assertEquals(
            listOf("go", "run", "-race", ".", "--port", "8080"),
            GoToolchain.command(
                GoOperation.Run(race = true, programArguments = listOf("--port", "8080")),
            ),
        )
        assertEquals(
            listOf("go", "test", "-json", "-count=1", "-run=TestAdd", "./..."),
            GoToolchain.command(GoOperation.Test(json = true, count = 1, run = "TestAdd")),
        )
    }

    @Test
    fun buildsModuleWorkspaceAndEnvironmentCommands() {
        assertEquals(listOf("go", "vet", "./..."), GoToolchain.command(GoOperation.Vet()))
        assertEquals(listOf("go", "fmt", "./..."), GoToolchain.command(GoOperation.Fmt()))
        assertEquals(
            listOf("go", "generate", "-run=stringer", "./..."),
            GoToolchain.command(GoOperation.Generate(run = "stringer")),
        )
        assertEquals(listOf("go", "mod", "tidy"), GoToolchain.command(GoOperation.ModTidy))
        assertEquals(listOf("go", "mod", "download"), GoToolchain.command(GoOperation.ModDownload))
        assertEquals(listOf("go", "mod", "vendor"), GoToolchain.command(GoOperation.ModVendor))
        assertEquals(listOf("go", "work", "sync"), GoToolchain.command(GoOperation.WorkSync))
        assertEquals(
            listOf("go", "clean", "-cache", "-testcache"),
            GoToolchain.command(GoOperation.Clean(cache = true, testCache = true)),
        )
        assertEquals(listOf("go", "list", "-json", "./..."), GoToolchain.command(GoOperation.ListPackages()))
        assertEquals(listOf("go", "env", "-json"), GoToolchain.command(GoOperation.Env()))
    }
}
