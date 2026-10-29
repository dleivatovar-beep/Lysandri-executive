package com.lysandri.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.model.whatsapp.*;
import com.lysandri.domain.ports.in.WhatsAppUseCase;
import com.lysandri.domain.ports.out.WhatsAppCloudClientPort;
import com.lysandri.domain.ports.out.WhatsAppRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService implements WhatsAppUseCase {

    private final WhatsAppCloudClientPort cloudClientPort;
    private final WhatsAppRepositoryPort repositoryPort;
    private final ObjectMapper objectMapper;

    @Override
    public String verificarWebhook(String mode, String token, String challenge) {
        log.info("Verificación de webhook de WhatsApp recibida (mode: {})", mode);

        if (!"subscribe".equalsIgnoreCase(mode)) {
            log.warn("Modo de verificación de webhook desconocido: {}", mode);
            throw new AccessDeniedException("Modo de suscripción inválido");
        }

        if (!cloudClientPort.validarTokenVerificacion(token)) {
            log.warn("Fallo de verificación de webhook: el token proporcionado no coincide con el verify-token configurado");
            throw new AccessDeniedException("Token de verificación de webhook de WhatsApp inválido");
        }

        log.info("Handshake de webhook de WhatsApp verificado exitosamente");
        return challenge != null ? challenge : "";
    }

    @Override
    @Transactional
    public void procesarWebhook(String rawPayload, String signatureHeader) {
        if (!cloudClientPort.validarFirmaWebhook(rawPayload, signatureHeader)) {
            log.warn("Rechazo de webhook de WhatsApp: firma HMAC-SHA256 ausente o inválida");
            throw new AccessDeniedException("Firma de webhook de WhatsApp inválida");
        }

        if (rawPayload == null || rawPayload.isBlank()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode entryArray = root.path("entry");
            if (!entryArray.isArray()) {
                return;
            }

            for (JsonNode entry : entryArray) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) continue;

                for (JsonNode change : changes) {
                    JsonNode value = change.path("value");

                    // 1. Procesar mensajes entrantes de clientes
                    JsonNode messages = value.path("messages");
                    if (messages.isArray()) {
                        for (JsonNode msg : messages) {
                            procesarMensajeEntrante(msg);
                        }
                    }

                    // 2. Procesar actualizaciones de estado de entrega de mensajes
                    JsonNode statuses = value.path("statuses");
                    if (statuses.isArray()) {
                        for (JsonNode st : statuses) {
                            procesarActualizacionEstado(st);
                        }
                    }
                }
            }
        } catch (AccessDeniedException ade) {
            throw ade;
        } catch (Exception ex) {
            log.error("Error al procesar notificación de webhook de WhatsApp: {}", ex.getMessage(), ex);
            throw new IllegalArgumentException("Payload de webhook mal formado", ex);
        }
    }

    private void procesarMensajeEntrante(JsonNode msg) {
        String wamid = msg.path("id").asText();
        if (wamid == null || wamid.isBlank()) {
            return;
        }

        // Deduplicación idempotente: si ya fue procesado, no repetir
        if (repositoryPort.existePorIdProveedor(wamid)) {
            log.info("Evento de mensaje entrante duplicado ignorado [wamid: {}]", wamid);
            return;
        }

        String from = msg.path("from").asText();
        String type = msg.path("type").asText("text");
        String textBody = "";

        TipoMensajeWhatsApp tipoMensaje = TipoMensajeWhatsApp.TEXTO;
        if ("text".equalsIgnoreCase(type)) {
            textBody = msg.path("text").path("body").asText();
        } else if ("reaction".equalsIgnoreCase(type)) {
            tipoMensaje = TipoMensajeWhatsApp.REACCION;
            textBody = msg.path("reaction").path("emoji").asText();
        } else {
            tipoMensaje = TipoMensajeWhatsApp.OTRO;
            textBody = "[" + type + "]";
        }

        long timestampEpoch = msg.path("timestamp").asLong(Instant.now().getEpochSecond());
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestampEpoch), ZoneOffset.UTC);

        MensajeWhatsApp mensaje = MensajeWhatsApp.builder()
                .idProveedor(wamid)
                .telefonoRemitente(from)
                .telefonoDestinatario("LYSANDRI_OFFICIAL")
                .direccion(DireccionMensajeWhatsApp.ENTRANTE)
                .contenido(textBody)
                .tipo(tipoMensaje)
                .estado(EstadoMensajeWhatsApp.RECIBIDO)
                .timestampMensaje(timestamp)
                .operador(null)
                .errorDetalle(null)
                .build();

        repositoryPort.guardar(mensaje);
        log.info("Mensaje WhatsApp entrante procesado y guardado desde {} [wamid: {}]",
                MensajeWhatsApp.enmascararTelefono(from), wamid);
    }

    private void procesarActualizacionEstado(JsonNode st) {
        String wamid = st.path("id").asText();
        String statusText = st.path("status").asText();
        if (wamid == null || wamid.isBlank() || statusText == null || statusText.isBlank()) {
            return;
        }

        EstadoMensajeWhatsApp nuevoEstado;
        switch (statusText.toLowerCase()) {
            case "sent" -> nuevoEstado = EstadoMensajeWhatsApp.ENVIADO;
            case "delivered" -> nuevoEstado = EstadoMensajeWhatsApp.ENTREGADO;
            case "read" -> nuevoEstado = EstadoMensajeWhatsApp.LEIDO;
            case "failed" -> nuevoEstado = EstadoMensajeWhatsApp.FALLIDO;
            default -> {
                log.info("Estado de WhatsApp no reconocido: {}", statusText);
                return;
            }
        }

        String errorDetalle = null;
        JsonNode errors = st.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            errorDetalle = errors.get(0).path("title").asText(errors.get(0).path("message").asText());
        }

        String bizOpaqueData = st.path("biz_opaque_callback_data").asText(null);

        repositoryPort.actualizarEstadoConReconciliacion(wamid, bizOpaqueData, nuevoEstado, errorDetalle);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alIniciarAplicacion() {
        try {
            repositoryPort.recuperarSolicitudesInciertasTrasReinicio();
        } catch (Exception e) {
            log.warn("No se pudo ejecutar reconciliación de inicio para WhatsApp: {}", e.getMessage());
        }
    }

    @Override
    public MensajeWhatsApp enviarMensajeOperador(String destinatario, String texto, String operador) {
        return enviarMensajeOperador(destinatario, texto, operador, null);
    }

    @Override
    public MensajeWhatsApp enviarMensajeOperador(String destinatario, String texto, String operador, String idSolicitud) {
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("El número de teléfono de destino es requerido");
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El contenido del mensaje no puede estar vacío");
        }

        String solicitudId = (idSolicitud != null && !idSolicitud.isBlank())
                ? idSolicitud.trim()
                : UUID.randomUUID().toString();

        // 1. Reserva atómica en base de datos: autoriza a un único ejecutor y comprueba correspondencia
        ReservaEnvioWhatsApp reserva = repositoryPort.reservarEnvio(
                solicitudId,
                destinatario.trim(),
                texto,
                operador != null ? operador : "sistema"
        );

        // Si no está autorizado para el envío (porque otro hilo lo procesó o está en curso o es repetido):
        if (!reserva.autorizadoParaEnvio()) {
            log.info("Solicitud WhatsApp {} no autorizada para nuevo despacho de red (estado: {}). Retornando estado actual.",
                    solicitudId, reserva.mensaje().getEstado());
            return reserva.mensaje();
        }

        // 2. Operación de red fuera de cualquier transacción de base de datos
        log.info("Operador {} enviando mensaje a WhatsApp destinatario {} [solicitud: {}, tokenIntento: {}]",
                operador != null ? operador : "sistema",
                MensajeWhatsApp.enmascararTelefono(destinatario),
                solicitudId,
                reserva.tokenIntento() != null ? reserva.tokenIntento().substring(0, Math.min(8, reserva.tokenIntento().length())) : "n/a");

        ResultadoLlamadaWhatsApp resultadoRed;
        try {
            resultadoRed = cloudClientPort.enviarMensajeTexto(destinatario, texto, solicitudId);
        } catch (Exception ex) {
            log.error("Excepción inesperada durante llamada de red para solicitud {}: {}", solicitudId, ex.getMessage());
            resultadoRed = ResultadoLlamadaWhatsApp.incierto("Fallo durante invocación a proveedor: " + ex.getMessage());
        }

        // 3. Persistir el resultado obtenido validando propiedad del token de intento
        try {
            return repositoryPort.actualizarResultadoEnvio(solicitudId, reserva.tokenIntento(), resultadoRed);
        } catch (Exception ex) {
            log.error("Error crítico de persistencia al guardar resultado de envío para solicitud {}. " +
                            "La solicitud permanece en estado reservado y no se reenviará automáticamente a la red: {}",
                    solicitudId, ex.getMessage());
            return reserva.mensaje();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeWhatsApp> consultarConversacion(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return List.of();
        }
        return repositoryPort.listarPorTelefono(telefono.trim());
    }
}
