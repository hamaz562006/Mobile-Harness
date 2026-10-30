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

    const val KILO_NOTE = "Kilo · ~200 req/hour per IP"
    const val POLLINATIONS_NOTE = "Pollinations · ~4 req/min per IP"

    data class Entry(
        val id: String,
        val title: String,
        val note: String,
        /** Upstream base URL for this free model (Kilo vs Pollinations). */
        val baseUrl: String = KILO_BASE_URL,
        val contextTokens: Long? = null,
        val supportsThinking: Boolean = true,
        val supportsTools: Boolean = true,
        val supportsImages: Boolean = false,
    )

    val curated: List<Entry> = listOf(
        Entry(DEFAULT_ID, "kilo-auto/free", KILO_NOTE, contextTokens = 256_000L, supportsThinking = true, supportsTools = true),
        Entry(POLLINATIONS_ID, "openai-fast", POLLINATIONS_NOTE, POLLINATIONS_BASE_URL, contextTokens = 16_000L, supportsThinking = false, supportsTools = false),
        Entry("nvidia/nemotron-3.5-super-vl:free", "nvidia/nemotron-3.5-super-vl:free", KILO_NOTE, contextTokens = 256_000L, supportsThinking = true, supportsTools = true, supportsImages = true),
        Entry("nvidia/nemotron-3-ultra-550b-a55b:free", "nvidia/nemotron-3-ultra-550b-a55b:free", KILO_NOTE, contextTokens = 256_000L, supportsThinking = true, supportsTools = true),
        Entry("nvidia/nemotron-3.5-lightning:free", "nvidia/nemotron-3.5-lightning:free", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("google/gemma-4-31b-it:free", "google/gemma-4-31b-it:free", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("google/gemma-4-26b-a4b-it:free", "google/gemma-4-26b-a4b-it:free", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("cohere/north-mini-code:free", "cohere/north-mini-code:free", KILO_NOTE, contextTokens = 256_000L, supportsThinking = true, supportsTools = true),
        Entry("recursive/flint-v1.0:free", "recursive/flint-v1.0:free", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("reflection/beam-free", "reflection/beam-free", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("dots-studio/dots-3-note-preview:free", "dots-studio/dots-3-note-preview:free", KILO_NOTE, contextTokens = 512_000L, supportsThinking = true, supportsTools = true, supportsImages = true),
        Entry("stealth/glyph-cluster", "stealth/glyph-cluster", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("thinkingmachines/inkling:free", "thinkingmachines/inkling:free", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("thinkingmachines/inkling-small:free", "thinkingmachines/inkling-small:free", KILO_NOTE, contextTokens = 64_000L, supportsThinking = true, supportsTools = true),
        Entry("poolside/laguna-s-2.1:free", "poolside/laguna-s-2.1:free", KILO_NOTE, contextTokens = 128_000L, supportsThinking = true, supportsTools = true),
        Entry("poolside/laguna-xs-2.1:free", "poolside/laguna-xs-2.1:free", KILO_NOTE, contextTokens = 64_000L, supportsThinking = true, supportsTools = true),
        Entry("inclusionai/ling-3.1-flash-1019-free", "inclusionai/ling-3.1-flash-1019-free", KILO_NOTE, contextTokens = 1_000_000L, supportsThinking = true, supportsTools = true),
    )

    fun asDiscovered(): List<DiscoveredModel> =
        curated.map { entry ->
            DiscoveredModel(
                id = entry.id,
                displayName = entry.title,
                isFree = true,
                note = entry.note,
                contextTokens = entry.contextTokens,
                supportsThinking = entry.supportsThinking,
                supportsTools = entry.supportsTools,
                supportsImages = entry.supportsImages,
            )
        }

    fun baseUrlFor(modelId: String): String =
        curated.firstOrNull { it.id == modelId }?.baseUrl ?: KILO_BASE_URL

    /** Prefer live free rows when present; otherwise curated offline list. */
    private fun enrich(model: DiscoveredModel): DiscoveredModel {
        val match = curated.firstOrNull { it.id.equals(model.id, ignoreCase = true) }
        val isPollinations = model.id.equals(POLLINATIONS_ID, ignoreCase = true)
        return model.copy(
            isFree = true,
            note = model.note
                ?: match?.note
                ?: if (isPollinations) POLLINATIONS_NOTE else KILO_NOTE,
            contextTokens = model.contextTokens ?: match?.contextTokens,
            supportsThinking = model.supportsThinking ?: match?.supportsThinking,
            supportsTools = model.supportsTools ?: match?.supportsTools ?: true,
            supportsImages = model.supportsImages ?: match?.supportsImages,
            displayName = if (model.displayName == model.id || model.displayName.isBlank()) {
                match?.title ?: model.id
            } else {
                model.displayName
            },
        )
    }

    fun mergeLive(live: List<DiscoveredModel>): List<DiscoveredModel> {
        val freeLive = live
            .filter { model ->
                model.isFree ||
                    model.id.equals(DEFAULT_ID, ignoreCase = true) ||
                    model.id.endsWith(":free", ignoreCase = true) ||
                    model.id.contains("/free", ignoreCase = true)
            }
            .map { enrich(it) }
            .distinctBy { it.id }
        if (freeLive.isEmpty()) return asDiscovered()
        val pollinations = asDiscovered().first { it.id == POLLINATIONS_ID }
        val ordered = freeLive.sortedWith(
            compareByDescending<DiscoveredModel> { it.id == DEFAULT_ID }
                .thenBy { it.displayName.lowercase() },
        )
        return (ordered + pollinations).distinctBy { it.id }
    }
}
