package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.BloqueRequest;
import com.lysandri.api.dto.response.BloqueResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Bloque;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.repository.BloqueRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.service.AccesoAcademicoService;
import com.lysandri.api.service.BloqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BloqueServiceImpl implements BloqueService {

    private final BloqueRepository bloqueRepository;
    private final ProgramaRepository programaRepository;
    private final AccesoAcademicoService accesoAcademicoService;

    @Override
    @Transactional
    public BloqueResponse crearBloque(
            Integer programaId,
            BloqueRequest request
    ) {
        Programa programa = buscarPrograma(programaId);
        validarRequest(request);

        boolean ordenOcupado =
                bloqueRepository
                        .existsByProgramaIdProgramaAndOrden(
                                programaId,
                                request.getOrden()
                        );

        if (ordenOcupado) {
            throw new BadRequestException(
                    "Ya existe un bloque con el orden "
                            + request.getOrden()
                            + " dentro del programa"
            );
        }

        Bloque bloque = new Bloque();

        bloque.setPrograma(programa);
        bloque.setTitulo(
                request.getTitulo().trim()
        );
        bloque.setDescripcion(
                request.getDescripcion()
        );
        bloque.setOrden(
                request.getOrden()
        );
        bloque.setActivo(
                request.getActivo() != null
                        ? request.getActivo()
                        : true
        );

        Bloque guardado =
                bloqueRepository.save(bloque);

        return convertirRespuesta(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BloqueResponse> listarPorPrograma(
            Integer programaId
    ) {
        buscarPrograma(programaId);

        List<Bloque> bloques =
                bloqueRepository
                        .findByProgramaIdProgramaOrderByOrdenAsc(
                                programaId
                        );

        List<BloqueResponse> respuestas =
                new ArrayList<>();

        for (Bloque bloque : bloques) {
            respuestas.add(
                    convertirRespuesta(bloque)
            );
        }

        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public BloqueResponse obtenerBloque(
            Integer bloqueId
    ) {
        Bloque bloque = buscarBloque(bloqueId);

        return convertirRespuesta(bloque);
    }

    @Override
    @Transactional
    public BloqueResponse actualizarBloque(
            Integer bloqueId,
            BloqueRequest request
    ) {
        Bloque bloque = buscarBloque(bloqueId);
        validarRequest(request);

        Integer programaId =
                bloque.getPrograma().getIdPrograma();

        boolean ordenOcupado =
                bloqueRepository
                        .existsByProgramaIdProgramaAndOrdenAndIdBloqueNot(
                                programaId,
                                request.getOrden(),
                                bloqueId
                        );

        if (ordenOcupado) {
            throw new BadRequestException(
                    "Ya existe otro bloque con el orden "
                            + request.getOrden()
                            + " dentro del programa"
            );
        }

        bloque.setTitulo(
                request.getTitulo().trim()
        );
        bloque.setDescripcion(
                request.getDescripcion()
        );
        bloque.setOrden(
                request.getOrden()
        );

        if (request.getActivo() != null) {
            bloque.setActivo(
                    request.getActivo()
            );
        }

        Bloque actualizado =
                bloqueRepository.save(bloque);

        return convertirRespuesta(actualizado);
    }

    @Override
    @Transactional
    public void eliminarBloque(
            Integer bloqueId
    ) {
        Bloque bloque = buscarBloque(bloqueId);

        bloqueRepository.delete(bloque);
    }

    private Programa buscarPrograma(
            Integer programaId
    ) {
        if (programaId == null) {
            throw new BadRequestException(
                    "El identificador del programa es obligatorio"
            );
        }

        Programa programa =
                programaRepository
                        .findById(programaId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Programa no encontrado con id: "
                                                + programaId
                                )
                        );

        accesoAcademicoService.validarGestionPrograma(
                programaId
        );

        return programa;
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

    private void validarRequest(
            BloqueRequest request
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "Los datos del bloque son obligatorios"
            );
        }

        if (
                request.getTitulo() == null
                        || request.getTitulo().isBlank()
        ) {
            throw new BadRequestException(
                    "El título del bloque es obligatorio"
            );
        }

        if (
                request.getOrden() == null
                        || request.getOrden() <= 0
        ) {
            throw new BadRequestException(
                    "El orden del bloque debe ser mayor que cero"
            );
        }
    }

    private BloqueResponse convertirRespuesta(
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
}