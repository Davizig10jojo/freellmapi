package com.davizig.blazergateway.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davizig.blazergateway.data.repository.ChatRepository
import com.davizig.blazergateway.data.repository.ProviderRepository
import com.davizig.blazergateway.domain.model.ChatMessage
import com.davizig.blazergateway.domain.model.Conversation
import com.davizig.blazergateway.domain.model.ModelInfo
import com.davizig.blazergateway.domain.model.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = chatRepository.getConversationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId = _currentConversationId.asStateFlow()

    val currentConversation: StateFlow<Conversation?> = _currentConversationId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            conversations.map { list -> list.firstOrNull { it.id == id } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<ChatMessage>> = _currentConversationId.flatMapLatest { id ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            chatRepository.getMessagesFlow(id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _inputPrompt = MutableStateFlow("")
    val inputPrompt = _inputPrompt.asStateFlow()

    private val _attachedImageBase64 = MutableStateFlow<String?>(null)
    val attachedImageBase64 = _attachedImageBase64.asStateFlow()

    private val _cooldownNotice = MutableStateFlow<String?>(null)
    val cooldownNotice = _cooldownNotice.asStateFlow()

    private val _errorNotice = MutableStateFlow<String?>(null)
    val errorNotice = _errorNotice.asStateFlow()

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels = _availableModels.asStateFlow()

    init {
        viewModelScope.launch {
            // Auto-seleciona a primeira conversa se existir, ou cria uma inicial
            conversations.collect { list ->
                if (_currentConversationId.value == null && list.isNotEmpty()) {
                    _currentConversationId.value = list.first().id
                    loadModelsForCurrentProvider(list.first().providerId)
                }
            }
        }
    }

    fun selectConversation(id: String) {
        _currentConversationId.value = id
        _errorNotice.value = null
        _cooldownNotice.value = null
        viewModelScope.launch {
            val conv = chatRepository.getConversation(id)
            if (conv != null) {
                loadModelsForCurrentProvider(conv.providerId)
            }
        }
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val newConv = chatRepository.createConversation()
            _currentConversationId.value = newConv.id
            loadModelsForCurrentProvider(newConv.providerId)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                val remaining = conversations.value.filterNot { it.id == id }
                _currentConversationId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun setInputPrompt(text: String) {
        _inputPrompt.value = text
    }

    fun setAttachedImage(base64: String?) {
        _attachedImageBase64.value = base64
    }

    fun clearNotices() {
        _errorNotice.value = null
        _cooldownNotice.value = null
    }

    fun loadModelsForCurrentProvider(providerId: String) {
        viewModelScope.launch {
            val models = providerRepository.getCachedModels(providerId).ifEmpty {
                providerRepository.refreshModelsForProvider(providerId)
            }
            _availableModels.value = models
        }
    }

    fun updateConversationSettings(
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        providerId: String,
        modelId: String
    ) {
        val conv = currentConversation.value ?: return
        viewModelScope.launch {
            val updated = conv.copy(
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = maxTokens,
                providerId = providerId,
                modelId = modelId
            )
            chatRepository.updateConversation(updated)
            loadModelsForCurrentProvider(providerId)
        }
    }

    fun sendMessage() {
        val prompt = _inputPrompt.value.trim()
        val image = _attachedImageBase64.value
        if (prompt.isEmpty() && image == null) return

        val convId = _currentConversationId.value
        if (convId == null) {
            viewModelScope.launch {
                val newConv = chatRepository.createConversation()
                _currentConversationId.value = newConv.id
                sendInternal(newConv.id, prompt, image)
            }
        } else {
            sendInternal(convId, prompt, image)
        }
    }

    private fun sendInternal(conversationId: String, prompt: String, image: String?) {
        _inputPrompt.value = ""
        _attachedImageBase64.value = null
        _isStreaming.value = true
        _errorNotice.value = null
        _cooldownNotice.value = null

        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    conversationId = conversationId,
                    userPrompt = prompt,
                    imageBase64 = image,
                    onCooldownWait = { waitSeconds ->
                        _cooldownNotice.value = "Limite de requisições por minuto atingido (ex: NVIDIA NIM tem 40 RPM). Aguardando ${waitSeconds}s..."
                    },
                    onError = { err ->
                        _errorNotice.value = err
                    }
                )
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun resetCooldownsAndRetry() {
        viewModelScope.launch {
            chatRepository.clearCooldowns()
            _cooldownNotice.value = null
            _errorNotice.value = null
            regenerate()
        }
    }

    fun stopStreaming() {
        chatRepository.stopStreaming()
        _isStreaming.value = false
    }

    fun regenerate() {
        val convId = _currentConversationId.value ?: return
        _isStreaming.value = true
        _errorNotice.value = null
        _cooldownNotice.value = null

        viewModelScope.launch {
            try {
                chatRepository.regenerateLastMessage(
                    conversationId = convId,
                    onCooldownWait = { waitSeconds ->
                        _cooldownNotice.value = "Em espera: ${waitSeconds}s"
                    },
                    onError = { err ->
                        _errorNotice.value = err
                    }
                )
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun editMessageAndResend(messageId: String, newContent: String) {
        _isStreaming.value = true
        _errorNotice.value = null
        _cooldownNotice.value = null

        viewModelScope.launch {
            try {
                chatRepository.editMessageAndResend(
                    messageId = messageId,
                    newContent = newContent,
                    onCooldownWait = { waitSeconds ->
                        _cooldownNotice.value = "Em espera: ${waitSeconds}s"
                    },
                    onError = { err ->
                        _errorNotice.value = err
                    }
                )
            } finally {
                _isStreaming.value = false
            }
        }
    }

    class Factory(
        private val chatRepository: ChatRepository,
        private val providerRepository: ProviderRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(chatRepository, providerRepository) as T
        }
    }
}
