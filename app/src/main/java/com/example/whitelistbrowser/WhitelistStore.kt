package com.example.whitelistbrowser

import android.content.Context
import android.content.SharedPreferences

/**
 * 白名单存储：基于 SharedPreferences 的域名集合。
 * 匹配规则：域名完全匹配，或其子域名（如白名单含 example.com，则 www.example.com 也允许）。
 */
class WhitelistStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 获取全部白名单域名（已规范化、去重、排序由调用方决定） */
    fun getAll(): Set<String> =
        prefs.getStringSet(KEY_DOMAINS, emptySet())
            ?.map { normalize(it) }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    /** 判断某个 host 是否在白名单内（含子域名） */
    fun contains(host: String): Boolean {
        val h = normalize(host)
        if (h.isEmpty()) return false
        return getAll().any { it == h || h.endsWith(".$it") }
    }

    /** 添加域名，返回是否实际发生了新增 */
    fun add(domain: String): Boolean {
        val d = normalize(domain)
        if (d.isEmpty()) return false
        val current = getAll().toMutableSet()
        if (!current.add(d)) return false
        prefs.edit().putStringSet(KEY_DOMAINS, current).apply()
        return true
    }

    /** 删除域名 */
    fun remove(domain: String) {
        val d = normalize(domain)
        val current = getAll().toMutableSet()
        if (current.remove(d)) {
            prefs.edit().putStringSet(KEY_DOMAINS, current).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "whitelist"
        private const val KEY_DOMAINS = "domains"

        /**
         * 规范化域名：转小写、去 scheme、去路径、去端口、去前导 "*."。
         * 输入 "https://WWW.Example.com:443/path" 得到 "example.com"。
         */
        fun normalize(domain: String): String {
            var d = domain.trim().lowercase()
            val schemeIdx = d.indexOf("://")
            if (schemeIdx >= 0) d = d.substring(schemeIdx + 3)
            val pathIdx = d.indexOf('/')
            if (pathIdx >= 0) d = d.substring(0, pathIdx)
            val portIdx = d.indexOf(':')
            if (portIdx >= 0) d = d.substring(0, portIdx)
            if (d.startsWith("*.")) d = d.substring(2)
            return d
        }
    }
}
