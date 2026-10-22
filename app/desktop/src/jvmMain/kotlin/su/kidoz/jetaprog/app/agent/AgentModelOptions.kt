package su.kidoz.jetaprog.app.agent

import su.kidoz.jetaprog.acp.protocol.ModelInfo
import su.kidoz.jetaprog.app.ui.agent.ModelOption

/** Maps a model reported by the connected agent to a picker entry. */
internal fun ModelInfo.toModelOption(): ModelOption =
    ModelOption(
        id = modelId,
        displayName = name,
        shortName = name.removePrefix("Claude ").ifBlank { modelId },
    )
