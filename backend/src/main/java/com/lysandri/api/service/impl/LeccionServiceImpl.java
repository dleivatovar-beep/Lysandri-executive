package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.LeccionRequest;
import com.lysandri.api.dto.response.LeccionResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Leccion;
import com.lysandri.api.model.entity.Modulo;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.enums.TipoContenido;
import com.lysandri.api.repository.LeccionRepository;
import com.lysandri.api.repository.ModuloRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.service.AccesoAcademicoService;
import com.lysandri.api.service.LeccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeccionServiceImpl implements LeccionService {

    private final LeccionRepository leccionRepository;
    private final ProgramaRepository programaRepository;
    private final ModuloRepository moduloRepository;
    private final AccesoAcademicoService accesoAcademicoService;

    /*
     * Método anterior: crea una lección directamente
     * dentro de un programa.
     */
    @Override
    @Transactional
    public LeccionResponse crearLeccion(
            Long programaId,
            LeccionRequest request
    ) {
        Programa programa = buscarPrograma(programaId);

        accesoAcademicoService.validarGestionPrograma(
                programa.getIdPrograma()
        );

        validarRequest(request);

        Leccion leccion = new Leccion();

        leccion.setPrograma(programa);
        leccion.setModulo(null);
        leccion.setTituloLeccion(
                request.getTitulo().trim()
        );
        leccion.setDescripcion(
                request.getDescripcion()
        );
        leccion.setTipoContenido(
                obtenerTipoContenido(request)
        );
        leccion.setMediaUrl(
                request.getContenidoUrl()
        );
        leccion.setDuracionLeccion(
                convertirDuracion(
                        request.getDuracionMinutos()
                )
        );
        leccion.setOrden(
                request.getOrden()
        );

        Leccion guardada =
                leccionRepository.save(leccion);

        return convertirRespuesta(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeccionResponse> listarPorPrograma(
            Long programaId
    ) {
        Programa programa = buscarPrograma(programaId);

        accesoAcademicoService.validarLecturaPrograma(
                programa.getIdPrograma()
        );

        List<Leccion> lecciones =
                leccionRepository
                        .findByProgramaIdOrderByOrdenAsc(
                                programa.getIdPrograma()
                        );

        return convertirLista(lecciones);
    }

    /*
     * Método nuevo: crea un contenido
     * dentro de un módulo.
     */
    @Override
    @Transactional
    public LeccionResponse crearLeccionEnModulo(
            Integer moduloId,
            LeccionRequest request
    ) {
        Modulo modulo = buscarModulo(moduloId);

        validarRequest(request);

        boolean ordenOcupado =
                leccionRepository
                        .existsByModuloIdModuloAndOrden(
                                moduloId,
                                request.getOrden()
                        );

        if (ordenOcupado) {
            throw new BadRequestException(
                    "Ya existe un contenido con el orden "
                            + request.getOrden()
                            + " dentro del módulo"
            );
        }

        Programa programa =
                modulo.getBloque().getPrograma();

        Leccion leccion = new Leccion();

        leccion.setPrograma(programa);
        leccion.setModulo(modulo);
        leccion.setTituloLeccion(
                request.getTitulo().trim()
        );
        leccion.setDescripcion(
                request.getDescripcion()
        );
        leccion.setTipoContenido(
                obtenerTipoContenido(request)
        );
        leccion.setMediaUrl(
                request.getContenidoUrl()
        );
        leccion.setDuracionLeccion(
                convertirDuracion(
                        request.getDuracionMinutos()
                )
        );
        leccion.setOrden(
                request.getOrden()
        );

        Leccion guardada =
                leccionRepository.save(leccion);

        return convertirRespuesta(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeccionResponse> listarPorModulo(
            Integer moduloId
    ) {
        buscarModulo(moduloId);

        List<Leccion> lecciones =
                leccionRepository
                        .findByModuloIdModuloOrderByOrdenAsc(
                                moduloId
                        );

        return convertirLista(lecciones);
    }

    @Override
    @Transactional
    public LeccionResponse actualizarLeccion(
            Long leccionId,
            LeccionRequest request
    ) {
        Leccion leccion = buscarLeccion(leccionId);

        validarRequest(request);

        if (leccion.getModulo() != null) {
            Integer moduloId =
                    leccion.getModulo().getIdModulo();

            boolean ordenOcupado =
                    leccionRepository
                            .existsByModuloIdModuloAndOrdenAndIdLeccionNot(
                                    moduloId,
                                    request.getOrden(),
                                    leccion.getIdLeccion()
                            );

            if (ordenOcupado) {
                throw new BadRequestException(
                        "Ya existe otro contenido con el orden "
                                + request.getOrden()
                                + " dentro del módulo"
                );
            }
        }

        leccion.setTituloLeccion(
                request.getTitulo().trim()
        );
        leccion.setDescripcion(
                request.getDescripcion()
        );

        if (request.getTipoContenido() != null) {
            leccion.setTipoContenido(
                    request.getTipoContenido()
            );
        }

        leccion.setMediaUrl(
                request.getContenidoUrl()
        );
        leccion.setDuracionLeccion(
                convertirDuracion(
                        request.getDuracionMinutos()
                )
        );
        leccion.setOrden(
                request.getOrden()
        );

        Leccion actualizada =
                leccionRepository.save(leccion);

        return convertirRespuesta(actualizada);
    }

    @Override
    @Transactional
    public void eliminarLeccion(
            Long leccionId
    ) {
        Leccion leccion =
                buscarLeccion(leccionId);

        leccionRepository.delete(leccion);
    }

    private Programa buscarPrograma(
            Long programaId
    ) {
        if (programaId == null) {
            throw new BadRequestException(
                    "El identificador del programa es obligatorio"
            );
        }

        int id = programaId.intValue();

        return programaRepository
                .findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Programa no encontrado con id: "
                                        + programaId
                        )
                );
    }

    private Modulo buscarModulo(
            Integer moduloId
    ) {
        if (moduloId == null) {
            throw new BadRequestException(
                    "El identificador del módulo es obligatorio"
            );
        }

        Modulo modulo =
                moduloRepository
                        .findById(moduloId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Módulo no encontrado con id: "
                                                + moduloId
                                )
                        );

        accesoAcademicoService.validarGestionPrograma(
                modulo.getBloque()
                        .getPrograma()
                        .getIdPrograma()
        );

        return modulo;
    }

    private Leccion buscarLeccion(
            Long leccionId
    ) {
        if (leccionId == null) {
            throw new BadRequestException(
                    "El identificador de la lección es obligatorio"
            );
        }

        int id = leccionId.intValue();

        Leccion leccion =
                leccionRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Lección no encontrada con id: "
                                                + leccionId
                                )
                        );

        accesoAcademicoService.validarGestionPrograma(
                leccion.getPrograma().getIdPrograma()
        );

        return leccion;
    }

    private void validarRequest(
            LeccionRequest request
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "Los datos de la lección son obligatorios"
            );
        }

        if (
                request.getTitulo() == null
                        || request.getTitulo().isBlank()
        ) {
            throw new BadRequestException(
                    "El título de la lección es obligatorio"
            );
        }

        if (
                request.getOrden() == null
                        || request.getOrden() <= 0
        ) {
            throw new BadRequestException(
                    "El orden de la lección debe ser mayor que cero"
            );
        }

        if (
                request.getDuracionMinutos() != null
                        && request.getDuracionMinutos() <= 0
        ) {
            throw new BadRequestException(
                    "La duración debe ser mayor que cero"
            );
        }
    }

    private TipoContenido obtenerTipoContenido(
            LeccionRequest request
    ) {
        if (request.getTipoContenido() != null) {
            return request.getTipoContenido();
        }

        return TipoContenido.VIDEO;
    }

    private String convertirDuracion(
            Integer duracionMinutos
    ) {
        if (duracionMinutos == null) {
            return null;
        }

        return String.valueOf(
                duracionMinutos
        );
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

    private List<LeccionResponse> convertirLista(
            List<Leccion> lecciones
    ) {
        List<LeccionResponse> respuestas =
                new ArrayList<>();

        for (Leccion leccion : lecciones) {
            respuestas.add(
                    convertirRespuesta(leccion)
            );
        }

        return respuestas;
    }

    private LeccionResponse convertirRespuesta(
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
}