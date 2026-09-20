package com.stremiolivetv.data

import com.stremiolivetv.model.Channel

object M3uParser {
    private val attr = Regex("([\\w-]+)=\"([^\"]*)\"")
    fun parse(text:String):List<Channel> {
        val lines=text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val out=mutableListOf<Channel>()
        var info:String?=null
        for (line in lines) {
            if (line.startsWith("#EXTINF", true)) info=line
            else if (!line.startsWith("#") && info != null) {
                val i=info!!; val attrs=attr.findAll(i).associate { it.groupValues[1] to it.groupValues[2] }
                val name=i.substringAfterLast(',').trim().ifBlank { attrs["tvg-name"] ?: "Channel" }
                out += Channel(name,line,attrs["group-title"] ?: "Other",attrs["tvg-logo"],attrs["tvg-id"])
                info=null
            }
        }
        return out
    }
}
