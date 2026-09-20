package com.stremiolivetv.data

import com.stremiolivetv.model.Channel
import com.stremiolivetv.model.Media
import com.stremiolivetv.model.Stream
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class XtreamClient(private val server:String, private val username:String, private val password:String) {
    private val base = server.trim().trimEnd('/')
    private val api = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}"

    fun authenticate(): String {
        val info = JSONObject(get(api)).getJSONObject("user_info")
        if (info.optInt("auth") != 1) error("Xtream login was rejected")
        val status = info.optString("status", "Active")
        if (!status.equals("Active", true)) error("Account status: $status")
        return info.optString("exp_date").takeIf { it.isNotBlank() && it != "null" } ?: "Active"
    }

    fun playlistUrl():String = "$base/get.php?username=${enc(username)}&password=${enc(password)}&type=m3u_plus&output=ts"
    fun epgUrl():String = "$base/xmltv.php?username=${enc(username)}&password=${enc(password)}"

    fun liveChannels(): List<Channel> {
        val categories = categoryMap("get_live_categories")
        val rows = JSONArray(get("$api&action=get_live_streams"))
        return (0 until rows.length()).mapNotNull { index ->
            val item = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("stream_id")
            if (id <= 0) return@mapNotNull null
            Channel(item.optString("name","Channel"),"$base/live/${enc(username)}/${enc(password)}/$id.ts",categories[item.optString("category_id")]?:"Other",item.optString("stream_icon").takeIf(String::isNotBlank),item.optString("epg_channel_id").takeIf(String::isNotBlank))
        }
    }

    fun movies(): List<Media> {
        val rows = JSONArray(get("$api&action=get_vod_streams"))
        return (0 until rows.length()).mapNotNull { index ->
            val item=rows.optJSONObject(index)?:return@mapNotNull null
            val id=item.optLong("stream_id");if(id<=0)return@mapNotNull null
            val ext=item.optString("container_extension","mp4")
            Media("xtream:movie:$id:$ext","movie",item.optString("name","Movie"),item.optString("stream_icon").takeIf(String::isNotBlank),year=item.optString("year").takeIf(String::isNotBlank),rating=item.optString("rating").takeIf(String::isNotBlank))
        }
    }

    fun series(): List<Media> {
        val rows=JSONArray(get("$api&action=get_series"))
        return (0 until rows.length()).mapNotNull { index ->
            val item=rows.optJSONObject(index)?:return@mapNotNull null
            val id=item.optLong("series_id");if(id<=0)return@mapNotNull null
            Media("xtream:series:$id","series",item.optString("name","Series"),item.optString("cover").takeIf(String::isNotBlank),item.optString("plot").takeIf(String::isNotBlank),item.optString("releaseDate").takeIf(String::isNotBlank),item.optString("rating").takeIf(String::isNotBlank))
        }
    }

    fun streams(media:Media):List<Stream>{
        val parts=media.id.split(':');if(parts.size<3||parts[0]!="xtream")return emptyList()
        if(parts[1]=="movie"){val ext=parts.getOrNull(3)?:"mp4";return listOf(Stream("Play "+media.name,"$base/movie/${enc(username)}/${enc(password)}/${parts[2]}.$ext"))}
        val info=JSONObject(get("$api&action=get_series_info&series_id=${parts[2]}"))
        val episodes=info.optJSONObject("episodes")?:return emptyList();val output=mutableListOf<Stream>()
        episodes.keys().forEach { season ->
            val rows=episodes.optJSONArray(season)?:return@forEach
            for(index in 0 until rows.length()){
                val episode=rows.optJSONObject(index)?:continue;val id=episode.optString("id");val ext=episode.optString("container_extension","mp4")
                if(id.isNotBlank())output+=Stream("S"+season.padStart(2,'0')+" E"+episode.optString("episode_num",(index+1).toString()).padStart(2,'0')+"  "+episode.optString("title"),"$base/series/${enc(username)}/${enc(password)}/$id.$ext")
            }
        }
        return output
    }

    private fun categoryMap(action:String):Map<String,String>{val rows=JSONArray(get("$api&action=$action"));return(0 until rows.length()).associate{index->val item=rows.getJSONObject(index);item.optString("category_id") to item.optString("category_name","Other")}}
    private fun get(url:String):String{val connection=URL(url).openConnection() as HttpURLConnection;connection.connectTimeout=15_000;connection.readTimeout=45_000;connection.setRequestProperty("User-Agent","StreamFusionTV/0.3");return connection.inputStream.bufferedReader().use{it.readText()}}
    private fun enc(value:String)=URLEncoder.encode(value,"UTF-8")
}
