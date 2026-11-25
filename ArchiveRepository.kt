package com.example.archiveplayer

import android.net.Uri\nimport kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class ArchiveFileChoice(val name: String, val format: String, val size: Long, val url: String)

class ArchiveRepository {
    suspend fun search(query: String, page: Int = 1): List<ArchiveHit> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://archive.org/advancedsearch.php?q=$q&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&rows=30&page=$page&output=json"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 20000; requestMethod = "GET"
        }
        try {
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until docs.length()) {
                    val d = docs.optJSONObject(i) ?: continue
                    val id = d.optString("identifier")
                    if (id.isNotBlank()) add(ArchiveHit(id, d.optString("title", id), d.optString("creator", ""), d.optString("year", "")))
                }
            }
        } finally { connection.disconnect() }
    }

    suspend fun files(identifier: String): List<ArchiveFileChoice> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(identifier, "UTF-8")
        val connection = (URL("https://archive.org/metadata/$encoded").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 20000
        }
        try {
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val array = root.optJSONArray("files") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val f = array.optJSONObject(i) ?: continue
                    val name = f.optString("name")
                    if (name.isBlank() || f.optString("private") == "true") continue
                    val format = f.optString("format")
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in setOf("mp4","m4v","webm","mp3","ogg","flac","wav","m4a")) {
                        val size = f.optString("size").toLongOrNull() ?: 0L
                        val direct = "https://archive.org/download/${UriEncoder.path(identifier)}/${UriEncoder.path(name)}"
                        add(ArchiveFileChoice(name, format, size, direct))
                    }
                }
            }.sortedWith(compareBy({ it.format.lowercase() }, { it.name }))
        } finally { connection.disconnect() }
    }
}

private object UriEncoder {
    fun path(value: String): String = value.split("/").joinToString("/") { Uri.encode(it) }
}
