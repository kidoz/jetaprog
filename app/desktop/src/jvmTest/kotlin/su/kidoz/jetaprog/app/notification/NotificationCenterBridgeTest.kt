package su.kidoz.jetaprog.app.notification

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.app.plugin.PluginDialogRequest
import su.kidoz.jetaprog.app.plugin.PluginDialogRequests
import su.kidoz.jetaprog.plugins.api.services.SimpleQuickPickItem
import su.kidoz.jetaprog.plugins.runtime.services.UiMessageSeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the app-side bridge that renders plugin notifications as toasts and
 * plugin dialogs (input box, quick pick) as suspencing requests.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationCenterBridgeTest {
    private val center = NotificationCenter()
    private val dialogRequests = PluginDialogRequests()
    private val bridge = NotificationCenterBridge(center, dialogRequests)

    @Test
    fun messageWithActionsSuspendsAndReturnsThePickedLabel() =
        runTest {
            val answer =
                async {
                    bridge.showMessage(UiMessageSeverity.WARNING, "unsaved changes", listOf("Save", "Discard"))
                }
            advanceUntilIdle()

            val pushed = center.notifications.first { it.isNotEmpty() }.last()
            assertEquals(2, pushed.actions.size)
            assertNull(pushed.autoDismissMs, "action messages stay until answered")

            // The overlay invokes the action, then dismisses the toast.
            pushed.actions[1].onClick()
            center.dismiss(pushed.id)

            assertEquals("Discard", withTimeout(1000) { answer.await() })
            assertTrue(center.notifications.value.isEmpty(), "answering the action dismisses the toast")
        }

    @Test
    fun dismissingAnActionMessageResumesWithNull() =
        runTest {
            val answer =
                async {
                    bridge.showMessage(UiMessageSeverity.INFO, "hello", listOf("Retry"))
                }
            advanceUntilIdle()

            center.dismiss(
                center.notifications.value
                    .last()
                    .id,
            )

            assertNull(withTimeout(1000) { answer.await() })
        }

    @Test
    fun messageWithoutActionsIsFireAndForget() =
        runTest {
            assertNull(bridge.showMessage(UiMessageSeverity.INFO, "fyi"))
            assertEquals(1, center.notifications.value.size)
        }

    @Test
    fun progressHandleUpdatesAndClosesItsNotification() =
        runTest {
            val handle = bridge.beginProgress("Indexing")
            val id =
                center.notifications.value
                    .single()
                    .id

            handle.report("half way")
            assertEquals(
                "half way",
                center.notifications.value
                    .single()
                    .message,
            )

            handle.close()
            assertTrue(center.notifications.value.isEmpty())
        }

    @Test
    fun inputBoxRequestSuspendsUntilAnswered() =
        runTest {
            val answer =
                async {
                    bridge.requestInput(
                        title = "Rename",
                        prompt = null,
                        value = "old",
                        placeholder = "name",
                        password = false,
                    )
                }
            advanceUntilIdle()

            val request = dialogRequests.requests.filterNotNull().first()
            assertTrue(request is PluginDialogRequest.Input)
            assertEquals("old", request.value)
            request.completion.complete("new")

            assertEquals("new", withTimeout(1000) { answer.await() })
        }

    @Test
    fun quickPickReturnsPickedItems() =
        runTest {
            val items = listOf(SimpleQuickPickItem("a"), SimpleQuickPickItem("b"))
            val answer =
                async {
                    bridge.requestQuickPick(items, title = "Pick", placeholder = null, multiSelect = false)
                }
            advanceUntilIdle()

            val request = dialogRequests.requests.filterNotNull().first()
            assertTrue(request is PluginDialogRequest.QuickPick)
            request.completion.complete(listOf(items[1]))

            assertEquals(listOf("b"), withTimeout(1000) { answer.await() }?.map { it.label })
        }
}
