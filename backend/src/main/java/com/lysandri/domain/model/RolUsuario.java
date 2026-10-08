package com.lysandri.domain.model;

public enum RolUsuario {
    CLIENTE,
    ESTUDIANTE,
    INSTRUCTOR,
    DOCENTE,
    ADMIN;

    public static RolUsuario fromString(String rol) {
        if (rol == null) return CLIENTE;
        try {
            return RolUsuario.valueOf(rol.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String upper = rol.trim().toUpperCase();
            if ("PROFESOR".equals(upper)) return INSTRUCTOR;
            if ("EJECUTIVO".equals(upper) || "ALUMNO".equals(upper)) return ESTUDIANTE;
            if ("ADMINISTRADOR".equals(upper)) return ADMIN;
            return CLIENTE;
        }
    }
}
