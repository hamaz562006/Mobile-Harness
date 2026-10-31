package com.jarves.mh.router

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Talks to the running 9Router control API on 127.0.0.1:[PORT]
 * to create/reuse the integration API key (OpenAI-compatible /v1).
 */
class NineRouterControl(private val dataDir: File) {
    private fun persisted(file: File, create: () -> String): String {
        file.parentFile?.mkdirs()
        if (!file.isFile || file.length() == 0L) {
            file.writeText(create())
        }
        return file.readText().trim().also { check(it.isNotEmpty()) { "9Router control credentials unavailable." } }
    }

    private fun token(): String {
        val machine = persisted(File(dataDir, "machine-id")) { UUID.randomUUID().toString() }
        val secret = persisted(File(dataDir, "auth/cli-secret")) {
            ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        }
        return cliToken(machine, secret)
    }

    fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        require(path.startsWith("/api/") && !path.contains("..") && !path.contains('?'))
        val connection = URL("http://127.0.0.1:${NineRouterManager.PORT}$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.requestMethod = method
        connection.setRequestProperty("x-9r-cli-token", token())
        connection.setRequestProperty("Accept", "application/json")
        if (method in listOf("POST", "PUT", "PATCH")) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray()) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(code in 200..299) { "9Router control failed (HTTP $code). Start 9Router and try again." }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    /** Creates or reuses the app integration key named [KEY_NAME]. */
    fun integrationKey(): String {
        val keys = request("GET", "/api/keys").optJSONArray("keys") ?: JSONArray()
        for (i in 0 until keys.length()) {
            val obj = keys.optJSONObject(i) ?: continue
            if (obj.optString("name") == KEY_NAME && obj.optBoolean("isActive", true)) {
                val key = obj.optString("key")
                if (key.isNotBlank()) return key
            }
        }
        val created = request("POST", "/api/keys", JSONObject().put("name", KEY_NAME))
        return created.optString("key").takeIf { it.isNotBlank() }
            ?: error("9Router did not return an API key.")
    }

    companion object {
        const val KEY_NAME = "1code-harness"
        internal fun cliToken(machine: String, secret: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest((machine + "9r-cli-auth" + secret).toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
    }
}
