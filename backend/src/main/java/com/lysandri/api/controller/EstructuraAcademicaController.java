package com.lysandri.api.controller;

import com.lysandri.api.dto.request.ActividadBloqueRequest;
import com.lysandri.api.dto.request.BloqueRequest;
import com.lysandri.api.dto.request.LeccionRequest;
import com.lysandri.api.dto.request.ModuloRequest;
import com.lysandri.api.dto.response.ActividadBloqueResponse;
import com.lysandri.api.dto.response.BloqueResponse;
import com.lysandri.api.dto.response.EstructuraProgramaResponse;
import com.lysandri.api.dto.response.LeccionResponse;
import com.lysandri.api.dto.response.ModuloResponse;
import com.lysandri.api.service.ActividadBloqueService;
import com.lysandri.api.service.BloqueService;
import com.lysandri.api.service.EstructuraAcademicaService;
import com.lysandri.api.service.LeccionService;
import com.lysandri.api.service.ModuloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "Estructura académica",
        description = "Gestión de bloques, módulos, contenidos y actividades"
)
public class EstructuraAcademicaController {

    private static final String ROLES_GESTION =
            "hasAnyRole('ADMIN', 'ADMINISTRADOR', "
                    + "'INSTRUCTOR', 'PROFESOR')";

    private final BloqueService bloqueService;
    private final ModuloService moduloService;
    private final ActividadBloqueService actividadService;
    private final LeccionService leccionService;
    private final EstructuraAcademicaService estructuraService;

    /*
     * BLOQUES
     */

    @PostMapping(
            "/api/v1/academico/programas/{programaId}/bloques"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Crear un bloque dentro de un programa")
    public ResponseEntity<BloqueResponse> crearBloque(
            @PathVariable Integer programaId,
            @Valid @RequestBody BloqueRequest request
    ) {
        BloqueResponse response =
                bloqueService.crearBloque(
                        programaId,
                        request
                );

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED
        );
    }

    @GetMapping(
            "/api/v1/academico/programas/{programaId}/bloques"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Listar los bloques de un programa")
    public ResponseEntity<List<BloqueResponse>> listarBloques(
            @PathVariable Integer programaId
    ) {
        List<BloqueResponse> response =
                bloqueService.listarPorPrograma(programaId);

        return ResponseEntity.ok(response);
    }

    @GetMapping(
            "/api/v1/academico/bloques/{bloqueId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Obtener un bloque por su identificador")
    public ResponseEntity<BloqueResponse> obtenerBloque(
            @PathVariable Integer bloqueId
    ) {
        BloqueResponse response =
                bloqueService.obtenerBloque(bloqueId);

        return ResponseEntity.ok(response);
    }

    @PutMapping(
            "/api/v1/academico/bloques/{bloqueId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Actualizar un bloque")
    public ResponseEntity<BloqueResponse> actualizarBloque(
            @PathVariable Integer bloqueId,
            @Valid @RequestBody BloqueRequest request
    ) {
        BloqueResponse response =
                bloqueService.actualizarBloque(
                        bloqueId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping(
            "/api/v1/academico/bloques/{bloqueId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Eliminar un bloque")
    public ResponseEntity<Void> eliminarBloque(
            @PathVariable Integer bloqueId
    ) {
        bloqueService.eliminarBloque(bloqueId);
        return ResponseEntity.noContent().build();
    }

    /*
     * MÓDULOS
     */

    @PostMapping(
            "/api/v1/academico/bloques/{bloqueId}/modulos"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Crear un módulo dentro de un bloque")
    public ResponseEntity<ModuloResponse> crearModulo(
            @PathVariable Integer bloqueId,
            @Valid @RequestBody ModuloRequest request
    ) {
        ModuloResponse response =
                moduloService.crearModulo(
                        bloqueId,
                        request
                );

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED
        );
    }

    @GetMapping(
            "/api/v1/academico/bloques/{bloqueId}/modulos"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Listar los módulos de un bloque")
    public ResponseEntity<List<ModuloResponse>> listarModulos(
            @PathVariable Integer bloqueId
    ) {
        List<ModuloResponse> response =
                moduloService.listarPorBloque(bloqueId);

        return ResponseEntity.ok(response);
    }

    @GetMapping(
            "/api/v1/academico/modulos/{moduloId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Obtener un módulo por su identificador")
    public ResponseEntity<ModuloResponse> obtenerModulo(
            @PathVariable Integer moduloId
    ) {
        ModuloResponse response =
                moduloService.obtenerModulo(moduloId);

        return ResponseEntity.ok(response);
    }

    @PutMapping(
            "/api/v1/academico/modulos/{moduloId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Actualizar un módulo")
    public ResponseEntity<ModuloResponse> actualizarModulo(
            @PathVariable Integer moduloId,
            @Valid @RequestBody ModuloRequest request
    ) {
        ModuloResponse response =
                moduloService.actualizarModulo(
                        moduloId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping(
            "/api/v1/academico/modulos/{moduloId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Eliminar un módulo")
    public ResponseEntity<Void> eliminarModulo(
            @PathVariable Integer moduloId
    ) {
        moduloService.eliminarModulo(moduloId);
        return ResponseEntity.noContent().build();
    }

    /*
     * CONTENIDOS DEL MÓDULO
     */

    @PostMapping(
            "/api/v1/academico/modulos/{moduloId}/contenidos"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Crear un contenido dentro de un módulo")
    public ResponseEntity<LeccionResponse> crearContenido(
            @PathVariable Integer moduloId,
            @Valid @RequestBody LeccionRequest request
    ) {
        LeccionResponse response =
                leccionService.crearLeccionEnModulo(
                        moduloId,
                        request
                );

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED
        );
    }

    @GetMapping(
            "/api/v1/academico/modulos/{moduloId}/contenidos"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Listar los contenidos de un módulo")
    public ResponseEntity<List<LeccionResponse>> listarContenidos(
            @PathVariable Integer moduloId
    ) {
        List<LeccionResponse> response =
                leccionService.listarPorModulo(moduloId);

        return ResponseEntity.ok(response);
    }

    /*
     * RETOS, CASOS, SIMULACIONES Y DESAFÍOS
     */

    @PostMapping(
            "/api/v1/academico/bloques/{bloqueId}/actividades"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Crear una actividad dentro de un bloque")
    public ResponseEntity<ActividadBloqueResponse> crearActividad(
            @PathVariable Integer bloqueId,
            @Valid @RequestBody ActividadBloqueRequest request
    ) {
        ActividadBloqueResponse response =
                actividadService.crearActividad(
                        bloqueId,
                        request
                );

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED
        );
    }

    @GetMapping(
            "/api/v1/academico/bloques/{bloqueId}/actividades"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Listar las actividades de un bloque")
    public ResponseEntity<List<ActividadBloqueResponse>>
    listarActividades(
            @PathVariable Integer bloqueId
    ) {
        List<ActividadBloqueResponse> response =
                actividadService.listarPorBloque(bloqueId);

        return ResponseEntity.ok(response);
    }

    @GetMapping(
            "/api/v1/academico/actividades/{actividadId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Obtener una actividad")
    public ResponseEntity<ActividadBloqueResponse>
    obtenerActividad(
            @PathVariable Integer actividadId
    ) {
        ActividadBloqueResponse response =
                actividadService.obtenerActividad(
                        actividadId
                );

        return ResponseEntity.ok(response);
    }

    @PutMapping(
            "/api/v1/academico/actividades/{actividadId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Actualizar una actividad")
    public ResponseEntity<ActividadBloqueResponse>
    actualizarActividad(
            @PathVariable Integer actividadId,
            @Valid @RequestBody ActividadBloqueRequest request
    ) {
        ActividadBloqueResponse response =
                actividadService.actualizarActividad(
                        actividadId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping(
            "/api/v1/academico/actividades/{actividadId}"
    )
    @PreAuthorize(ROLES_GESTION)
    @Operation(summary = "Eliminar una actividad")
    public ResponseEntity<Void> eliminarActividad(
            @PathVariable Integer actividadId
    ) {
        actividadService.eliminarActividad(actividadId);
        return ResponseEntity.noContent().build();
    }

    /*
     * AULA DEL ESTUDIANTE
     */

    @GetMapping(
            "/api/v1/aula/programas/{programaId}/estructura"
    )
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener la estructura completa del programa")
    public ResponseEntity<EstructuraProgramaResponse>
    obtenerEstructura(
            @PathVariable Integer programaId
    ) {
        EstructuraProgramaResponse response =
                estructuraService
                        .obtenerEstructuraPrograma(programaId);

        return ResponseEntity.ok(response);
    }
}