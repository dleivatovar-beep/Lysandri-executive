package com.lysandri.application.service;

import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.LmsClientPort;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import com.lysandri.domain.ports.out.PaymentPort;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyCourseService implements BuyCourseUseCase {

    private final OrderRepositoryPort orderRepository;
    private final UserRepositoryPort userRepository;
    private final ProgramRepositoryPort programRepository;
    private final PaymentPort paymentPort;
    private final LmsClientPort lmsClientPort;

    @Override
    @Transactional
    public CheckoutSessionResponse iniciarCompra(CreateOrderCommand command) {
        log.info("Iniciando compra para usuario ID: {} con programas: {}", command.idUsuario(), command.programaIds());

        Usuario usuario = userRepository.buscarPorId(command.idUsuario())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + command.idUsuario()));

        if (command.programaIds() == null || command.programaIds().isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un programa para comprar");
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
        Orden orden = Orden.builder()
                .idUser(usuario.getIdUser())
                .usuario(usuario)
                .codigoOrden(codigoOrden)
                .fechaOrden(OffsetDateTime.now())
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(total)
                .moneda("USD")
                .metodoPago("STRIPE")
                .moodleMatriculaSincronizada(false)
                .items(items)
                .build();

        orden = orderRepository.guardar(orden);

        // Crear la sesión en Stripe
        PaymentPort.PaymentSessionResult sessionResult = paymentPort.crearSesionPago(
                orden,
                usuario.getEmail(),
                usuario.getNombreCompleto()
        );

        orden.setStripeSessionId(sessionResult.sessionId());
        orden.setStripePaymentIntentId(sessionResult.paymentIntentId());
        orderRepository.guardar(orden);

        log.info("Orden {} creada con éxito. Sesión Stripe: {}", codigoOrden, sessionResult.sessionId());

        return new CheckoutSessionResponse(
                codigoOrden,
                sessionResult.checkoutUrl(),
                sessionResult.sessionId()
        );
    }

    @Override
    @Transactional
    public Orden confirmarPagoYMatricular(String stripeSessionId) {
        log.info("Confirmando pago y procesando matrículas para sesión Stripe: {}", stripeSessionId);

        Orden orden = orderRepository.buscarPorStripeSessionId(stripeSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada para la sesión de pago: " + stripeSessionId));

        if (orden.getEstadoOrden() == EstadoOrden.PAGADO && orden.isMoodleMatriculaSincronizada()) {
            log.info("La orden {} ya fue procesada y matriculada previamente.", orden.getCodigoOrden());
            return orden;
        }

        Usuario usuario = userRepository.buscarPorId(orden.getIdUser())
                .orElseThrow(() -> new IllegalStateException("Usuario asociado a la orden no existe"));

        // 1. Asegurar usuario en Moodle LMS
        Long moodleUserId = usuario.getMoodleUserId();
        if (moodleUserId == null) {
            moodleUserId = lmsClientPort.buscarUsuarioPorEmail(usuario.getEmail())
                    .orElseGet(() -> {
                        String defaultPassword = "Lysandri#" + UUID.randomUUID().toString().substring(0, 8);
                        String username = usuario.getEmail().toLowerCase().replaceAll("[^a-z0-9._-]", "");
                        return lmsClientPort.crearUsuario(
                                username,
                                defaultPassword,
                                usuario.getNombres(),
                                usuario.getApellidos(),
                                usuario.getEmail()
                        );
                    });

            usuario.setMoodleUserId(moodleUserId);
            userRepository.guardar(usuario);
            log.info("ID de Moodle {} sincronizado para usuario {}", moodleUserId, usuario.getEmail());
        }

        // 2. Matricular en cada curso de Moodle correspondiente
        for (DetalleOrden detalle : orden.getItems()) {
            Programa programa = detalle.getPrograma() != null ? detalle.getPrograma() :
                    programRepository.buscarPorId(detalle.getIdPrograma()).orElse(null);

            if (programa != null && programa.getMoodleCourseId() != null) {
                try {
                    lmsClientPort.matricularUsuarioEnCurso(moodleUserId, programa.getMoodleCourseId(), 5); // Rol 5 = Student
                    detalle.setMoodleMatriculado(true);
                    log.info("Usuario {} matriculado exitosamente en curso Moodle {}", moodleUserId, programa.getMoodleCourseId());
                } catch (Exception e) {
                    log.error("Fallo al matricular en curso Moodle {}: {}", programa.getMoodleCourseId(), e.getMessage());
                }
            }
        }

        orden.setEstadoOrden(EstadoOrden.PAGADO);
        orden.setMoodleMatriculaSincronizada(true);
        orden.setFechaPago(OffsetDateTime.now());

        return orderRepository.guardar(orden);
    }

    @Override
    @Transactional(readOnly = true)
    public Orden obtenerOrdenPorCodigo(String codigoOrden) {
        return orderRepository.buscarPorCodigo(codigoOrden)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada: " + codigoOrden));
    }
}
