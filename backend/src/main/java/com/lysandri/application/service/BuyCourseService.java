package com.lysandri.application.service;

import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.in.MatriculaUseCase;
import com.lysandri.domain.ports.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyCourseService implements BuyCourseUseCase, MatriculaUseCase {

    private final OrderRepositoryPort orderRepository;
    private final UserRepositoryPort userRepository;
    private final ProgramRepositoryPort programRepository;
    private final PaymentPort paymentPort;
    private final LmsClientPort lmsClientPort;
    private final BillingPort billingPort;
    private final NotificationPort notificationPort;

    @Override
    @Transactional
    public CheckoutSessionResponse iniciarCompra(CreateOrderCommand command) {
        log.info("Iniciando checkout con programas: {}", command.programaIds());

        if (command.programaIds() == null || command.programaIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un programa para comprar");
        }

        Usuario usuario;
        if (command.idUsuario() != null) {
            usuario = userRepository.buscarPorId(command.idUsuario())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + command.idUsuario()));
        } else if (command.guestEmail() != null && !command.guestEmail().isBlank()) {
            // Flujo de compra como invitado (Guest checkout)
            String email = command.guestEmail().trim().toLowerCase();
            usuario = userRepository.buscarPorEmail(email).orElseGet(() -> {
                String fullName = command.guestNombre() != null && !command.guestNombre().isBlank()
                        ? command.guestNombre().trim()
                        : "Cliente Invitado";
                String[] parts = fullName.split(" ", 2);
                String nombres = parts[0];
                String apellidos = parts.length > 1 ? parts[1] : "Executive";

                Usuario nuevo = Usuario.builder()
                        .nombres(nombres)
                        .apellidos(apellidos)
                        .email(email)
                        .passw("$2a$10$7EqJtq98hPqEX7fNZaFWoO.83.R3yR/Xh7b.d8Zt.m6qDk6H/H9G6") // Password temporal cifrada
                        .rol(RolUsuario.CLIENTE)
                        .activo(true)
                        .fechaCreacion(OffsetDateTime.now())
                        .fechaActualizacion(OffsetDateTime.now())
                        .build();
                return userRepository.guardar(nuevo);
            });
        } else {
            throw new IllegalArgumentException("Debe proporcionar un ID de usuario o un correo electrónico para invitado");
        }

        List<Programa> programas = programRepository.buscarPorIds(command.programaIds());
        if (programas.size() != command.programaIds().size()) {
            throw new IllegalArgumentException("Uno o más programas seleccionados no existen o no están disponibles");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<DetalleOrden> items = new ArrayList<>();

        for (Programa prog : programas) {
            BigDecimal subtotal = prog.getPrecio().multiply(BigDecimal.valueOf(1));
            total = total.add(subtotal);

            items.add(DetalleOrden.builder()
                    .idPrograma(prog.getIdPrograma())
                    .programa(prog)
                    .precioUnitario(prog.getPrecio())
                    .cantidad(1)
                    .subtotal(subtotal)
                    .moodleMatriculado(false)
                    .build());
        }

        String codigoOrden = UUID.randomUUID().toString();
        String tipoComp = (command.tipoComprobante() != null && !command.tipoComprobante().isBlank())
                ? command.tipoComprobante().toUpperCase()
                : "BOLETA";
        String docCliente = command.numeroDocumento() != null ? command.numeroDocumento().trim() : null;
        String razonSocial = (command.nombreFacturacion() != null && !command.nombreFacturacion().isBlank())
                ? command.nombreFacturacion().trim()
                : usuario.getNombreCompleto();

        String metodoPago = (command.metodoPago() != null && !command.metodoPago().isBlank())
                ? command.metodoPago().toUpperCase()
                : "IZIPAY";

        Orden orden = Orden.builder()
                .idUser(usuario.getIdUser())
                .usuario(usuario)
                .codigoOrden(codigoOrden)
                .fechaOrden(OffsetDateTime.now())
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(total)
                .moneda("PEN")
                .metodoPago(metodoPago)
                .numeroOperacion(command.numeroOperacion())
                .tipoComprobanteSolicitado(tipoComp)
                .numeroDocumentoCliente(docCliente)
                .nombreFacturacion(razonSocial)
                .moodleMatriculaSincronizada(false)
                .items(items)
                .build();

        orden = orderRepository.guardar(orden);

        // Si es pago manual (Yape, Plin, Transferencia bancaria directa), no llamamos a la pasarela izipay
        boolean isOnlineGateway = "IZIPAY".equalsIgnoreCase(metodoPago)
                || "STRIPE".equalsIgnoreCase(metodoPago)
                || "TARJETA".equalsIgnoreCase(metodoPago)
                || "ONLINE".equalsIgnoreCase(metodoPago);

        if (!isOnlineGateway) {
            String localCheckoutUrl = "/checkout/success?session_id=" + codigoOrden + "&manual=true";
            log.info("Orden {} registrada con método manual {}. Esperando validación administrativa.", codigoOrden, metodoPago);
            return new CheckoutSessionResponse(
                    codigoOrden,
                    localCheckoutUrl,
                    codigoOrden,
                    localCheckoutUrl,
                    null,
                    null,
                    codigoOrden
            );
        }

        // Crear la sesión en la pasarela de pagos (izipay Online)
        PaymentPort.PaymentSessionResult sessionResult = paymentPort.crearSesionPago(
                orden,
                usuario.getEmail(),
                razonSocial
        );

        orden.setIzipayTransactionId(sessionResult.paymentIntentId());
        orden.setIzipayOrderId(sessionResult.sessionId());
        orden.setStripeSessionId(sessionResult.sessionId());
        orden.setStripePaymentIntentId(sessionResult.paymentIntentId());
        orderRepository.guardar(orden);

        log.info("Orden {} creada con éxito en izipay Online. Sesión/Tx: {}", codigoOrden, sessionResult.sessionId());

        return new CheckoutSessionResponse(
                codigoOrden,
                sessionResult.checkoutUrl(),
                sessionResult.sessionId(),
                sessionResult.checkoutUrl(),
                sessionResult.formToken(),
                sessionResult.publicKey(),
                sessionResult.sessionId()
        );
    }

    @Override
    @Transactional
    public Orden procesarPagoExitoso(String sessionIdOrTransactionId) {
        log.info("Procesando pago exitoso y aprovisionamiento para sesión/transacción: {}", sessionIdOrTransactionId);

        Orden orden = orderRepository.buscarPorCualquierIdentificador(sessionIdOrTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada para la sesión de pago: " + sessionIdOrTransactionId));

        if (orden.getEstadoOrden() == EstadoOrden.PAGADO && orden.isMoodleMatriculaSincronizada()) {
            log.info("La orden {} ya fue procesada y matriculada previamente. Idempotencia garantizada.", orden.getCodigoOrden());
            return orden;
        }

        return ejecutarAprovisionamientoYFacturacion(orden);
    }

    @Override
    @Transactional
    public Orden confirmarPagoYMatricular(String sessionIdOrTransactionId) {
        return procesarPagoExitoso(sessionIdOrTransactionId);
    }

    @Override
    @Transactional
    public Orden confirmarPagoManual(Long idOrden, ConfirmarPagoManualCommand command) {
        log.info("Confirmando pago manual para orden ID: {}", idOrden);

        Orden orden = orderRepository.buscarPorId(idOrden)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con ID: " + idOrden));

        if (orden.getEstadoOrden() == EstadoOrden.PAGADO && orden.isMoodleMatriculaSincronizada()) {
            log.info("La orden {} ya fue aprobada y matriculada previamente. Idempotencia garantizada.", orden.getCodigoOrden());
            return orden;
        }

        if (command != null) {
            if (command.numeroOperacion() != null && !command.numeroOperacion().isBlank()) {
                orden.setNumeroOperacion(command.numeroOperacion().trim());
            }
            if (command.metodoPago() != null && !command.metodoPago().isBlank()) {
                orden.setMetodoPago(command.metodoPago().trim().toUpperCase());
            }
            if (command.notasAdmin() != null && !command.notasAdmin().isBlank()) {
                orden.setNotasAdmin(command.notasAdmin().trim());
            }
        }

        return ejecutarAprovisionamientoYFacturacion(orden);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Orden> listarOrdenes(String estado, String search) {
        List<Orden> ordenes;
        if (estado != null && !estado.isBlank()) {
            try {
                EstadoOrden estadoOrden = EstadoOrden.valueOf(estado.trim().toUpperCase());
                ordenes = orderRepository.listarPorEstado(estadoOrden);
            } catch (IllegalArgumentException e) {
                ordenes = orderRepository.listarTodas();
            }
        } else {
            ordenes = orderRepository.listarTodas();
        }

        if (search != null && !search.trim().isBlank()) {
            String q = search.trim().toLowerCase();
            ordenes = ordenes.stream()
                    .filter(o -> (o.getCodigoOrden() != null && o.getCodigoOrden().toLowerCase().contains(q))
                            || (o.getNombreFacturacion() != null && o.getNombreFacturacion().toLowerCase().contains(q))
                            || (o.getNumeroDocumentoCliente() != null && o.getNumeroDocumentoCliente().contains(q))
                            || (o.getNumeroOperacion() != null && o.getNumeroOperacion().toLowerCase().contains(q))
                            || (o.getUsuario() != null && o.getUsuario().getEmail() != null && o.getUsuario().getEmail().toLowerCase().contains(q))
                    )
                    .toList();
        }

        return ordenes;
    }

    @Override
    @Transactional(readOnly = true)
    public Orden obtenerOrdenPorCodigo(String codigoOrden) {
        return orderRepository.buscarPorCodigo(codigoOrden)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada: " + codigoOrden));
    }

    /**
     * Orquestación central de aprovisionamiento en Moodle LMS, facturación electrónica y notificación.
     */
    private Orden ejecutarAprovisionamientoYFacturacion(Orden orden) {
        Usuario usuario = userRepository.buscarPorId(orden.getIdUser())
                .orElseThrow(() -> new IllegalStateException("Usuario asociado a la orden no existe"));

        String email = usuario.getEmail();
        String nombre = (orden.getNombreFacturacion() != null && !orden.getNombreFacturacion().isBlank())
                ? orden.getNombreFacturacion()
                : usuario.getNombreCompleto();
        String docCliente = orden.getNumeroDocumentoCliente();

        log.info("Datos comprador para emisión y accesos - Nombre: {}, Email: {}, Documento: {}", nombre, email, docCliente);

        // Generar clave temporal para el aula virtual
        String moodlePassword = generarPasswordSegura10Chars();

        // Crear o asociar usuario en Moodle
        Long moodleUserId = usuario.getMoodleUserId();
        if (moodleUserId == null) {
            moodleUserId = lmsClientPort.buscarUsuarioPorEmail(email).orElse(null);
        }

        if (moodleUserId == null) {
            String username = email.toLowerCase().replaceAll("[^a-z0-9._-]", "");
            moodleUserId = lmsClientPort.crearUsuario(
                    username,
                    moodlePassword,
                    usuario.getNombres(),
                    usuario.getApellidos(),
                    email
            );
            usuario.setMoodleUserId(moodleUserId);
            userRepository.guardar(usuario);
            log.info("Usuario creado en Moodle con ID: {}", moodleUserId);
        } else {
            log.info("Usuario ya existente en Moodle con ID: {}", moodleUserId);
        }

        // Matricular al estudiante en los cursos adquiridos
        StringBuilder cursoTitulos = new StringBuilder();
        for (DetalleOrden detalle : orden.getItems()) {
            Programa programa = detalle.getPrograma() != null ? detalle.getPrograma() :
                    programRepository.buscarPorId(detalle.getIdPrograma()).orElse(null);

            if (programa != null) {
                if (cursoTitulos.length() > 0) cursoTitulos.append(", ");
                cursoTitulos.append(programa.getTitulo());

                if (programa.getMoodleCourseId() != null) {
                    try {
                        lmsClientPort.matricularUsuarioEnCurso(moodleUserId, programa.getMoodleCourseId(), 5);
                        detalle.setMoodleMatriculado(true);
                        log.info("Usuario {} matriculado en curso Moodle {}", moodleUserId, programa.getMoodleCourseId());
                    } catch (Exception e) {
                        log.error("Fallo al matricular en curso Moodle {}: {}", programa.getMoodleCourseId(), e.getMessage());
                    }
                }
            }
        }

        // Emisión de comprobante de pago SUNAT
        ComprobantePago comprobante = billingPort.emitirComprobante(orden);

        // Actualizar estado de orden
        orden.setEstadoOrden(EstadoOrden.PAGADO);
        orden.setMoodleMatriculaSincronizada(true);
        orden.setFechaPago(OffsetDateTime.now());
        Orden ordenGuardada = orderRepository.guardar(orden);

        // Enviar accesos y factura por correo
        String cursoTitulo = cursoTitulos.length() > 0 ? cursoTitulos.toString() : "Programa Ejecutivo";
        notificationPort.enviarAccesosYFactura(email, nombre, cursoTitulo, moodlePassword, comprobante);

        log.info("Orden {} procesada con éxito. Comprobante {}-{} emitido y accesos enviados por email.",
                orden.getCodigoOrden(), comprobante.getSerie(), comprobante.getCorrelativo());

        return ordenGuardada;
    }

    /**
     * Genera una contraseña segura aleatoria de exactamente 10 caracteres,
     * cumpliendo la política de contraseñas de Moodle (mayúsculas, minúsculas, números y caracteres especiales).
     */
    private String generarPasswordSegura10Chars() {
        final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String LOWER = "abcdefghijkmnpqrstuvwxyz";
        final String DIGITS = "23456789";
        final String SYMBOLS = "!@#$*";
        final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

        SecureRandom random = new SecureRandom();
        List<Character> chars = new ArrayList<>();
        // Garantizar al menos un carácter de cada grupo obligatorio
        chars.add(UPPER.charAt(random.nextInt(UPPER.length())));
        chars.add(UPPER.charAt(random.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(random.nextInt(LOWER.length())));
        chars.add(LOWER.charAt(random.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        chars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        chars.add(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));

        while (chars.size() < 10) {
            chars.add(ALL.charAt(random.nextInt(ALL.length())));
        }

        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
