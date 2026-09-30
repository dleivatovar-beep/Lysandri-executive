package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.HistorialConsultaEntity;
import com.lysandri.adapters.out.database.repository.SpringDataHistorialRepository;
import com.lysandri.domain.model.HistorialConsulta;
import com.lysandri.domain.ports.out.HistorialConsultaRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HistorialConsultaRepositoryAdapter implements HistorialConsultaRepositoryPort {

    private final SpringDataHistorialRepository repository;

    @Override
    public HistorialConsulta guardar(HistorialConsulta h) {
        HistorialConsultaEntity entity = HistorialConsultaEntity.builder()
                .idHistorial(h.getIdHistorial())
                .idUser(h.getIdUser())
                .idPrograma(h.getIdPrograma())
                .sesionId(h.getSesionId())
                .pregunta(h.getPregunta())
                .respuestaIa(h.getRespuestaIa())
                .tokensPrompt(h.getTokensPrompt())
                .tokensCompletion(h.getTokensCompletion())
                .tiempoRespuestaMs(h.getTiempoRespuestaMs())
                .calificacionUsuario(h.getCalificacionUsuario())
                .fechaConsulta(h.getFechaConsulta())
                .build();

        HistorialConsultaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    private HistorialConsulta toDomain(HistorialConsultaEntity e) {
        return HistorialConsulta.builder()
                .idHistorial(e.getIdHistorial())
                .idUser(e.getIdUser())
                .idPrograma(e.getIdPrograma())
                .sesionId(e.getSesionId())
                .pregunta(e.getPregunta())
                .respuestaIa(e.getRespuestaIa())
                .tokensPrompt(e.getTokensPrompt())
                .tokensCompletion(e.getTokensCompletion())
                .tiempoRespuestaMs(e.getTiempoRespuestaMs())
                .calificacionUsuario(e.getCalificacionUsuario())
                .fechaConsulta(e.getFechaConsulta())
                .build();
    }
}
