package su.kidoz.jetaprog.plugins.java.spring

import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.common.text.TextRange
import su.kidoz.jetaprog.editor.document.DocumentUri
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeDocument(
    private val content: String,
    override val fileName: String,
) : TextDocument {
    private val lines = content.lines()
    override val uri: DocumentUri = DocumentUri.fromPath("/workspace/src/main/resources/$fileName")
    override val languageId: LanguageId =
        if (fileName.endsWith(".properties")) LanguageId("properties") else LanguageId.YAML
    override val version: Int = 0
    override val isDirty: Boolean = false
    override val isUntitled: Boolean = false
    override val lineCount: Int = lines.size

    override fun getText(): String = content

    override fun getText(range: TextRange): String = content

    override fun getLine(lineNumber: Int): String = lines.getOrElse(lineNumber) { "" }

    override fun offsetAt(position: TextPosition): Int = 0

    override fun positionAt(offset: Int): TextPosition = TextPosition(0, 0)

    override suspend fun save(): Boolean = false
}

class SpringConfigTest {
    @Test
    fun yamlKeyContextResolvesEnclosingPath() {
        val document =
            FakeDocument(
                content =
                    """
                    spring:
                      datasource:
                        ur
                    """.trimIndent(),
                fileName = "application.yml",
            )
        val context = SpringConfigDocuments.yamlKeyContext(document, TextPosition(line = 2, column = 6))
        assertEquals("spring.datasource", context?.parentPath)
        assertEquals("ur", context?.token)
    }

    @Test
    fun yamlKeyAtBuildsFullDottedKey() {
        val document =
            FakeDocument(
                content =
                    """
                    spring:
                      jpa:
                        show-sql: true
                    """.trimIndent(),
                fileName = "application.yml",
            )
        assertEquals("spring.jpa.show-sql", SpringConfigDocuments.yamlKeyAt(document, 2))
        assertEquals("spring.jpa", SpringConfigDocuments.yamlKeyAt(document, 1))
    }

    @Test
    fun propertiesKeyIgnoresCommentsAndValues() {
        val document =
            FakeDocument(
                content =
                    """
                    # server settings
                    server.port=9090
                    """.trimIndent(),
                fileName = "application.properties",
            )
        assertNull(SpringConfigDocuments.propertiesKeyAt(document, 0))
        assertEquals("server.port", SpringConfigDocuments.propertiesKeyAt(document, 1))
    }

    @Test
    fun builtinKeysAreAvailableWithoutClasspath() =
        runTest {
            val index = SpringConfigMetadataIndex()
            index.ensureLoaded(emptyList())
            assertTrue(index.withPrefix("server.po").any { it.name == "server.port" })
        }

    @Test
    fun metadataIsReadFromClasspathJars() =
        runTest {
            val jar = File.createTempFile("spring-metadata", ".jar")
            jar.deleteOnExit()
            ZipOutputStream(jar.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/spring-configuration-metadata.json"))
                zip.write(
                    """
                    {"properties": [
                      {"name": "acme.mode", "type": "java.lang.String", "description": "Acme mode.", "defaultValue": "safe"}
                    ]}
                    """.trimIndent().toByteArray(),
                )
                zip.closeEntry()
            }

            val index = SpringConfigMetadataIndex()
            index.ensureLoaded(listOf(jar.absolutePath))
            val property = index.find("acme.mode")
            assertEquals("java.lang.String", property?.type)
            assertEquals("safe", property?.defaultValue)
        }
}
