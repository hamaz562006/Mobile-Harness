package com.jarves.mh.router

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Talks to the local 9Router control API (http://127.0.0.1:20128).
 *
 * CLI token must match 9Router's getConsistentMachineId("9r-cli-auth"):
 * sha256(machineId + "9r-cli-auth" + cliSecret).hex.take(16)
 * using the same files 9Router wrote under its effective DATA_DIR.
 *
 * Upstream default DATA_DIR is ~/.9router when env DATA_DIR is unset/unwritable;
 * we may also set DATA_DIR=/root/9router/data. Always resolve from real files.
 */
class NineRouterControl(private val dataDir: File) {
    fun token(): String {
        val machineFile = File(dataDir, "machine-id")
        val secretFile = File(dataDir, "auth/cli-secret")
        check(machineFile.isFile && machineFile.length() > 0L) {
            "9Router auth not ready (machine-id in ${dataDir.absolutePath}). Start 9Router and try again."
        }
        check(secretFile.isFile && secretFile.length() > 0L) {
            "9Router auth not ready (cli-secret in ${dataDir.absolutePath}). Start 9Router and try again."
        }
        return cliToken(machineFile.readText().trim(), secretFile.readText().trim())
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
        check(code in 200..299) {
            "9Router control failed (HTTP $code) dataDir=${dataDir.absolutePath}. Start 9Router and try again."
        }
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
        // Upstream may return { key } or { apiKey: { key } }
        val key = created.optString("key").ifBlank {
            created.optJSONObject("apiKey")?.optString("key").orEmpty()
        }
        return key.takeIf { it.isNotBlank() }
            ?: error("9Router did not return an API key: $created")
    }

    companion object {
        const val KEY_NAME = "1code-harness"
        internal fun cliToken(machine: String, secret: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest((machine + "9r-cli-auth" + secret).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
    }
}
