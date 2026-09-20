package com.stremiolivetv.data

import com.stremiolivetv.model.Media
import com.stremiolivetv.model.Stream
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object StremioClient {
    private const val CINEMETA = "https://v3-cinemeta.strem.io"

    fun catalog(type: String, search: String? = null): List<Media> {
        val suffix = if (search.isNullOrBlank()) "" else "/search=${URLEncoder.encode(search, "UTF-8")}" 
        val json = getJson("$CINEMETA/catalog/$type/top$suffix.json")
        val metas = json.optJSONArray("metas") ?: return emptyList()
        return (0 until metas.length()).mapNotNull { index ->
            val item = metas.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val name = item.optString("name")
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            Media(
                id = id,
                type = item.optString("type", type),
                name = name,
                poster = item.optString("poster").takeIf(String::isNotBlank),
                description = item.optString("description").takeIf(String::isNotBlank),
                year = item.optString("releaseInfo").takeIf(String::isNotBlank),
                rating = item.optString("imdbRating").takeIf(String::isNotBlank)
            )
        }
    }

    fun streams(addonUrls: List<String>, media: Media): List<Stream> = addonUrls.flatMap { configured ->
        runCatching {
            val base = configured.trim().trimEnd('/').removeSuffix("/manifest.json")
            val json = getJson("$base/stream/${media.type}/${media.id}.json")
            val streams = json.optJSONArray("streams") ?: return@runCatching emptyList()
            (0 until streams.length()).mapNotNull { index ->
                val item = streams.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").ifBlank { item.optString("externalUrl") }
                if (url.isBlank()) return@mapNotNull null
                Stream(
                    item.optString("title").ifBlank { item.optString("name").ifBlank { "Play stream" } },
                    url
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "StreamFusionTV/0.2")
        connection.setRequestProperty("Accept", "application/json")
        return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    }
}
