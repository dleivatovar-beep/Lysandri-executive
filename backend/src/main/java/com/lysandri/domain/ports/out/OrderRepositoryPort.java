package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.Orden;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {

    Orden guardar(Orden orden);

    Optional<Orden> buscarPorId(Long idOrden);

    Optional<Orden> buscarPorCodigo(String codigoOrden);

    Optional<Orden> buscarPorStripeSessionId(String stripeSessionId);

    List<Orden> buscarPorUsuario(Long idUsuario);

    List<Orden> listarTodas();
}
