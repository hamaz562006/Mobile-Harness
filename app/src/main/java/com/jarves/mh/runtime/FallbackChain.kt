package com.jarves.mh.runtime

import com.jarves.mh.model.FallbackPolicy
import com.jarves.mh.model.FallbackTarget
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile

/** Builds the ordered backup upstreams for any agent that uses the shared provider gateway. */
object FallbackChain {
    fun build(
        primary: ProviderProfile,
        policy: FallbackPolicy,
        resolve: (FallbackTarget) -> FallbackUpstream?,
    ): Pair</* enabled */ Boolean, List<FallbackUpstream>> {
        if (!policy.enabled || !policy.valid() || policy.targets.isEmpty()) {
            return false to emptyList()
        }
        val chain = policy.targets.mapNotNull { target ->
            if (policy.sameProviderOnly && target.providerId != primary.kind.name) {
                null
            } else {
                resolve(target)
            }
        }
        return (chain.isNotEmpty()) to chain
    }

    fun openAiCompatible(kind: ProviderKind, dshApi: String = ""): Boolean {
        if (kind == ProviderKind.FREE_MODELS) return true
        if (kind == ProviderKind.NVIDIA_NIM) return true
        val api = dshApi.ifBlank {
            when (kind) {
                ProviderKind.OPENCODE_ZEN -> "openai-responses"
                else -> ""
            }
        }
        return api == "openai-completions" || api == "openai-responses"
    }
}
