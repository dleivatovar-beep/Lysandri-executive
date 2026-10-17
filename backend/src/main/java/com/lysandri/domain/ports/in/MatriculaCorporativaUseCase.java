package com.lysandri.domain.ports.in;

import java.util.List;

public interface MatriculaCorporativaUseCase {

    record ColaboradorItem(
            String nombres,
            String apellidos,
            String email,
            String cargo,
            String telefono
    ) {}

    record MatricularNominaCommand(
            Long idPrograma,
            Long idSolicitud,
            String empresaNombre,
            List<ColaboradorItem> colaboradores,
            boolean enviarCredencialesEmail,
            String notasAdmin
    ) {
        public MatricularNominaCommand(Long idPrograma, String empresaNombre, List<ColaboradorItem> colaboradores) {
            this(idPrograma, null, empresaNombre, colaboradores, true, null);
        }
    }

    record MatriculaColaboradorResultado(
            String nombres,
            String apellidos,
            String email,
            Long moodleUserId,
            String username,
            String passwordGenerada,
            String estado,
            String mensaje
    ) {}

    record MatriculaNominaResponse(
            Long idPrograma,
            String programaTitulo,
            Long moodleCourseId,
            String empresaNombre,
            int totalProcesados,
            int totalExitosos,
            int totalFallidos,
            List<MatriculaColaboradorResultado> resultados
    ) {}

    MatriculaNominaResponse matricularNomina(MatricularNominaCommand command);
}
