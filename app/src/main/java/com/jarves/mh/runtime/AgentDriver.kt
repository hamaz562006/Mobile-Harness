package com.jarves.mh.runtime

import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.RuntimeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Capability flags advertised by an agent driver so the UI can show only
 * controls that agent actually supports (provider picker, approvals, etc.).
 */
enum class AgentCapability {
    API_KEY,
    ACCOUNT_LOGIN,
    PROVIDER_PICKER,
    MODEL_PICKER,
    REASONING_EFFORT,
    RESUME,
    INTERACTIVE_APPROVALS,
}

interface AgentDriver {
    val kind: AgentKind
    val runtime: RuntimeBridge
    val capabilities: Set<AgentCapability>

    fun isInstalled(installer: RuntimeInstaller): Boolean = installer.isAgentInstalled(kind)
    suspend fun install(installer: RuntimeInstaller, onProgress: suspend (RuntimeInstallProgress) -> Unit) =
        installer.ensureAgentInstalled(kind, onProgress)
}

private class BuiltInAgentDriver(
    override val kind: AgentKind,
    override val runtime: RuntimeBridge,
    override val capabilities: Set<AgentCapability>,
) : AgentDriver

/** Single selection point for agent runtimes. Adding an agent does not change existing bridges. */
class AgentRegistry(drivers: List<AgentDriver>) {
    private val byId = drivers.associateBy { it.kind.stableId }
    val all: List<AgentDriver> = drivers.toList()

    init {
        require(byId.size == drivers.size) { "Duplicate agent id" }
        require(AgentKind.entries.all { it.stableId in byId }) { "Every built-in agent must be registered" }
    }

    fun require(kind: AgentKind): AgentDriver =
        byId[kind.stableId] ?: error("Agent '${kind.stableId}' is not registered")

    /**
     * Merged runtime event stream for every registered agent.
     * ViewModel collects this once instead of wiring each bridge separately.
     */
    fun mergedEvents(): Flow<RuntimeEvent> = channelFlow {
        all.forEach { driver ->
            launch {
                driver.runtime.events.collect { send(it) }
            }
        }
    }

    companion object {
        fun builtIns(
            claude: RuntimeBridge,
            deepSeek: RuntimeBridge,
            antigravity: RuntimeBridge,
        ) = AgentRegistry(
            listOf(
                BuiltInAgentDriver(
                    AgentKind.CLAUDE_CODE,
                    claude,
                    setOf(
                        AgentCapability.API_KEY,
                        AgentCapability.ACCOUNT_LOGIN,
                        AgentCapability.PROVIDER_PICKER,
                        AgentCapability.MODEL_PICKER,
                        AgentCapability.RESUME,
                        AgentCapability.INTERACTIVE_APPROVALS,
                    ),
                ),
                BuiltInAgentDriver(
                    AgentKind.DEEPSEEK_HARNESS,
                    deepSeek,
                    setOf(
                        AgentCapability.API_KEY,
                        AgentCapability.PROVIDER_PICKER,
                        AgentCapability.MODEL_PICKER,
                        AgentCapability.RESUME,
                        AgentCapability.INTERACTIVE_APPROVALS,
                    ),
                ),
                BuiltInAgentDriver(
                    AgentKind.ANTIGRAVITY,
                    antigravity,
                    setOf(
                        AgentCapability.ACCOUNT_LOGIN,
                        AgentCapability.MODEL_PICKER,
                        AgentCapability.REASONING_EFFORT,
                        AgentCapability.RESUME,
                    ),
                ),
            ),
        )
    }
}
