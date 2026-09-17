package com.lysandri.api.service;

import com.lysandri.api.dto.response.EstructuraProgramaResponse;

public interface EstructuraAcademicaService {

    EstructuraProgramaResponse obtenerEstructuraPrograma(
            Integer programaId
    );
}