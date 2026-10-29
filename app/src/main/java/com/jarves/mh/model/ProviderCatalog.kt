package com.jarves.mh.model

import com.jarves.mh.network.DiscoveredModel

/**
 * Shared provider catalog for every coding agent.
 *
 * Agents do not own their own provider lists. They declare compatibility;
 * this catalog decides what appears in Connect your AI / Settings.
 * Adding a new agent should only extend [providersFor], not copy provider UI.
 */
object ProviderCatalog {

    /**
     * Providers offered for [agent], ordered for onboarding.
     * Free models stay first so a keyless-feeling path is obvious.
     */
    fun providersFor(agent: AgentKind): List<ProviderKind> = when (agent) {
        AgentKind.CLAUDE_CODE -> buildList {
            add(ProviderKind.FREE_MODELS)
            ProviderKind.entries.forEach { kind ->
                if (kind != ProviderKind.FREE_MODELS && kind != ProviderKind.OPENCODE_ZEN) add(kind)
            }
        }
        AgentKind.DEEPSEEK_HARNESS -> buildList {
            add(ProviderKind.FREE_MODELS)
            ProviderKind.entries.forEach { kind ->
                if (kind != ProviderKind.FREE_MODELS && kind in DEEPSEEK_HARNESS_PROVIDERS) add(kind)
            }
        }
        // Antigravity authenticates with Google only — no API provider list.
        AgentKind.ANTIGRAVITY -> emptyList()
    }

    fun supports(agent: AgentKind, kind: ProviderKind): Boolean =
        providersFor(agent).contains(kind)
}

/**
 * Curated OpenRouter free-tier models aimed at coding agents.
 * IDs use the `:free` suffix so cost stays $0 / token (rate limits still apply).
 *
 * Live discovery may return a larger free list; this catalog is the offline
 * fallback and the default onboarding picks.
 */
object FreeModelCatalog {
    const val DEFAULT_ID = "recursive/flint-v1.0:free"

    data class Entry(
        val id: String,
        val title: String,
        val note: String,
    )

    val curated: List<Entry> = listOf(
        Entry("recursive/flint-v1.0:free", "Flint", "Coding agents"),
        Entry("cohere/north-mini-code:free", "North Mini Code", "Agentic coding"),
        Entry("google/gemma-4-31b-it:free", "Gemma 4 31B", "General · tools"),
        Entry("google/gemma-4-26b-a4b-it:free", "Gemma 4 26B", "General · lighter"),
        Entry("nvidia/nemotron-3.5-lightning:free", "Nemotron 3.5 Lightning", "High-volume agents"),
        Entry("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra", "Reasoning"),
        Entry("nvidia/nemotron-3.5-super-vl:free", "Nemotron 3.5 Super VL", "Multimodal"),
        Entry("thinkingmachines/inkling:free", "Inkling", "General · long context"),
        Entry("thinkingmachines/inkling-small:free", "Inkling Small", "General · lighter"),
        Entry("xingchen-agi/xing4.0-29b-a4b:free", "Xing4.0 29B", "General"),
        Entry("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", "Nemotron 3 Nano Omni", "Reasoning"),
        Entry("liquid/lfm-2.5-2.6b:free", "LFM 2.5", "Small · fast"),
        Entry("dots-studio/dots-3-note-preview:free", "Dots3 Note", "General preview"),
        Entry("poolside/laguna-s-2.1:free", "Laguna S 2.1", "Coding · may expire"),
        Entry("poolside/laguna-xs-2.1:free", "Laguna XS 2.1", "Coding · lighter"),
    )

    fun asDiscovered(): List<DiscoveredModel> =
        curated.map { entry ->
            DiscoveredModel(
                id = entry.id,
                displayName = "${entry.title} · ${entry.note}",
                isFree = true,
            )
        }

    /** Prefer live free rows when present; otherwise curated offline list. */
    fun mergeLive(live: List<DiscoveredModel>): List<DiscoveredModel> {
        val freeLive = live.filter { it.isFree || it.id.endsWith(":free", ignoreCase = true) }
            .map { it.copy(isFree = true) }
            .distinctBy { it.id }
        if (freeLive.isEmpty()) return asDiscovered()
        val curatedIds = curated.map { it.id }.toSet()
        val preferred = freeLive.sortedWith(
            compareByDescending<DiscoveredModel> { it.id in curatedIds }
                .thenBy { it.displayName.lowercase() },
        )
        return preferred
    }
}
