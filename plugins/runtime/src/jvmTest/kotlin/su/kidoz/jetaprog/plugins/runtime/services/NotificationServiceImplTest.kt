package su.kidoz.jetaprog.plugins.runtime.services

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import su.kidoz.jetaprog.plugins.api.services.QuickPickItem
import su.kidoz.jetaprog.plugins.api.services.SimpleQuickPickItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that [NotificationServiceImpl] delegates every user-facing call to
 * the host UI bridge and still works (logging-only, null answers) without one.
 */
class NotificationServiceImplTest {
    @Test
    fun messagesDelegateToBridgeAndReturnItsAnswer() =
        runTest {
            val bridge =
                mockk<UiNotificationBridge> {
                    coEvery { showMessage(any(), any(), listOf("Retry", "Ignore")) } returns "Retry"
                }
            val service = NotificationServiceImpl(bridge)

            assertEquals("Retry", service.showInformationMessage("failed", "Retry", "Ignore"))
            coVerify {
                bridge.showMessage(UiMessageSeverity.INFO, "failed", listOf("Retry", "Ignore"))
            }
        }

    @Test
    fun messageWithoutActionsReturnsNullThroughBridge() =
        runTest {
            val bridge =
                mockk<UiNotificationBridge> {
                    coEvery { showMessage(any(), any(), emptyList()) } returns null
                }

            assertNull(NotificationServiceImpl(bridge).showErrorMessage("boom"))
        }

    @Test
    fun withoutBridgeEverythingFallsBackToLogging() =
        runTest {
            val service = NotificationServiceImpl()

            assertNull(service.showInformationMessage("m"))
            assertNull(service.showInputBox())
            assertNull(service.showQuickPick(listOf(SimpleQuickPickItem("a"))))
            assertTrue(service.showQuickPickMulti(listOf(SimpleQuickPickItem("a"))).isNullOrEmpty())
        }

    @Test
    fun progressHandleIsAlwaysClosed() =
        runTest {
            val handle = mockk<UiProgressHandle>(relaxed = true)
            val bridge =
                mockk<UiNotificationBridge> {
                    coEvery { beginProgress("Indexing") } returns handle
                }

            val result =
                NotificationServiceImpl(bridge).withProgress("Indexing") { progress ->
                    progress.report("half way", 50)
                    "done"
                }

            assertEquals("done", result)
            coVerify {
                handle.report("half way", 50)
                handle.close()
            }
        }

    @Test
    fun quickPickDelegatesWithMatchingOptions() =
        runTest {
            val items = listOf(SimpleQuickPickItem("a", description = "d"))
            val bridge =
                mockk<UiNotificationBridge> {
                    coEvery {
                        requestQuickPick<QuickPickItem>(items, "Pick", "search", false)
                    } returns listOf(items[0])
                }

            val picked =
                NotificationServiceImpl(bridge).showQuickPick(
                    items,
                    su.kidoz.jetaprog.plugins.api.services
                        .QuickPickOptions(title = "Pick", placeHolder = "search"),
                )

            assertEquals("a", picked?.label)
        }
}
