package com.lysandri.adapters.in.web.dto;

import com.lysandri.domain.model.Usuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDto {

    private Long idUser;
    private String nombres;
    private String apellidos;
    private String email;
    private String telefono;
    private String rol;
    private boolean activo;
    private OffsetDateTime fechaCreacion;

    public static UsuarioResponseDto fromDomain(Usuario u) {
        if (u == null) return null;
        return UsuarioResponseDto.builder()
                .idUser(u.getIdUser())
                .nombres(u.getNombres())
                .apellidos(u.getApellidos())
                .email(u.getEmail())
                .telefono(u.getTelefono())
                .rol(u.getRol() != null ? u.getRol().name() : "CLIENTE")
                .activo(u.isActivo())
                .fechaCreacion(u.getFechaCreacion())
                .build();
    }
}
