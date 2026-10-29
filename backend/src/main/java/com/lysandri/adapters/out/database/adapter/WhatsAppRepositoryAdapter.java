package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.WhatsAppMensajeEntity;
import com.lysandri.adapters.out.database.repository.SpringDataWhatsAppRepository;
import com.lysandri.domain.model.whatsapp.*;
import com.lysandri.domain.ports.out.WhatsAppRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppRepositoryAdapter implements WhatsAppRepositoryPort {

    private final SpringDataWhatsAppRepository repository;

    @Override
    @Transactional
    public MensajeWhatsApp guardar(MensajeWhatsApp domain) {
        WhatsAppMensajeEntity entity = toEntity(domain);
        WhatsAppMensajeEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existePorIdProveedor(String idProveedor) {
        if (idProveedor == null || idProveedor.isBlank()) {
            return false;
        }
        return repository.existsByIdProveedor(idProveedor);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MensajeWhatsApp> buscarPorIdProveedor(String idProveedor) {
        if (idProveedor == null || idProveedor.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdProveedor(idProveedor).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MensajeWhatsApp> buscarPorIdSolicitud(String idSolicitud) {
        if (idSolicitud == null || idSolicitud.isBlank()) {
            return Optional.empty();
        }
        return repository.findByIdSolicitud(idSolicitud.trim()).map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservaEnvioWhatsApp reservarEnvio(String idSolicitud, String destinatario, String contenido, String operador) {
        String idSol = idSolicitud.trim();
        String tel = sanitizarTelefono(destinatario);
        String txt = contenido != null ? contenido : "";

        // 1. Inserción atómica en PostgreSQL compatible con concurrencia (ON CONFLICT DO NOTHING no aborta la transacción)
        repository.insertarIntencionSiNoExiste(idSol, tel, txt, operador != null ? operador : "sistema");

        // 2. Bloqueo pesimista exclusivo sobre la fila para autorizar a un único ejecutor
        WhatsAppMensajeEntity entity = repository.findByIdSolicitudForUpdate(idSol)
                .orElseThrow(() -> new IllegalStateException("Fallo al recuperar la intención de envío para: " + idSol));

        // 3. Verificación estricta de correspondencia de datos: misma solicitud debe tener mismo destinatario y contenido
        String telRegistrado = sanitizarTelefono(entity.getTelefonoDestinatario());
        if (!telRegistrado.equals(tel) || !entity.getContenido().equals(txt)) {
            log.warn("Rechazo de solicitud WhatsApp {}: intento de reutilizar idSolicitud con datos diferentes.", idSol);
            throw new IllegalArgumentException("La identificación de solicitud '" + idSol + "' ya fue registrada previamente con un destinatario o contenido diferente.");
        }

        // 4. Si ya fue procesado o se encuentra en estado definitivo/incierto, no se autoriza envío a la red
        if (entity.getEstado() == EstadoMensajeWhatsApp.ENVIADO
                || entity.getEstado() == EstadoMensajeWhatsApp.ENTREGADO
                || entity.getEstado() == EstadoMensajeWhatsApp.LEIDO
                || entity.getEstado() == EstadoMensajeWhatsApp.FALLIDO
                || entity.getEstado() == EstadoMensajeWhatsApp.RESULTADO_INCIERTO) {
            log.info("Solicitud WhatsApp {} ya finalizada o incierta (estado: {}). No autoriza despacho redundante.", idSol, entity.getEstado());
            return new ReservaEnvioWhatsApp(toDomain(entity), false, null);
        }

        // 5. Si ya se encuentra en proceso por otro hilo activo
        if (entity.getEstado() == EstadoMensajeWhatsApp.EN_PROCESO_ENVIO) {
            log.info("Solicitud WhatsApp {} ya está en proceso de despacho por otro ejecutor. No autoriza envío simultáneo.", idSol);
            return new ReservaEnvioWhatsApp(toDomain(entity), false, null);
        }

        // 6. Adquisición exclusiva del lease de despacho
        String tokenIntento = UUID.randomUUID().toString();
        entity.setEstado(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO);
        entity.setTokenIntento(tokenIntento);
        entity.setIntentoCount((entity.getIntentoCount() != null ? entity.getIntentoCount() : 0) + 1);
        entity.setBloqueadoHasta(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        WhatsAppMensajeEntity reservada = repository.saveAndFlush(entity);
        log.info("Lease de envío WhatsApp adquirido exclusivamente para solicitud {} [tokenIntento: {}, intento: {}]",
                idSol, tokenIntento.substring(0, 8), reservada.getIntentoCount());
        return new ReservaEnvioWhatsApp(toDomain(reservada), true, tokenIntento);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MensajeWhatsApp actualizarResultadoEnvio(String idSolicitud, String tokenIntento, ResultadoLlamadaWhatsApp resultado) {
        if (idSolicitud == null || idSolicitud.isBlank()) {
            throw new IllegalArgumentException("El identificador de solicitud es requerido");
        }
        if (resultado == null) {
            throw new IllegalArgumentException("El resultado de llamada es requerido");
        }

        String idSol = idSolicitud.trim();
        WhatsAppMensajeEntity entity = repository.findByIdSolicitudForUpdate(idSol)
                .orElseThrow(() -> new IllegalStateException("No se encontró la intención registrada para solicitud: " + idSol));

        // 1. Exigir token de intento recibido no vacío
        if (tokenIntento == null || tokenIntento.isBlank()) {
            log.warn("Rechazo de actualización en solicitud {}: token recibido nulo o vacío. No se altera el registro.", idSol);
            return toDomain(entity);
        }
        String tokenLimpio = tokenIntento.trim();

        // 2. Si no existe token activo en BD (por ejemplo, liberado tras confirmación previa por webhook)
        if (entity.getTokenIntento() == null || entity.getTokenIntento().isBlank()) {
            log.warn("Rechazo de actualización para solicitud {}: no existe token de intento activo registrado en BD (posible confirmación previa por webhook). Estado actual: {}. No se altera el registro.",
                    idSol, entity.getEstado());
            return toDomain(entity);
        }

        // 3. Comprobación estricta de pertenencia del token de intento
        if (!tokenLimpio.equals(entity.getTokenIntento())) {
            log.warn("Conflicto de propiedad en solicitud {}: token recibido '{}' difiere del token activo registrado '{}'. No se altera el registro.",
                    idSol, tokenLimpio, entity.getTokenIntento());
            return toDomain(entity);
        }

        // 4. Idempotencia: si ya se encuentra en un estado finalizado por este mismo intento
        if (entity.getEstado() != EstadoMensajeWhatsApp.EN_PROCESO_ENVIO) {
            log.info("Llamada repetida con mismo token para solicitud {}: estado actual es {}. Operación idempotente sin mutación.",
                    idSol, entity.getEstado());
            return toDomain(entity);
        }

        // 5. Aplicar transición según resultado obtenido de la llamada a la red
        switch (resultado.tipo()) {
            case ACEPTADO -> {
                entity.setEstado(EstadoMensajeWhatsApp.ENVIADO);
                entity.setIdProveedor(resultado.wamid());
                entity.setErrorDetalle(null);
                log.info("Envío confirmado por proveedor [solicitud: {}, wamid: {}]", idSol, resultado.wamid());
            }
            case FALLIDO_CONFIRMADO -> {
                entity.setEstado(EstadoMensajeWhatsApp.FALLIDO);
                entity.setErrorDetalle(resultado.errorDetalle());
                log.warn("Fallo confirmado por proveedor [solicitud: {}]: {}", idSol, resultado.errorDetalle());
            }
            case RESULTADO_INCIERTO -> {
                entity.setEstado(EstadoMensajeWhatsApp.RESULTADO_INCIERTO);
                entity.setErrorDetalle(resultado.errorDetalle());
                log.warn("Resultado de envío incierto [solicitud: {}]: {}", idSol, resultado.errorDetalle());
            }
        }

        // Liberar el lease de tiempo para que no expire innecesariamente; se conserva tokenIntento para verificaciones idempotentes
        entity.setBloqueadoHasta(null);
        WhatsAppMensajeEntity actualizado = repository.saveAndFlush(entity);
        return toDomain(actualizado);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MensajeWhatsApp registrarOObtenerIntencion(String idSolicitud, String destinatario, String contenido, String operador) {
        ReservaEnvioWhatsApp reserva = reservarEnvio(idSolicitud, destinatario, contenido, operador);
        return reserva.mensaje();
    }

    @Override
    @Transactional
    public void actualizarEstado(String idProveedor, EstadoMensajeWhatsApp nuevoEstado, String errorDetalle) {
        actualizarEstadoConReconciliacion(idProveedor, null, nuevoEstado, errorDetalle);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void actualizarEstadoConReconciliacion(String idProveedor, String idSolicitud, EstadoMensajeWhatsApp nuevoEstado, String errorDetalle) {
        Optional<WhatsAppMensajeEntity> entityOpt = Optional.empty();
        if (idProveedor != null && !idProveedor.isBlank()) {
            entityOpt = repository.findByIdProveedorForUpdate(idProveedor.trim());
        }
        if (entityOpt.isEmpty() && idSolicitud != null && !idSolicitud.isBlank()) {
            entityOpt = repository.findByIdSolicitudForUpdate(idSolicitud.trim());
        }

        if (entityOpt.isEmpty()) {
            log.info("Notificación de estado para mensaje WhatsApp no registrado localmente [wamid: {}, solicitud: {}]",
                    idProveedor, idSolicitud);
            return;
        }

        WhatsAppMensajeEntity entity = entityOpt.get();

        // Conservar correlación del mensaje y evitar reemplazar un identificador de proveedor por otro incompatible
        if (idProveedor != null && !idProveedor.isBlank()) {
            String wamidEntrante = idProveedor.trim();
            if (entity.getIdProveedor() == null || entity.getIdProveedor().isBlank()) {
                entity.setIdProveedor(wamidEntrante);
            } else if (!entity.getIdProveedor().equals(wamidEntrante)) {
                log.warn("Conflicto de correlación en webhook: el mensaje registrado tiene idProveedor '{}', incompatible con '{}' entrante para solicitud '{}'. Se ignora la actualización.",
                        entity.getIdProveedor(), wamidEntrante, entity.getIdSolicitud());
                return;
            }
        }

        // Evaluar la regla de transición con el estado vigente bajo bloqueo pesimista
        if (entity.getEstado().puedeTransicionarA(nuevoEstado)) {
            EstadoMensajeWhatsApp previo = entity.getEstado();
            entity.setEstado(nuevoEstado);
            if (errorDetalle != null && !errorDetalle.isBlank()) {
                entity.setErrorDetalle(errorDetalle);
            }
            if (nuevoEstado == EstadoMensajeWhatsApp.ENVIADO || nuevoEstado == EstadoMensajeWhatsApp.ENTREGADO || nuevoEstado == EstadoMensajeWhatsApp.LEIDO) {
                entity.setBloqueadoHasta(null);
                entity.setTokenIntento(null);
            }
            repository.saveAndFlush(entity);
            log.info("Estado de mensaje WhatsApp conciliado exitosamente [id: {}, previo: {}, nuevo: {}]",
                    entity.getId(), previo, nuevoEstado);
        } else {
            log.info("Actualización de estado ignorada por regla de precedencia [id: {}, actual: {}, entrante: {}]",
                    entity.getId(), entity.getEstado(), nuevoEstado);
        }
    }

    @Override
    @Transactional
    public void recuperarSolicitudesInciertasTrasReinicio() {
        int actualizados = repository.reconciliarEnProcesoTrasReinicio();
        if (actualizados > 0) {
            log.warn("Recuperación tras reinicio: {} mensajes en estado EN_PROCESO_ENVIO fueron marcados conservadoramente como RESULTADO_INCIERTO",
                    actualizados);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeWhatsApp> listarPorTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return List.of();
        }
        return repository.findAllByTelefono(sanitizarTelefono(telefono))
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private String sanitizarTelefono(String telefono) {
        if (telefono == null) return "";
        return telefono.replaceAll("[^0-9]", "");
    }

    private WhatsAppMensajeEntity toEntity(MensajeWhatsApp d) {
        return WhatsAppMensajeEntity.builder()
                .id(d.getId())
                .idProveedor(d.getIdProveedor())
                .idSolicitud(d.getIdSolicitud())
                .telefonoRemitente(d.getTelefonoRemitente())
                .telefonoDestinatario(d.getTelefonoDestinatario())
                .direccion(d.getDireccion())
                .contenido(d.getContenido())
                .tipo(d.getTipo())
                .estado(d.getEstado())
                .timestampMensaje(d.getTimestampMensaje())
                .operador(d.getOperador())
                .tokenIntento(d.getTokenIntento())
                .bloqueadoHasta(d.getBloqueadoHasta())
                .intentoCount(d.getIntentoCount() != null ? d.getIntentoCount() : 0)
                .errorDetalle(d.getErrorDetalle())
                .fechaCreacion(d.getFechaCreacion())
                .build();
    }

    private MensajeWhatsApp toDomain(WhatsAppMensajeEntity e) {
        return MensajeWhatsApp.builder()
                .id(e.getId())
                .idProveedor(e.getIdProveedor())
                .idSolicitud(e.getIdSolicitud())
                .telefonoRemitente(e.getTelefonoRemitente())
                .telefonoDestinatario(e.getTelefonoDestinatario())
                .direccion(e.getDireccion())
                .contenido(e.getContenido())
                .tipo(e.getTipo())
                .estado(e.getEstado())
                .timestampMensaje(e.getTimestampMensaje())
                .operador(e.getOperador())
                .tokenIntento(e.getTokenIntento())
                .bloqueadoHasta(e.getBloqueadoHasta())
                .intentoCount(e.getIntentoCount() != null ? e.getIntentoCount() : 0)
                .errorDetalle(e.getErrorDetalle())
                .fechaCreacion(e.getFechaCreacion())
                .build();
    }
}
