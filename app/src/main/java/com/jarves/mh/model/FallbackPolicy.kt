package com.jarves.mh.model

import org.json.JSONArray
import org.json.JSONObject

data class FallbackTarget(
    val providerId: String,
    val model: String,
)

data class FallbackPolicy(
    val enabled: Boolean = false,
    val targets: List<FallbackTarget> = emptyList(),
    val retries: Int = 3,
    val sameProviderOnly: Boolean = false,
) {
    fun valid(): Boolean =
        retries in 0..5 &&
            targets.size <= MAX_TARGETS &&
            targets.distinctBy { "${it.providerId}\u0000${it.model}" }.size == targets.size &&
            targets.all {
                it.providerId.isNotBlank() && it.providerId.length <= 500 &&
                    it.model.isNotBlank() && it.model.length <= 500
            }

    companion object {
        const val MAX_TARGETS = 4

        fun decode(raw: String?): FallbackPolicy {
            if (raw.isNullOrBlank()) return FallbackPolicy()
            return runCatching {
                val o = JSONObject(raw)
                val targets = buildList {
                    val arr = o.optJSONArray("targets") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val t = arr.optJSONObject(i) ?: continue
                        val providerId = t.optString("providerId").trim()
                        val model = t.optString("model").trim()
                        if (providerId.isNotBlank() && model.isNotBlank()) {
                            add(FallbackTarget(providerId, model))
                        }
                    }
                }
                FallbackPolicy(
                    enabled = o.optBoolean("enabled", false),
                    targets = targets,
                    retries = o.optInt("retries", 3).coerceIn(0, 5),
                    sameProviderOnly = o.optBoolean("sameProviderOnly", false),
                ).takeIf { it.valid() } ?: FallbackPolicy()
            }.getOrDefault(FallbackPolicy())
        }

        fun encode(policy: FallbackPolicy): String {
            require(policy.valid()) { "Choose up to four unique models and zero to five retries." }
            val arr = JSONArray()
            policy.targets.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("providerId", t.providerId)
                        .put("model", t.model),
                )
            }
            return JSONObject()
                .put("enabled", policy.enabled)
                .put("targets", arr)
                .put("retries", policy.retries)
                .put("sameProviderOnly", policy.sameProviderOnly)
                .toString()
        }
    }
}
