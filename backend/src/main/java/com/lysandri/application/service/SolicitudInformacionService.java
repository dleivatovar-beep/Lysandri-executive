package com.lysandri.application.service;

import com.lysandri.domain.model.SolicitudInformacion;
import com.lysandri.domain.ports.in.SolicitudInformacionUseCase;
import com.lysandri.domain.ports.out.SolicitudInformacionRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolicitudInformacionService implements SolicitudInformacionUseCase {

    private final SolicitudInformacionRepositoryPort repository;

    @Override
    @Transactional
    public SolicitudInformacion registrarSolicitud(CreateSolicitudCommand command) {
        log.info("Registrando solicitud de información corporativa para: {} ({})", command.nombreCompleto(), command.email());

        SolicitudInformacion solicitud = SolicitudInformacion.builder()
                .idPrograma(command.idPrograma())
                .nombreCompleto(command.nombreCompleto())
                .email(command.email())
                .telefono(command.telefono())
                .empresa(command.empresa())
                .cargo(command.cargo())
                .mensaje(command.mensaje())
                .estado("PENDIENTE")
                .fechaCreacion(OffsetDateTime.now())
                .build();

        return repository.guardar(solicitud);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudInformacion> listarSolicitudes() {
        return repository.listarTodas();
    }
}
