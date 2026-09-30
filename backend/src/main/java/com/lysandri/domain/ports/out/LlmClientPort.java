package com.lysandri.domain.ports.out;

public interface LlmClientPort {

    record LlmCompletion(
            String textoRespuesta,
            int tokensPrompt,
            int tokensCompletion,
            int totalTokens
    ) {}

    /**
     * Genera un embedding vectorial denso (1536 dimensiones) para un texto dado.
     */
    float[] generarEmbedding(String texto);

    /**
     * Completa un prompt utilizando un Modelo de Lenguaje de gran escala (LLM).
     */
    LlmCompletion completarChat(String systemPrompt, String userPrompt, double temperatura);
}
