# BlazerGateway

**BlazerGateway** é um cliente de Inteligência Artificial multi-provedores GRATUITO para Android, desenvolvido em **Kotlin + Jetpack Compose + Material 3**. Todas as chamadas de rede saem diretamente do próprio dispositivo para os provedores, sem backend intermediário pago ou coleta de telemetria.

Criado por **Davizig10jojo**.

---

## 🚀 Funcionalidades Principais

1. **Roteador Inteligente (Smart Router)**:
   - Troca proativa de provedor ao atingir o limiar de saturação configurado (padrão 85%).
   - Failover automático reativo para 429 (Too Many Requests), 401/403 e 5xx.
   - Fórmula de pontuação baseada em taxa de sucesso histórico, cota restante e jitter aleatório.
   - Contagem regressiva em caso de saturação total e auto-retentativa.

2. **Descoberta Dinâmica de Modelos**:
   - Consulta em tempo real do endpoint `/models` dos provedores ativos.
   - Detecção automática de modelos gratuitos (badge `GRÁTIS`) e modelos com capacidade de visão multimodal (badge `VISÃO`).
   - Cache persistido no Room Database com re-fetch a cada 24 horas e atualização manual.
   - Busca instantânea e filtros por modelo gratuito / visão.

3. **Segurança Inegociável**:
   - Armazenamento de chaves exclusivamente em `EncryptedSharedPreferences` via **Android Keystore (MasterKey AES-256-GCM)**.
   - `android:allowBackup="false"` e regras de extração impedindo vazamento de segredos.
   - Exportação e importação de backups criptografados com senha (PBKDF2 HMAC-SHA256 + AES-256-GCM).
   - Bloqueio biométrico opcional para acesso à tela de chaves.
   - Mascaramento rigoroso de cabeçalhos de autenticação em mensagens e logs de erro.

4. **Provedores Suportados (OpenAI-Compatíveis)**:
   - **OpenRouter** (`https://openrouter.ai/api/v1`)
   - **Groq** (`https://api.groq.com/openai/v1`)
   - **Cerebras** (`https://api.cerebras.ai/v1`)
   - **Google Gemini** (`https://generativelanguage.googleapis.com/v1beta/openai`)
   - **NVIDIA NIM** (`https://integrate.api.nvidia.com/v1`)
   - **SiliconFlow** (`https://api.siliconflow.cn/v1`)
   - **HuggingFace** (`https://api-inference.huggingface.co/v1`)
   - **Cloudflare AI** (`https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/v1`)
   - **Agnes AI** (`https://apihub.agnes-ai.com/v1`)
   - **AnyAPI** (`https://api.anyapi.ai/v1`)
   - **Pollinations** (`https://gen.pollinations.ai/v1` - Acesso Anônimo Gratuito)
   - **Provedores Customizados**: adicione qualquer endpoint compatível com OpenAI (LM Studio, vLLM, Ollama, etc.).

5. **Chat Completo**:
   - Streaming SSE token-a-token com parser binário próprio.
   - Anexo de imagens (filtrando automaticamente modelos multimodais).
   - Múltiplas conversas salvas localmente no Room.
   - Parâmetros customizados por conversa (temperatura, max tokens, system prompt personalizado com padrão BlazerApex).
   - Ações de copiar, parar streaming, regenerar resposta e editar/reenviar mensagens.

---

## 🛠️ Como Compilar e Executar

### Pré-requisitos:
- Android SDK 36 (minSdk 26)
- JDK 21
- Gradle 9.3.1 (ou compatível com AGP 9.1.1)

### Compilação:
```bash
# Compilar o APK de depuração
gradle assembleDebug

# Rodar a suíte completa de testes unitários
gradle :app:testDebugUnitTest
```

O arquivo gerado estará em `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 Exemplo de Arquivo `.env` para Importação

Você pode importar suas chaves copiando e colando um arquivo `.env` na tela de Chaves e Modelos:

```env
# Configurações de Chaves de API para o BlazerGateway
OPENROUTER_KEY=sk-or-v1-seu-token-aqui
GROQ_KEY=gsk_seu-token-aqui
CEREBRAS_KEY=csk-seu-token-aqui
GOOGLE_KEY=AIzaSySuaChaveDoGemini
NVIDIA_KEY=nvapi-sua-chave-nvidia
SILICONFLOW_KEY=sk-sua-chave-siliconflow
HF_KEY=hf_seu-token-huggingface
CLOUDFLARE_KEY=seu-token-api-cloudflare
CLOUDFLARE_ACCOUNT_ID=seu-account-id-cloudflare
AGNES_KEY=sua-chave-agnes
ANYAPI_KEY=sua-chave-anyapi
```
