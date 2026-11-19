package com.lysandri.domain.ports.out;

import java.util.List;

public interface RecuperacionPort {
    record CorreoPendiente(long id, String correo, String nombre, String enlaceCifrado) {}
    boolean guardarSolicitud(String correo, String tokenHash, String enlaceCifrado);
    boolean confirmar(String tokenHash, String passwordHash);
    List<CorreoPendiente> tomarPendientes();
    void registrarEnvio(long id, boolean enviado);
}
