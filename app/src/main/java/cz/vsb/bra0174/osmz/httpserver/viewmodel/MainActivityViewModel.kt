package cz.vsb.bra0174.osmz.httpserver.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.view.View
import androidx.core.app.NotificationCompat
import androidx.databinding.ObservableList
import androidx.lifecycle.*
import cz.vsb.bra0174.osmz.httpserver.HttpServerService
import cz.vsb.bra0174.osmz.httpserver.R
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
    //UI element backing properties live data
    //Two-way bound live data input
    val serverPort = MutableLiveData(DEFAULT_PORT.toString())
    val serverThreadCount = MutableLiveData(DEFAULT_THREAD_COUNT.toString())

    //One way bound live data output
    val running = MutableLiveData(false)
    val logEntryList = MutableLiveData<ObservableList<LogEntry>?>(null)

    val serverService = MutableLiveData<HttpServerService?>(null)
    val boundToService = MutableLiveData<Boolean>(false)

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
//    private fun nonNull(vararg data: LiveData<out Any?>) = data.all { it.value != null }
}

