package io.github.mattpvaughn.chronicle.util

import android.app.ActivityManager
import android.content.Context


/** Static functions for managing services */
object ServiceUtils {
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}