package su.kidoz.jetaprog.plugins.kotlin.providers

import su.kidoz.jetaprog.common.text.TextPosition
import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.services.CompletionContext
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.settings.SettingsService
import su.kidoz.jetaprog.settings.model.CompletionProviderPreference
import su.kidoz.jetaprog.settings.model.LanguageConfig

/**
 * A completion provider that delegates to other providers based on user settings.
 */
public class ConfigurableKotlinCompletionProvider(
    private val nativeProvider: CompletionProvider,
    private val lspProvider: CompletionProvider,
    private val settingsService: SettingsService? = null,
    private val languageId: String = "kotlin",
) : CompletionProvider {
    override suspend fun provideCompletionItems(
        document: TextDocument,
        position: TextPosition,
        context: CompletionContext,
    ): CompletionList? {
        val languageConfig =
            settingsService
                ?.getCurrentSettings()
                ?.languages
                ?.languages
                ?.get(languageId)
                ?: LanguageConfig()

        return when (languageConfig.completionPreference) {
            CompletionProviderPreference.Native -> {
                nativeProvider.provideCompletionItems(document, position, context)
            }

            CompletionProviderPreference.Lsp -> {
                lspProvider.provideCompletionItems(document, position, context)
            }

            CompletionProviderPreference.Hybrid -> {
                val nativeResults = nativeProvider.provideCompletionItems(document, position, context)
                val lspResults = lspProvider.provideCompletionItems(document, position, context)

                val allItems = (nativeResults?.items.orEmpty() + lspResults?.items.orEmpty()).distinctBy { it.label }
                val isIncomplete = nativeResults?.isIncomplete == true || lspResults?.isIncomplete == true

                CompletionList(allItems, isIncomplete)
            }
        }
    }
}
