package com.lysandri.application.service;

import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.EstadoAprovisionamiento;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio transaccional dedicado a la confirmación financiera, control de arrendamiento
 * exclusivo de aprovisionamiento y transiciones atómicas de órdenes.
 * Garantiza que el bloqueo pesimista (SELECT ... FOR UPDATE) se ejecute en transacciones
 * cortas y aisladas, liberando la conexión de PostgreSQL antes de que se ejecuten
 * llamadas HTTP externas a Moodle, facturación electrónica o SMTP.
 */
@Slf4j
@Service
public class OrderFinancialTransactionService {

    private final OrderRepositoryPort orderRepository;
    private final com.lysandri.domain.ports.out.UserRepositoryPort userRepository;
    private final com.lysandri.domain.ports.out.UserActivationPort userActivationPort;
    private final ActivationOutboxService activationOutboxService;
    private final com.lysandri.config.CuentasBancariasProperties cuentasBancariasProperties;

    public OrderFinancialTransactionService(OrderRepositoryPort orderRepository) {
        this(orderRepository, null, null, null, new com.lysandri.config.CuentasBancariasProperties());
    }

    public OrderFinancialTransactionService(
            OrderRepositoryPort orderRepository,
            com.lysandri.config.CuentasBancariasProperties cuentasBancariasProperties
    ) {
        this(orderRepository, null, null, null, cuentasBancariasProperties);
    }

    public OrderFinancialTransactionService(
            OrderRepositoryPort orderRepository,
            com.lysandri.domain.ports.out.UserRepositoryPort userRepository,
            com.lysandri.domain.ports.out.UserActivationPort userActivationPort,
            ActivationOutboxService activationOutboxService
    ) {
        this(orderRepository, userRepository, userActivationPort, activationOutboxService, new com.lysandri.config.CuentasBancariasProperties());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderFinancialTransactionService(
            OrderRepositoryPort orderRepository,
            com.lysandri.domain.ports.out.UserRepositoryPort userRepository,
            com.lysandri.domain.ports.out.UserActivationPort userActivationPort,
            ActivationOutboxService activationOutboxService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.lysandri.config.CuentasBancariasProperties cuentasBancariasProperties
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userActivationPort = userActivationPort;
        this.activationOutboxService = activationOutboxService;
        this.cuentasBancariasProperties = cuentasBancariasProperties != null ? cuentasBancariasProperties : new com.lysandri.config.CuentasBancariasProperties();
    }

    /**
     * Adquiere un bloqueo pesimista exclusivo sobre la orden en PostgreSQL,
     * valida su estado financiero previo y la transiciona atómicamente a PAGADO.
     * Al retornar, la transacción comitea inmediatamente y el bloqueo de fila se libera.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orden confirmarPagoFinanciero(String codigoOrden) {
        log.info("Adquiriendo bloqueo pesimista exclusivo para confirmación financiera: {}", codigoOrden);

        Orden ordenBloqueada = orderRepository.buscarPorCodigoConBloqueo(codigoOrden)
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo adquirir el bloqueo pesimista exclusivo para la orden: " + codigoOrden));

        // Validación estricta de estados de la orden
        if (ordenBloqueada.getEstadoOrden() == EstadoOrden.CANCELADO) {
            log.warn("Rechazo de confirmación: La orden {} se encuentra CANCELADA", codigoOrden);
            throw new IllegalStateException("No se puede confirmar el pago de una orden CANCELADA: " + codigoOrden);
        }

        if (ordenBloqueada.getEstadoOrden() == EstadoOrden.FALLIDO) {
            log.warn("Rechazo de confirmación: La orden {} se encuentra en estado FALLIDO", codigoOrden);
            throw new IllegalStateException("No se puede confirmar el pago de una orden en estado FALLIDO: " + codigoOrden);
        }

        // Idempotencia si ya fue pagada por otro hilo concurrente
        if (ordenBloqueada.getEstadoOrden() == EstadoOrden.PAGADO) {
            log.info("La orden {} ya se encontraba en estado PAGADO en base de datos. Transacción idempotente.", codigoOrden);
            return ordenBloqueada;
        }

        // Transición de estado a PAGADO
        ordenBloqueada.setEstadoOrden(EstadoOrden.PAGADO);
        if (ordenBloqueada.getFechaPago() == null) {
            ordenBloqueada.setFechaPago(OffsetDateTime.now());
        }

        // Registrar duraderamente la intención de activación en Outbox dentro de la misma transacción financiera
        if (userRepository != null && userActivationPort != null && activationOutboxService != null
                && ordenBloqueada.getIdUser() != null) {
            userRepository.buscarPorId(ordenBloqueada.getIdUser()).ifPresent(usuario -> {
                boolean requiereActivacion = !usuario.isActivo()
                        || usuario.isPendienteActivacion()
                        || (usuario.getPassw() != null && usuario.getPassw().startsWith("$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH"));

                if (requiereActivacion) {
                    com.lysandri.domain.model.UsuarioActivacionToken tokenActivacion =
                            userActivationPort.crearTokenActivacion(usuario.getIdUser());
                    activationOutboxService.crearTareaActivacion(
                            ordenBloqueada.getIdOrden(),
                            ordenBloqueada.getCodigoOrden(),
                            usuario.getIdUser(),
                            tokenActivacion.getIdToken(),
                            tokenActivacion.getToken(),
                            usuario.getEmail(),
                            usuario.getNombres(),
                            "ACTIVACION_ORDEN_" + ordenBloqueada.getIdOrden()
                    );
                    log.info("Intención de activación persistida duraderamente en Outbox para orden {}", codigoOrden);
                }
            });
        }

        Orden ordenGuardada = orderRepository.guardar(ordenBloqueada);
        log.info("Orden {} marcada exitosamente como PAGADA. Bloqueo pesimista liberado.", codigoOrden);
        return ordenGuardada;
    }

    /**
     * Adquiere un bloqueo pesimista exclusivo sobre la orden en PostgreSQL,
     * valida estrictamente que la orden sea de un método manual admitido (rechazando órdenes de pasarelas online),
     * valida el monto, la moneda, el banco, la cuenta y que el abono bancario no haya sido aplicado a otra orden.
     * Transiciona atómicamente a PAGADO y registra los datos de verificación.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orden confirmarPagoManualFinanciero(Long idOrden, BuyCourseUseCase.ConfirmarPagoManualCommand command) {
        log.info("Adquiriendo bloqueo pesimista exclusivo para confirmación manual de orden ID: {}", idOrden);

        Orden ordenBloqueada = orderRepository.buscarPorIdConBloqueo(idOrden)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada con ID: " + idOrden));

        // 1. Rechazar órdenes canceladas o fallidas
        if (ordenBloqueada.getEstadoOrden() == EstadoOrden.CANCELADO) {
            log.warn("Rechazo de confirmación manual: La orden {} se encuentra CANCELADA", ordenBloqueada.getCodigoOrden());
            throw new IllegalStateException("No se puede confirmar el pago de una orden CANCELADA: " + ordenBloqueada.getCodigoOrden());
        }
        if (ordenBloqueada.getEstadoOrden() == EstadoOrden.FALLIDO) {
            log.warn("Rechazo de confirmación manual: La orden {} se encuentra en estado FALLIDO", ordenBloqueada.getCodigoOrden());
            throw new IllegalStateException("No se puede confirmar el pago de una orden en estado FALLIDO: " + ordenBloqueada.getCodigoOrden());
        }

        // 2. Rechazar órdenes de pasarela online y validar métodos manuales permitidos
        String metodoActual = ordenBloqueada.getMetodoPago() != null ? ordenBloqueada.getMetodoPago().toUpperCase() : "";
        boolean esOnline = "IZIPAY".equals(metodoActual) || "TARJETA".equals(metodoActual) || "ONLINE".equals(metodoActual)
                || "STRIPE".equals(metodoActual) || ordenBloqueada.getIzipayOrderId() != null
                || ordenBloqueada.getStripeSessionId() != null;
        if (esOnline) {
            log.warn("Rechazo de confirmación manual: La orden {} fue creada bajo pasarela online ({})",
                    ordenBloqueada.getCodigoOrden(), metodoActual);
            throw new IllegalArgumentException(
                    "No se puede aprobar manualmente una orden configurada para pasarela online (" + metodoActual + "). La orden debe confirmarse mediante la pasarela de pago.");
        }

        boolean esManualAdmitido = "TRANSFERENCIA".equals(metodoActual) || "YAPE".equals(metodoActual) || "PLIN".equals(metodoActual);
        if (!esManualAdmitido) {
            log.warn("Rechazo de confirmación manual: Método de pago no admitido para aprobación manual: {}", metodoActual);
            throw new IllegalArgumentException("Método de pago no admitido para aprobación manual: " + metodoActual);
        }

        // 3. Validar datos obligatorios de la operación
        if (command == null) {
            throw new IllegalArgumentException("Debe proporcionar los datos de verificación del abono bancario");
        }

        // Impedir alterar el método de pago original registrado en la orden
        if (command.metodoPago() != null && !command.metodoPago().isBlank()) {
            String metodoSolicitado = com.lysandri.config.CuentasBancariasProperties.normalizarIdentificador(command.metodoPago());
            String metodoActualNorm = com.lysandri.config.CuentasBancariasProperties.normalizarIdentificador(metodoActual);
            if (!metodoSolicitado.equals(metodoActualNorm)) {
                log.warn("Intento de alterar método de pago en orden {}: {} -> {}", ordenBloqueada.getCodigoOrden(), metodoActual, command.metodoPago());
                throw new IllegalArgumentException("No se permite alterar el método de pago original registrado en la orden ("
                        + metodoActual + " -> " + command.metodoPago() + ")");
            }
        }

        String nroOp = com.lysandri.config.CuentasBancariasProperties.normalizarIdentificador(command.numeroOperacion());
        if (nroOp.isBlank()) {
            throw new IllegalArgumentException("El número de operación es obligatorio para la aprobación manual");
        }
        String banco = com.lysandri.config.CuentasBancariasProperties.normalizarIdentificador(command.bancoDestino());
        if (banco.isBlank()) {
            throw new IllegalArgumentException("El banco de destino es obligatorio");
        }
        String cuenta = com.lysandri.config.CuentasBancariasProperties.normalizarIdentificador(command.cuentaDestino());
        if (cuenta.isBlank()) {
            throw new IllegalArgumentException("La cuenta bancaria de destino es obligatoria");
        }
        if (command.montoVerificado() == null || command.montoVerificado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El importe verificado es obligatorio y debe ser mayor a cero");
        }
        String moneda = com.lysandri.config.CuentasBancariasProperties.normalizarIdentificador(command.monedaVerificada());
        if (moneda.isBlank()) {
            throw new IllegalArgumentException("La moneda verificada es obligatoria");
        }
        if (command.fechaAbono() == null) {
            throw new IllegalArgumentException("La fecha del abono bancario es obligatoria");
        }
        if (command.fechaAbono().isAfter(OffsetDateTime.now().plusMinutes(5))) {
            throw new IllegalArgumentException("La fecha del abono no puede ser futura");
        }

        // 4. Verificar importe y moneda contra la orden
        if (command.montoVerificado().compareTo(ordenBloqueada.getTotal()) != 0) {
            throw new IllegalArgumentException("El importe verificado (" + command.montoVerificado()
                    + ") no coincide con el total de la orden (" + ordenBloqueada.getTotal() + ")");
        }
        if (!moneda.equalsIgnoreCase(ordenBloqueada.getMoneda())) {
            throw new IllegalArgumentException("La moneda verificada (" + moneda
                    + ") no coincide con la moneda de la orden (" + ordenBloqueada.getMoneda() + ")");
        }

        // 5. Manejo de repetición / Idempotencia si la orden ya fue aprobada
        // Se evalúa permitiendo cuentas históricas que hayan sido desactivadas con posterioridad
        if (ordenBloqueada.getEstadoOrden() == EstadoOrden.PAGADO) {
            com.lysandri.config.CuentasBancariasProperties.DestinoCanonico destinoHistorico = null;
            try {
                destinoHistorico = cuentasBancariasProperties.resolverDestinoCanonico(
                        metodoActual, command.bancoDestino(), command.cuentaDestino(), moneda, false);
            } catch (Exception ignored) {}

            String opRegistrada = com.lysandri.config.CuentasBancariasProperties.normalizarIdentificador(ordenBloqueada.getNumeroOperacion());
            boolean mismaOperacion = nroOp.equals(opRegistrada);
            String entidadCan = destinoHistorico != null ? destinoHistorico.entidadCanonica() : command.bancoDestino();
            String ctaCan = destinoHistorico != null ? destinoHistorico.cuentaCanonica() : null;
            String cciCan = destinoHistorico != null ? destinoHistorico.cciCanonico() : null;

            boolean mismoBanco = com.lysandri.config.CuentasBancariasProperties.esAliasDeEntidad(command.bancoDestino(), ordenBloqueada.getBancoDestino())
                    || com.lysandri.config.CuentasBancariasProperties.esAliasDeEntidad(ordenBloqueada.getBancoDestino(), entidadCan);
            boolean mismaCuenta = com.lysandri.config.CuentasBancariasProperties.coincideConCuentaOCI(command.cuentaDestino(), ordenBloqueada.getCuentaDestino(), cciCan)
                    || com.lysandri.config.CuentasBancariasProperties.coincideConCuentaOCI(ordenBloqueada.getCuentaDestino(), ctaCan, cciCan);

            if (mismaOperacion && mismoBanco && mismaCuenta) {
                log.info("Aprobación legítima de repetición (idempotente) de la orden {} ya aprobada, aun con variantes de formato.",
                        ordenBloqueada.getCodigoOrden());
                return ordenBloqueada;
            } else {
                throw new IllegalStateException("La orden ya fue pagada previamente con un abono diferente (Operación registrada: "
                        + ordenBloqueada.getNumeroOperacion() + ", Banco: " + ordenBloqueada.getBancoDestino() + ")");
            }
        }

        // 6. Validar que la cuenta de destino corresponda a una cuenta oficial configurada y activa y resolver al destino canónico
        com.lysandri.config.CuentasBancariasProperties.DestinoCanonico destino;
        try {
            destino = cuentasBancariasProperties.validarYResolverDestinoCanonico(metodoActual, command.bancoDestino(), command.cuentaDestino(), moneda);
        } catch (IllegalArgumentException iae) {
            log.warn("Rechazo de confirmación manual: Destino bancario no resoluble o ambiguo en orden {}: {}",
                    ordenBloqueada.getCodigoOrden(), iae.getMessage());
            throw new IllegalArgumentException("No se puede resolver con certeza el destino del abono bancario. " + iae.getMessage(), iae);
        }

        // 7. Búsqueda y protección contra reutilización de abonos históricos con variantes
        // Busca todas las órdenes PAGADAS cuyo número de operación normalizado coincida
        List<Orden> candidatosHistoricos = orderRepository.buscarAbonosHistoricosPorOperacion(nroOp);

        List<Orden> abonosCoincidentes = candidatosHistoricos.stream().filter(candidato -> {
            boolean bancoMatch = com.lysandri.config.CuentasBancariasProperties.esAliasDeEntidad(candidato.getBancoDestino(), destino.entidadCanonica())
                    || com.lysandri.config.CuentasBancariasProperties.esAliasDeEntidad(candidato.getBancoDestino(), command.bancoDestino());
            boolean cuentaMatch = com.lysandri.config.CuentasBancariasProperties.coincideConCuentaOCI(candidato.getCuentaDestino(), destino.cuentaCanonica(), destino.cciCanonico())
                    || com.lysandri.config.CuentasBancariasProperties.coincideConCuentaOCI(candidato.getCuentaDestino(), command.cuentaDestino(), null);
            return bancoMatch && cuentaMatch;
        }).toList();

        // 7.1 Caso de colisión / ambigüedad entre registros históricos:
        // Si dos o más registros históricos pagados distintos convergen al mismo identificador normalizado, bloquear y auditar
        long distinctOrdersCount = abonosCoincidentes.stream().map(Orden::getIdOrden).distinct().count();
        if (distinctOrdersCount > 1) {
            String codigos = abonosCoincidentes.stream().map(Orden::getCodigoOrden).distinct().collect(java.util.stream.Collectors.joining(", "));
            log.error("ANOMALÍA CONTABLE AUDITADA: Se detectaron múltiples órdenes históricas pagadas convergentes ({}) con la misma operación bancaria {} en entidad {}. Operación bloqueada para revisión manual.",
                    codigos, nroOp, destino.entidadCanonica());
            throw new IllegalStateException("Conflicto por ambigüedad contable: existen múltiples registros históricos pagados ("
                    + codigos + ") que convergen a la misma operación bancaria " + nroOp + " en " + destino.entidadCanonica()
                    + ". Se requiere intervención manual y auditoría administrativa.");
        }

        // 7.2 Si existe exactamente un registro coincidente y pertenece a otra orden: rechazar por reutilización de abono
        if (!abonosCoincidentes.isEmpty()) {
            Orden existente = abonosCoincidentes.get(0);
            if (!existente.getIdOrden().equals(ordenBloqueada.getIdOrden())) {
                log.warn("Rechazo por abono duplicado con variantes históricas: El abono [banco: {}, cuenta: {}, op: {}] ya fue aplicado a la orden {} (registrado como banco: '{}', cuenta: '{}')",
                        destino.entidadCanonica(), destino.cuentaCanonica(), nroOp, existente.getCodigoOrden(), existente.getBancoDestino(), existente.getCuentaDestino());
                throw new IllegalStateException("El abono bancario [banco: " + destino.entidadCanonica() + ", cuenta: " + destino.cuentaCanonica()
                        + ", operación: " + nroOp + "] ya fue utilizado para pagar la orden " + existente.getCodigoOrden()
                        + " (registrado con variante banco: '" + existente.getBancoDestino() + "', cuenta: '" + existente.getCuentaDestino() + "')");
            }
        }

        // 7.3 Verificación tradicional por abono canónico exacto como salvaguarda adicional
        Optional<Orden> ordenConMismoAbono = orderRepository.buscarPorAbonoBancario(destino.entidadCanonica(), destino.cuentaCanonica(), nroOp);
        if (ordenConMismoAbono.isPresent() && !ordenConMismoAbono.get().getIdOrden().equals(ordenBloqueada.getIdOrden())) {
            log.warn("Rechazo por abono duplicado canónico: El abono canónico [banco: {}, cuenta: {}, op: {}] ya fue aplicado a la orden {}",
                    destino.entidadCanonica(), destino.cuentaCanonica(), nroOp, ordenConMismoAbono.get().getCodigoOrden());
            throw new IllegalStateException("El abono bancario [banco: " + destino.entidadCanonica() + ", cuenta: " + destino.cuentaCanonica()
                    + ", operación: " + nroOp + "] ya fue utilizado para pagar la orden " + ordenConMismoAbono.get().getCodigoOrden());
        }

        // 8. Aplicar datos de verificación canónicos y transicionar a PAGADO
        ordenBloqueada.setEstadoOrden(EstadoOrden.PAGADO);
        ordenBloqueada.setFechaPago(OffsetDateTime.now());
        ordenBloqueada.setNumeroOperacion(nroOp);
        ordenBloqueada.setBancoDestino(destino.entidadCanonica());
        ordenBloqueada.setCuentaDestino(destino.cuentaCanonica());
        ordenBloqueada.setMontoVerificado(command.montoVerificado());
        ordenBloqueada.setMonedaVerificada(destino.monedaCanonica());
        ordenBloqueada.setFechaAbono(command.fechaAbono());
        ordenBloqueada.setAprobadoPor(command.aprobadoPor() != null && !command.aprobadoPor().isBlank()
                ? command.aprobadoPor().trim()
                : "SISTEMA");
        ordenBloqueada.setFechaAprobacion(OffsetDateTime.now());
        if (command.observacionVerificacion() != null && !command.observacionVerificacion().isBlank()) {
            ordenBloqueada.setObservacionVerificacion(command.observacionVerificacion().trim());
        }
        if (command.notasAdmin() != null && !command.notasAdmin().isBlank()) {
            ordenBloqueada.setNotasAdmin(command.notasAdmin().trim());
        }

        // 8. Registrar Outbox de activación para usuarios pendientes
        if (userRepository != null && userActivationPort != null && activationOutboxService != null
                && ordenBloqueada.getIdUser() != null) {
            userRepository.buscarPorId(ordenBloqueada.getIdUser()).ifPresent(usuario -> {
                boolean requiereActivacion = !usuario.isActivo()
                        || usuario.isPendienteActivacion()
                        || (usuario.getPassw() != null && usuario.getPassw().startsWith("$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH"));

                if (requiereActivacion) {
                    com.lysandri.domain.model.UsuarioActivacionToken tokenActivacion =
                            userActivationPort.crearTokenActivacion(usuario.getIdUser());
                    activationOutboxService.crearTareaActivacion(
                            ordenBloqueada.getIdOrden(),
                            ordenBloqueada.getCodigoOrden(),
                            usuario.getIdUser(),
                            tokenActivacion.getIdToken(),
                            tokenActivacion.getToken(),
                            usuario.getEmail(),
                            usuario.getNombres(),
                            "ACTIVACION_ORDEN_" + ordenBloqueada.getIdOrden()
                    );
                    log.info("Intención de activación persistida duraderamente en Outbox para orden manual {}", ordenBloqueada.getCodigoOrden());
                }
            });
        }

        try {
            Orden ordenGuardada = orderRepository.guardar(ordenBloqueada);
            log.info("Orden manual ID {} (código {}) aprobada y marcada exitosamente como PAGADA. Bloqueo pesimista liberado.",
                    idOrden, ordenBloqueada.getCodigoOrden());
            return ordenGuardada;
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            log.warn("Violación de concurrencia al persistir abono bancario en orden {}: {}", ordenBloqueada.getCodigoOrden(), dive.getMessage());
            throw new IllegalStateException("Conflicto de concurrencia: el abono bancario ya fue registrado por otra transacción simultánea.");
        }
    }

    /**
     * Reserva de forma atómica y exclusiva el derecho de aprovisionar la orden.
     * Utiliza bloqueo pesimista corto para evitar que dos trabajadores ejecuten
     * llamadas HTTP a Moodle, facturación o correos simultáneamente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoReservaAprovisionamiento reservarAprovisionamiento(String codigoOrden) {
        log.info("Adquiriendo bloqueo pesimista para reservar aprovisionamiento de orden: {}", codigoOrden);

        Orden orden = orderRepository.buscarPorCodigoConBloqueo(codigoOrden)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada para reservar aprovisionamiento: " + codigoOrden));

        if (orden.getEstadoOrden() != EstadoOrden.PAGADO) {
            throw new IllegalStateException("No se puede reservar aprovisionamiento para orden en estado: " + orden.getEstadoOrden());
        }

        // 1. Si ya está completado y matriculado
        if (orden.getEstadoAprovisionamiento() == EstadoAprovisionamiento.COMPLETADO
                && orden.isMoodleMatriculaSincronizada()) {
            log.info("Aprovisionamiento ya completado previamente para orden {}. Omitiendo reserva.", codigoOrden);
            return ResultadoReservaAprovisionamiento.yaCompletado(orden);
        }

        // 2. Si está en proceso activo por otro trabajador (lease vigente)
        OffsetDateTime now = OffsetDateTime.now();
        if (orden.getEstadoAprovisionamiento() == EstadoAprovisionamiento.PROCESANDO
                && orden.getAprovisionamientoLeaseExpiresAt() != null
                && orden.getAprovisionamientoLeaseExpiresAt().isAfter(now)) {
            log.warn("Orden {} en aprovisionamiento activo por otro proceso. Lease expira en: {}",
                    codigoOrden, orden.getAprovisionamientoLeaseExpiresAt());
            return ResultadoReservaAprovisionamiento.enProceso(orden);
        }

        // 3. Adquirir o recuperar lease (estado PENDIENTE, FALLIDO o lease expirado)
        String ownerToken = UUID.randomUUID().toString();
        orden.setEstadoAprovisionamiento(EstadoAprovisionamiento.PROCESANDO);
        orden.setAprovisionamientoLeaseToken(ownerToken);
        orden.setAprovisionamientoLeaseExpiresAt(now.plusMinutes(5));
        orden.setAprovisionamientoIntentos(orden.getAprovisionamientoIntentos() + 1);

        Orden guardada = orderRepository.guardar(orden);
        log.info("Lease de aprovisionamiento reservado exitosamente para orden {}. Token: {}, Intento: {}",
                codigoOrden, ownerToken, guardada.getAprovisionamientoIntentos());

        return ResultadoReservaAprovisionamiento.reservado(guardada, ownerToken);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orden completarAprovisionamiento(
            String codigoOrden,
            String ownerToken,
            boolean allEnrolled,
            List<Long> programasMatriculadosIds) {
        return completarAprovisionamiento(codigoOrden, ownerToken, allEnrolled, programasMatriculadosIds, null);
    }

    /**
     * Completa el proceso de aprovisionamiento persistiendo el resultado en PostgreSQL
     * y liberando el arrendamiento (lease), validando la posesión del token de propietario.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orden completarAprovisionamiento(
            String codigoOrden,
            String ownerToken,
            boolean allEnrolled,
            List<Long> programasMatriculadosIds,
            String errorMensaje) {

        log.info("Completando aprovisionamiento para orden {} (allEnrolled={})", codigoOrden, allEnrolled);

        Orden orden = orderRepository.buscarPorCodigoConBloqueo(codigoOrden)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada al completar aprovisionamiento: " + codigoOrden));

        // Validar propiedad del lease
        if (!Objects.equals(ownerToken, orden.getAprovisionamientoLeaseToken())) {
            log.error("Pérdida de propiedad del lease de aprovisionamiento para orden {}. Token actual: {}, Token esperado: {}",
                    codigoOrden, orden.getAprovisionamientoLeaseToken(), ownerToken);
            throw new IllegalStateException("Pérdida de propiedad: el arrendamiento de la orden expiró o fue transferido.");
        }

        orden.setMoodleMatriculaSincronizada(allEnrolled);
        orden.setEstadoAprovisionamiento(allEnrolled ? EstadoAprovisionamiento.COMPLETADO : EstadoAprovisionamiento.FALLIDO);
        orden.setAprovisionamientoLeaseToken(null);
        orden.setAprovisionamientoLeaseExpiresAt(null);
        String sanitizedError = sanitizeError(errorMensaje);
        orden.setAprovisionamientoUltimoError(allEnrolled ? null : (sanitizedError != null ? sanitizedError : "Fallo en aprovisionamiento Moodle (HTTP 504 Gateway Timeout o error de conexión)"));

        if (orden.getItems() != null && programasMatriculadosIds != null && !programasMatriculadosIds.isEmpty()) {
            for (DetalleOrden item : orden.getItems()) {
                if (programasMatriculadosIds.contains(item.getIdPrograma())) {
                    item.setMoodleMatriculado(true);
                }
            }
        }

        Orden guardada = orderRepository.guardar(orden);
        log.info("Aprovisionamiento de orden {} persistido como {}. Lease liberado.",
                codigoOrden, guardada.getEstadoAprovisionamiento());
        return guardada;
    }

    /**
     * Registra un fallo durante el aprovisionamiento y libera el lease para reintento posterior.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orden registrarFalloAprovisionamiento(String codigoOrden, String ownerToken, String errorMensaje) {
        String sanitizedError = sanitizeError(errorMensaje);
        log.warn("Registrando fallo de aprovisionamiento para orden {}: {}", codigoOrden, sanitizedError);

        Orden orden = orderRepository.buscarPorCodigoConBloqueo(codigoOrden)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada al registrar fallo de aprovisionamiento: " + codigoOrden));

        if (ownerToken != null && !Objects.equals(ownerToken, orden.getAprovisionamientoLeaseToken())) {
            log.warn("Token de lease no coincide al registrar fallo de orden {}. Se conserva estado actual.", codigoOrden);
            return orden;
        }

        orden.setEstadoAprovisionamiento(EstadoAprovisionamiento.FALLIDO);
        orden.setAprovisionamientoLeaseToken(null);
        orden.setAprovisionamientoLeaseExpiresAt(null);
        orden.setAprovisionamientoUltimoError(sanitizedError);

        return orderRepository.guardar(orden);
    }

    private String sanitizeError(String error) {
        if (error == null) return null;
        String sanitized = error.replaceAll("(?i)(//[^:/@]+:)[^@]+@", "$1***@");
        sanitized = sanitized.replaceAll("(?i)([?&](?:password|secret|token|passw|apikey|key|wstoken)=)[^&\\s'\"]*", "$1***");
        sanitized = sanitized.replaceAll("(?i)((?:password|secret|token|passw|wstoken)\\s*[:=]\\s*)[^\\s,;'\"]+", "$1***");
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 1000) + "...";
        }
        return sanitized;
    }

    /**
     * Centraliza las transiciones de pago fallido o expirado protegiendo órdenes
     * que ya se encuentren confirmadas como PAGADAS ante eventos tardíos o desordenados.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orden registrarPagoFallidoOExpirado(String codigoOrden, String transaccionId, EstadoOrden nuevoEstado, String motivo) {
        log.info("Procesando evento de pago fallido o expirado para orden {}: Estado={} Motivo={}",
                codigoOrden, nuevoEstado, motivo);

        Orden orden = orderRepository.buscarPorCodigoConBloqueo(codigoOrden)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada para registrar estado: " + codigoOrden));

        // Regla inquebrantable: Ningún evento tardío puede revertir una orden PAGADA
        if (orden.getEstadoOrden() == EstadoOrden.PAGADO) {
            log.warn("PROTECCIÓN FINANCIERA: Ignorando evento tardío {} para orden {}: ya se encuentra confirmada como PAGADA",
                    nuevoEstado, codigoOrden);
            return orden;
        }

        // Solo permitir transición desde PENDIENTE
        if (orden.getEstadoOrden() == EstadoOrden.PENDIENTE) {
            orden.setEstadoOrden(nuevoEstado);
            orden.setNotasAdmin(motivo);
            orden = orderRepository.guardar(orden);
            log.warn("Orden {} actualizada exitosamente a {} por evento financiero {}",
                    codigoOrden, nuevoEstado, transaccionId);
        }

        return orden;
    }

    /**
     * Actualiza el resultado del aprovisionamiento en Moodle LMS (conservado para compatibilidad).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Orden actualizarResultadoAprovisionamiento(String codigoOrden, boolean moodleSincronizada, List<Long> programasMatriculadosIds) {
        log.info("Actualizando estado de matrícula Moodle para orden {}: sincronizada={}", codigoOrden, moodleSincronizada);

        Orden orden = orderRepository.buscarPorCodigo(codigoOrden)
                .orElseThrow(() -> new IllegalStateException("Orden no encontrada para actualizar sincronización: " + codigoOrden));

        orden.setMoodleMatriculaSincronizada(moodleSincronizada);

        if (orden.getItems() != null && programasMatriculadosIds != null && !programasMatriculadosIds.isEmpty()) {
            for (DetalleOrden item : orden.getItems()) {
                if (programasMatriculadosIds.contains(item.getIdPrograma())) {
                    item.setMoodleMatriculado(true);
                }
            }
        }

        return orderRepository.guardar(orden);
    }
}
