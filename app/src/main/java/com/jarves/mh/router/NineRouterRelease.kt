package com.jarves.mh.router

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Pinned / registry helpers for the npm `9router` package. */
object NineRouterRelease {
    const val PINNED = "0.5.112"
    const val SQL_JS = "1.14.1"

    fun validate(version: String) {
        require(version.matches(Regex("""\d+\.\d+\.\d+"""))) { "Invalid 9Router version: $version" }
    }

    fun latest(): String {
        val connection = URL("https://registry.npmjs.org/9router/latest").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        return connection.inputStream.bufferedReader().use { reader ->
            JSONObject(reader.readText()).getString("version")
        }.also { validate(it) }
    }

    fun newer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
