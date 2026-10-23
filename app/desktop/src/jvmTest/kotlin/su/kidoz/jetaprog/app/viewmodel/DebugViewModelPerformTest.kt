package su.kidoz.jetaprog.app.viewmodel

import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.app.ui.debug.DebugIntent
import su.kidoz.jetaprog.app.ui.debug.DebugStatus
import su.kidoz.jetaprog.dap.service.DebugService
import su.kidoz.jetaprog.platform.filesystem.FileSystem
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Breakpoints are stored without a debug session. Stepping does nothing until one exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelPerformTest {
    private val scope = CoroutineScope(SupervisorJob())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun togglesABreakpointAndLeavesSteppingIdle() =
        runTest {
            val service = DebugService(mockk<ProcessExecutor>(relaxed = true), scope)
            val viewModel = DebugViewModel(service, "/proj", mockk<FileSystem>(relaxed = true))
            try {
                val added = viewModel.perform(DebugIntent.ToggleBreakpoint("/proj/src/A.kt", 4))
                assertEquals(listOf("/proj/src/A.kt:4"), added.breakpoints.map { it.id })
                assertEquals(DebugStatus.TERMINATED, added.status)

                val removed = viewModel.perform(DebugIntent.ToggleBreakpoint("/proj/src/A.kt", 4))
                assertEquals(emptyList(), removed.breakpoints.map { it.id })

                val stepped = viewModel.perform(DebugIntent.StepOver)
                assertEquals(DebugStatus.TERMINATED, stepped.status)
                assertFalse(stepped.hasSession)
            } finally {
                viewModel.dispose()
                service.dispose()
            }
        }
}
