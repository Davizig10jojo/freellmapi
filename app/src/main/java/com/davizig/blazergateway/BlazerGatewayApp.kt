package com.davizig.blazergateway

import android.app.Application
import com.davizig.blazergateway.data.local.AppDatabase
import com.davizig.blazergateway.data.local.SecurePreferences
import com.davizig.blazergateway.data.remote.OpenAIApiClient
import com.davizig.blazergateway.data.repository.ChatRepository
import com.davizig.blazergateway.data.repository.ProviderRepository
import com.davizig.blazergateway.data.repository.SmartRouter
import com.davizig.blazergateway.server.LocalProxyServer

class BlazerGatewayApp : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var apiClient: OpenAIApiClient
        private set

    lateinit var smartRouter: SmartRouter
        private set

    lateinit var providerRepository: ProviderRepository
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var localProxyServer: LocalProxyServer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        securePreferences = SecurePreferences(this)
        database = AppDatabase.getInstance(this)
        apiClient = OpenAIApiClient()
        smartRouter = SmartRouter(database.usageTrackerDao())

        providerRepository = ProviderRepository(
            securePreferences = securePreferences,
            cachedModelDao = database.cachedModelDao(),
            apiClient = apiClient
        )

        chatRepository = ChatRepository(
            conversationDao = database.conversationDao(),
            messageDao = database.messageDao(),
            providerRepository = providerRepository,
            smartRouter = smartRouter,
            securePreferences = securePreferences,
            apiClient = apiClient
        )

        localProxyServer = LocalProxyServer(
            context = this,
            providerRepository = providerRepository,
            smartRouter = smartRouter,
            securePreferences = securePreferences,
            apiClient = apiClient
        )
    }

    companion object {
        lateinit var instance: BlazerGatewayApp
            private set
    }
}
