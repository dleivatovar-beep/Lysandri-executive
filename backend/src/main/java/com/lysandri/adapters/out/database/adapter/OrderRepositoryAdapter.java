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

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository repository;
    private final ProgramRepositoryAdapter programAdapter;

    @Override
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
    public Optional<Orden> buscarPorStripeSessionId(String stripeSessionId) {
        return repository.findByStripeSessionId(stripeSessionId).map(this::toDomain);
    }

    @Override
    public List<Orden> buscarPorUsuario(Long idUsuario) {
        return repository.findByIdUserOrderByFechaOrdenDesc(idUsuario).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Orden> listarTodas() {
        return repository.findAllByOrderByFechaOrdenDesc().stream().map(this::toDomain).toList();
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
                .metodoPago(d.getMetodoPago() != null ? d.getMetodoPago() : "STRIPE")
                .stripeSessionId(d.getStripeSessionId())
                .stripePaymentIntentId(d.getStripePaymentIntentId())
                .moodleMatriculaSincronizada(d.isMoodleMatriculaSincronizada())
                .fechaPago(d.getFechaPago())
                .tipoComprobanteSolicitado(d.getTipoComprobanteSolicitado())
                .numeroDocumentoCliente(d.getNumeroDocumentoCliente())
                .nombreFacturacion(d.getNombreFacturacion())
                .build();

        if (d.getItems() != null) {
            List<DetalleOrdenEntity> itemEntities = new ArrayList<>();
            for (DetalleOrden item : d.getItems()) {
                itemEntities.add(DetalleOrdenEntity.builder()
                        .idDetalle(item.getIdDetalle())
                        .idOrden(d.getIdOrden())
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
                .moodleMatriculaSincronizada(e.isMoodleMatriculaSincronizada())
                .fechaPago(e.getFechaPago())
                .tipoComprobanteSolicitado(e.getTipoComprobanteSolicitado())
                .numeroDocumentoCliente(e.getNumeroDocumentoCliente())
                .nombreFacturacion(e.getNombreFacturacion())
                .items(items)
                .build();
    }
}
