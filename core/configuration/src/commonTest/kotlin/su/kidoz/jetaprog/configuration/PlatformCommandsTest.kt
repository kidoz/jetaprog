package su.kidoz.jetaprog.configuration

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCommandsTest {
    @Test
    fun resolvesThePlatformGradleWrapper() {
        val project = temporaryDirectory()
        File(project, "gradlew").writeText("#!/bin/sh")
        File(project, "gradlew.bat").writeText("@echo off")

        assertEquals(File(project, "gradlew.bat").absolutePath, gradleCommand(project, osName = "Windows 11"))
        assertEquals(File(project, "gradlew").absolutePath, gradleCommand(project, osName = "Linux"))
    }

    @Test
    fun fallsBackToTheGradleCommandWithoutAWrapper() {
        val project = temporaryDirectory()

        assertEquals("gradle", gradleCommand(project, osName = "Windows 11"))
        assertEquals("gradle", gradleCommand(project, osName = "Linux"))
    }

    @Test
    fun prefersTheConfiguredGradleExecutable() {
        assertEquals(
            "C:\\custom\\gradle.bat",
            gradleCommand(temporaryDirectory(), executable = "C:\\custom\\gradle.bat", osName = "Windows 11"),
        )
    }

    @Test
    fun resolvesThePlatformMavenWrapper() {
        val project = temporaryDirectory()
        File(project, "mvnw").writeText("#!/bin/sh")
        File(project, "mvnw.cmd").writeText("@echo off")

        assertEquals(File(project, "mvnw.cmd").absolutePath, mavenCommand(project, osName = "Windows 11"))
        assertEquals(File(project, "mvnw").absolutePath, mavenCommand(project, osName = "Linux"))
    }

    @Test
    fun fallsBackToTheMavenCommandWithoutAWrapper() {
        val project = temporaryDirectory()

        assertEquals("mvn", mavenCommand(project, osName = "Windows 11"))
        assertEquals("mvn", mavenCommand(project, osName = "Linux"))
    }

    @Test
    fun selectsThePlatformPythonInterpreter() {
        assertEquals("python", defaultPythonInterpreter("Windows 11"))
        assertEquals("python3", defaultPythonInterpreter("Linux"))
        assertEquals("python3", defaultPythonInterpreter("Mac OS X"))
    }

    @Test
    fun doesNotConfuseDarwinWithWindows() {
        assertTrue(isWindowsOs("Windows 11"))
        assertFalse(isWindowsOs("Darwin"))
        assertFalse(isWindowsOs("Linux"))
    }

    @Test
    fun pythonConfigurationUsesThePlatformDefault() {
        val interpreter = ConfigurationSettings.Python(scriptPath = "main.py").pythonInterpreter

        assertEquals(defaultPythonInterpreter(), interpreter)
    }

    private fun temporaryDirectory(): String = Files.createTempDirectory("jetaprog-commands").toFile().absolutePath
}
