package com.stremiolivetv.model

data class Channel(val name:String, val url:String, val group:String="Other", val logo:String?=null, val tvgId:String?=null)
