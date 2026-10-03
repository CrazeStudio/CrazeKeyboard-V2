package com.example.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.settings.KeyboardPreferences

object NetworkCheck {
    
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
            else -> false
        }
    }

    /**
     * Checks if internet is available AND if the user's "Network Mode" setting allows it.
     */
    fun canUseNetwork(context: Context): Boolean {
        val prefs = KeyboardPreferences.getInstance(context)
        val mode = prefs.networkMode
        
        if (mode == "OFFLINE_ONLY") return false
        
        // If mode is ONLINE_ONLY or AUTO, we check physical connectivity
        return isNetworkAvailable(context)
    }
    
    fun enforceOfflineMode(context: Context): Boolean {
        val prefs = KeyboardPreferences.getInstance(context)
        return prefs.networkMode == "OFFLINE_ONLY"
    }
}
