package com.davizig.blazergateway.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.davizig.blazergateway.data.local.SecurePreferences
import com.davizig.blazergateway.data.remote.OpenAIApiClient
import com.davizig.blazergateway.data.repository.ProviderRepository
import com.davizig.blazergateway.data.repository.SmartRouter
import com.davizig.blazergateway.domain.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ServerLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val method: String,
    val path: String,
    val clientIp: String,
    val statusCode: Int
)

class LocalProxyServer(
    private val context: Context,
    private val providerRepository: ProviderRepository,
    private val smartRouter: SmartRouter,
    private val securePreferences: SecurePreferences,
    private val apiClient: OpenAIApiClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(8080)
    val port = _port.asStateFlow()

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    /**
     * Obtém o endereço IPv4 do dispositivo na rede local Wi-Fi.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.startsWith("192.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun startServer(portToUse: Int = 8080) {
        if (_isRunning.value) return
        _port.value = portToUse

        serverJob = scope.launch {
            try {
                val socket = ServerSocket(portToUse)
                serverSocket = socket
                _isRunning.value = true
                addLog("SYSTEM", "/start", "0.0.0.0", 200)

                while (isActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch { handleClientSocket(client) }
                    } catch (_: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalProxyServer", "Erro ao iniciar servidor local: ${e.message}")
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
        _isRunning.value = false
        addLog("SYSTEM", "/stop", "0.0.0.0", 200)
    }

    private fun addLog(method: String, path: String, clientIp: String, statusCode: Int) {
        val entry = ServerLogEntry(
            timestamp = dateFormat.format(Date()),
            method = method,
            path = path,
            clientIp = clientIp,
            statusCode = statusCode
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 50) current.removeAt(current.size - 1)
        _logs.value = current
    }

    private suspend fun handleClientSocket(client: Socket) = withContext(Dispatchers.IO) {
        val clientIp = client.inetAddress.hostAddress ?: "desconhecido"
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val output = client.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val path = parts[1]

            // Lê cabeçalhos HTTP
            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            // Tratamento de requisição OPTIONS (CORS preflight)
            if (method == "OPTIONS") {
                sendCorsHeaders(output, 204)
                output.flush()
                addLog(method, path, clientIp, 204)
                return@withContext
            }

            when {
                path == "/" || path == "/v1" || path == "/status" -> {
                    handleStatus(output)
                    addLog(method, path, clientIp, 200)
                }
                path == "/v1/models" || path == "/models" -> {
                    handleModels(output)
                    addLog(method, path, clientIp, 200)
                }
                path == "/v1/chat/completions" || path == "/chat/completions" -> {
                    handleChatCompletions(reader, contentLength, output, clientIp)
                }
                else -> {
                    sendJsonResponse(output, 404, """{"error":{"message":"Endpoint não encontrado"}}""")
                    addLog(method, path, clientIp, 404)
                }
            }
        } catch (e: Exception) {
            Log.w("LocalProxyServer", "Erro cliente: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleStatus(output: OutputStream) {
        val ip = getLocalIpAddress()
        val port = _port.value
        val jsonBody = """
            {
              "status": "online",
              "service": "BlazerGateway Local AI Hub",
              "version": "1.0",
              "free_tier": true,
              "local_endpoint": "http://$ip:$port/v1",
              "supported_apis": ["OpenAI", "Anthropic-Compatible"],
              "endpoints": {
                "models": "http://$ip:$port/v1/models",
                "chat_completions": "http://$ip:$port/v1/chat/completions"
              }
            }
        """.trimIndent()
        sendJsonResponse(output, 200, jsonBody)
    }

    private suspend fun handleModels(output: OutputStream) {
        val allProviders = providerRepository.getAllProviders().filter { it.enabled }
        val modelsList = mutableListOf<String>()
        modelsList.add("auto")

        for (p in allProviders) {
            val cached = providerRepository.getCachedModels(p.id)
            for (m in cached) {
                modelsList.add(m.id)
            }
        }

        val modelsJson = buildJsonObject {
            put("object", "list")
            putJsonArray("data") {
                for (id in modelsList.distinct()) {
                    add(buildJsonObject {
                        put("id", id)
                        put("object", "model")
                        put("owned_by", "blazergateway")
                        put("permission", buildJsonArray {})
                    })
                }
            }
        }.toString()

        sendJsonResponse(output, 200, modelsJson)
    }

    private suspend fun handleChatCompletions(
        reader: BufferedReader,
        contentLength: Int,
        output: OutputStream,
        clientIp: String
    ) {
        val bodyChars = CharArray(contentLength)
        var readSoFar = 0
        while (readSoFar < contentLength) {
            val count = reader.read(bodyChars, readSoFar, contentLength - readSoFar)
            if (count == -1) break
            readSoFar += count
        }
        val requestBody = String(bodyChars, 0, readSoFar)

        var requestedModel = "auto"
        var stream = false
        var temperature = 0.7f
        var maxTokens = 2048
        val messages = mutableListOf<ChatMessage>()
        var systemPrompt = "Você é o BlazerApex, um assistente de IA brasileiro criado por Davizig10jojo."

        try {
            val root = json.parseToJsonElement(requestBody).jsonObject
            requestedModel = root["model"]?.jsonPrimitive?.content ?: "auto"
            stream = root["stream"]?.jsonPrimitive?.booleanOrNull == true
            temperature = root["temperature"]?.jsonPrimitive?.floatOrNull ?: 0.7f
            maxTokens = root["max_tokens"]?.jsonPrimitive?.intOrNull ?: 2048

            val rawMessages = root["messages"]?.jsonArray ?: emptyList()
            for (m in rawMessages) {
                val mObj = m.jsonObject
                val role = mObj["role"]?.jsonPrimitive?.content ?: "user"
                val content = mObj["content"]?.jsonPrimitive?.content ?: ""
                if (role == "system") {
                    systemPrompt = content
                } else {
                    messages.add(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            conversationId = "local_server",
                            role = role,
                            content = content
                        )
                    )
                }
            }
        } catch (e: Exception) {
            sendJsonResponse(output, 400, """{"error":{"message":"JSON inválido: ${e.message}"}}""")
            addLog("POST", "/v1/chat/completions", clientIp, 400)
            return
        }

        // Roteamento inteligente de candidato
        val allProviders = providerRepository.getAllProviders().filter { it.enabled }
        val apiKeys = securePreferences.getAllApiKeys()

        val candidatePairs = mutableListOf<Pair<com.davizig.blazergateway.domain.model.Provider, String>>()
        for (prov in allProviders) {
            if (prov.requiresKey && apiKeys[prov.id].isNullOrBlank()) continue
            val models = providerRepository.getCachedModels(prov.id)
            val modelToUse = if (requestedModel != "auto") requestedModel else (prov.selectedModelId ?: models.firstOrNull()?.id ?: "openai")
            candidatePairs.add(prov to modelToUse)
        }

        val threshold = securePreferences.getProactiveThreshold()
        val candidate = smartRouter.selectCandidate(candidatePairs, apiKeys, threshold)
            ?: candidatePairs.firstOrNull()?.let {
                SmartRouter.RouteCandidate(it.first, it.second, apiKeys[it.first.id], 1.0)
            }

        if (candidate == null) {
            sendJsonResponse(output, 503, """{"error":{"message":"Nenhum provedor disponível no momento"}}""")
            addLog("POST", "/v1/chat/completions", clientIp, 503)
            return
        }

        val cloudflareAccountId = securePreferences.getCloudflareAccountId()

        if (stream) {
            // Streaming SSE para o cliente local (Cursor, cURL, Continue, etc.)
            val headerString = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: keep-alive\r\n" +
                    "Access-Control-Allow-Origin: *\r\n\r\n"
            output.write(headerString.toByteArray(Charsets.UTF_8))
            output.flush()

            val completionId = "chatcmpl-" + UUID.randomUUID().toString().take(12)
            try {
                val flow = apiClient.streamChat(
                    provider = candidate.provider,
                    modelId = candidate.modelId,
                    apiKey = candidate.apiKey,
                    messages = messages,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    cloudflareAccountId = cloudflareAccountId
                )

                flow.collect { token ->
                    val chunkJson = buildJsonObject {
                        put("id", completionId)
                        put("object", "chat.completion.chunk")
                        put("created", System.currentTimeMillis() / 1000)
                        put("model", candidate.modelId)
                        putJsonArray("choices") {
                            add(buildJsonObject {
                                put("index", 0)
                                putJsonObject("delta") {
                                    put("content", token)
                                }
                            })
                        }
                    }.toString()

                    output.write("data: $chunkJson\n\n".toByteArray(Charsets.UTF_8))
                    output.flush()
                }

                output.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                output.flush()
                addLog("POST", "/v1/chat/completions [SSE]", clientIp, 200)
                smartRouter.recordSuccess(candidate.provider.id, candidate.modelId)
            } catch (e: Exception) {
                output.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                output.flush()
                addLog("POST", "/v1/chat/completions [ERRO]", clientIp, 500)
            }
        } else {
            // Resposta completa não-streaming
            val completionId = "chatcmpl-" + UUID.randomUUID().toString().take(12)
            val fullText = StringBuilder()
            try {
                val flow = apiClient.streamChat(
                    provider = candidate.provider,
                    modelId = candidate.modelId,
                    apiKey = candidate.apiKey,
                    messages = messages,
                    systemPrompt = systemPrompt,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    cloudflareAccountId = cloudflareAccountId
                )
                flow.collect { fullText.append(it) }

                val responseJson = buildJsonObject {
                    put("id", completionId)
                    put("object", "chat.completion")
                    put("created", System.currentTimeMillis() / 1000)
                    put("model", candidate.modelId)
                    putJsonArray("choices") {
                        add(buildJsonObject {
                            put("index", 0)
                            putJsonObject("message") {
                                put("role", "assistant")
                                put("content", fullText.toString())
                            }
                            put("finish_reason", "stop")
                        })
                    }
                }.toString()

                sendJsonResponse(output, 200, responseJson)
                addLog("POST", "/v1/chat/completions", clientIp, 200)
                smartRouter.recordSuccess(candidate.provider.id, candidate.modelId)
            } catch (e: Exception) {
                sendJsonResponse(output, 500, """{"error":{"message":"${e.message}"}}""")
                addLog("POST", "/v1/chat/completions", clientIp, 500)
            }
        }
    }

    private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"

        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendCorsHeaders(output: OutputStream, statusCode: Int) {
        val headers = "HTTP/1.1 $statusCode No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: *\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
