package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.whatsapp.EstadoMensajeWhatsApp;
import com.lysandri.domain.model.whatsapp.MensajeWhatsApp;
import com.lysandri.domain.model.whatsapp.ReservaEnvioWhatsApp;
import com.lysandri.domain.model.whatsapp.ResultadoLlamadaWhatsApp;

import java.util.List;
import java.util.Optional;

public interface WhatsAppRepositoryPort {

    MensajeWhatsApp guardar(MensajeWhatsApp mensaje);

    boolean existePorIdProveedor(String idProveedor);

    Optional<MensajeWhatsApp> buscarPorIdProveedor(String idProveedor);

    Optional<MensajeWhatsApp> buscarPorIdSolicitud(String idSolicitud);

    ReservaEnvioWhatsApp reservarEnvio(String idSolicitud, String destinatario, String contenido, String operador);

    MensajeWhatsApp actualizarResultadoEnvio(String idSolicitud, String tokenIntento, ResultadoLlamadaWhatsApp resultado);

    MensajeWhatsApp registrarOObtenerIntencion(String idSolicitud, String destinatario, String contenido, String operador);

    void actualizarEstado(String idProveedor, EstadoMensajeWhatsApp nuevoEstado, String errorDetalle);

    void actualizarEstadoConReconciliacion(String idProveedor, String idSolicitud, EstadoMensajeWhatsApp nuevoEstado, String errorDetalle);

    void recuperarSolicitudesInciertasTrasReinicio();

    List<MensajeWhatsApp> listarPorTelefono(String telefono);
}
