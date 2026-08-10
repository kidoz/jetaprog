package su.kidoz.jetaprog.plugins.java.spring

import io.github.oshai.kotlinlogging.KotlinLogging
import su.kidoz.jetaprog.editor.document.LanguageId
import su.kidoz.jetaprog.plugins.api.BasePlugin
import su.kidoz.jetaprog.plugins.api.PluginManifest
import su.kidoz.jetaprog.plugins.api.language.DocumentSelector
import su.kidoz.jetaprog.plugins.api.services.LanguageConfiguration
import su.kidoz.jetaprog.plugins.java.JavaPlugin

private val logger = KotlinLogging.logger {}

/**
 * Spring Boot framework support, layered on top of the Java plugin.
 *
 * Detection is dependency-based: the plugin activates on any JVM build file but stays
 * dormant unless the workspace depends on `org.springframework.boot`. When detected it
 * provides configuration-key completion and hover for `application.properties` and
 * `application.yml`, sourced from the `spring-configuration-metadata.json` files inside
 * the dependency jars — no language server required.
 */
public class SpringPlugin :
    BasePlugin(
        manifest =
            PluginManifest(
                id = PLUGIN_ID,
                name = "Spring Boot Support",
                version = "1.0.0",
                description = "Spring Boot detection and configuration-key completion",
                activationEvents =
                    listOf(
                        "workspaceContains:build.gradle",
                        "workspaceContains:build.gradle.kts",
                        "workspaceContains:pom.xml",
                    ),
                dependencies = mapOf(JavaPlugin.PLUGIN_ID to "*"),
            ),
    ) {
    override suspend fun onActivate() {
        if (!context.build.hasDependency(SPRING_BOOT_GROUP)) {
            logger.debug { "No Spring Boot dependency found; Spring support stays dormant" }
            return
        }
        logger.info { "Spring Boot detected; enabling configuration-file intelligence" }

        context.languages
            .registerLanguage(
                LanguageConfiguration(
                    id = PROPERTIES_LANGUAGE,
                    extensions = listOf(".properties"),
                    aliases = listOf("Properties"),
                ),
            ).also { context.subscriptions.add(it) }

        val index = SpringConfigMetadataIndex()
        val classpathJars: suspend () -> List<String> = { context.build.classpathJars() }
        val completionProvider = SpringConfigCompletionProvider(index, classpathJars)
        val hoverProvider = SpringConfigHoverProvider(index, classpathJars)

        val selectors =
            listOf(
                DocumentSelector(
                    languages = listOf(LanguageId.YAML),
                    pattern = "**/application*.{yml,yaml}",
                ),
                DocumentSelector(
                    languages = listOf(PROPERTIES_LANGUAGE),
                    pattern = "**/application*.properties",
                ),
            )
        for (selector in selectors) {
            context.languages
                .registerCompletionProvider(selector, completionProvider)
                .also { context.subscriptions.add(it) }
            context.languages
                .registerHoverProvider(selector, hoverProvider)
                .also { context.subscriptions.add(it) }
        }
    }

    override suspend fun onDeactivate() {
        logger.info { "Deactivating Spring Boot support" }
    }

    public companion object {
        /** Plugin identifier used by the bundled plugin manager. */
        public const val PLUGIN_ID: String = "su.kidoz.jetaprog.spring"

        private const val SPRING_BOOT_GROUP = "org.springframework.boot"
        private val PROPERTIES_LANGUAGE = LanguageId("properties")
    }
}
