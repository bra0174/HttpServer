package cz.vsb.bra0174.osmz.httpserver.server

import cz.vsb.bra0174.osmz.httpserver.date
import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import kotlinx.html.dom.serialize
import java.io.File

class FileServerModule(private val root: File) : HttpServerModule() {
    companion object{
        private const val TAG = "FileServer"
    }

    override fun canHandle(uri: String): Boolean = true //can handle any URI
    override val requestHandler: suspend HttpServer.(HttpRequest) -> HttpResponse = {
        when(it.method){
            HttpMethod.GET -> handleUri(it.uri)
            else -> httpError(
                StatusCode.HTTP_501
            )
        }
    }

    private fun handleUri(uri: String): HttpResponse {
        val file = root.resolve(uri.removePrefix("/"))
        return when (file.status) {
            FileStatus.NONEXISTENT -> fileNotFound(uri)
            FileStatus.FOLDER -> indexDirectory(file)
            FileStatus.FILE -> serveFile(file)
        }
    }

    private enum class FileStatus { NONEXISTENT, FILE, FOLDER }
    private val File.status
        get() = when {
            exists() && isFile -> FileStatus.FILE
            exists() && isDirectory -> FileStatus.FOLDER
            else -> FileStatus.NONEXISTENT
        }


    private fun fileNotFound(file: String) =
        htmlResponse(
            StatusCode.HTTP_404,
            createHTMLDocument().html {
                body {
                    h1 {
                        +"Error 404: $file not found"
                    }
                    br
                    span {
                        +"Sorry, server did not found specified file"
                    }
                }
            }.serialize()
        )

    private fun fileContentType(file: File) = "Content-Type" to when (file.extension) {
        "htm", "html" -> "text/html"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "txt" -> "text/plain"
        "css" -> "text/css"
        "js" -> "application/javascript"
        else -> "application/octet-stream"
    }

    private fun indexDirectory(dir: File) =
        htmlResponse(
            StatusCode.HTTP_200,
            createHTMLDocument().body {
                h1 {
                    +"Index of ${dir.withoutRoot(root)}"
                }
                br
                table {
                    style = "width:100%"
                    tr {
                        th { +"Filename" }
                        th { +"Date" }
                        th { +"Size" }
                    }
                    tr {
                        td { a("./") { +"." } }
                        td { +dir.date() }
                        td { +"-" }
                    }
                    if (dir != root) {
                        tr {
                            td { a("../") { +".." } }
                            td { +(dir.parentFile?.date() ?: "N/A") }
                            td { +"-" }
                        }
                    }
                    dir.listFiles()?.map {
                        tr {
                            td { a(it.withoutRoot(root)) { +it.name } }
                            td { +it.date() }
                            td { +(if (it.isDirectory) "-" else "${it.length()}") }
                        }
                    }
                }
            }.serialize()
        )

    private fun File.withoutRoot(root: File) =
        toRelativeString(root).run { if (startsWith("/")) this else "/$this" }

    private fun serveFile(file: File) =
        FinalHttpResponse(
            StatusCode.HTTP_200,
            mapOf(
                fileContentType(file)
            ),
            file.readBytes()
        )
}