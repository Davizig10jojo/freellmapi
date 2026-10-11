package com.davizig.blazergateway.data.repository

import com.davizig.blazergateway.data.local.SecurePreferences
import com.davizig.blazergateway.data.local.dao.ConversationDao
import com.davizig.blazergateway.data.local.dao.MessageDao
import com.davizig.blazergateway.data.local.entity.ConversationEntity
import com.davizig.blazergateway.data.local.entity.MessageEntity
import com.davizig.blazergateway.data.remote.ApiException
import com.davizig.blazergateway.data.remote.OpenAIApiClient
import com.davizig.blazergateway.domain.model.ChatMessage
import com.davizig.blazergateway.domain.model.Conversation
import com.davizig.blazergateway.domain.model.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val providerRepository: ProviderRepository,
    private val smartRouter: SmartRouter,
    private val securePreferences: SecurePreferences,
    private val apiClient: OpenAIApiClient
) {

    private var activeStreamingJob: Job? = null

    fun getConversationsFlow(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getConversation(id: String): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    suspend fun createConversation(
        title: String = "Nova Conversa",
        systemPrompt: String = "Você é o BlazerApex, um assistente de IA brasileiro criado por Davizig10jojo.",
        temperature: Float = 0.7f,
        maxTokens: Int = 2048,
        providerId: String = "pollinations",
        modelId: String = "openai"
    ): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            providerId = providerId,
            modelId = modelId,
            createdAt = now,
            updatedAt = now
        )
        conversationDao.insertOrUpdate(conversation.toEntity())
        return conversation
    }

    suspend fun updateConversation(conversation: Conversation) {
        conversationDao.insertOrUpdate(conversation.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun deleteConversation(id: String) {
        messageDao.deleteForConversation(id)
        conversationDao.deleteById(id)
    }

    fun getMessagesFlow(conversationId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun stopStreaming() {
        activeStreamingJob?.cancel()
        activeStreamingJob = null
    }

    /**
     * Envia mensagem do usuário, gerencia roteamento inteligente e consome o stream SSE.
     */
    suspend fun sendMessage(
        conversationId: String,
        userPrompt: String,
        imageBase64: String?,
        onCooldownWait: (remainingSeconds: Long) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val conv = getConversation(conversationId) ?: return@withContext

        // 1. Salva mensagem do usuário
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "user",
            content = userPrompt,
            imageBase64 = imageBase64,
            isStreaming = false
        )
        messageDao.insertOrUpdate(userMessage.toEntity())

        // Atualiza título da conversa se for a primeira mensagem
        if (conv.title == "Nova Conversa") {
            val autoTitle = userPrompt.take(30).trim()
            if (autoTitle.isNotEmpty()) {
                updateConversation(conv.copy(title = autoTitle))
            }
        }

        // Executa streaming da resposta
        executeAssistantResponse(
            conversation = conv,
            hasImage = imageBase64 != null,
            onCooldownWait = onCooldownWait,
            onError = onError
        )
    }

    suspend fun regenerateLastMessage(
        conversationId: String,
        onCooldownWait: (Long) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val conv = getConversation(conversationId) ?: return@withContext
        val messages = messageDao.getMessagesSnapshot(conversationId)
        val lastAssistant = messages.lastOrNull { it.role == "assistant" }
        if (lastAssistant != null) {
            messageDao.deleteById(lastAssistant.id)
        }
        val lastUserMessage = messages.lastOrNull { it.role == "user" }
        val hasImage = lastUserMessage?.imageBase64 != null

        executeAssistantResponse(
            conversation = conv,
            hasImage = hasImage,
            onCooldownWait = onCooldownWait,
            onError = onError
        )
    }

    suspend fun editMessageAndResend(
        messageId: String,
        newContent: String,
        onCooldownWait: (Long) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val messages = messageDao.getMessagesSnapshot("") // snapshot across
        // Encontra a mensagem
        val target = messages.firstOrNull { it.id == messageId } ?: return@withContext
        val conv = getConversation(target.conversationId) ?: return@withContext

        // Apaga mensagens posteriores nesta conversa
        val convMessages = messageDao.getMessagesSnapshot(conv.id)
        val targetIndex = convMessages.indexOfFirst { it.id == messageId }
        if (targetIndex >= 0) {
            for (i in (targetIndex + 1) until convMessages.size) {
                messageDao.deleteById(convMessages[i].id)
            }
        }

        // Atualiza a mensagem do usuário
        messageDao.insertOrUpdate(target.copy(content = newContent))

        executeAssistantResponse(
            conversation = conv,
            hasImage = target.imageBase64 != null,
            onCooldownWait = onCooldownWait,
            onError = onError
        )
    }

    private suspend fun executeAssistantResponse(
        conversation: Conversation,
        hasImage: Boolean,
        onCooldownWait: (Long) -> Unit,
        onError: (String) -> Unit
    ) {
        val assistantMessageId = UUID.randomUUID().toString()
        val placeholder = MessageEntity(
            id = assistantMessageId,
            conversationId = conversation.id,
            role = "assistant",
            content = "",
            imageBase64 = null,
            providerId = null,
            modelId = null,
            isStreaming = true,
            createdAt = System.currentTimeMillis()
        )
        messageDao.insertOrUpdate(placeholder)

        // Prepara lista de candidatos elegíveis
        val allProviders = providerRepository.getAllProviders().filter { it.enabled }
        val apiKeys = securePreferences.getAllApiKeys()

        // Monta pares (Provedor, Modelo)
        val candidatePairs = mutableListOf<Pair<Provider, String>>()

        // Provedor/modelo preferido da conversa
        val preferredProvider = allProviders.firstOrNull { it.id == conversation.providerId }
        if (preferredProvider != null) {
            candidatePairs.add(preferredProvider to conversation.modelId)
        }

        // Adiciona demais provedores habilitados com suas chaves/modelos
        for (prov in allProviders) {
            if (prov.requiresKey && apiKeys[prov.id].isNullOrBlank()) {
                continue
            }
            val models = providerRepository.getCachedModels(prov.id).ifEmpty {
                providerRepository.refreshModelsForProvider(prov.id)
            }

            val validModels = if (hasImage) {
                models.filter { it.hasVision }
            } else {
                models
            }

            val chosenModel = prov.selectedModelId
                ?: validModels.firstOrNull()?.id
                ?: if (prov.id == "pollinations") "openai" else "default"

            val pair = prov to chosenModel
            if (!candidatePairs.contains(pair)) {
                candidatePairs.add(pair)
            }
        }

        if (candidatePairs.isEmpty()) {
            messageDao.deleteById(assistantMessageId)
            onError("Nenhum provedor com chave configurada está disponível.")
            return
        }

        val threshold = securePreferences.getProactiveThreshold()
        val cloudflareAccountId = securePreferences.getCloudflareAccountId()

        var currentCandidates = candidatePairs.toList()
        var streamSucceeded = false
        val fullAccumulator = StringBuilder()

        while (!streamSucceeded && currentCandidates.isNotEmpty()) {
            val candidate = smartRouter.selectCandidate(currentCandidates, apiKeys, threshold)
            if (candidate == null) {
                // Todos em cooldown ou saturados
                val waitSeconds = smartRouter.getMinCooldownRemainingSeconds(currentCandidates)
                onCooldownWait(waitSeconds)
                messageDao.deleteById(assistantMessageId)
                onError("Todos os provedores estão em período de espera. Aguarde $waitSeconds segundos.")
                return
            }

            val currentProvider = candidate.provider
            val currentModel = candidate.modelId
            val currentKey = candidate.apiKey

            // Atualiza o placeholder com o provedor eleito
            messageDao.insertOrUpdate(
                placeholder.copy(
                    providerId = currentProvider.id,
                    modelId = currentModel
                )
            )

            val currentMessages = messageDao.getMessagesSnapshot(conversation.id)
                .filter { it.id != assistantMessageId }
                .map { it.toDomain() }

            try {
                fullAccumulator.clear()
                val flow = apiClient.streamChat(
                    provider = currentProvider,
                    modelId = currentModel,
                    apiKey = currentKey,
                    messages = currentMessages,
                    systemPrompt = conversation.systemPrompt,
                    temperature = conversation.temperature,
                    maxTokens = conversation.maxTokens,
                    cloudflareAccountId = cloudflareAccountId
                )

                flow.collect { deltaToken ->
                    fullAccumulator.append(deltaToken)
                    messageDao.insertOrUpdate(
                        placeholder.copy(
                            content = fullAccumulator.toString(),
                            providerId = currentProvider.id,
                            modelId = currentModel,
                            isStreaming = true
                        )
                    )
                }

                // Finalizou com sucesso!
                smartRouter.recordSuccess(currentProvider.id, currentModel)
                messageDao.insertOrUpdate(
                    placeholder.copy(
                        content = fullAccumulator.toString(),
                        providerId = currentProvider.id,
                        modelId = currentModel,
                        isStreaming = false
                    )
                )
                streamSucceeded = true
            } catch (e: CancellationException) {
                // Cancelado pelo usuário
                messageDao.insertOrUpdate(
                    placeholder.copy(
                        content = fullAccumulator.toString(),
                        providerId = currentProvider.id,
                        modelId = currentModel,
                        isStreaming = false
                    )
                )
                return
            } catch (e: Exception) {
                val statusCode = if (e is ApiException) e.statusCode else 500
                val retryAfter = (e as? ApiException)?.retryAfterSeconds

                smartRouter.recordFailure(currentProvider.id, currentModel, statusCode, retryAfter)

                // Remove candidato falho e tenta o próximo pelo roteador
                currentCandidates = currentCandidates.filterNot { it.first.id == currentProvider.id }

                if (currentCandidates.isEmpty()) {
                    messageDao.deleteById(assistantMessageId)
                    onError("Falha em todos os provedores: ${e.message}")
                    return
                }
            }
        }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        providerId = providerId,
        modelId = modelId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        providerId = providerId,
        modelId = modelId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MessageEntity.toDomain() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        imageBase64 = imageBase64,
        providerId = providerId,
        modelId = modelId,
        isStreaming = isStreaming,
        createdAt = createdAt
    )

    private fun ChatMessage.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        content = content,
        imageBase64 = imageBase64,
        providerId = providerId,
        modelId = modelId,
        isStreaming = isStreaming,
        createdAt = createdAt
    )
}
