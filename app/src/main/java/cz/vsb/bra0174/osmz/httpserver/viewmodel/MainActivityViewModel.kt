package cz.vsb.bra0174.osmz.httpserver.viewmodel

import android.app.Application
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.databinding.ObservableList
import androidx.lifecycle.*
import cz.vsb.bra0174.osmz.httpserver.HttpServerService
import cz.vsb.bra0174.osmz.httpserver.model.LogEntry

private const val TAG = "MainActivityViewModel"

//Defaults
private const val DEFAULT_PORT = 1234
private const val DEFAULT_THREAD_COUNT = 4

//Input validation parameters
private const val MIN_THREAD_COUNT = 1
private const val MAX_THREAD_COUNT = 32
private const val MIN_PORT_VALUE = 1
private const val MAX_PORT_VALUE = 65535

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    companion object{
        private const val NOTIFICATION_CHANNEL_ID = "ServiceNotificationChannel"
    }
    //UI element backing properties live data
    //Two-way bound live data input
    val serverPort = MutableLiveData(DEFAULT_PORT.toString())
    val serverThreadCount = MutableLiveData(DEFAULT_THREAD_COUNT.toString())

    //One way bound live data output
    val running = MutableLiveData(false)
    val logEntryList = MutableLiveData<ObservableList<LogEntry>?>(null)

    //NOTE: take care when accessing the data in the observable list, since it may be invalid if the
    //      service stops and is destroyed (there is probably way to check it)
    //TODO:
    val serverService = MutableLiveData<HttpServerService?>(null)
    val boundToService = MutableLiveData<Boolean>(false)

    private val notification =
        NotificationCompat.Builder(getApplication(), NOTIFICATION_CHANNEL_ID).build()

    //Input validation live data
    val serverPortValid = MediatorLiveData<Boolean>().apply {
        addSource(serverPort) {
            value = serverPort.value?.toIntOrNull()?.let { it in MIN_PORT_VALUE..MAX_PORT_VALUE }
                ?: false
        }
    }
    val serverThreadCountValid = MediatorLiveData<Boolean>().apply {
        addSource(serverThreadCount) {
            value =
                serverThreadCount.value?.toIntOrNull()
                    ?.let { it in MIN_THREAD_COUNT..MAX_THREAD_COUNT }
                    ?: false
        }
    }

    private fun isInputValid() =
        serverPortValid.value ?: false && serverThreadCountValid.value ?: false

    val inputValid = MediatorLiveData<Boolean>().apply {
        addSource(serverPortValid) { value = isInputValid() }
        addSource(serverThreadCountValid) { value = isInputValid() }
    }

    fun onButtonClick(view: View) {

    }

    private fun nonNull(vararg data: LiveData<out Any?>) = data.all { it.value != null }

//        view.isClickable = false
////        view.overlay.add() TODO: add loading overlay
//        when (running.value ?: false) {
//            true -> {
//                socketServer.value?.shutdown()
//                socketServer.value = null
//                running.value = false
//            }
//            false -> {
//                if (nonNull(serverPort, serverThreadCount, fileServerModule, cameraServerModule)) {
//                    socketServer.value = HttpServer(
//                        serverPort.value!!.toInt(),
//                        serverThreadCount.value!!.toInt(),
//                        arrayOf(fileServerModule.value!!, cameraServerModule.value!!),
//                        addLog
//                    ).apply {
//                        start()
//                    }
//                    running.value = true
//                } else {
//                    Log.e(TAG, "Input validation error: allowed invalid input")
//                }
//            }
//        }
//        view.isClickable = true
//    }
}