package com.lysandri.domain.ports.out;
import java.util.List;
public interface CuentaPort {
 record Aviso(long id, String correo, String tipo) {}
 boolean cambiarPassword(long usuario, String hashAnterior, String hashNuevo);
 List<Aviso> tomarAvisos();
 void confirmarAviso(long id, boolean enviado);
}
