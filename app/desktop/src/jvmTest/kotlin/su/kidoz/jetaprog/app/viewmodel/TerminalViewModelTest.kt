package su.kidoz.jetaprog.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import su.kidoz.jetaprog.app.terminal.TerminalBackend
import su.kidoz.jetaprog.app.terminal.TerminalBackendOutput
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun backendFinishedAfterTabCloseIsDisposedWithoutBeingCollected() =
        runTest {
            val backend = FakeTerminalBackend()
            val factoryStarted = CountDownLatch(1)
            val finishFactory = CountDownLatch(1)
            val viewModel =
                TerminalViewModel(
                    defaultWorkingDirectory = ".",
                    backendFactory = {
                        factoryStarted.countDown()
                        assertTrue(finishFactory.await(5, TimeUnit.SECONDS))
                        Result.success(backend)
                    },
                )

            viewModel.dispatch(TerminalIntent.CreateTerminal())
            assertTrue(factoryStarted.await(5, TimeUnit.SECONDS))
            viewModel.dispatch(TerminalIntent.CloseTerminal(1))
            finishFactory.countDown()
            withContext(Dispatchers.Default) {
                withTimeout(5_000) {
                    while (backend.disposeCount == 0) delay(10)
                }
            }

            assertEquals(1, backend.disposeCount)
            assertEquals(0, backend.collectorCount)
            val state = viewModel.state.value
            assertTrue(state.tabs.isEmpty())
            viewModel.dispose()
            assertEquals(1, backend.disposeCount)
        }

    @Test
    fun closingBackgroundTabKeepsActiveTerminalIdentity() =
        runTest {
            val first = FakeTerminalBackend()
            val second = FakeTerminalBackend()
            val third = FakeTerminalBackend()
            val viewModel = viewModelWith(first, second, third)
            repeat(3) { viewModel.dispatch(TerminalIntent.CreateTerminal()) }
            val initialState = viewModel.state.value
            val activeId = initialState.activeTab?.id

            viewModel.dispatch(TerminalIntent.CloseTerminal(first.id))

            val state = viewModel.state.value
            assertEquals(third.id, state.activeTab?.id)
            assertEquals(activeId, state.activeTab?.id)
            assertEquals(1, state.activeTabIndex)
            assertEquals(listOf(second.id, third.id), state.tabs.map { it.id })
            assertEquals(1, first.disposeCount)
            assertEquals(0, second.disposeCount)
            assertEquals(0, third.disposeCount)
            viewModel.dispose()
        }

    @Test
    fun forcedKillStopsTabAndCollectsSingleExit() =
        runTest {
            val backend = FakeTerminalBackend()
            val viewModel = viewModelWith(backend)
            viewModel.dispatch(TerminalIntent.CreateTerminal())

            viewModel.dispatch(TerminalIntent.KillProcess)

            val killedState = viewModel.state.value
            assertEquals(false, killedState.activeTab?.isRunning)
            assertEquals(1, backend.killCount)
            assertEquals(1, backend.collectorCount)
            assertEquals(0, backend.disposeCount)

            backend.emit(TerminalBackendOutput.Exited(137))

            val state = viewModel.state.value
            assertEquals(false, state.activeTab?.isRunning)
            assertEquals(137, state.activeTab?.exitCode)
            assertEquals(1, backend.disposeCount)
            viewModel.dispose()
            assertEquals(1, backend.disposeCount)
        }

    @Test
    fun outputChunksRemainOrderedAcrossExit() =
        runTest {
            val backend = FakeTerminalBackend()
            val viewModel = viewModelWith(backend)
            viewModel.dispatch(TerminalIntent.CreateTerminal())

            backend.emit(TerminalBackendOutput.Text("hello"))
            backend.emit(TerminalBackendOutput.Text(" ✅"))
            backend.emit(TerminalBackendOutput.Exited(0))

            val state = viewModel.state.value
            assertEquals("hello ✅", state.activeTab?.lines?.single())
            assertEquals(0, state.activeTab?.exitCode)
            assertEquals(false, state.activeTab?.isRunning)
            assertEquals(1, backend.disposeCount)
            viewModel.dispose()
        }

    @Test
    fun writeFailureIsVisibleInTerminalErrors() =
        runTest {
            val backend = FakeTerminalBackend(writeFailure = IOException("broken pipe"))
            val viewModel = viewModelWith(backend)
            viewModel.dispatch(TerminalIntent.CreateTerminal())

            viewModel.dispatch(TerminalIntent.SendInput("input"))

            val state = viewModel.state.value
            assertEquals(
                listOf("Terminal write failed: broken pipe"),
                state.activeTab?.errorMessages,
            )
            viewModel.dispose()
        }

    @Test
    fun backendFailureIsVisibleInTerminalErrors() =
        runTest {
            val backend = FakeTerminalBackend()
            val viewModel = viewModelWith(backend)
            viewModel.dispatch(TerminalIntent.CreateTerminal())

            backend.emit(TerminalBackendOutput.Error("read failed"))

            val state = viewModel.state.value
            assertEquals(listOf("read failed"), state.activeTab?.errorMessages)
            viewModel.dispose()
        }

    private fun viewModelWith(vararg backends: FakeTerminalBackend): TerminalViewModel {
        var index = 0
        return TerminalViewModel(
            defaultWorkingDirectory = ".",
            backendFactory = { Result.success(backends[index++]) },
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }
}

private class FakeTerminalBackend(
    private val writeFailure: IOException? = null,
) : TerminalBackend {
    private val events = Channel<TerminalBackendOutput>(Channel.UNLIMITED)

    override val output: Flow<TerminalBackendOutput> = events.receiveAsFlow().onStart { collectorCount += 1 }

    override var isAlive: Boolean = true
        private set

    var collectorCount: Int = 0
        private set
    var killCount: Int = 0
        private set
    var disposeCount: Int = 0
        private set

    val id: Int = nextId++

    override suspend fun write(bytes: ByteArray) {
        writeFailure?.let { throw it }
    }

    override fun resize(
        columns: Int,
        rows: Int,
    ) = Unit

    override fun kill() {
        killCount += 1
        isAlive = false
    }

    override fun dispose() {
        disposeCount += 1
        isAlive = false
        events.close()
    }

    fun emit(event: TerminalBackendOutput) {
        events.trySend(event).getOrThrow()
    }

    private companion object {
        private var nextId = 1
    }
}
