package cz.vsb.bra0174.osmz.httpserver

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Environment
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cz.vsb.bra0174.osmz.httpserver.model.LogEntry
import cz.vsb.bra0174.osmz.httpserver.server.CameraServerModule
import cz.vsb.bra0174.osmz.httpserver.server.CgiBinModule
import cz.vsb.bra0174.osmz.httpserver.server.FileServerModule
import cz.vsb.bra0174.osmz.httpserver.server.HttpServer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

//Requested camera resolution
private const val REQUESTED_WIDTH = 1280
private const val REQUESTED_HEIGHT = 720

//Requested camera side
private const val REQUESTED_FACING = CameraSelector.LENS_FACING_BACK

class HttpServerService : LifecycleService() {
    companion object {
        private const val TAG = "HttpServerService"
        val SERVER_PORT_KEY = "ServerPortIntentKey"
        val THREAD_COUNT_KEY = "ServerThreadCountIntentKey"
    }

    //Expose received parameters for activity to sync on bind
    private var _port: Int? = null
    val port: Int? get() = _port
    private var _threads: Int? = null
    val threads: Int? get() = _threads

    //Server instance itself
    private var httpServer: HttpServer? = null

    //Observable server status
    private val _serverRunning = MutableLiveData(false) //updated by the server
    val serverRunning: LiveData<Boolean> get() = _serverRunning

    private var _logs = ObservableArrayList<LogEntry>()
    private val logCollector: (String, String) -> Unit = { src, msg ->
        _logs.add(LogEntry(System.currentTimeMillis(), src, msg))
    }
    val logs: ObservableArrayList<LogEntry> get() = _logs
//    private val _requestsUnbind = MutableLiveData<Boolean>(false)
//    val requestsUnbind: LiveData<Boolean> get() = _requestsUnbind

    fun stop() {
        if (_serverRunning.value == true) httpServer?.shutdown()
        _serverRunning.value = false
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
    }

    //TODO: create started service with option to bind to it, use pending intent to pass permissions
    //NOTE: we did not specifically request to run in separate process in manifest, so no IPC is needed
    //TODO: expose status as live data as well
    //TODO: elevate to foreground service by itself
    private val initExecutor = Executors.newSingleThreadExecutor()
    private val initializing = AtomicBoolean(false)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.apply {
            val port = extras?.getInt(SERVER_PORT_KEY, -1).takeIf { it != -1 }
            val threadCount = extras?.getInt(THREAD_COUNT_KEY, -1).takeIf { it != -1 }
            if (port != null && threadCount != null) {
                if (_serverRunning.value != true && initializing.compareAndSet(false, true)) {
                    val providerFuture = ProcessCameraProvider.getInstance(this@HttpServerService)
                    // Runs init stuff on init thread,
                    // so we don't block the UI thread where onStartCommand is called from
                    providerFuture.addListener(Runnable {
                        //Get the ImageCapture object and bind it to this Service
                        val cameraProvider = providerFuture.get()
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(REQUESTED_FACING)
                            .build()
                        val imageCapture = ImageCapture.Builder()
                            .setTargetResolution(Size(REQUESTED_WIDTH, REQUESTED_HEIGHT))
                            .build()
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            this@HttpServerService,
                            cameraSelector,
                            imageCapture
                        )
                        //Instantiate and start the server itself
                        httpServer = HttpServer(
                            port, threadCount,
                            arrayOf(
                                CameraServerModule(imageCapture),
                                CgiBinModule(),
                                FileServerModule(Environment.getExternalStorageDirectory())
                            ),
                            logCollector,
                            { _serverRunning.postValue(true) },
                            { _serverRunning.postValue(false) }
                        ).apply { start() }.also { _serverRunning.postValue(true) }
                        //Elevate service to foregroundService and display its notification
                        //TODO

                        initializing.set(false)
                    }, initExecutor)
                }
            }
        }
        super.onStartCommand(intent, flags, startId)
        //TODO: may need to store startId for future use in stopSelf() call
        return Service.START_NOT_STICKY
    }

    inner class HttpServerServiceBinder : Binder() {
        fun getServiceInstance(): HttpServerService = this@HttpServerService
    }
    private val binder = HttpServerServiceBinder()

    override fun onBind(intent: Intent): HttpServerServiceBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        httpServer?.isAlive?.takeIf { it }?.let { httpServer?.shutdown() }
        super.onDestroy()
    }
}
//
//    private val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
//        setContentTitle("Http Server status")
//        setContentText("Http Server is running")
//        setSmallIcon(R.drawable.ic_launcher_foreground)
//        setShowWhen(false)
//    }