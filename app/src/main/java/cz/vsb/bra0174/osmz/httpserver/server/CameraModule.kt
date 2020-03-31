package cz.vsb.bra0174.osmz.httpserver.server

import androidx.camera.core.ImageCapture
import kotlinx.coroutines.channels.Channel
import java.util.stream.Stream


private const val SNAPSHOT_URI = "/camera/snapshot"
private const val STREAM_URI = "/camera/stream"
private val HANDLED_URIS = setOf(
    SNAPSHOT_URI,
    STREAM_URI
)

class CameraServerModule(private val imageCapture: ImageCapture) : HttpServerModule() {
    override fun canHandle(uri: String): Boolean = uri in HANDLED_URIS
    override val requestHandler: suspend HttpServer.(HttpRequest) -> HttpResponse = {
        when (it.method) {
            HttpMethod.GET -> {
                when (it.uri) {
                    SNAPSHOT_URI -> {
                        val snapshot = getSnapshot()
                        if(snapshot != null){
                            FinalHttpResponse(
                                StatusCode.HTTP_200,
                                mapOf(),
                                snapshot//TODO: add image content type
                            )
                        }else{
                            httpError(StatusCode.HTTP_500)
                        }
                    }
                    STREAM_URI -> {
                        val streamEmitter = getStreamEmitter()
                        if(streamEmitter != null){
                            StreamHttpResponse(
                                StatusCode.HTTP_200,
                                mapOf(),
                                streamEmitter
                            )
                        }else{
                            httpError(StatusCode.HTTP_500)
                        }
                    }
                    else -> httpError(StatusCode.HTTP_500)
                }
            }
            else -> httpError(StatusCode.HTTP_501)
        }
    }
    private suspend fun getSnapshot(): ByteArray?{

    }
    private suspend fun getStreamEmitter(): Channel<ByteArray>? {

    }
}