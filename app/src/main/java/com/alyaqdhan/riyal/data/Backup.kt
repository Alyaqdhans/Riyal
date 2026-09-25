package com.alyaqdhan.riyal.data

import org.json.JSONArray
import org.json.JSONObject

object Backup {

    private val TEXT = listOf("txns" to "body", "reviews" to "body", "muted" to "sample")

    fun of(storeJson: String, includeMessages: Boolean): String {
        if (includeMessages) return storeJson
        val root = JSONObject(storeJson)
        for ((array, key) in TEXT) blank(root.optJSONArray(array), key)
        return root.toString()
    }

    fun versionOf(json: String): Int = JSONObject(json).optInt("v", 0)

    fun countOf(json: String): Int = JSONObject(json).optJSONArray("txns")?.length() ?: 0

    fun hasMessages(json: String): Boolean {
        val rows = JSONObject(json).optJSONArray("txns") ?: return false
        for (i in 0 until rows.length()) {
            val body = (rows.opt(i) as? JSONObject)?.optString("body").orEmpty()
            if (body.isNotEmpty()) return true
        }
        return false
    }

    private fun blank(rows: JSONArray?, key: String) {
        if (rows == null) return
        for (i in 0 until rows.length()) {
            val row = rows.opt(i) as? JSONObject ?: continue
            if (row.has(key)) row.put(key, "")
        }
    }
}
