package cz.vsb.bra0174.osmz.httpserver.server

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import cz.vsb.bra0174.osmz.httpserver.threadName
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.channels.CompletionHandler
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val SOCKET_TIMEOUT = 0L //Set to non-zero value to enable
private const val HTTP_REQUEST_MAX_SIZE = 8192

abstract class HttpServerModule {
    abstract fun canHandle(uri: String): Boolean
    abstract val requestHandler: suspend HttpServer.(HttpRequest) -> HttpResponse
}

class HttpServer(
    port: Int,
    threadPoolSize: Int,
    private val modules: Array<HttpServerModule>,
    private val emitLog: (src: String, msg: String) -> Unit,
    private val startupCallback: () -> Unit,
    private val shutdownCallback: () -> Unit
) : Thread("HttpServerThread(port:$port,threadPool:$threadPoolSize)") {
    companion object {
        private const val TAG = "HttpServer"
    }

    private val running = AtomicBoolean(false)

    private val dispatcher = Executors.newFixedThreadPool(threadPoolSize).asCoroutineDispatcher()
    private val dispatcherScope = CoroutineScope(dispatcher)
    private val serverSocket = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(port))

    override fun start() {
        running.set(true)
        super.start()
    }

    fun shutdown() {
        running.set(false)
        serverSocket.close()
        dispatcherScope.cancel()
        dispatcher.close()
        join()
    }

    override fun run() {
        startupCallback()
        mainLoop@ while (running.compareAndSet(true, true)) {
            //Get incoming connection
            val socket = try {
                serverSocket.accept().get()
            } catch (ex: Exception) {
                when (ex) {
                    is AcceptPendingException -> {
                        Log.w(TAG, "Accept already pending")
                        continue@mainLoop
                    }
                    else -> {
                        Log.e(
                            TAG, "Caught unexpected exception while"
                                    + "waiting for incoming connection. Ex=$ex"
                        )
                        break@mainLoop //TODO: may need to handle closing socket and such
                    }
                }
            }
            //Dispatch handling of the connection to the pool
            val exceptionHandler = CoroutineExceptionHandler { _, exc ->
                socket.log("Encountered exception $exc, closing socket")
                socket.close()
            }
            dispatcherScope.launch(exceptionHandler) {
                socket.log("New incoming connection from ${socket.remoteAddress}")
                val buffer = ByteBuffer.allocate(HTTP_REQUEST_MAX_SIZE)
                val requestSize = try {
                    socket.aRead(
                        buffer,
                        SOCKET_TIMEOUT
                    )
                } catch (ex: InterruptedByTimeoutException) {
                    socket.log("Connection timed out: No incoming data within the window of $SOCKET_TIMEOUT ms")
                    throw ex //gets passed to the coroutine exception handler, which closes socket
                }
                if (requestSize > 0) {
                    val response: HttpResponse = buffer.parseHttpRequest()?.let { request ->
                        socket.log("Received HTTP request for ${request.uri}")
                        modules.firstOrNull() { it.canHandle(request.uri) }?.run {
                            requestHandler(this@HttpServer, request)
                        } ?: httpError(
                            StatusCode.HTTP_501
                        ) //No module found for the request uri
                    } ?: httpError(
                        StatusCode.HTTP_400
                    ) //Malformed/wrong request
                    socket.log("Responding with ${response.statusCode.statusLine}")
                    when (response) {
                        is FinalHttpResponse -> socket.aWrite(response.encode())
                        is StreamHttpResponse -> {
                            socket.aWrite(response.encodeHeader())
                            response.partEmitter.consumeEach {
                                socket.aWrite(response.boundaryBytes + it)
                            }
                        }
                    }
                }
                socket.log("Closing connection to ${socket.remoteAddress}")
                socket.takeIf { it.isOpen }?.close()
            }
        }
        if(serverSocket.isOpen) serverSocket.close()
        shutdownCallback()
    }

    private suspend fun AsynchronousSocketChannel.aRead(buf: ByteBuffer, timeout: Long): Int =
        suspendCoroutine {
            read(buf, timeout, TimeUnit.MILLISECONDS, Unit, object : CompletionHandler<Int, Unit> {
                override fun completed(result: Int, attachment: Unit) {
                    it.resume(result)
                }

                override fun failed(exc: Throwable, attachment: Unit) {
                    it.resumeWithException(exc)
                }
            })
        }

    private suspend fun AsynchronousSocketChannel.aWrite(data: ByteArray) =
        aWrite(ByteBuffer.allocate(data.size).put(data))

    private suspend fun AsynchronousSocketChannel.aWrite(data: ByteBuffer): Int = suspendCoroutine {
        write(data, Unit, object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit) {
                it.resume(result)
            }

            override fun failed(exc: Throwable, attachment: Unit) {
                it.resumeWithException(exc)
            }
        })
    }

    private fun AsynchronousSocketChannel.log(msg: String) =
        emitLog("$threadName:$remoteAddress", msg)
}