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
     * Free models stay first so a keyless path is obvious.
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
 * Free models as used by AndroidHarness / and-code Harness provider:
 * keyless Kilo gateway (+ Pollinations fallback), not OpenRouter paid-account free tier.
 *
 * Live catalog is fetched from Kilo (`isFree` + tools). Offline fallback keeps
 * the same default Auto Free entry and Pollinations so setup works offline.
 */
object FreeModelCatalog {
    const val KILO_BASE_URL = "https://api.kilo.ai/api/gateway"
    const val POLLINATIONS_BASE_URL = "https://text.pollinations.ai/openai"
    const val DEFAULT_ID = "kilo-auto/free"
    const val POLLINATIONS_ID = "openai-fast"

    data class Entry(
        val id: String,
        val title: String,
        val note: String,
        /** Upstream base URL for this free model (Kilo vs Pollinations). */
        val baseUrl: String = KILO_BASE_URL,
    )

    val curated: List<Entry> = listOf(
        Entry(DEFAULT_ID, "Auto Free", "Kilo · rotates free models · ~200 req/h · no key"),
        Entry(POLLINATIONS_ID, "Pollinations Fast", "Pollinations · ~4 req/min · no key", POLLINATIONS_BASE_URL),
        Entry("nvidia/nemotron-3.5-super-vl:free", "Nemotron 3.5 Super VL", "Kilo free · tools"),
        Entry("nvidia/nemotron-3-ultra-550b-a55b:free", "Nemotron 3 Ultra", "Kilo free · tools"),
        Entry("nvidia/nemotron-3.5-lightning:free", "Nemotron 3.5 Lightning", "Kilo free · tools"),
        Entry("google/gemma-4-31b-it:free", "Gemma 4 31B", "Kilo free · tools"),
        Entry("google/gemma-4-26b-a4b-it:free", "Gemma 4 26B", "Kilo free · tools"),
        Entry("cohere/north-mini-code:free", "North Mini Code", "Kilo free · coding"),
        Entry("recursive/flint-v1.0:free", "Flint", "Kilo free · coding"),
        Entry("reflection/beam-free", "Beam Free", "Kilo free"),
        Entry("dots-studio/dots-3-note-preview:free", "Dots3 Note", "Kilo free"),
        Entry("stealth/glyph-cluster", "Glyph Cluster", "Kilo free"),
        Entry("thinkingmachines/inkling:free", "Inkling", "Kilo free"),
        Entry("thinkingmachines/inkling-small:free", "Inkling Small", "Kilo free"),
        Entry("poolside/laguna-s-2.1:free", "Laguna S 2.1", "Kilo free · coding"),
        Entry("poolside/laguna-xs-2.1:free", "Laguna XS 2.1", "Kilo free · coding"),
    )

    fun asDiscovered(): List<DiscoveredModel> =
        curated.map { entry ->
            DiscoveredModel(
                id = entry.id,
                displayName = "${entry.title} · ${entry.note}",
                isFree = true,
            )
        }

    fun baseUrlFor(modelId: String): String =
        curated.firstOrNull { it.id == modelId }?.baseUrl ?: KILO_BASE_URL

    /** Prefer live free rows when present; otherwise curated offline list. */
    fun mergeLive(live: List<DiscoveredModel>): List<DiscoveredModel> {
        val freeLive = live.filter { it.isFree }
            .map { it.copy(isFree = true) }
            .distinctBy { it.id }
        if (freeLive.isEmpty()) return asDiscovered()
        // Keep Pollinations offline fallback visible even when Kilo returns a full list.
        val pollinations = asDiscovered().first { it.id == POLLINATIONS_ID }
        return (freeLive + pollinations).distinctBy { it.id }
    }
}
