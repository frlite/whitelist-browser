package com.example.whitelistbrowser

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 白名单同步管理：负责从服务端拉取白名单、提交访问请求、轮询审批结果。
 */
class WhitelistSyncManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("server_config", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    var onWhitelistUpdated: ((Set<String>) -> Unit)? = null
    var onRequestResolved: ((domain: String, approved: Boolean) -> Unit)? = null

    fun getServerUrl(): String = prefs.getString("server_url", "") ?: ""

    fun setServerUrl(url: String) {
        prefs.edit().putString("server_url", url).apply()
    }

    /** 从服务端拉取白名单并更新本地缓存 */
    fun fetchWhitelist() {
        val serverUrl = getServerUrl().trimEnd('/')
        if (serverUrl.isEmpty()) return

        executor.execute {
            try {
                val url = URL("$serverUrl/api/whitelist")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val arr = json.getJSONArray("whitelist")
                    val domains = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        domains.add(arr.getString(i))
                    }
                    saveLocalCache(domains)
                    handler.post { onWhitelistUpdated?.invoke(domains) }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("WhitelistSync", "fetchWhitelist failed", e)
            }
        }
    }

    /** 向服务端提交访问请求 */
    fun requestAccess(domain: String) {
        val serverUrl = getServerUrl().trimEnd('/')
        if (serverUrl.isEmpty()) return

        executor.execute {
            try {
                val url = URL("$serverUrl/api/request")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val body = JSONObject().apply {
                    put("domain", domain)
                    put("requested_by", "android_client")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(resp)
                    val status = json.getString("status")
                    val requestId = json.optInt("request_id", -1)

                    when (status) {
                        "already_whitelisted" -> handler.post {
                            onRequestResolved?.invoke(domain, true)
                        }
                        "pending" -> pollRequestResult(requestId, domain)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("WhitelistSync", "requestAccess failed", e)
            }
        }
    }

    /** 轮询请求审批结果 */
    private fun pollRequestResult(requestId: Int, domain: String) {
        val serverUrl = getServerUrl().trimEnd('/')
        if (serverUrl.isEmpty()) return

        var attempts = 0
        val maxAttempts = 60 // 最多轮询 60 次，约 2 分钟

        fun poll() {
            if (attempts >= maxAttempts) {
                handler.post { onRequestResolved?.invoke(domain, false) }
                return
            }
            attempts++

            executor.execute {
                try {
                    val url = URL("$serverUrl/api/request/$requestId")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "GET"

                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().readText()
                        val json = JSONObject(resp)
                        val status = json.getString("status")

                        when (status) {
                            "approved" -> handler.post {
                                fetchWhitelist()
                                onRequestResolved?.invoke(domain, true)
                            }
                            "rejected" -> handler.post {
                                onRequestResolved?.invoke(domain, false)
                            }
                            else -> handler.postDelayed({ poll() }, 2000)
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    handler.postDelayed({ poll() }, 5000)
                }
            }
        }
        poll()
    }

    /** 本地缓存白名单 */
    fun getCachedWhitelist(): Set<String> {
        val json = prefs.getString("cached_whitelist", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            set
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun saveLocalCache(domains: Set<String>) {
        val arr = JSONArray()
        domains.forEach { arr.put(it) }
        prefs.edit().putString("cached_whitelist", arr.toString()).apply()
    }

    fun isServerConfigured(): Boolean = getServerUrl().isNotEmpty()
}
