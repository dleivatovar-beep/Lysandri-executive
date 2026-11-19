package com.lysandri.domain.ports.out;
public interface ConsentimientoPort {
    void registrar(long usuario, String version);

    boolean existeParaUsuario(long usuario);
}
