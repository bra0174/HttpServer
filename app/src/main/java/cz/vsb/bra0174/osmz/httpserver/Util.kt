package cz.vsb.bra0174.osmz.httpserver

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DateFormats{
    val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
    val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss GMT", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }
}

fun getTimestampString(date: Long): String = DateFormats.timestampFormat.format(Date(date))

fun File.date(): String = DateFormats.httpDateFormat.format(Date(this.lastModified()))
val Any.threadName: String get() = Thread.currentThread().name
