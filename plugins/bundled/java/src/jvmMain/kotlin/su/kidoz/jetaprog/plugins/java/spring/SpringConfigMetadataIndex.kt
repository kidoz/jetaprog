package su.kidoz.jetaprog.plugins.java.spring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * A configuration property published by a Spring Boot dependency.
 */
public data class SpringConfigProperty(
    /** Full dotted key, e.g. `spring.datasource.url`. */
    val name: String,
    /** Declared Java type of the value, when known. */
    val type: String? = null,
    /** Human-readable description from the configuration metadata. */
    val description: String? = null,
    /** Default value rendered as text, when declared. */
    val defaultValue: String? = null,
)

/**
 * Index of Spring Boot configuration keys.
 *
 * Keys come from `META-INF/spring-configuration-metadata.json` files inside the
 * dependency jars on the workspace classpath — the same source Spring's own tooling
 * uses — plus a small built-in set of common keys so completion works before the
 * build import provides a classpath.
 */
public class SpringConfigMetadataIndex {
    private val mutex = Mutex()
    private var properties: Map<String, SpringConfigProperty> = builtinProperties()
    private var loadedJars: Set<String> = emptySet()

    /**
     * Loads metadata from any classpath jars not read yet. Cheap when the classpath
     * is unchanged.
     */
    public suspend fun ensureLoaded(classpathJars: List<String>): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val jars = classpathJars.toSet()
                if (jars == loadedJars) return@withLock
                val collected = builtinProperties().toMutableMap()
                jars.forEach { jarPath -> collectFromJar(jarPath, collected) }
                properties = collected
                loadedJars = jars
            }
        }

    /**
     * All known configuration properties.
     */
    public fun allProperties(): List<SpringConfigProperty> = properties.values.toList()

    /**
     * The property with the exact dotted key, or null.
     */
    public fun find(name: String): SpringConfigProperty? = properties[name]

    /**
     * Properties whose key starts with the given text.
     */
    public fun withPrefix(prefix: String): List<SpringConfigProperty> =
        properties.values.filter { it.name.startsWith(prefix) }.sortedBy { it.name }

    private fun collectFromJar(
        jarPath: String,
        into: MutableMap<String, SpringConfigProperty>,
    ) {
        val file = File(jarPath)
        if (!file.isFile || !jarPath.endsWith(".jar")) return
        try {
            ZipFile(file).use { zip ->
                for (entryName in METADATA_ENTRIES) {
                    val entry = zip.getEntry(entryName) ?: continue
                    val text = zip.getInputStream(entry).bufferedReader().readText()
                    parseMetadata(text, into)
                }
            }
        } catch (_: IOException) {
            // Unreadable or malformed jar: skip, other jars still contribute keys
        }
    }

    private fun parseMetadata(
        text: String,
        into: MutableMap<String, SpringConfigProperty>,
    ) {
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return
        val declared = root["properties"] as? JsonArray ?: return
        for (element in declared) {
            val property = element as? JsonObject ?: continue
            val name = property.string("name") ?: continue
            into.getOrPut(name) {
                SpringConfigProperty(
                    name = name,
                    type = property.string("type"),
                    description = property.string("description"),
                    defaultValue = (property["defaultValue"] as? JsonPrimitive)?.content,
                )
            }
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private companion object {
        val METADATA_ENTRIES =
            listOf(
                "META-INF/spring-configuration-metadata.json",
                "META-INF/additional-spring-configuration-metadata.json",
            )

        fun builtinProperties(): Map<String, SpringConfigProperty> =
            listOf(
                SpringConfigProperty("server.port", "java.lang.Integer", "Server HTTP port.", "8080"),
                SpringConfigProperty(
                    "server.servlet.context-path",
                    "java.lang.String",
                    "Context path of the application.",
                ),
                SpringConfigProperty("spring.application.name", "java.lang.String", "Application name."),
                SpringConfigProperty(
                    "spring.profiles.active",
                    "java.util.List",
                    "Comma-separated list of active profiles.",
                ),
                SpringConfigProperty("spring.config.import", "java.util.List", "Import additional config data."),
                SpringConfigProperty("spring.datasource.url", "java.lang.String", "JDBC URL of the database."),
                SpringConfigProperty(
                    "spring.datasource.username",
                    "java.lang.String",
                    "Login username of the database.",
                ),
                SpringConfigProperty(
                    "spring.datasource.password",
                    "java.lang.String",
                    "Login password of the database.",
                ),
                SpringConfigProperty(
                    "spring.datasource.driver-class-name",
                    "java.lang.String",
                    "Fully qualified name of the JDBC driver.",
                ),
                SpringConfigProperty(
                    "spring.jpa.hibernate.ddl-auto",
                    "java.lang.String",
                    "DDL mode (none, validate, update, create, create-drop).",
                ),
                SpringConfigProperty(
                    "spring.jpa.show-sql",
                    "java.lang.Boolean",
                    "Whether to enable SQL logging.",
                    "false",
                ),
                SpringConfigProperty("logging.level.root", "java.lang.String", "Root logging level."),
                SpringConfigProperty(
                    "management.endpoints.web.exposure.include",
                    "java.util.Set",
                    "Endpoint IDs that should be exposed, or '*' for all.",
                ),
            ).associateBy { it.name }
    }
}
