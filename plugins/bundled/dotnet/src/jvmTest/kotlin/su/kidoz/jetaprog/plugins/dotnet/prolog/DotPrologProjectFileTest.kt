package su.kidoz.jetaprog.plugins.dotnet.prolog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DotPrologProjectFileTest {
    private val sdkElementProject =
        """
        <Project Sdk="Microsoft.NET.Sdk">
          <Sdk Name="DotProlog.Sdk" Version="0.5.0" />
          <PropertyGroup>
            <OutputType>Exe</OutputType>
            <TargetFramework>net10.0</TargetFramework>
          </PropertyGroup>
        </Project>
        """.trimIndent()

    @Test
    fun detectsTheNestedSdkElement() {
        assertTrue(DotPrologProjectFile.isDotPrologProject(sdkElementProject))
    }

    @Test
    fun detectsTheProjectSdkAttribute() {
        val content = """<Project Sdk="DotProlog.Sdk/0.5.0"></Project>"""
        assertTrue(DotPrologProjectFile.isDotPrologProject(content))
    }

    @Test
    fun ignoresProjectsWithoutTheDotPrologSdk() {
        val content =
            """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net10.0</TargetFramework>
              </PropertyGroup>
            </Project>
            """.trimIndent()
        assertFalse(DotPrologProjectFile.isDotPrologProject(content))
    }

    @Test
    fun readsTheSdkVersionFromTheSdkElement() {
        assertEquals("0.5.0", DotPrologProjectFile.sdkVersion(sdkElementProject))
    }

    @Test
    fun readsTheSdkVersionWhenAttributesAreReordered() {
        val content = """<Project><Sdk Version="0.6.1" Name="DotProlog.Sdk" /></Project>"""
        assertEquals("0.6.1", DotPrologProjectFile.sdkVersion(content))
    }

    @Test
    fun reportsNoVersionWithoutAnSdkElement() {
        assertNull(DotPrologProjectFile.sdkVersion("""<Project Sdk="DotProlog.Sdk/0.5.0"></Project>"""))
    }
}
