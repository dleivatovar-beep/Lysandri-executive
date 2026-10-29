package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.DetalleOrdenEntity;
import com.lysandri.adapters.out.database.entity.OrdenEntity;
import com.lysandri.adapters.out.database.repository.SpringDataOrderRepository;
import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository repository;
    private final ProgramRepositoryAdapter programAdapter;

    @Override
    @Transactional
    public Orden guardar(Orden orden) {
        OrdenEntity entity = toEntity(orden);
        OrdenEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Orden> buscarPorId(Long idOrden) {
        return repository.findById(idOrden).map(this::toDomain);
    }

    @Override
    public Optional<Orden> buscarPorCodigo(String codigoOrden) {
        return repository.findByCodigoOrden(codigoOrden).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<Orden> buscarPorCodigoConBloqueo(String codigoOrden) {
        return repository.findByCodigoOrdenWithLock(codigoOrden).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<Orden> buscarPorIdConBloqueo(Long idOrden) {
        return repository.findByIdWithLock(idOrden).map(this::toDomain);
    }

    @Override
    public Optional<Orden> buscarPorStripeSessionId(String stripeSessionId) {
        return repository.findByStripeSessionId(stripeSessionId).map(this::toDomain);
    }

    @Override
    public Optional<Orden> buscarPorStripePaymentIntentId(String stripePaymentIntentId) {
        return repository.findByStripePaymentIntentId(stripePaymentIntentId).map(this::toDomain);
    }

    @Override
    public Optional<Orden> buscarPorIzipayTransactionId(String izipayTransactionId) {
        return repository.findByIzipayTransactionId(izipayTransactionId).map(this::toDomain);
    }

    @Override
    public Optional<Orden> buscarPorClaveIdempotencia(String claveIdempotencia) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank()) return Optional.empty();
        return repository.findByClaveIdempotencia(claveIdempotencia.trim()).map(this::toDomain);
    }

    @Override
    public Optional<Orden> buscarPorCualquierIdentificador(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        // 1. Buscar por izipayTransactionId
        Optional<Orden> orden = repository.findByIzipayTransactionId(id).map(this::toDomain);
        if (orden.isPresent()) return orden;

        // 2. Buscar por izipayOrderId
        orden = repository.findByIzipayOrderId(id).map(this::toDomain);
        if (orden.isPresent()) return orden;

        // 3. Buscar por codigoOrden
        orden = repository.findByCodigoOrden(id).map(this::toDomain);
        if (orden.isPresent()) return orden;

        // 4. Buscar por stripeSessionId
        orden = repository.findByStripeSessionId(id).map(this::toDomain);
        if (orden.isPresent()) return orden;

        // 5. Buscar por stripePaymentIntentId
        return repository.findByStripePaymentIntentId(id).map(this::toDomain);
    }

    @Override
    public Optional<Orden> buscarPorAbonoBancario(String bancoDestino, String cuentaDestino, String numeroOperacion) {
        if (bancoDestino == null || cuentaDestino == null || numeroOperacion == null) return Optional.empty();
        List<OrdenEntity> matches = repository.findAbonosBancariosPagados(
                bancoDestino.trim(),
                cuentaDestino.trim(),
                numeroOperacion.trim()
        );
        return matches.isEmpty() ? Optional.empty() : Optional.of(toDomain(matches.get(0)));
    }

    @Override
    public List<Orden> buscarAbonosHistoricosPorOperacion(String numeroOperacionNormalizado) {
        if (numeroOperacionNormalizado == null || numeroOperacionNormalizado.isBlank()) {
            return List.of();
        }
        return repository.findAbonosHistoricosCandidatos(numeroOperacionNormalizado.trim().toUpperCase())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Orden> buscarPorUsuario(Long idUsuario) {
        return repository.findByIdUserOrderByFechaOrdenDesc(idUsuario).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Orden> listarTodas() {
        return repository.findAllByOrderByFechaOrdenDesc().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Orden> listarPorEstado(com.lysandri.domain.model.EstadoOrden estado) {
        return repository.findByEstadoOrdenOrderByFechaOrdenDesc(estado).stream().map(this::toDomain).toList();
    }

    private OrdenEntity toEntity(Orden d) {
        OrdenEntity entity = OrdenEntity.builder()
                .idOrden(d.getIdOrden())
                .idUser(d.getIdUser())
                .codigoOrden(d.getCodigoOrden())
                .fechaOrden(d.getFechaOrden())
                .estadoOrden(d.getEstadoOrden())
                .total(d.getTotal())
                .moneda(d.getMoneda() != null ? d.getMoneda() : "USD")
                .metodoPago(d.getMetodoPago() != null ? d.getMetodoPago() : "IZIPAY")
                .stripeSessionId(d.getStripeSessionId())
                .stripePaymentIntentId(d.getStripePaymentIntentId())
                .izipayTransactionId(d.getIzipayTransactionId())
                .izipayOrderId(d.getIzipayOrderId())
                .izipayUuid(d.getIzipayUuid())
                .moodleMatriculaSincronizada(d.isMoodleMatriculaSincronizada())
                .fechaPago(d.getFechaPago())
                .tipoComprobanteSolicitado(d.getTipoComprobanteSolicitado())
                .numeroDocumentoCliente(d.getNumeroDocumentoCliente())
                .nombreFacturacion(d.getNombreFacturacion())
                .numeroOperacion(d.getNumeroOperacion())
                .notasAdmin(d.getNotasAdmin())
                .claveIdempotencia(d.getClaveIdempotencia())
                .guestSessionHash(d.getGuestSessionHash())
                .bancoDestino(d.getBancoDestino())
                .cuentaDestino(d.getCuentaDestino())
                .montoVerificado(d.getMontoVerificado())
                .monedaVerificada(d.getMonedaVerificada())
                .fechaAbono(d.getFechaAbono())
                .aprobadoPor(d.getAprobadoPor())
                .fechaAprobacion(d.getFechaAprobacion())
                .observacionVerificacion(d.getObservacionVerificacion())
                .estadoAprovisionamiento(d.getEstadoAprovisionamiento())
                .aprovisionamientoLeaseToken(d.getAprovisionamientoLeaseToken())
                .aprovisionamientoLeaseExpiresAt(d.getAprovisionamientoLeaseExpiresAt())
                .aprovisionamientoIntentos(d.getAprovisionamientoIntentos())
                .aprovisionamientoUltimoError(d.getAprovisionamientoUltimoError())
                .build();

        if (d.getItems() != null) {
            List<DetalleOrdenEntity> itemEntities = new ArrayList<>();
            for (DetalleOrden item : d.getItems()) {
                itemEntities.add(DetalleOrdenEntity.builder()
                        .idDetalle(item.getIdDetalle())
                        .orden(entity)
                        .idPrograma(item.getIdPrograma())
                        .precioUnitario(item.getPrecioUnitario())
                        .cantidad(item.getCantidad())
                        .subtotal(item.getSubtotal())
                        .moodleMatriculado(item.isMoodleMatriculado())
                        .build());
            }
            entity.setItems(itemEntities);
        }

        return entity;
    }

    private Orden toDomain(OrdenEntity e) {
        List<DetalleOrden> items = new ArrayList<>();
        if (e.getItems() != null) {
            for (DetalleOrdenEntity item : e.getItems()) {
                items.add(DetalleOrden.builder()
                        .idDetalle(item.getIdDetalle())
                        .idOrden(e.getIdOrden())
                        .idPrograma(item.getIdPrograma())
                        .programa(item.getPrograma() != null ? programAdapter.toDomain(item.getPrograma()) : null)
                        .precioUnitario(item.getPrecioUnitario())
                        .cantidad(item.getCantidad())
                        .subtotal(item.getSubtotal())
                        .moodleMatriculado(item.isMoodleMatriculado())
                        .build());
            }
        }

        return Orden.builder()
                .idOrden(e.getIdOrden())
                .idUser(e.getIdUser())
                .codigoOrden(e.getCodigoOrden())
                .fechaOrden(e.getFechaOrden())
                .estadoOrden(e.getEstadoOrden())
                .total(e.getTotal())
                .moneda(e.getMoneda())
                .metodoPago(e.getMetodoPago())
                .stripeSessionId(e.getStripeSessionId())
                .stripePaymentIntentId(e.getStripePaymentIntentId())
                .izipayTransactionId(e.getIzipayTransactionId())
                .izipayOrderId(e.getIzipayOrderId())
                .izipayUuid(e.getIzipayUuid())
                .moodleMatriculaSincronizada(e.isMoodleMatriculaSincronizada())
                .fechaPago(e.getFechaPago())
                .tipoComprobanteSolicitado(e.getTipoComprobanteSolicitado())
                .numeroDocumentoCliente(e.getNumeroDocumentoCliente())
                .nombreFacturacion(e.getNombreFacturacion())
                .numeroOperacion(e.getNumeroOperacion())
                .notasAdmin(e.getNotasAdmin())
                .claveIdempotencia(e.getClaveIdempotencia())
                .guestSessionHash(e.getGuestSessionHash())
                .bancoDestino(e.getBancoDestino())
                .cuentaDestino(e.getCuentaDestino())
                .montoVerificado(e.getMontoVerificado())
                .monedaVerificada(e.getMonedaVerificada())
                .fechaAbono(e.getFechaAbono())
                .aprobadoPor(e.getAprobadoPor())
                .fechaAprobacion(e.getFechaAprobacion())
                .observacionVerificacion(e.getObservacionVerificacion())
                .estadoAprovisionamiento(e.getEstadoAprovisionamiento())
                .aprovisionamientoLeaseToken(e.getAprovisionamientoLeaseToken())
                .aprovisionamientoLeaseExpiresAt(e.getAprovisionamientoLeaseExpiresAt())
                .aprovisionamientoIntentos(e.getAprovisionamientoIntentos())
                .aprovisionamientoUltimoError(e.getAprovisionamientoUltimoError())
                .items(items)
                .build();
    }
}
