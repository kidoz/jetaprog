package su.kidoz.jetaprog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import su.kidoz.jetaprog.app.ui.dialogs.settings.panels.PluginsPanel
import su.kidoz.jetaprog.app.ui.theme.JetaProgTheme
import su.kidoz.jetaprog.app.ui.theme.LocalIntelliJColors
import su.kidoz.jetaprog.app.ui.theme.Spacing
import su.kidoz.jetaprog.app.ui.toolbar.RunConfigurationSelector
import su.kidoz.jetaprog.configuration.ConfigurationId
import su.kidoz.jetaprog.configuration.ConfigurationState
import su.kidoz.jetaprog.configuration.ConfigurationType
import su.kidoz.jetaprog.configuration.DockerBuildSettings
import su.kidoz.jetaprog.configuration.RunConfiguration
import su.kidoz.jetaprog.settings.model.PluginsSettings
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FeatureAvailabilityUiTest {
    @Test
    fun unavailableFeaturesInDarkTheme() = checkUnavailableFeatures(darkTheme = true)

    @Test
    fun unavailableFeaturesInLightTheme() = checkUnavailableFeatures(darkTheme = false)

    private fun checkUnavailableFeatures(darkTheme: Boolean) =
        runComposeUiTest {
            val config =
                RunConfiguration(
                    id = ConfigurationId("docker"),
                    name = "Image",
                    type = ConfigurationType.DOCKER_BUILD,
                    settings = DockerBuildSettings(imageName = "app"),
                )
            setContent {
                JetaProgTheme(darkTheme = darkTheme) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(
                                LocalIntelliJColors.current.background,
                            ).padding(Spacing.md.dp),
                    ) {
                        RunConfigurationSelector(
                            state =
                                ConfigurationState(
                                    configurations = listOf(config),
                                    activeConfigurationId = config.id,
                                ),
                            onSelect = {},
                            onRun = { error("Unavailable Run was invoked") },
                            onDebug = { error("Unavailable Debug was invoked") },
                            onStop = {},
                            onEditConfigurations = {},
                            onCreateRecommended = {},
                        )
                        PluginsPanel(settings = PluginsSettings(), onIntent = {})
                    }
                }
            }
            onNodeWithText("Image (unavailable)").assertExists()
            onNodeWithContentDescription("Run").assertIsNotEnabled()
            onNodeWithContentDescription("Debug").assertIsNotEnabled()
            onNodeWithText("Plugin updates are not available in this version").assertExists()
            onRoot().saveUiArtifacts("feature-availability-${if (darkTheme) "dark" else "light"}")
        }
}
