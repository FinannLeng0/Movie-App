package com.example.konkhmermovie


import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData

class NetworkUtils(context: Context) : LiveData<Boolean>() {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            postValue(true)
        }

        override fun onLost(network: Network) {
            postValue(false)
        }
    }

    override fun onActive() {
        super.onActive()
        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        postValue(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
        cm.registerDefaultNetworkCallback(callback)
    }

    override fun onInactive() {
        super.onInactive()
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) { }
    }
}

