package com.lysandri.api.controller;

import com.lysandri.api.dto.request.UsuarioRequest;
import com.lysandri.api.dto.response.UsuarioResponse;
import com.lysandri.api.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Endpoints para la gestión de usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @Operation(summary = "Listar todos los usuarios")
    public ResponseEntity<List<UsuarioResponse>> getAllUsuarios() {
        return ResponseEntity.ok(usuarioService.getAllUsuarios());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de un usuario por ID")
    public ResponseEntity<UsuarioResponse> getUsuarioById(@PathVariable Integer id) {
        return ResponseEntity.ok(usuarioService.getUsuarioById(id));
    }

    @PostMapping
    @Operation(summary = "Crear o registrar un nuevo usuario")
    public ResponseEntity<UsuarioResponse> createUsuario(@Valid @RequestBody UsuarioRequest request) {
        return new ResponseEntity<>(usuarioService.createUsuario(request), HttpStatus.CREATED);
    }
}
