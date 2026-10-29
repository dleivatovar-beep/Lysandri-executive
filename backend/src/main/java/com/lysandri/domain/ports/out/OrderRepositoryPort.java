package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.Orden;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {

    Orden guardar(Orden orden);

    Optional<Orden> buscarPorId(Long idOrden);

    Optional<Orden> buscarPorCodigo(String codigoOrden);

    Optional<Orden> buscarPorCodigoConBloqueo(String codigoOrden);

    Optional<Orden> buscarPorIdConBloqueo(Long idOrden);

    Optional<Orden> buscarPorStripeSessionId(String stripeSessionId);

    Optional<Orden> buscarPorStripePaymentIntentId(String stripePaymentIntentId);

    Optional<Orden> buscarPorIzipayTransactionId(String izipayTransactionId);

    Optional<Orden> buscarPorCualquierIdentificador(String id);

    Optional<Orden> buscarPorClaveIdempotencia(String claveIdempotencia);

    Optional<Orden> buscarPorAbonoBancario(String bancoDestino, String cuentaDestino, String numeroOperacion);

    List<Orden> buscarAbonosHistoricosPorOperacion(String numeroOperacionNormalizado);

    List<Orden> buscarPorUsuario(Long idUsuario);

    List<Orden> listarTodas();

    List<Orden> listarPorEstado(com.lysandri.domain.model.EstadoOrden estado);
}
