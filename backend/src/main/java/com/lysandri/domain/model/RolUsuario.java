package com.lysandri.domain.model;

import java.util.Locale;

public enum RolUsuario {
    CLIENTE,
    ESTUDIANTE,
    INSTRUCTOR,
    DOCENTE,
    TRABAJADOR,
    ADMIN,
    GERENTE,
    CONTABILIDAD,
    FINANZAS,
    VENTAS,
    COORDINADOR_ACADEMICO,
    AUDITOR;

    public boolean esRolInterno() {
        return switch (this) {
            case ADMIN, GERENTE, TRABAJADOR, CONTABILIDAD, FINANZAS, COORDINADOR_ACADEMICO, DOCENTE, AUDITOR -> true;
            default -> false;
        };
    }

    public static RolUsuario fromString(String rol) {
        if (rol == null || rol.isBlank()) return CLIENTE;

        String normalized = rol.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);

        if (java.util.Set.of("VENTAS", "VENDEDOR", "COMERCIAL", "SALES").contains(normalized)) {
            throw new IllegalArgumentException("El rol Vendedor no está habilitado en Lysandri Executive.");
        }

        try {
            return RolUsuario.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return switch (normalized) {
                case "PROFESOR" -> INSTRUCTOR;
                case "EJECUTIVO", "ALUMNO" -> ESTUDIANTE;
                case "ADMINISTRADOR" -> ADMIN;
                case "COORDINADORACADEMICO", "COORDINADOR_ACADEMICO" -> COORDINADOR_ACADEMICO;
                case "CONTADOR" -> CONTABILIDAD;
                case "CONTABLE" -> CONTABILIDAD;
                case "COMERCIAL", "SALES" -> VENTAS;
                case "AUDIT" -> AUDITOR;
                default -> CLIENTE;
            };
        }
    }
}
