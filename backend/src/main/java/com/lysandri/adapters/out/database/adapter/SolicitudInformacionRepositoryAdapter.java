package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.SolicitudInformacionEntity;
import com.lysandri.adapters.out.database.repository.SpringDataSolicitudRepository;
import com.lysandri.domain.model.SolicitudInformacion;
import com.lysandri.domain.ports.out.SolicitudInformacionRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SolicitudInformacionRepositoryAdapter implements SolicitudInformacionRepositoryPort {

    private final SpringDataSolicitudRepository repository;

    @Override
    public SolicitudInformacion guardar(SolicitudInformacion s) {
        SolicitudInformacionEntity entity = SolicitudInformacionEntity.builder()
                .idSolicitud(s.getIdSolicitud())
                .idPrograma(s.getIdPrograma())
                .nombreCompleto(s.getNombreCompleto())
                .email(s.getEmail())
                .telefono(s.getTelefono())
                .empresa(s.getEmpresa())
                .cargo(s.getCargo())
                .mensaje(s.getMensaje())
                .estado(s.getEstado() != null ? s.getEstado() : "PENDIENTE")
                .fechaCreacion(s.getFechaCreacion())
                .build();

        SolicitudInformacionEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<SolicitudInformacion> listarTodas() {
        return repository.findAllByOrderByFechaCreacionDesc().stream().map(this::toDomain).toList();
    }

    private SolicitudInformacion toDomain(SolicitudInformacionEntity e) {
        return SolicitudInformacion.builder()
                .idSolicitud(e.getIdSolicitud())
                .idPrograma(e.getIdPrograma())
                .nombreCompleto(e.getNombreCompleto())
                .email(e.getEmail())
                .telefono(e.getTelefono())
                .empresa(e.getEmpresa())
                .cargo(e.getCargo())
                .mensaje(e.getMensaje())
                .estado(e.getEstado())
                .fechaCreacion(e.getFechaCreacion())
                .build();
    }
}
