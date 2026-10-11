package com.davizig.blazergateway.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.davizig.blazergateway.server.LocalProxyServer
import com.davizig.blazergateway.server.ServerLogEntry
import kotlinx.coroutines.flow.StateFlow

class ServerViewModel(
    private val server: LocalProxyServer
) : ViewModel() {

    val isRunning: StateFlow<Boolean> = server.isRunning
    val port: StateFlow<Int> = server.port
    val logs: StateFlow<List<ServerLogEntry>> = server.logs

    fun getLocalIpAddress(): String {
        return server.getLocalIpAddress()
    }

    fun startServer(port: Int = 8080) {
        server.startServer(port)
    }

    fun stopServer() {
        server.stopServer()
    }

    fun toggleServer(port: Int = 8080) {
        if (isRunning.value) {
            stopServer()
        } else {
            startServer(port)
        }
    }

    class Factory(
        private val server: LocalProxyServer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ServerViewModel(server) as T
        }
    }
}
