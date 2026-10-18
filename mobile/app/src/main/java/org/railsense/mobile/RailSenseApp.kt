package org.railsense.mobile

import android.app.Application
import kotlinx.coroutines.*

class RailSenseApp : Application() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    lateinit var ready: Deferred<Unit>; private set
    override fun onCreate() {
        super.onCreate()
        ready=scope.async { Store.db(this@RailSenseApp).trips().recover(System.currentTimeMillis()) }
    }
}

