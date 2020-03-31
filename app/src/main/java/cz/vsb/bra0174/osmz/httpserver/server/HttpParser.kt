package cz.vsb.bra0174.osmz.httpserver.server

import android.util.Log
import cz.vsb.bra0174.osmz.httpserver.DateFormats
import kotlinx.coroutines.channels.Channel
import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import kotlinx.html.dom.serialize
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList

private const val TAG = "HttpParser"
val HTTP_HEADER_CHARSET = Charsets.US_ASCII
val HTTP_DEFAULT_CHARSET = Charsets.ISO_8859_1
val HTML_DEFAULT_CHARSET = Charsets.UTF_8

enum class HttpMethod {
    GET, HEAD, POST, PUT, DELETE, OPTIONS;

    companion object {
        val LONGEST_NAME = values().map { it.name.length }.max()!!
    }
}

data class HttpRequest(
    val method: HttpMethod,
    val uri: String,
    val headers: Map<String, String>,
    val body: ByteArray? = null
)


fun ByteBuffer.parseHttpRequest(): HttpRequest? {
    try {
        val method = readUntilSpace(HttpMethod.LONGEST_NAME + 1).let {
            try {
                HttpMethod.valueOf(it)
            } catch (ex: IllegalArgumentException) {
                return null //Unknown method
            }
        }
        val uri = readUntilSpace()
        val httpVersion = readUntilNewline()
        if (httpVersion != "HTTP/1.1") {
            Log.w(TAG, "Unexpected http version string $httpVersion")
        }
        val headerLines = ArrayList<String>()
        while (true) {
            val line = readUntilNewline()
            if (line == "") break
            headerLines.add(line)
        }
        val headers = headerLines.associate { it.split(": ").let { parts -> parts[0] to parts[1] } }
        val body = if (remaining() > 0) ByteArray(remaining()).also { get(it) } else null
        return HttpRequest(
            method,
            uri,
            headers,
            body
        )
    } catch (ex: BufferUnderflowException) {
        return null //malformed request
    }
}

private fun ByteBuffer.readUntilSpace(): String {
    val spaceByte = ' '.toByte()
    return StringBuilder().apply {
        while (true) {
            val byte = get()
            if (byte == spaceByte) break
            append(byte.toChar())
        }
    }.toString()
}

private fun ByteBuffer.readUntilSpace(limit: Int): String {
    val spaceByte = ' '.toByte()
    return StringBuilder(limit).apply {
        for (i in 0..limit) {
            val byte = get()
            if (byte == spaceByte) break
            append(byte.toChar())
            if (i == limit) throw BufferUnderflowException()
        }
    }.toString()
}

private fun ByteBuffer.readUntilNewline(): String {
    val crByte = '\r'.toByte()
    val lfByte = '\n'.toByte()
    return StringBuilder().apply {
        while (true) {
            val byte = get()
            if (byte == crByte) {
                if (get() == lfByte) {
                    break
                } else {
                    throw BufferUnderflowException()
                }
            }
            append(byte.toChar())
        }
    }.toString()
}

enum class StatusCode(val value: String) {
    //1xx
    HTTP_100("Continue"),

    //2xx
    HTTP_200("OK"),
    HTTP_201("Created"),
    HTTP_202("Accepted"),
    HTTP_204("No Content"),
    HTTP_206("Partial Content"),

    //3xx
    HTTP_300("Multiple Choice"),
    HTTP_301("Moved Permanently"),
    HTTP_302("Found"),
    HTTP_303("See Other"),
    HTTP_304("Not Modified"),
    HTTP_307("Temporary Redirect"),
    HTTP_308("Permanent Redirect"),

    //4xx
    HTTP_400("Bad Request"),
    HTTP_401("Unauthorized"),
    HTTP_403("Forbidden"),
    HTTP_404("Not Found"),
    HTTP_405("Method Not Allowed"),
    HTTP_408("Request Timeout"),

    //5xx
    HTTP_500("Internal Server Error"),
    HTTP_501("Not Implemented"),
    HTTP_503("Service Unavailable");

    val code: Int get() = name.removePrefix("HTTP_").toInt()
    val statusLine: String get() = "$code $value"
    fun defaultHtml() = createHTMLDocument().html {
        body {
            h1 {
                +"HTTP ${if (code in 100..299) "status" else "error"} code $code"
            }
            br
            span {
                +value
            }
        }
    }.serialize()

    fun defaultResponse() =
        htmlResponse(this, defaultHtml())
}

sealed class HttpResponse(val statusCode: StatusCode, headers: Map<String, String>) {
    open val headers: Map<String, String> =
        headers + ("Date" to DateFormats.httpDateFormat.format(Date()))

    fun encodeHeader(): ByteArray = """
    ${statusCode.statusLine}
    ${
    if (headers.isEmpty())
        ""
    else
        headers.map { "${it.key}: ${it.value}\n" }.reduce(String::plus)
    }
    """.trimIndent().plus("\n").toByteArray(HTTP_HEADER_CHARSET)
}

class FinalHttpResponse(
    statusCode: StatusCode,
    headers: Map<String, String>,
    val body: ByteArray?
) : HttpResponse(statusCode, headers) {
    override val headers =
        super.headers.run { body?.let { plus("Content-Length" to body.size.toString()) } ?: this }

    fun encode(): ByteArray = encodeHeader() + (body ?: byteArrayOf())
}

class StreamHttpResponse(
    statusCode: StatusCode,
    headers: Map<String, String>,
    val partEmitter: Channel<ByteArray>,
    val boundary: String = "MyDefaultBoundaryString"
) : HttpResponse(statusCode, headers) {
    override val headers =
        super.headers - "Content-Length" + ("Content-Type" to "multipart/x-mixed-replace;boundary=$boundary")
    val boundaryBytes = "--$boundary\r\n".toByteArray(HTTP_HEADER_CHARSET)

    companion object {
        fun constructPart(contentType: String, data: ByteArray) =
            "Content-Type: $contentType\r\nContent-Length: ${data.size}\r\n\r\n"
                .toByteArray(HTTP_HEADER_CHARSET) + data
    }
}

fun htmlResponse(code: StatusCode, html: String) =
    FinalHttpResponse(
        code,
        mapOf("Content-Type" to "text/html;charset=$HTML_DEFAULT_CHARSET"),
        html.toByteArray(HTML_DEFAULT_CHARSET)
    )

fun httpError(code: StatusCode) =
    htmlResponse(code, code.defaultHtml())

//class HttpResponse(
//    val status: StatusCode,
//    headers: Map<String, String>,
//    val body: ByteArray? = null
//) {
//    //Adds content-length if applicable and date
//    val headers = (body?.let { headers + ("Content-Length" to body.size) } ?: headers) +
//            ("Date" to SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss GMT", Locale.US)
//                .also { it.timeZone = TimeZone.getTimeZone("GMT") }.format(Date()))
//}

//fun HttpResponse.encode() =
//    """
//        ${status.statusLine}
//        ${if (headers.isEmpty()) "\n" else headers.map { "${it.key}: ${it.value}\n" }
//        .reduce(String::plus)}
//    """.trimIndent()
//        .plus("\n")
//        .toByteArray(HTTP_DEFAULT_CHARSET)
//        .let { if (body != null) it.plus(body) else it }


//fun Socket.sendHttpResponse(response: HttpResponse) {
//    getOutputStream().apply {
//        write(response.encode())
//        flush()
//        close()
//    }
//}
