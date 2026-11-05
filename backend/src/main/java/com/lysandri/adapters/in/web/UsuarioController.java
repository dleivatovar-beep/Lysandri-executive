package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.UsuarioCreateRequest;
import com.lysandri.adapters.in.web.dto.UsuarioResponseDto;
import com.lysandri.adapters.in.web.dto.UsuarioUpdateRequest;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Usuarios", description = "Gestión de perfiles de usuario y administración")
public class UsuarioController {

    private final UserManagementUseCase userManagementUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'COORDINADOR_ACADEMICO', 'AUDITOR')")
    @Operation(summary = "Lista todos los usuarios (personal interno autorizado)")
    public ResponseEntity<List<UsuarioResponseDto>> listarUsuarios(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String rol
    ) {
        RolUsuario rolEnum = rol != null && !rol.isBlank() ? RolUsuario.fromString(rol) : null;
        List<Usuario> usuarios = userManagementUseCase.listarUsuarios(search, rolEnum);
        List<UsuarioResponseDto> dtos = usuarios.stream()
                .map(UsuarioResponseDto::fromDomain)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/trabajadores")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista solo cuentas internas autorizadas por roles de personal")
    public ResponseEntity<List<UsuarioResponseDto>> listarTrabajadores() {
        return ResponseEntity.ok(userManagementUseCase.listarTrabajadores().stream()
                .map(UsuarioResponseDto::fromDomain)
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene los datos de un usuario por su ID (propietario o administrador)")
    public ResponseEntity<UsuarioResponseDto> obtenerPorId(@PathVariable Long id, Authentication authentication) {
        Usuario usuario = userManagementUseCase.obtenerPorId(id);
        if (!isAdmin(authentication) && !usuario.getEmail().equalsIgnoreCase(authentication.getName())) {
            log.warn("Usuario {} intentó acceder a los datos del usuario ID {} sin autorización", authentication.getName(), id);
            throw new AccessDeniedException("No tiene permisos para consultar información de otros usuarios");
        }
        return ResponseEntity.ok(UsuarioResponseDto.fromDomain(usuario));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crea un nuevo usuario desde el panel administrativo")
    public ResponseEntity<UsuarioResponseDto> crearUsuario(@Valid @RequestBody UsuarioCreateRequest request) {
        log.info("Creando usuario desde panel administrativo: {}", request.getEmail());
        Usuario u = Usuario.builder()
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .email(request.getEmail())
                .passw(request.getPassw())
                .telefono(request.getTelefono())
                .rol(request.getRol() != null ? RolUsuario.fromString(request.getRol()) : RolUsuario.CLIENTE)
                .build();

        Usuario creado = userManagementUseCase.crearUsuario(u);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponseDto.fromDomain(creado));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza los datos de un usuario (propietario o administrador)")
    public ResponseEntity<UsuarioResponseDto> actualizarUsuario(
            @PathVariable Long id,
            @RequestBody UsuarioUpdateRequest request,
            Authentication authentication
    ) {
        log.info("Actualizando usuario ID: {} por usuario {}", id, authentication.getName());
        Usuario existente = userManagementUseCase.obtenerPorId(id);
        boolean admin = isAdmin(authentication);

        if (!admin && !existente.getEmail().equalsIgnoreCase(authentication.getName())) {
            log.warn("Usuario {} intentó modificar el perfil del usuario ID {} sin autorización", authentication.getName(), id);
            throw new AccessDeniedException("No tiene permisos para modificar la información de otro usuario");
        }

        // Los cambios de rol y estado solo pueden pasar por las operaciones auditadas dedicadas.
        if (request.getRol() != null || request.getActivo() != null) {
            log.warn("Usuario {} intentó cambiar rol/estado fuera de la operación auditada", authentication.getName());
            throw new AccessDeniedException("Use los endpoints administrativos auditados para cambiar rol o estado");
        }

        Usuario u = Usuario.builder()
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .email(request.getEmail())
                .passw(request.getPassw())
                .telefono(request.getTelefono())
                .rol(null)
                .activo(existente.isActivo())
                .build();

        Usuario actualizado = userManagementUseCase.actualizarUsuario(id, u);
        return ResponseEntity.ok(UsuarioResponseDto.fromDomain(actualizado));
    }

    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualiza el rol de un usuario interno y deja registro de auditoría")
    public ResponseEntity<UsuarioResponseDto> actualizarRol(
            @PathVariable Long id,
            @RequestParam String rol,
            @RequestParam(required = false) String motivo,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Debe autenticarse para asignar roles internos");
        }

        Usuario actualizado = userManagementUseCase.actualizarRol(
                id,
                RolUsuario.fromString(rol),
                motivo,
                authentication.getName()
        );
        return ResponseEntity.ok(UsuarioResponseDto.fromDomain(actualizado));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactiva una cuenta interna y registra la revocación de permisos")
    public ResponseEntity<UsuarioResponseDto> desactivarUsuario(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Debe autenticarse para desactivar cuentas internas");
        }

        Usuario actualizado = userManagementUseCase.desactivarUsuario(id, motivo, authentication.getName());
        return ResponseEntity.ok(UsuarioResponseDto.fromDomain(actualizado));
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consulta el historial de cambios de permisos y acceso de un usuario")
    public ResponseEntity<List<com.lysandri.domain.model.UsuarioCambioAudit>> historialCambios(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementUseCase.obtenerHistorialCambios(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactiva una cuenta; no elimina físicamente datos ni su auditoría")
    public ResponseEntity<Void> eliminarUsuario(
            @PathVariable Long id,
            @RequestParam(required = false) String motivo,
            Authentication authentication) {
        log.info("Desactivando usuario ID: {} por solicitud administrativa", id);
        userManagementUseCase.desactivarUsuario(id, motivo, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(authority -> {
                    String normalized = authority != null ? authority.replace("ROLE_", "") : "";
                        return "ADMIN".equals(normalized);
                });
    }
}
