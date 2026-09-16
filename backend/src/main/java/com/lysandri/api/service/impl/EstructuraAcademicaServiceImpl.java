package com.lysandri.api.service.impl;

import com.lysandri.api.dto.response.ActividadBloqueResponse;
import com.lysandri.api.dto.response.BloqueResponse;
import com.lysandri.api.dto.response.EstructuraProgramaResponse;
import com.lysandri.api.dto.response.LeccionResponse;
import com.lysandri.api.dto.response.ModuloResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.ActividadBloque;
import com.lysandri.api.model.entity.Bloque;
import com.lysandri.api.model.entity.Leccion;
import com.lysandri.api.model.entity.Modulo;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.repository.ActividadBloqueRepository;
import com.lysandri.api.repository.BloqueRepository;
import com.lysandri.api.repository.LeccionRepository;
import com.lysandri.api.repository.ModuloRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.service.AccesoAcademicoService;
import com.lysandri.api.service.EstructuraAcademicaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EstructuraAcademicaServiceImpl
        implements EstructuraAcademicaService {

    private final ProgramaRepository programaRepository;
    private final BloqueRepository bloqueRepository;
    private final ModuloRepository moduloRepository;
    private final LeccionRepository leccionRepository;
    private final ActividadBloqueRepository actividadRepository;
    private final AccesoAcademicoService accesoAcademicoService;

    @Override
    @Transactional(readOnly = true)
    public EstructuraProgramaResponse obtenerEstructuraPrograma(
            Integer programaId
    ) {
        Programa programa = buscarPrograma(programaId);

        accesoAcademicoService.validarLecturaPrograma(
                programaId
        );

        List<Bloque> bloques =
                bloqueRepository
                        .findByProgramaIdProgramaOrderByOrdenAsc(
                                programaId
                        );

        List<BloqueResponse> bloquesResponse =
                new ArrayList<>();

        for (Bloque bloque : bloques) {
            BloqueResponse bloqueResponse =
                    convertirBloque(bloque);

            List<ModuloResponse> modulosResponse =
                    obtenerModulos(
                            bloque.getIdBloque()
                    );

            List<ActividadBloqueResponse> actividadesResponse =
                    obtenerActividades(
                            bloque.getIdBloque()
                    );

            bloqueResponse.setModulos(
                    modulosResponse
            );
            bloqueResponse.setActividades(
                    actividadesResponse
            );

            bloquesResponse.add(
                    bloqueResponse
            );
        }

        EstructuraProgramaResponse response =
                new EstructuraProgramaResponse();

        response.setProgramaId(
                programa.getIdPrograma()
        );
        response.setTituloPrograma(
                programa.getTituloPrograma()
        );
        response.setBloques(
                bloquesResponse
        );

        return response;
    }

    private Programa buscarPrograma(
            Integer programaId
    ) {
        if (programaId == null) {
            throw new BadRequestException(
                    "El identificador del programa es obligatorio"
            );
        }

        return programaRepository
                .findById(programaId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Programa no encontrado con id: "
                                        + programaId
                        )
                );
    }

    private List<ModuloResponse> obtenerModulos(
            Integer bloqueId
    ) {
        List<Modulo> modulos =
                moduloRepository
                        .findByBloqueIdBloqueOrderByOrdenAsc(
                                bloqueId
                        );

        List<ModuloResponse> respuestas =
                new ArrayList<>();

        for (Modulo modulo : modulos) {
            ModuloResponse response =
                    convertirModulo(modulo);

            List<Leccion> lecciones =
                    leccionRepository
                            .findByModuloIdModuloOrderByOrdenAsc(
                                    modulo.getIdModulo()
                            );

            List<LeccionResponse> leccionesResponse =
                    convertirLecciones(lecciones);

            response.setLecciones(
                    leccionesResponse
            );

            respuestas.add(
                    response
            );
        }

        return respuestas;
    }

    private List<ActividadBloqueResponse> obtenerActividades(
            Integer bloqueId
    ) {
        List<ActividadBloque> actividades =
                actividadRepository
                        .findByBloqueIdBloqueOrderByOrdenAsc(
                                bloqueId
                        );

        List<ActividadBloqueResponse> respuestas =
                new ArrayList<>();

        for (ActividadBloque actividad : actividades) {
            respuestas.add(
                    convertirActividad(
                            actividad
                    )
            );
        }

        return respuestas;
    }

    private BloqueResponse convertirBloque(
            Bloque bloque
    ) {
        BloqueResponse response =
                new BloqueResponse();

        response.setIdBloque(
                bloque.getIdBloque()
        );
        response.setProgramaId(
                bloque.getPrograma().getIdPrograma()
        );
        response.setTitulo(
                bloque.getTitulo()
        );
        response.setDescripcion(
                bloque.getDescripcion()
        );
        response.setOrden(
                bloque.getOrden()
        );
        response.setActivo(
                bloque.getActivo()
        );

        return response;
    }

    private ModuloResponse convertirModulo(
            Modulo modulo
    ) {
        ModuloResponse response =
                new ModuloResponse();

        response.setIdModulo(
                modulo.getIdModulo()
        );
        response.setBloqueId(
                modulo.getBloque().getIdBloque()
        );
        response.setTitulo(
                modulo.getTitulo()
        );
        response.setDescripcion(
                modulo.getDescripcion()
        );
        response.setOrden(
                modulo.getOrden()
        );
        response.setActivo(
                modulo.getActivo()
        );

        return response;
    }

    private ActividadBloqueResponse convertirActividad(
            ActividadBloque actividad
    ) {
        ActividadBloqueResponse response =
                new ActividadBloqueResponse();

        response.setIdActividad(
                actividad.getIdActividad()
        );
        response.setBloqueId(
                actividad.getBloque().getIdBloque()
        );
        response.setTitulo(
                actividad.getTitulo()
        );
        response.setDescripcion(
                actividad.getDescripcion()
        );
        response.setInstrucciones(
                actividad.getInstrucciones()
        );
        response.setTipoActividad(
                actividad.getTipoActividad()
        );
        response.setContenidoUrl(
                actividad.getContenidoUrl()
        );
        response.setOrden(
                actividad.getOrden()
        );
        response.setActivo(
                actividad.getActivo()
        );

        return response;
    }

    private List<LeccionResponse> convertirLecciones(
            List<Leccion> lecciones
    ) {
        List<LeccionResponse> respuestas =
                new ArrayList<>();

        for (Leccion leccion : lecciones) {
            respuestas.add(
                    convertirLeccion(
                            leccion
                    )
            );
        }

        return respuestas;
    }

    private LeccionResponse convertirLeccion(
            Leccion leccion
    ) {
        LeccionResponse response =
                new LeccionResponse();

        response.setId(
                Long.valueOf(
                        leccion.getIdLeccion()
                )
        );

        response.setProgramaId(
                Long.valueOf(
                        leccion.getPrograma()
                                .getIdPrograma()
                )
        );

        if (leccion.getModulo() != null) {
            response.setModuloId(
                    Long.valueOf(
                            leccion.getModulo()
                                    .getIdModulo()
                    )
            );
        }

        response.setTitulo(
                leccion.getTituloLeccion()
        );
        response.setDescripcion(
                leccion.getDescripcion()
        );
        response.setTipoContenido(
                leccion.getTipoContenido()
        );
        response.setContenidoUrl(
                leccion.getMediaUrl()
        );
        response.setDuracionMinutos(
                convertirDuracion(
                        leccion.getDuracionLeccion()
                )
        );
        response.setOrden(
                leccion.getOrden()
        );

        return response;
    }

    private Integer convertirDuracion(
            String duracion
    ) {
        if (
                duracion == null
                        || duracion.isBlank()
        ) {
            return null;
        }

        try {
            String soloDigitos =
                    duracion.replaceAll(
                            "[^0-9]",
                            ""
                    );

            if (soloDigitos.isEmpty()) {
                return null;
            }

            return Integer.parseInt(
                    soloDigitos
            );
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}