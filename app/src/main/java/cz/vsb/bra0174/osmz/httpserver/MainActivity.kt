package cz.vsb.bra0174.osmz.httpserver

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.observe
import cz.vsb.bra0174.osmz.httpserver.databinding.ActivityMainBinding
import cz.vsb.bra0174.osmz.httpserver.view.LogEntryAdapter
import cz.vsb.bra0174.osmz.httpserver.viewmodel.MainActivityViewModel
import kotlin.math.log

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private val NEEDED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
        )
        private const val PERMISSION_REQUEST_ID = 1
    }

    private val viewModel: MainActivityViewModel by viewModels() //ensures there is only one instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding =
            DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.viewmodel = viewModel
        binding.lifecycleOwner = this
        getPermissions(NEEDED_PERMISSIONS)
        //Attach/detach LogEntryAdapter adapter to the RecyclerView based on availability of logEntryList
        binding.viewmodel.logEntryList.observe(this) { logs ->
            if (logs != null) {
                binding.logEntryList.adapter = LogEntryAdapter(this, logs)
            } else {
                binding.logEntryList.adapter = null
            }
        }
        viewModel.boundToService.observe(this) { bound ->
            if (bound) { //Update UI and set observers
                //Synchronize UI input
                viewModel.serverPort.value =
                    viewModel.serverService.value!!.port.toString()
                viewModel.serverThreadCount.value =
                    viewModel.serverService.value!!.threads.toString()
                //NOTE: service cant be destroyed when it is bound
                //Observe server status, when it stops, unbind so that the service can be destroyed
                viewModel.serverService.value!!.serverRunning.observe(this){
                    
                }
                viewModel.logEntryList.value = viewModel.serverService.value!!.logs
            } else { //remove references and observers

            }
        }
    }

    override fun onStart() {
        super.onStart()
        tryToUnbind()
        if (serviceIsRunning(HttpServerService::class.java)) {
            bindToService()
        }
    }

    override fun onStop() { //Unbinds on stop, so service can be stopped when activity is not shown
        super.onStop()
        tryToUnbind()
    }

    //NOTE: start the service with pending intent, so it gets permissions of activity

    private fun tryToUnbind(): Unit = try {
        viewModel.serverService.value = null
        unbindService(serviceConnection)
        viewModel.boundToService.value = false
    } catch (ex: SecurityException) {
    }

    private fun bindToService() = bindService(Intent(), serviceConnection, 0)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            viewModel.serverService.value =
                (binder as HttpServerService.HttpServerServiceBinder).getServiceInstance()
            viewModel.boundToService.value = true
        }

        //WARNING: ONLY CALLED ON CRASH OR EXCEPTION!!!
        override fun onServiceDisconnected(className: ComponentName?) {
            viewModel.serverService.value = null
            viewModel.boundToService.value = false
        }
    }

    private fun getPermissions(list: Array<String>) {
        val neededPermissions = list.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (neededPermissions.isNotEmpty())
            ActivityCompat.requestPermissions(this, neededPermissions, PERMISSION_REQUEST_ID)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_ID) {
            if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(
                    this,
                    "This application can NOT work without its permissions.",
                    Toast.LENGTH_LONG
                ).show().also { finish() }
            }
        }
    }

    @Suppress("DEPRECATION")
    // ^^ the deprecation concerns only third-party services, function should still return applications own services
    //TODO: may be safe to reduce the maximum number in the list from Integer.MAX_VALUE to 1
    private fun <T : Service> Context.serviceIsRunning(serviceClass: Class<T>): Boolean =
        getSystemService(ActivityManager::class.java)!!.getRunningServices(Integer.MAX_VALUE).any {
            it.service.className == serviceClass.name
        }

}