package com.lysandri.domain.ports.in;

import java.util.List;

public interface ChatRagUseCase {

    record ChatRagCommand(
            String pregunta,
            String sesionId,
            Long idUsuario,
            Long idPrograma
    ) {}

    record SourceCitation(
            Long idDocumento,
            Integer pagina,
            String extracto,
            Double similitud,
            String titulo,
            String seccion,
            String version,
            String estado,
            String url
    ) {
        public SourceCitation(Long idDocumento, Integer pagina, String extracto, Double similitud) {
            this(idDocumento, pagina, extracto, similitud, null, null, null, null, null);
        }
    }

    record ChatRagResponse(
            String respuesta,
            List<SourceCitation> fuentes,
            int tokensUsados,
            long latenciaMs
    ) {}

    ChatRagResponse procesarConsulta(ChatRagCommand command);
}
