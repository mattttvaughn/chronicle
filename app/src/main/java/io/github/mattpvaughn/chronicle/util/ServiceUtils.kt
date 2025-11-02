package io.github.mattpvaughn.chronicle.util

import android.app.Service
import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** Static functions for tracking service state within the app process. */
object ServiceUtils {
    private val runningServices = ConcurrentHashMap<Class<*>, Boolean>()

    fun notifyServiceStarted(service: Service) {
        runningServices[service::class.java] = true
    }

    fun notifyServiceStopped(service: Service) {
        runningServices.remove(service::class.java)
    }

    fun isServiceRunning(
        @Suppress("UNUSED_PARAMETER") context: Context,
        serviceClass: Class<*>,
    ): Boolean = runningServices[serviceClass] == true
}
