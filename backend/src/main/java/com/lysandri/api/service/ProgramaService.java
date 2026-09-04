package com.lysandri.api.service;

import com.lysandri.api.dto.request.ProgramaRequest;
import com.lysandri.api.dto.response.ProgramaResponse;

import java.util.List;

public interface ProgramaService {

    List<ProgramaResponse> getAllProgramas();

    ProgramaResponse getProgramaById(Integer id);

    ProgramaResponse createPrograma(ProgramaRequest request);

    ProgramaResponse updatePrograma(Integer id, ProgramaRequest request);

    void deletePrograma(Integer id);
}
