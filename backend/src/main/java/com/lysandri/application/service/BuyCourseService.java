package com.lysandri.application.service;

import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.in.MatriculaUseCase;
import com.lysandri.domain.ports.out.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
public class BuyCourseService implements BuyCourseUseCase, MatriculaUseCase {

    private final OrderRepositoryPort orderRepository;
    private final UserRepositoryPort userRepository;
    private final ProgramRepositoryPort programRepository;
    private final PaymentPort izipayPaymentPort;
    private final PaymentPort stripePaymentPort;
    private final LmsClientPort lmsClientPort;
    private final BillingPort billingPort;
    private final NotificationPort notificationPort;
    private final OrderFinancialTransactionService orderFinancialTransactionService;
    private final UserActivationPort userActivationPort;
    private final PasswordEncoder passwordEncoder;
    private final ActivationOutboxService activationOutboxService;
    private final com.lysandri.security.JwtService jwtService;
    private final String frontendUrl;

    public BuyCourseService(
            OrderRepositoryPort orderRepository,
            UserRepositoryPort userRepository,
            ProgramRepositoryPort programRepository,
            @Qualifier("izipayPaymentAdapter") PaymentPort izipayPaymentPort,
            @Qualifier("stripePaymentAdapter") PaymentPort stripePaymentPort,
            LmsClientPort lmsClientPort,
            BillingPort billingPort,
            NotificationPort notificationPort,
            OrderFinancialTransactionService orderFinancialTransactionService,
            UserActivationPort userActivationPort,
            PasswordEncoder passwordEncoder
    ) {
        this(orderRepository, userRepository, programRepository, izipayPaymentPort, stripePaymentPort,
                lmsClientPort, billingPort, notificationPort, orderFinancialTransactionService,
                userActivationPort, passwordEncoder, null, null, "http://localhost:5173");
    }

    public BuyCourseService(
            OrderRepositoryPort orderRepository,
            UserRepositoryPort userRepository,
            ProgramRepositoryPort programRepository,
            @Qualifier("izipayPaymentAdapter") PaymentPort izipayPaymentPort,
            @Qualifier("stripePaymentAdapter") PaymentPort stripePaymentPort,
            LmsClientPort lmsClientPort,
            BillingPort billingPort,
            NotificationPort notificationPort,
            OrderFinancialTransactionService orderFinancialTransactionService,
            UserActivationPort userActivationPort,
            PasswordEncoder passwordEncoder,
            String frontendUrl
    ) {
        this(orderRepository, userRepository, programRepository, izipayPaymentPort, stripePaymentPort,
                lmsClientPort, billingPort, notificationPort, orderFinancialTransactionService,
                userActivationPort, passwordEncoder, null, null, frontendUrl);
    }

    public BuyCourseService(
            OrderRepositoryPort orderRepository,
            UserRepositoryPort userRepository,
            ProgramRepositoryPort programRepository,
            @Qualifier("izipayPaymentAdapter") PaymentPort izipayPaymentPort,
            @Qualifier("stripePaymentAdapter") PaymentPort stripePaymentPort,
            LmsClientPort lmsClientPort,
            BillingPort billingPort,
            NotificationPort notificationPort,
            OrderFinancialTransactionService orderFinancialTransactionService,
            UserActivationPort userActivationPort,
            PasswordEncoder passwordEncoder,
            ActivationOutboxService activationOutboxService,
            String frontendUrl
    ) {
        this(orderRepository, userRepository, programRepository, izipayPaymentPort, stripePaymentPort,
                lmsClientPort, billingPort, notificationPort, orderFinancialTransactionService,
                userActivationPort, passwordEncoder, activationOutboxService, null, frontendUrl);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BuyCourseService(
            OrderRepositoryPort orderRepository,
            UserRepositoryPort userRepository,
            ProgramRepositoryPort programRepository,
            @Qualifier("izipayPaymentAdapter") PaymentPort izipayPaymentPort,
            @Qualifier("stripePaymentAdapter") PaymentPort stripePaymentPort,
            LmsClientPort lmsClientPort,
            BillingPort billingPort,
            NotificationPort notificationPort,
            OrderFinancialTransactionService orderFinancialTransactionService,
            UserActivationPort userActivationPort,
            PasswordEncoder passwordEncoder,
            ActivationOutboxService activationOutboxService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.lysandri.security.JwtService jwtService,
            @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:5173}") String frontendUrl
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.programRepository = programRepository;
        this.izipayPaymentPort = izipayPaymentPort;
        this.stripePaymentPort = stripePaymentPort;
        this.lmsClientPort = lmsClientPort;
        this.billingPort = billingPort;
        this.notificationPort = notificationPort;
        this.orderFinancialTransactionService = orderFinancialTransactionService;
        this.userActivationPort = userActivationPort;
        this.passwordEncoder = passwordEncoder;
        this.activationOutboxService = activationOutboxService;
        this.jwtService = jwtService;
        this.frontendUrl = frontendUrl != null && !frontendUrl.isBlank() ? frontendUrl.replaceAll("/+$", "") : "http://localhost:5173";
    }

    @Override
    public CheckoutSessionResponse iniciarCompra(CreateOrderCommand command) {
        log.info("Iniciando checkout con programas: {}", command.programaIds());

        if (command.programaIds() == null || command.programaIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un programa para comprar");
        }

        // Validar que no existan IDs de programas duplicados
        Set<Long> uniqueProgramIds = new HashSet<>(command.programaIds());
        if (uniqueProgramIds.size() != command.programaIds().size()) {
            throw new IllegalArgumentException("No se permiten programas duplicados en la orden");
        }

        List<Programa> programas = programRepository.buscarPorIds(command.programaIds());
        if (programas.size() != uniqueProgramIds.size()) {
            throw new IllegalArgumentException("Uno o más programas seleccionados no existen o no están disponibles");
        }

        // Validar disponibilidad comercial, estado activo y precios aprobados
        Set<String> monedas = new HashSet<>();
        for (Programa prog : programas) {
            if (!prog.isActivo()) {
                throw new IllegalArgumentException("El programa '" + prog.getTitulo() + "' (ID: " + prog.getIdPrograma() + ") no está activo para la venta comercial");
            }
            if (!prog.isPrecioAprobado()) {
                throw new IllegalArgumentException("El programa '" + prog.getTitulo() + "' (ID: " + prog.getIdPrograma() + ") tiene un precio provisional no aprobado comercialmente");
            }
            if (prog.getPrecio() == null || prog.getPrecio().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El precio del programa '" + prog.getTitulo() + "' no es válido");
            }
            String mon = (prog.getMoneda() != null && !prog.getMoneda().isBlank()) ? prog.getMoneda().trim().toUpperCase() : "USD";
            monedas.add(mon);
        }

        // Rechazar carritos con monedas diferentes
        if (monedas.size() > 1) {
            throw new IllegalArgumentException("No se permiten carritos con monedas diferentes (" + String.join(", ", monedas) + ") mientras no exista una política explícita de conversión.");
        }
        String monedaOrden = monedas.isEmpty() ? "USD" : monedas.iterator().next();

        BigDecimal total = BigDecimal.ZERO;
        List<DetalleOrden> items = new ArrayList<>();

        for (Programa prog : programas) {
            BigDecimal unitPrice = prog.getPrecio().setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(1)).setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal);

            items.add(DetalleOrden.builder()
                    .idPrograma(prog.getIdPrograma())
                    .programa(prog)
                    .precioUnitario(unitPrice)
                    .cantidad(1)
                    .subtotal(subtotal)
                    .moodleMatriculado(false)
                    .build());
        }
        total = total.setScale(2, RoundingMode.HALF_UP);

        String claveIdempotencia = (command.claveIdempotencia() != null && !command.claveIdempotencia().isBlank())
                ? command.claveIdempotencia().trim()
                : null;

        String metodoPagoRaw = (command.metodoPago() != null && !command.metodoPago().isBlank())
                ? command.metodoPago().trim().toUpperCase()
                : "IZIPAY";

        if ("TARJETA".equals(metodoPagoRaw) || "ONLINE".equals(metodoPagoRaw)) {
            metodoPagoRaw = "IZIPAY";
        }

        // Métodos de pago permitidos en el backend
        Set<String> METODOS_PERMITIDOS = Set.of("IZIPAY", "STRIPE", "TRANSFERENCIA", "YAPE", "PLIN");
        if (!METODOS_PERMITIDOS.contains(metodoPagoRaw)) {
            log.warn("Rechazo de compra: Método de pago no admitido: {}", metodoPagoRaw);
            throw new IllegalArgumentException("Método de pago no admitido: " + metodoPagoRaw
                    + ". Los métodos admitidos son: IZIPAY, STRIPE, TRANSFERENCIA, YAPE, PLIN.");
        }
        final String metodoPago = metodoPagoRaw;

        String guestSessionToken = command.guestSessionToken() != null && !command.guestSessionToken().isBlank()
                ? command.guestSessionToken().trim()
                : null;

        // 1. Idempotencia persistente en compras: si la orden ya existe, verificar autorización y retornar
        if (claveIdempotencia != null) {
            Optional<Orden> ordenExistenteOpt = orderRepository.buscarPorClaveIdempotencia(claveIdempotencia);
            if (ordenExistenteOpt.isPresent()) {
                Orden ordenExistente = ordenExistenteOpt.get();
                log.info("Clave de idempotencia detectada: {}. Validando autorización de acceso para orden {}",
                        claveIdempotencia, ordenExistente.getCodigoOrden());
                validarAutorizacionYConsistenciaIdempotente(
                        ordenExistente,
                        command.idUsuario(),
                        command.guestEmail(),
                        guestSessionToken,
                        uniqueProgramIds,
                        metodoPago,
                        total,
                        monedaOrden
                );
                return construirCheckoutResponse(ordenExistente, guestSessionToken);
            }
        }

        // 2. Creación de NUEVA orden: Para invitados, exigir credencial de sesión previa antes de registrar usuarios u órdenes
        String guestSessionId = null;
        String guestSessionHash = null;

        if (command.idUsuario() == null) {
            if (guestSessionToken == null || guestSessionToken.isBlank()) {
                log.warn("Rechazo de creación de orden para invitado: Falta token de sesión para correo {}", command.guestEmail());
                throw new IllegalArgumentException("Se requiere un token de sesión de compra de invitado válido. Inicie la compra nuevamente desde el catálogo.");
            }
            if (jwtService != null) {
                com.lysandri.security.JwtService.GuestSessionClaims claims = jwtService.validateGuestCheckoutToken(guestSessionToken);
                if (claims == null || claims.sessionId() == null) {
                    log.warn("Rechazo de creación de orden para invitado: Token de sesión expirado o inválido para correo {}", command.guestEmail());
                    throw new IllegalArgumentException("La sesión de compra de invitado ha expirado o es inválida. Por favor reinicie la compra desde el catálogo.");
                }
                String email = command.guestEmail() != null ? command.guestEmail().trim().toLowerCase() : "";
                if (claims.email() != null && !claims.email().isBlank() && !claims.email().equalsIgnoreCase(email)) {
                    log.warn("Rechazo de creación de orden para invitado: Discrepancia de correo en token de sesión");
                    throw new IllegalArgumentException("La sesión de compra no corresponde al correo del comprador.");
                }
                guestSessionId = claims.sessionId();
                guestSessionHash = calcularHashSesion(guestSessionId);
            } else {
                throw new IllegalStateException("Servicio de validación criptográfica de credenciales no disponible");
            }
        }

        Usuario usuario;
        if (command.idUsuario() != null) {
            usuario = userRepository.buscarPorId(command.idUsuario())
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + command.idUsuario()));
        } else if (command.guestEmail() != null && !command.guestEmail().isBlank()) {
            String email = command.guestEmail().trim().toLowerCase();
            Optional<Usuario> usuarioExistente = userRepository.buscarPorEmail(email);
            if (usuarioExistente.isPresent()) {
                throw new IllegalArgumentException("Ya existe una cuenta registrada con el correo ingresado. Inicie sesión antes de realizar la compra.");
            } else {
                String fullName = command.guestNombre() != null && !command.guestNombre().isBlank()
                        ? command.guestNombre().trim()
                        : "Cliente Invitado";
                String[] parts = fullName.split(" ", 2);
                String nombres = parts[0];
                String apellidos = parts.length > 1 ? parts[1] : "Executive";

                // Generar credencial interna única y segura (no compartida, no comunicada)
                String randomSecurePassword = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
                String encodedPassword = passwordEncoder.encode(randomSecurePassword);

                Usuario nuevo = Usuario.builder()
                        .nombres(nombres)
                        .apellidos(apellidos)
                        .email(email)
                        .passw(encodedPassword)
                        .rol(RolUsuario.CLIENTE)
                        .activo(false)
                        .bloqueadoAdmin(false)
                        .pendienteActivacion(true)
                        .fechaCreacion(OffsetDateTime.now())
                        .fechaActualizacion(OffsetDateTime.now())
                        .build();
                usuario = userRepository.guardar(nuevo);

                userActivationPort.crearTokenActivacion(usuario.getIdUser());
            }
        } else {
            throw new IllegalArgumentException("Debe proporcionar un ID de usuario o un correo electrónico para invitado");
        }

        String codigoOrden = UUID.randomUUID().toString();
        String tipoComp = (command.tipoComprobante() != null && !command.tipoComprobante().isBlank())
                ? command.tipoComprobante().toUpperCase()
                : "BOLETA";
        String docCliente = command.numeroDocumento() != null ? command.numeroDocumento().trim() : null;
        String razonSocial = (command.nombreFacturacion() != null && !command.nombreFacturacion().isBlank())
                ? command.nombreFacturacion().trim()
                : usuario.getNombreCompleto();

        Orden orden = Orden.builder()
                .idUser(usuario.getIdUser())
                .usuario(usuario)
                .codigoOrden(codigoOrden)
                .fechaOrden(OffsetDateTime.now())
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(total)
                .moneda(monedaOrden)
                .metodoPago(metodoPago)
                .numeroOperacion(command.numeroOperacion())
                .claveIdempotencia(claveIdempotencia)
                .guestSessionHash(guestSessionHash)
                .tipoComprobanteSolicitado(tipoComp)
                .numeroDocumentoCliente(docCliente)
                .nombreFacturacion(razonSocial)
                .moodleMatriculaSincronizada(false)
                .items(items)
                .build();

        try {
            orden = orderRepository.guardar(orden);
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            if (claveIdempotencia != null) {
                log.warn("Violación de unicidad en clave de idempotencia por inserción concurrente: {}. Recuperando orden ganadora.", claveIdempotencia);
                Optional<Orden> ganadoraOpt = orderRepository.buscarPorClaveIdempotencia(claveIdempotencia);
                if (ganadoraOpt.isPresent()) {
                    Orden ganadora = ganadoraOpt.get();
                    validarAutorizacionYConsistenciaIdempotente(
                            ganadora,
                            command.idUsuario(),
                            command.guestEmail(),
                            guestSessionToken,
                            uniqueProgramIds,
                            metodoPago,
                            total,
                            monedaOrden
                    );
                    return construirCheckoutResponse(ganadora, guestSessionToken);
                }
            }
            throw dive;
        }

        // Si es pago manual (Yape, Plin, Transferencia bancaria directa), no llamamos a pasarela online
        boolean isStripe = "STRIPE".equalsIgnoreCase(metodoPago);
        boolean isIzipay = "IZIPAY".equalsIgnoreCase(metodoPago);
        boolean isOnlineGateway = isStripe || isIzipay;

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
                    codigoOrden,
                    guestSessionToken
            );
        }

        // Seleccionar pasarela según el método de pago explícito (nunca etiquetar Izipay como Stripe)
        PaymentPort selectedPaymentPort = isStripe ? stripePaymentPort : izipayPaymentPort;
        PaymentPort.PaymentSessionResult sessionResult = selectedPaymentPort.crearSesionPago(
                orden,
                usuario.getEmail(),
                razonSocial
        );

        if (isStripe) {
            orden.setStripeSessionId(sessionResult.sessionId());
            orden.setStripePaymentIntentId(sessionResult.paymentIntentId());
        } else {
            orden.setIzipayOrderId(sessionResult.sessionId());
            orden.setIzipayTransactionId(sessionResult.paymentIntentId());
        }
        orderRepository.guardar(orden);

        log.info("Orden {} creada con éxito en pasarela {}. Sesión/Tx: {}",
                codigoOrden, isStripe ? "Stripe" : "Izipay", sessionResult.sessionId());

        return new CheckoutSessionResponse(
                codigoOrden,
                sessionResult.checkoutUrl(),
                sessionResult.sessionId(),
                sessionResult.checkoutUrl(),
                sessionResult.formToken(),
                sessionResult.publicKey(),
                sessionResult.sessionId(),
                guestSessionToken
        );
    }

    @Override
    public Orden procesarPagoExitoso(String sessionIdOrTransactionId) {
        log.info("Procesando pago exitoso y aprovisionamiento para sesión/transacción: {}", sessionIdOrTransactionId);

        Orden orden = orderRepository.buscarPorCualquierIdentificador(sessionIdOrTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada para la sesión de pago: " + sessionIdOrTransactionId));

        String metodo = orden.getMetodoPago() != null ? orden.getMetodoPago().trim().toUpperCase() : "";
        boolean isManual = "TRANSFERENCIA".equals(metodo) || "YAPE".equals(metodo) || "PLIN".equals(metodo) || "MANUAL".equals(metodo);
        if (isManual) {
            throw new IllegalStateException("Las órdenes con método de pago manual (" + metodo + ") no pueden procesarse mediante pasarela online.");
        }

        if (orden.getEstadoOrden() == EstadoOrden.PAGADO && orden.isMoodleMatriculaSincronizada()) {
            log.info("La orden {} ya fue procesada y matriculada previamente. Idempotencia garantizada.", orden.getCodigoOrden());
            return orden;
        }

        // 1. Confirmación financiera atómica con bloqueo pesimista en PostgreSQL (SELECT ... FOR UPDATE).
        // La transacción comitea de inmediato y libera el bloqueo de base de datos antes de llamadas HTTP.
        Orden ordenConfirmada = orderFinancialTransactionService.confirmarPagoFinanciero(orden.getCodigoOrden());

        // 2. Reserva atómica y exclusiva del aprovisionamiento (mecanismo de lease distribuido)
        ResultadoReservaAprovisionamiento reserva = orderFinancialTransactionService.reservarAprovisionamiento(ordenConfirmada.getCodigoOrden());

        if (reserva.isYaCompletado()) {
            log.info("La orden {} ya fue matriculada y aprovisionada previamente. Idempotencia garantizada.", ordenConfirmada.getCodigoOrden());
            return reserva.getOrden();
        }

        if (reserva.isEnProceso()) {
            log.info("La orden {} ya está siendo aprovisionada por otro trabajador concurrente. Omitiendo duplicados.", ordenConfirmada.getCodigoOrden());
            return reserva.getOrden();
        }

        // 3. Efectos secundarios externos (Moodle LMS, facturación, correos) sin retener transacción de BD
        return ejecutarAprovisionamientoYFacturacion(reserva.getOrden(), reserva.getOwnerToken());
    }

    @Override
    public Orden confirmarPagoYMatricular(String sessionIdOrTransactionId) {
        log.info("Confirmando pago y aprovisionando para sesión/transacción: {}", sessionIdOrTransactionId);

        Orden orden = orderRepository.buscarPorCualquierIdentificador(sessionIdOrTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada para la sesión de pago: " + sessionIdOrTransactionId));

        // Impedir que la ruta de confirmación online apruebe una orden de pago manual
        String metodo = orden.getMetodoPago() != null ? orden.getMetodoPago().trim().toUpperCase() : "";
        boolean isManual = "TRANSFERENCIA".equals(metodo) || "YAPE".equals(metodo) || "PLIN".equals(metodo) || "MANUAL".equals(metodo);
        if (isManual) {
            throw new IllegalStateException("Las órdenes con método de pago manual (" + metodo + ") no pueden confirmarse mediante la ruta online. Deben ser aprobadas por un administrador.");
        }

        if (orden.getEstadoOrden() == EstadoOrden.PAGADO) {
            ResultadoReservaAprovisionamiento reserva = orderFinancialTransactionService.reservarAprovisionamiento(orden.getCodigoOrden());
            if (reserva.isYaCompletado()) {
                log.info("La orden {} ya fue procesada y matriculada previamente. Idempotencia garantizada.", orden.getCodigoOrden());
                return reserva.getOrden();
            }
            if (reserva.isEnProceso()) {
                log.info("La orden {} ya está siendo aprovisionada por otro proceso concurrente.", orden.getCodigoOrden());
                return reserva.getOrden();
            }
            return ejecutarAprovisionamientoYFacturacion(reserva.getOrden(), reserva.getOwnerToken());
        }

        // Validación activa con la pasarela correspondiente para impedir confirmaciones falsas
        boolean isStripe = "STRIPE".equalsIgnoreCase(metodo) || (orden.getStripeSessionId() != null && orden.getStripeSessionId().equals(sessionIdOrTransactionId));
        PaymentPort verificationPort = isStripe ? stripePaymentPort : izipayPaymentPort;
        boolean pagoVerificado = verificationPort.verificarPagoCompletado(sessionIdOrTransactionId);
        if (!pagoVerificado) {
            log.warn("Rechazando confirmación de orden {}: la pasarela de pagos no ha confirmado la transacción {}",
                    orden.getCodigoOrden(), sessionIdOrTransactionId);
            throw new IllegalStateException("El pago aún no ha sido confirmado por la pasarela de pagos.");
        }

        Orden ordenConfirmada = orderFinancialTransactionService.confirmarPagoFinanciero(orden.getCodigoOrden());
        if (activationOutboxService != null) {
            try {
                activationOutboxService.procesarTareasPendientes();
            } catch (Exception e) {
                log.debug("Procesamiento outbox pospuesto a tarea programada: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()));
            }
        }
        ResultadoReservaAprovisionamiento reserva = orderFinancialTransactionService.reservarAprovisionamiento(ordenConfirmada.getCodigoOrden());
        if (reserva.isYaCompletado() || reserva.isEnProceso()) {
            return reserva.getOrden();
        }
        return ejecutarAprovisionamientoYFacturacion(reserva.getOrden(), reserva.getOwnerToken());
    }

    @Override
    public Orden confirmarPagoManual(Long idOrden, ConfirmarPagoManualCommand command) {
        log.info("Iniciando confirmación de pago manual para orden ID: {}", idOrden);

        // 1. Confirmación financiera atómica con validación estricta y bloqueo pesimista en PostgreSQL
        Orden ordenConfirmada = orderFinancialTransactionService.confirmarPagoManualFinanciero(idOrden, command);

        // 2. Procesar tareas pendientes de outbox (si corresponde)
        if (activationOutboxService != null) {
            try {
                activationOutboxService.procesarTareasPendientes();
            } catch (Exception e) {
                log.debug("Procesamiento outbox pospuesto a tarea programada: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()));
            }
        }

        // 3. Reserva y aprovisionamiento en Moodle / Facturación
        ResultadoReservaAprovisionamiento reserva = orderFinancialTransactionService.reservarAprovisionamiento(ordenConfirmada.getCodigoOrden());
        if (reserva.isYaCompletado() || reserva.isEnProceso()) {
            return reserva.getOrden();
        }

        return ejecutarAprovisionamientoYFacturacion(reserva.getOrden(), reserva.getOwnerToken());
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
    public List<Orden> listarOrdenesPorUsuario(Long idUsuario) {
        if (idUsuario == null) {
            return List.of();
        }
        return orderRepository.buscarPorUsuario(idUsuario);
    }

    @Override
    @Transactional
    public Orden reintentarMatricula(Long idOrden) {
        log.info("Reintentando matrícula para orden ID: {}", idOrden);
        Orden orden = orderRepository.buscarPorId(idOrden)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con ID: " + idOrden));

        if (orden.getEstadoOrden() != EstadoOrden.PAGADO) {
            throw new IllegalStateException("Solo se puede reintentar la matrícula para órdenes ya confirmadas como PAGADO.");
        }

        ResultadoReservaAprovisionamiento reserva = orderFinancialTransactionService.reservarAprovisionamiento(orden.getCodigoOrden());
        if (reserva.isYaCompletado()) {
            log.info("La orden {} ya se encuentra con matrícula completa.", orden.getCodigoOrden());
            return reserva.getOrden();
        }
        if (reserva.isEnProceso()) {
            log.info("La orden {} ya está en proceso de aprovisionamiento activo por otro trabajador.", orden.getCodigoOrden());
            return reserva.getOrden();
        }

        return ejecutarAprovisionamientoYFacturacion(reserva.getOrden(), reserva.getOwnerToken());
    }

    @Override
    @Transactional(readOnly = true)
    public Orden obtenerOrdenPorCodigo(String codigoOrden) {
        return orderRepository.buscarPorCodigo(codigoOrden)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada: " + codigoOrden));
    }

    /**
     * Orquestación central de aprovisionamiento en Moodle LMS, facturación electrónica y notificación.
     * Ejecutada únicamente por el trabajador que posee el lease activo (ownerToken).
     */
    private Orden ejecutarAprovisionamientoYFacturacion(Orden orden, String ownerToken) {
        Usuario usuario = userRepository.buscarPorId(orden.getIdUser())
                .orElseThrow(() -> new IllegalStateException("Usuario asociado a la orden no existe"));

        String email = usuario.getEmail();
        String nombre = (orden.getNombreFacturacion() != null && !orden.getNombreFacturacion().isBlank())
                ? orden.getNombreFacturacion()
                : usuario.getNombreCompleto();

        log.info("Iniciando aprovisionamiento y facturación para orden ID: {}, código: {}", orden.getIdOrden(), orden.getCodigoOrden());

        // Generar clave temporal para el aula virtual
        String moodlePassword = generarPasswordSegura10Chars();

        // Crear o asociar usuario en Moodle
        Long moodleUserId = usuario.getMoodleUserId();
        if (moodleUserId == null) {
            moodleUserId = lmsClientPort.buscarUsuarioPorEmail(email).orElse(null);
        }

        boolean moodleUserOk = (moodleUserId != null);
        String moodleError = null;
        if (moodleUserId == null) {
            String username = email.toLowerCase().replaceAll("[^a-z0-9._-]", "");
            try {
                moodleUserId = lmsClientPort.crearUsuario(
                        username,
                        moodlePassword,
                        usuario.getNombres(),
                        usuario.getApellidos(),
                        email
                );
                if (moodleUserId != null) {
                    usuario.setMoodleUserId(moodleUserId);
                    userRepository.guardar(usuario);
                    moodleUserOk = true;
                    log.info("Usuario creado en Moodle con ID: {}", moodleUserId);
                }
            } catch (Exception e) {
                moodleError = com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage());
                log.error("Fallo al crear usuario en Moodle para orden {}: {}", orden.getCodigoOrden(), moodleError);
            }
        } else {
            log.info("Usuario ya existente en Moodle con ID: {}", moodleUserId);
        }

        // Matricular al estudiante en los cursos adquiridos de forma idempotente y con validación remota
        StringBuilder cursoTitulos = new StringBuilder();
        boolean allEnrolled = moodleUserOk;
        List<Long> programasMatriculadosIds = new ArrayList<>();

        for (DetalleOrden detalle : orden.getItems()) {
            Programa programa = detalle.getPrograma() != null ? detalle.getPrograma() :
                    programRepository.buscarPorId(detalle.getIdPrograma()).orElse(null);

            if (programa != null) {
                if (cursoTitulos.length() > 0) cursoTitulos.append(", ");
                cursoTitulos.append(programa.getTitulo());

                if (programa.getMoodleCourseId() != null) {
                    if (detalle.isMoodleMatriculado()) {
                        log.info("El programa {} ya estaba matriculado localmente.", programa.getTitulo());
                        programasMatriculadosIds.add(programa.getIdPrograma());
                        continue;
                    }

                    if (moodleUserId != null) {
                        // Comprobar si el usuario ya se encuentra matriculado remotamente en Moodle
                        boolean yaMatriculadoRemoto = lmsClientPort.estaUsuarioMatriculado(moodleUserId, programa.getMoodleCourseId());
                        if (yaMatriculadoRemoto) {
                            log.info("Usuario {} ya figura matriculado remotamente en curso Moodle {}", moodleUserId, programa.getMoodleCourseId());
                            detalle.setMoodleMatriculado(true);
                            programasMatriculadosIds.add(programa.getIdPrograma());
                            continue;
                        }

                        try {
                            lmsClientPort.matricularUsuarioEnCurso(moodleUserId, programa.getMoodleCourseId(), 5);
                            detalle.setMoodleMatriculado(true);
                            programasMatriculadosIds.add(programa.getIdPrograma());
                            log.info("Usuario {} matriculado con éxito en curso Moodle {}", moodleUserId, programa.getMoodleCourseId());
                        } catch (Exception e) {
                            allEnrolled = false;
                            moodleError = com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage());
                            log.error("Fallo al matricular en curso Moodle {} para orden {}: {}", programa.getMoodleCourseId(), orden.getCodigoOrden(), moodleError);
                        }
                    } else {
                        allEnrolled = false;
                        if (moodleError == null) {
                            moodleError = "No se pudo matricular en curso " + programa.getMoodleCourseId() + " porque el usuario no tiene moodleUserId asignado";
                        }
                        log.error("No se pudo matricular en curso {} para orden {}: usuario sin moodleUserId", programa.getMoodleCourseId(), orden.getCodigoOrden());
                    }
                }
            }
        }

        // Emisión de comprobante de pago SUNAT (idempotente)
        ComprobantePago comprobante = billingPort.emitirComprobante(orden);

        // Completar aprovisionamiento persistiendo el resultado y liberando el lease
        Orden ordenGuardada;
        try {
            ordenGuardada = orderFinancialTransactionService.completarAprovisionamiento(
                    orden.getCodigoOrden(), ownerToken, allEnrolled, programasMatriculadosIds);
        } catch (Exception ex) {
            log.error("Error al persistir conclusión de aprovisionamiento con token {}: {}", ownerToken, com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
            orderFinancialTransactionService.registrarFalloAprovisionamiento(orden.getCodigoOrden(), ownerToken, com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
            throw ex;
        }

        // Enviar accesos y factura por correo con idempotencia persistente
        String cursoTitulo = cursoTitulos.length() > 0 ? cursoTitulos.toString() : "Programa Ejecutivo";
        try {
            notificationPort.enviarAccesosYFactura(email, nombre, cursoTitulo, moodlePassword, comprobante);
        } catch (Exception ex) {
            log.warn("No se pudo enviar notificación de accesos/factura para orden {}: {}", orden.getCodigoOrden(), com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
        }

        // Procesamiento en background/inmediato de notificaciones outbox desacopladas
        if (activationOutboxService != null) {
            try {
                activationOutboxService.procesarTareasPendientes();
            } catch (Exception ex) {
                log.debug("Procesamiento outbox pospuesto: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
            }
        }

        String serie = comprobante != null ? comprobante.getSerie() : "N/A";
        Object correlativo = comprobante != null ? comprobante.getCorrelativo() : 0;
        log.info("Orden {} procesada con éxito. Comprobante {}-{} emitido. Sincronización Moodle: {}",
                orden.getCodigoOrden(), serie, correlativo, allEnrolled);

        return ordenGuardada;
    }

    private void validarAutorizacionYConsistenciaIdempotente(
            Orden ordenExistente,
            Long idUsuario,
            String guestEmail,
            Set<Long> uniqueProgramIds,
            String metodoPago,
            BigDecimal total,
            String monedaOrden
    ) {
        validarAutorizacionYConsistenciaIdempotente(
                ordenExistente,
                idUsuario,
                guestEmail,
                null,
                uniqueProgramIds,
                metodoPago,
                total,
                monedaOrden
        );
    }

    private void validarAutorizacionYConsistenciaIdempotente(
            Orden ordenExistente,
            Long idUsuario,
            String guestEmail,
            String guestSessionToken,
            Set<Long> uniqueProgramIds,
            String metodoPago,
            BigDecimal total,
            String monedaOrden
    ) {
        // A. Aislamiento entre compradores (Prevención IDOR y verificación de contexto seguro)
        if (idUsuario != null) {
            if (!idUsuario.equals(ordenExistente.getIdUser())) {
                log.warn("Violación de aislamiento IDOR: El usuario {} intentó acceder a la orden {} creada por otro usuario",
                        idUsuario, ordenExistente.getCodigoOrden());
                throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado: la clave de compra pertenece a otro usuario");
            }
        } else {
            // Comprador invitado:
            // 1. Si la orden existente pertenece a un usuario registrado no anónimo:
            if (ordenExistente.getIdUser() != null && userRepository != null) {
                boolean esUsuarioRegistrado = userRepository.buscarPorId(ordenExistente.getIdUser())
                        .map(u -> u.isActivo() && !u.isPendienteActivacion())
                        .orElse(false);
                if (esUsuarioRegistrado) {
                    log.warn("Violación de aislamiento IDOR: Intento de recuperar anónimamente la orden {} de usuario registrado",
                            ordenExistente.getCodigoOrden());
                    throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado: la orden pertenece a un usuario registrado");
                }
            }

            // 2. Validación obligatoria del contexto criptográfico de sesión del invitado
            if (ordenExistente.getGuestSessionHash() != null) {
                if (guestSessionToken == null || guestSessionToken.isBlank()) {
                    log.warn("Violación de aislamiento IDOR para invitado: Ausencia de token de sesión en intento de recuperación de orden {}",
                            ordenExistente.getCodigoOrden());
                    throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado: la clave de compra pertenece a otro comprador");
                }

                if (jwtService == null) {
                    throw new org.springframework.security.access.AccessDeniedException("Servicio de validación de credenciales no disponible");
                }

                com.lysandri.security.JwtService.GuestSessionClaims claims = jwtService.validateGuestCheckoutToken(guestSessionToken);
                if (claims == null || claims.sessionId() == null) {
                    log.warn("Violación de aislamiento IDOR para invitado: Token de sesión inválido o expirado en orden {}",
                            ordenExistente.getCodigoOrden());
                    throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado: credencial de recuperación inválida o expirada");
                }

                String claimHash = calcularHashSesion(claims.sessionId());
                if (!claimHash.equals(ordenExistente.getGuestSessionHash())) {
                    log.warn("Violación de aislamiento IDOR para invitado: Hash de sesión no coincide con la orden {}",
                            ordenExistente.getCodigoOrden());
                    throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado: la clave de compra pertenece a otro comprador");
                }
            } else {
                // Orden histórica o sin hash de sesión: no permitir recuperarla meramente con correo
                log.warn("Violación de aislamiento para invitado en orden {}: Sin contexto de autorización seguro",
                        ordenExistente.getCodigoOrden());
                throw new org.springframework.security.access.AccessDeniedException("Acceso no autorizado: la clave de compra pertenece a otro comprador");
            }
        }

        // B. Conflicto de datos: Programas
        Set<Long> existingProgramIds = ordenExistente.getItems() != null
                ? ordenExistente.getItems().stream().map(DetalleOrden::getIdPrograma).collect(java.util.stream.Collectors.toSet())
                : Collections.emptySet();
        if (!existingProgramIds.equals(uniqueProgramIds)) {
            log.warn("Conflicto de idempotencia en orden {}: Programas difieren (existente: {}, solicitado: {})",
                    ordenExistente.getCodigoOrden(), existingProgramIds, uniqueProgramIds);
            throw new IllegalStateException("Conflicto de idempotencia: La clave ya existe pero con una selección de programas diferente");
        }

        // Metodo de pago
        String metodoExistente = ordenExistente.getMetodoPago() != null ? ordenExistente.getMetodoPago().toUpperCase() : "";
        if (!metodoExistente.equalsIgnoreCase(metodoPago)) {
            log.warn("Conflicto de idempotencia en orden {}: Método de pago difiere (existente: {}, solicitado: {})",
                    ordenExistente.getCodigoOrden(), metodoExistente, metodoPago);
            throw new IllegalStateException("Conflicto de idempotencia: La clave ya existe pero con un método de pago diferente ("
                    + metodoExistente + " vs " + metodoPago + ")");
        }

        // Monto y Moneda
        if (ordenExistente.getTotal() != null && total != null && ordenExistente.getTotal().compareTo(total) != 0) {
            log.warn("Conflicto de idempotencia en orden {}: Monto difiere (existente: {}, solicitado: {})",
                    ordenExistente.getCodigoOrden(), ordenExistente.getTotal(), total);
            throw new IllegalStateException("Conflicto de idempotencia: La clave ya existe pero con un monto diferente");
        }
        if (ordenExistente.getMoneda() != null && !ordenExistente.getMoneda().equalsIgnoreCase(monedaOrden)) {
            log.warn("Conflicto de idempotencia en orden {}: Moneda difiere (existente: {}, solicitado: {})",
                    ordenExistente.getCodigoOrden(), ordenExistente.getMoneda(), monedaOrden);
            throw new IllegalStateException("Conflicto de idempotencia: La clave ya existe pero con una moneda diferente");
        }
    }

    private String calcularHashSesion(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error calculando hash de sesión", e);
        }
    }

    private CheckoutSessionResponse construirCheckoutResponse(Orden orden) {
        return construirCheckoutResponse(orden, null);
    }

    private CheckoutSessionResponse construirCheckoutResponse(Orden orden, String guestSessionToken) {
        String metodo = orden.getMetodoPago() != null ? orden.getMetodoPago().toUpperCase() : "IZIPAY";
        boolean isStripe = "STRIPE".equalsIgnoreCase(metodo);
        boolean isIzipay = "IZIPAY".equalsIgnoreCase(metodo) || "TARJETA".equalsIgnoreCase(metodo) || "ONLINE".equalsIgnoreCase(metodo);

        if (!isStripe && !isIzipay) {
            String localCheckoutUrl = "/checkout/success?session_id=" + orden.getCodigoOrden() + "&manual=true";
            return new CheckoutSessionResponse(
                    orden.getCodigoOrden(),
                    localCheckoutUrl,
                    orden.getCodigoOrden(),
                    localCheckoutUrl,
                    null,
                    null,
                    orden.getCodigoOrden(),
                    guestSessionToken
            );
        }

        if (isStripe) {
            return new CheckoutSessionResponse(
                    orden.getCodigoOrden(),
                    orden.getStripeSessionId(),
                    orden.getStripeSessionId(),
                    null,
                    null,
                    null,
                    orden.getStripeSessionId(),
                    guestSessionToken
            );
        }

        return new CheckoutSessionResponse(
                orden.getCodigoOrden(),
                null,
                orden.getIzipayOrderId(),
                null,
                null,
                null,
                orden.getIzipayTransactionId(),
                guestSessionToken
        );
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
