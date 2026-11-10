package su.kidoz.jetaprog.app.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationIntent
import su.kidoz.jetaprog.configuration.ConfigurationManager
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.DockerBuildSettings
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.dap.service.DebugService
import su.kidoz.jetaprog.platform.process.ProcessExecutor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigurationAvailabilityTest {
    @Test
    fun unsupportedConfigurationCannotStartThroughRunOrDebugIntents() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val config =
                RunConfiguration(
                    id = ConfigurationId("docker"),
                    name = "Image",
                    type = ConfigurationType.DOCKER_BUILD,
                    settings = DockerBuildSettings(imageName = "app"),
                )
            val manager = mockk<ConfigurationManager>()
            coEvery { manager.initialize(any()) } returns
                Result.success(
                    ConfigurationState(configurations = listOf(config), activeConfigurationId = config.id),
                )
            val executor = mockk<ProcessExecutor>()
            val debugger = mockk<DebugService>()
            val vm = ConfigurationViewModel(manager, executor, mockk(), mockk(), mockk(), debugger)
            try {
                vm.perform(ConfigurationIntent.Initialize("/project"))
                vm.perform(ConfigurationIntent.RunActive)
                advanceUntilIdle()
                assertFalse(vm.state.value.isRunning)
                assertTrue(
                    vm.state.value.error
                        .orEmpty()
                        .contains("not available"),
                )
                vm.perform(ConfigurationIntent.ClearError)
                vm.perform(ConfigurationIntent.DebugActive)
                assertFalse(vm.state.value.isRunning)
                assertTrue(
                    vm.state.value.error
                        .orEmpty()
                        .contains("not available"),
                )
                coVerify(exactly = 0) { debugger.startDebugSession(any(), any()) }
            } finally {
                vm.dispose()
                Dispatchers.resetMain()
            }
        }
}
