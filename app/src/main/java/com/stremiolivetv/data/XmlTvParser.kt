package com.stremiolivetv.data

import android.util.Xml
import com.stremiolivetv.model.Programme
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

object XmlTvParser {
    fun parse(input: InputStream): List<Programme> {
        val parser = Xml.newPullParser().apply { setInput(input, null) }
        val output = mutableListOf<Programme>()
        var channel = ""
        var start = 0L
        var stop = 0L
        var title = ""
        var description: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel") ?: ""
                        start = parseTime(parser.getAttributeValue(null, "start"))
                        stop = parseTime(parser.getAttributeValue(null, "stop"))
                        title = ""
                        description = null
                    }
                    "title" -> title = parser.nextText()
                    "desc" -> description = parser.nextText()
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "programme" && channel.isNotBlank()) {
                output += Programme(channel, title.ifBlank { "Program" }, start, stop, description)
            }
            event = parser.next()
        }
        return output
    }

    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val compact = raw.trim().substringBefore(' ')
        val patterns = listOf("yyyyMMddHHmmss", "yyyyMMddHHmm")
        for (pattern in patterns) {
            runCatching { return SimpleDateFormat(pattern, Locale.US).parse(compact)?.time ?: 0L }
        }
        return 0L
    }
}
