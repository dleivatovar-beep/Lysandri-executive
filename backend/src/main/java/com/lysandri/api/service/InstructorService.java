package com.lysandri.api.service;

import com.lysandri.api.dto.request.InstructorRequest;
import com.lysandri.api.dto.response.InstructorResponse;

import java.util.List;

public interface InstructorService {

    List<InstructorResponse> listarInstructores();

    InstructorResponse obtenerInstructor(
        String dni
    );

    InstructorResponse crearInstructor(
        InstructorRequest request
    );

    InstructorResponse actualizarInstructor(
        String dni,
        InstructorRequest request
    );

    void eliminarInstructor(String dni);
}