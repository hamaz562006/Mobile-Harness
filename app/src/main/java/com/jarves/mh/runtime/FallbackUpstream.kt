package com.jarves.mh.runtime

/** One upstream endpoint the local format gateway can call during Fallbacks. */
data class FallbackUpstream(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val label: String = model,
)
