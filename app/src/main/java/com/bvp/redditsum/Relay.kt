package com.bvp.redditsum

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object Relay {
    fun summarize(relayUrl: String, postUrl: String): JSONObject {
        val conn = URL(relayUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 90000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use {
            it.write(JSONObject().put("url", postUrl).toString().toByteArray(Charsets.UTF_8))
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        val json = try { JSONObject(text) } catch (e: Exception) {
            throw RuntimeException("Relay trả về dữ liệu không hợp lệ (HTTP $code):\n${text.take(300)}")
        }
        if (code !in 200..299 || json.has("error")) {
            throw RuntimeException(json.optString("error", "HTTP $code"))
        }
        return json
    }
}
