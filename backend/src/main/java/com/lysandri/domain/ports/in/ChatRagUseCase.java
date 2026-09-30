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
            Double similitud
    ) {}

    record ChatRagResponse(
            String respuesta,
            List<SourceCitation> fuentes,
            int tokensUsados,
            long latenciaMs
    ) {}

    ChatRagResponse procesarConsulta(ChatRagCommand command);
}
