package su.kidoz.jetaprog.common.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests intent ordering guarantees shared by all MVI view models. */
@OptIn(ExperimentalCoroutinesApi::class)
public class MviViewModelTest {
    @BeforeTest
    public fun setUp(): Unit = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    public fun tearDown(): Unit = Dispatchers.resetMain()

    @Test
    public fun intentsCompleteInDispatchOrder(): Unit =
        runTest {
            val viewModel = OrderedViewModel()

            viewModel.dispatch(TestIntent.Append(1, delayMillis = 100))
            viewModel.dispatch(TestIntent.Append(2, delayMillis = 0))
            advanceUntilIdle()

            assertEquals(listOf(1, 2), viewModel.state.value.values)
            viewModel.dispose()
        }

    private data class TestState(
        val values: List<Int> = emptyList(),
    ) : State

    private sealed interface TestIntent : Intent {
        data class Append(
            val value: Int,
            val delayMillis: Long,
        ) : TestIntent
    }

    private data object TestEffect : Effect

    private class OrderedViewModel : MviViewModel<TestIntent, TestState, TestEffect>(TestState()) {
        override suspend fun handleIntent(intent: TestIntent) {
            when (intent) {
                is TestIntent.Append -> {
                    delay(intent.delayMillis)
                    updateState { copy(values = values + intent.value) }
                }
            }
        }
    }
}
