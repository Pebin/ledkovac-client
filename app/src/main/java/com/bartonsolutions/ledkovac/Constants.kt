package com.bartonsolutions.ledkovac

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object Constants {
    val DATEFORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").also {
        it.timeZone = TimeZone.getTimeZone("UTC")
    }
    const val SERVER_PORT: Int = 5600
}