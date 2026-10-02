package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.ComprobantePago;
import com.lysandri.domain.model.Orden;

import java.util.List;
import java.util.Optional;

public interface BillingPort {

    /**
     * Emite un comprobante de pago tributario (Boleta o Factura) para la orden pagada.
     * Calcula base imponible, IGV (18%), genera serie y correlativo secuencial, y persiste el comprobante.
     *
     * @param orden Orden de compra confirmada y pagada.
     * @return ComprobantePago emitido y persistido.
     */
    ComprobantePago emitirComprobante(Orden orden);

    /**
     * Lista todos los comprobantes de pago emitidos ordenados cronológicamente descendente.
     */
    List<ComprobantePago> listarComprobantes();

    /**
     * Busca el comprobante de pago asociado a una orden específica.
     */
    Optional<ComprobantePago> buscarPorOrdenId(Long idOrden);
}
