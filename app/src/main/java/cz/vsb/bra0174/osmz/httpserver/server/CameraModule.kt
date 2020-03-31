package cz.vsb.bra0174.osmz.httpserver.server

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import java.util.concurrent.Executors
import java.util.stream.Stream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private const val SNAPSHOT_URI = "/camera/snapshot"
private const val STREAM_URI = "/camera/stream"
private val HANDLED_URIS = setOf(
    SNAPSHOT_URI,
    STREAM_URI
)

class CameraServerModule(private val imageCapture: ImageCapture) : HttpServerModule() {
    private val callbackExecutorPool = Executors.newCachedThreadPool()
    private val streamExecutorContext =
        CoroutineScope(Executors.newCachedThreadPool().asCoroutineDispatcher())

    override fun canHandle(uri: String): Boolean = uri in HANDLED_URIS

    @ExperimentalCoroutinesApi
    override val requestHandler: suspend HttpServer.(HttpRequest) -> HttpResponse = {
        when (it.method) {
            HttpMethod.GET -> {
                when (it.uri) {
                    SNAPSHOT_URI -> {
                        val data = imageCapture.aTakePicture()
                        if (data != null) {
                            FinalHttpResponse(
                                StatusCode.HTTP_200,
                                mapOf("Content-Type" to "image/jpeg"),
                                data
                            )
                        } else {
                            httpError(StatusCode.HTTP_500)
                        }
                    }
                    STREAM_URI -> {
                        val receiveChannel = streamExecutorContext.produce {
                            while (true) {
                                val frame = imageCapture.aTakePicture() ?: break
                                send(StreamHttpResponse.constructPart("image/jpeg", frame))
                            }
                        }
                        StreamHttpResponse(
                            StatusCode.HTTP_200,
                            mapOf(),
                            receiveChannel
                        )
                    }
                    else -> httpError(StatusCode.HTTP_500)
                }
            }
            else -> httpError(StatusCode.HTTP_501)
        }
    }

    private suspend fun ImageCapture.aTakePicture() = suspendCoroutine<ByteArray?> {
        takePicture(callbackExecutorPool, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.capacity()).also { array -> buffer.get(array) }
                super.onCaptureSuccess(image) //Closes the image
                it.resume(bytes)
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                it.resume(null)
            }
        })
    }
}