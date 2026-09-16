package com.lysandri.api.service;

import com.lysandri.api.dto.request.InstructorRequest;
import com.lysandri.api.dto.response.InstructorResponse;

import java.util.List;

public interface InstructorService {

    InstructorResponse crearInstructor(
            InstructorRequest request
    );

    List<InstructorResponse> listarInstructores();

    InstructorResponse obtenerInstructor(
            String instructorDni
    );
}