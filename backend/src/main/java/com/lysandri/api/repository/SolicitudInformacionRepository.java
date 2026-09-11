package com.lysandri.api.repository;

import com.lysandri.api.model.entity.SolicitudInformacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SolicitudInformacionRepository extends JpaRepository<SolicitudInformacion, Integer> {

    List<SolicitudInformacion> findByEstadoOrderByFechaCreacionDesc(String estado);

    List<SolicitudInformacion> findAllByOrderByFechaCreacionDesc();

    List<SolicitudInformacion> findByEmailOrderByFechaCreacionDesc(String email);

    List<SolicitudInformacion> findByProgramaIdProgramaOrderByFechaCreacionDesc(Integer idPrograma);
}
