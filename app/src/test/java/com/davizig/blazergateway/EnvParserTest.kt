package com.davizig.blazergateway

import com.davizig.blazergateway.util.EnvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvParserTest {

    @Test
    fun `test parse env with comments quotes and empty lines`() {
        val envContent = """
            # Arquivo de configuração de teste
            OPENROUTER_KEY="sk-or-v1-abcdef123456"
            GROQ_KEY='gsk_987654321'
            CEREBRAS_KEY=csk-test123
            
            # Provedores adicionais
            GOOGLE_KEY="AIzaSyTestGemini"
            NVIDIA_KEY=nvapi-xyz789
            SILICONFLOW_KEY=sk-silicon-001
            HF_KEY=hf_token_secret
            CLOUDFLARE_KEY=cf_token_123
            CLOUDFLARE_ACCOUNT_ID="acc_id_9999"
            AGNES_KEY=agnes_secret_token
            ANYAPI_KEY=anyapi_token_val
            
            # Variável não mapeada
            UNKNOWN_CONFIG=some_other_value
        """.trimIndent()

        val parsed = EnvParser.parse(envContent)

        assertEquals("sk-or-v1-abcdef123456", parsed.providerKeys["openrouter"])
        assertEquals("gsk_987654321", parsed.providerKeys["groq"])
        assertEquals("csk-test123", parsed.providerKeys["cerebras"])
        assertEquals("AIzaSyTestGemini", parsed.providerKeys["google_gemini"])
        assertEquals("nvapi-xyz789", parsed.providerKeys["nvidia"])
        assertEquals("sk-silicon-001", parsed.providerKeys["siliconflow"])
        assertEquals("hf_token_secret", parsed.providerKeys["huggingface"])
        assertEquals("cf_token_123", parsed.providerKeys["cloudflare"])
        assertEquals("acc_id_9999", parsed.cloudflareAccountId)
        assertEquals("agnes_secret_token", parsed.providerKeys["agnes_ai"])
        assertEquals("anyapi_token_val", parsed.providerKeys["anyapi"])

        assertEquals("some_other_value", parsed.unmappedVariables["UNKNOWN_CONFIG"])
    }

    @Test
    fun `test empty env returns empty map`() {
        val parsed = EnvParser.parse("")
        assertEquals(0, parsed.providerKeys.size)
        assertNull(parsed.cloudflareAccountId)
    }
}
