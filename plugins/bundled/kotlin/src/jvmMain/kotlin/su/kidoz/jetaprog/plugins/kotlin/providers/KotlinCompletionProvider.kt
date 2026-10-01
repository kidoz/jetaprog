package su.kidoz.jetaprog.plugins.kotlin.providers

import su.kidoz.jetaprog.plugins.api.language.CompletionList
import su.kidoz.jetaprog.plugins.api.services.CompletionContext
import su.kidoz.jetaprog.plugins.api.services.CompletionProvider
import su.kidoz.jetaprog.plugins.api.services.TextDocument
import su.kidoz.jetaprog.plugins.kotlin.KotlinLanguageService

/**
 * Completion provider that wraps the KotlinLanguageService.
 */
public class KotlinCompletionProvider(
    private val languageService: KotlinLanguageService,
) : CompletionProvider {
    override suspend fun provideCompletionItems(
        document: TextDocument,
        position: su.kidoz.jetaprog.common.text.TextPosition,
        context: CompletionContext,
    ): CompletionList {
        val items = languageService.getCompletions(position, document.getText())
        return CompletionList(items = items, isIncomplete = false)
    }
}
