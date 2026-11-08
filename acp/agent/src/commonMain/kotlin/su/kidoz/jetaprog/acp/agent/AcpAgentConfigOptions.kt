package su.kidoz.jetaprog.acp.agent

import su.kidoz.jetaprog.acp.protocol.ClientCapabilities
import su.kidoz.jetaprog.acp.protocol.SessionConfigOption

/** Omits unadvertised boolean controls while preserving the agent's ordering. */
internal fun List<SessionConfigOption>.forClient(capabilities: ClientCapabilities): List<SessionConfigOption> =
    if (capabilities.session?.configOptions?.boolean != null) this else filterNot { it.type == "boolean" }
