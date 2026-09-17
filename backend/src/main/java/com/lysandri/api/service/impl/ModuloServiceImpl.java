package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.ModuloRequest;
import com.lysandri.api.dto.response.ModuloResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Bloque;
import com.lysandri.api.model.entity.Modulo;
import com.lysandri.api.repository.BloqueRepository;
import com.lysandri.api.repository.ModuloRepository;
import com.lysandri.api.service.AccesoAcademicoService;
import com.lysandri.api.service.ModuloService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ModuloServiceImpl implements ModuloService {

    private final ModuloRepository moduloRepository;
    private final BloqueRepository bloqueRepository;
    private final AccesoAcademicoService accesoAcademicoService;

    @Override
    @Transactional
    public ModuloResponse crearModulo(
            Integer bloqueId,
            ModuloRequest request
    ) {
        Bloque bloque = buscarBloque(bloqueId);
        validarRequest(request);

        boolean ordenOcupado =
                moduloRepository
                        .existsByBloqueIdBloqueAndOrden(
                                bloqueId,
                                request.getOrden()
                        );

        if (ordenOcupado) {
            throw new BadRequestException(
                    "Ya existe un módulo con el orden "
                            + request.getOrden()
                            + " dentro del bloque"
            );
        }

        Modulo modulo = new Modulo();

        modulo.setBloque(bloque);
        modulo.setTitulo(
                request.getTitulo().trim()
        );
        modulo.setDescripcion(
                request.getDescripcion()
        );
        modulo.setOrden(
                request.getOrden()
        );
        modulo.setActivo(
                request.getActivo() != null
                        ? request.getActivo()
                        : true
        );

        Modulo guardado =
                moduloRepository.save(modulo);

        return convertirRespuesta(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModuloResponse> listarPorBloque(
            Integer bloqueId
    ) {
        buscarBloque(bloqueId);

        List<Modulo> modulos =
                moduloRepository
                        .findByBloqueIdBloqueOrderByOrdenAsc(
                                bloqueId
                        );

        List<ModuloResponse> respuestas =
                new ArrayList<>();

        for (Modulo modulo : modulos) {
            respuestas.add(
                    convertirRespuesta(modulo)
            );
        }

        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public ModuloResponse obtenerModulo(
            Integer moduloId
    ) {
        Modulo modulo = buscarModulo(moduloId);

        return convertirRespuesta(modulo);
    }

    @Override
    @Transactional
    public ModuloResponse actualizarModulo(
            Integer moduloId,
            ModuloRequest request
    ) {
        Modulo modulo = buscarModulo(moduloId);
        validarRequest(request);

        Integer bloqueId =
                modulo.getBloque().getIdBloque();

        boolean ordenOcupado =
                moduloRepository
                        .existsByBloqueIdBloqueAndOrdenAndIdModuloNot(
                                bloqueId,
                                request.getOrden(),
                                moduloId
                        );

        if (ordenOcupado) {
            throw new BadRequestException(
                    "Ya existe otro módulo con el orden "
                            + request.getOrden()
                            + " dentro del bloque"
            );
        }

        modulo.setTitulo(
                request.getTitulo().trim()
        );
        modulo.setDescripcion(
                request.getDescripcion()
        );
        modulo.setOrden(
                request.getOrden()
        );

        if (request.getActivo() != null) {
            modulo.setActivo(
                    request.getActivo()
            );
        }

        Modulo actualizado =
                moduloRepository.save(modulo);

        return convertirRespuesta(actualizado);
    }

    @Override
    @Transactional
    public void eliminarModulo(
            Integer moduloId
    ) {
        Modulo modulo = buscarModulo(moduloId);

        moduloRepository.delete(modulo);
    }

    private Bloque buscarBloque(
            Integer bloqueId
    ) {
        if (bloqueId == null) {
            throw new BadRequestException(
                    "El identificador del bloque es obligatorio"
            );
        }

        Bloque bloque =
                bloqueRepository
                        .findById(bloqueId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Bloque no encontrado con id: "
                                                + bloqueId
                                )
                        );

        accesoAcademicoService.validarGestionPrograma(
                bloque.getPrograma().getIdPrograma()
        );

        return bloque;
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

    private void validarRequest(
            ModuloRequest request
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "Los datos del módulo son obligatorios"
            );
        }

        if (
                request.getTitulo() == null
                        || request.getTitulo().isBlank()
        ) {
            throw new BadRequestException(
                    "El título del módulo es obligatorio"
            );
        }

        if (
                request.getOrden() == null
                        || request.getOrden() <= 0
        ) {
            throw new BadRequestException(
                    "El orden del módulo debe ser mayor que cero"
            );
        }
    }

    private ModuloResponse convertirRespuesta(
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
}