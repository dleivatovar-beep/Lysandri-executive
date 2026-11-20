package com.lysandri.domain.ports.out;

public interface LlmClientPort {

    record LlmCompletion(
            String textoRespuesta,
            int tokensPrompt,
            int tokensCompletion,
            int totalTokens
    ) {}

    /**
     * Genera un embedding vectorial denso (las dimensiones del modelo configurado) para un texto dado.
     */
    float[] generarEmbedding(String texto);

    /**
     * Completa un prompt utilizando un Modelo de Lenguaje de gran escala (LLM).
     */
    LlmCompletion completarChat(String systemPrompt, String userPrompt, double temperatura);

    default LlmCompletion completarChat(String systemPrompt, String userPrompt, double temperatura, int maxTokens) {
        return completarChat(systemPrompt, userPrompt, temperatura);
    }
    default LlmCompletion completarChat(String systemPrompt, String userPrompt, double temperatura, int maxTokens,
                                       java.util.Map<String, Object> formato) {
        return completarChat(systemPrompt, userPrompt, temperatura, maxTokens);
    }
}