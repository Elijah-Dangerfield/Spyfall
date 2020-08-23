package com.dangerfield.spyfall.api

import com.dangerfield.spyfall.util.ConnectivityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class Connectivity : ConnectivityHelper {

    /**
     * Checks whether device is currently online or not by Pinging a server
     * This uses Google's DNS (very unlikely to ever be down)
     * Preferable compared to connectivity/session manager as connected != online
     */

    override suspend fun isOnline(): Boolean {

        val runtime = Runtime.getRuntime()
        try {
            val exitVal = withContext(Dispatchers.IO) {
                val ipProcess = runtime.exec("/system/bin/ping -c 1 8.8.8.8")
                return@withContext ipProcess.waitFor()
            }
            return exitVal == 0
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        return false
    }
}