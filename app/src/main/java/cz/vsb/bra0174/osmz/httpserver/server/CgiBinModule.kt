package cz.vsb.bra0174.osmz.httpserver.server

import kotlinx.coroutines.*
import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import kotlinx.html.dom.serialize
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

private const val COMMAND_CHARSET = "UTF-8"
private const val COMMAND_TIMEOUT = 5000L //5s command timeout

class CgiBinModule(private val uriPrefix: String = "/cgi-bin") : HttpServerModule() {
    companion object {
        private const val TAG = "CgiBinModule"
        private const val FORM_ID = "cgiform"
    }

    override fun canHandle(uri: String): Boolean = uri.startsWith(uriPrefix)
    override val requestHandler: suspend HttpServer.(HttpRequest) -> HttpResponse = {
        when (it.method) {
            HttpMethod.GET -> {
                when (it.uri) {
                    uriPrefix -> htmlResponse(StatusCode.HTTP_200, cgiBinPage)
                    else -> {
                        val command = URLDecoder.decode(
                            it.uri.removePrefix("$uriPrefix/"),
                            COMMAND_CHARSET
                        )
                        val result = executeCommand(command, COMMAND_TIMEOUT)
                        if (result == null) {
                            htmlResponse(StatusCode.HTTP_408, commandTimeoutHtml(command))
                        } else {
                            FinalHttpResponse(
                                StatusCode.HTTP_200,
                                mapOf("Content-Type" to "text/plain"),
                                result.toByteArray()
                            )
                        }
                    }
                }
            }
            else -> httpError(
                StatusCode.HTTP_501
            )
        }
    }

    private val javascriptOnChange =
        "document.getElementById('$FORM_ID').setAttribute('action','$uriPrefix/'+encodeURI(this.value))"
    private val cgiBinPage = createHTMLDocument().html {
        body {
            h1 {
                +"Cgi-bin"
            }
            span {
                +"Enter your command and click submit"
            }
            form(action = ".", method = FormMethod.get) {
                id = FORM_ID
                input(type = InputType.text) { onChange = javascriptOnChange }
                input(type = InputType.submit) { value = "Submit" }
            }
        }
    }.serialize()

    private fun commandTimeoutHtml(command: String): String = createHTMLDocument().html {
        body {
            h1 {
                +"Error - Command execution timed out"
            }
            span {
                +"Your command $command did not finish execution within $COMMAND_TIMEOUT ms window and was cancelled."
            }
        }
    }.serialize()

    private suspend fun executeCommand(command: String, timeout: Long): String? = coroutineScope {
        val commands = //Split by space but ignore it if inside quotation marks
            command.split(Regex(" (?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*\$)"))
        val process = withContext(Dispatchers.IO) { ProcessBuilder(commands).start() }
        val resultBuilder = StringBuilder()
        val readerJob = launch(Dispatchers.IO) {
            try {
                while (true) {
                    val byte = process.inputStream.read()
                    if (byte == -1) break
                    resultBuilder.append(byte.toChar())
                }
            } catch (ex: Exception) {
                return@launch
            }
        }
        return@coroutineScope withContext(Dispatchers.IO) {
            val result = process.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (readerJob.isActive) readerJob.cancel()
            if (result) resultBuilder.toString() else null
        }
    }
}
