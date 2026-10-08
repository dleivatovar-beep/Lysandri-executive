package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.UsuarioCreateRequest;
import com.lysandri.adapters.in.web.dto.UsuarioResponseDto;
import com.lysandri.adapters.in.web.dto.UsuarioUpdateRequest;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Slf4j
public class UsuarioController {

    private final UserManagementUseCase userManagementUseCase;

    @GetMapping
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

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDto> obtenerPorId(@PathVariable Long id) {
        Usuario usuario = userManagementUseCase.obtenerPorId(id);
        return ResponseEntity.ok(UsuarioResponseDto.fromDomain(usuario));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponseDto> crearUsuario(@Valid @RequestBody UsuarioCreateRequest request) {
        log.info("Creando usuario desde API: {}", request.getEmail());
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
    public ResponseEntity<UsuarioResponseDto> actualizarUsuario(
            @PathVariable Long id,
            @RequestBody UsuarioUpdateRequest request
    ) {
        log.info("Actualizando usuario ID: {}", id);
        Usuario u = Usuario.builder()
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .email(request.getEmail())
                .passw(request.getPassw())
                .telefono(request.getTelefono())
                .rol(request.getRol() != null ? RolUsuario.fromString(request.getRol()) : null)
                .build();

        Usuario actualizado = userManagementUseCase.actualizarUsuario(id, u);
        return ResponseEntity.ok(UsuarioResponseDto.fromDomain(actualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Long id) {
        log.info("Eliminando usuario ID: {}", id);
        userManagementUseCase.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
