package com.stremiolivetv.model

data class Media(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val description: String? = null,
    val year: String? = null,
    val rating: String? = null
)

data class Stream(val title: String, val url: String)

data class Programme(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val stopMillis: Long,
    val description: String? = null
)
