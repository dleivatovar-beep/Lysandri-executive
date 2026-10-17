package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.MatricularNominaRequest;
import com.lysandri.domain.ports.in.MatriculaCorporativaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/v1/corporativo", "/api/v1/admin/corporativo"})
@RequiredArgsConstructor
@Tag(name = "Formación Corporativa & In-Company", description = "Matrícula masiva de colaboradores y gestión de cohortes empresariales")
public class CorporativoController {

    private final MatriculaCorporativaUseCase matriculaCorporativaUseCase;

    @PostMapping("/matricular-nomina")
    @Operation(summary = "Matricula en lote a la nómina de colaboradores de una empresa en el Campus Moodle")
    public ResponseEntity<MatriculaCorporativaUseCase.MatriculaNominaResponse> matricularNomina(
            @Valid @RequestBody MatricularNominaRequest request) {

        log.info("Recibida solicitud de matrícula corporativa para empresa: {} ({} colaboradores)",
                request.getEmpresaNombre(), request.getColaboradores().size());

        List<MatriculaCorporativaUseCase.ColaboradorItem> items = request.getColaboradores().stream()
                .map(c -> new MatriculaCorporativaUseCase.ColaboradorItem(
                        c.getNombres(),
                        c.getApellidos(),
                        c.getEmail(),
                        c.getCargo(),
                        c.getTelefono()
                ))
                .toList();

        MatriculaCorporativaUseCase.MatricularNominaCommand command = new MatriculaCorporativaUseCase.MatricularNominaCommand(
                request.getIdPrograma(),
                request.getIdSolicitud(),
                request.getEmpresaNombre(),
                items,
                request.getEnviarCredencialesEmail() == null || request.getEnviarCredencialesEmail(),
                request.getNotasAdmin()
        );

        MatriculaCorporativaUseCase.MatriculaNominaResponse response = matriculaCorporativaUseCase.matricularNomina(command);
        return ResponseEntity.ok(response);
    }
}
