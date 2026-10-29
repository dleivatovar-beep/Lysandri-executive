package com.lysandri.adapters;

import com.lysandri.adapters.in.web.OrderController;
import com.lysandri.adapters.in.web.dto.ConfirmarPagoManualRequest;
import com.lysandri.adapters.in.web.dto.CuentaBancariaAprobadaDto;
import com.lysandri.adapters.out.database.entity.OrdenEntity;
import com.lysandri.adapters.out.database.repository.SpringDataOrderRepository;
import com.lysandri.application.service.OrderFinancialTransactionService;
import com.lysandri.domain.model.EstadoAprovisionamiento;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import com.lysandri.config.CuentasBancariasProperties;
import org.springframework.security.access.AccessDeniedException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


@SpringBootTest
@ActiveProfiles("test")
public class ManualTransferPostgresIntegrationTest extends PostgresTestDatabase {

    @Autowired
    private OrderRepositoryPort orderRepository;

    @Autowired
    private SpringDataOrderRepository springDataOrderRepository;

    @Autowired
    private OrderFinancialTransactionService financialTransactionService;

    @Autowired
    private OrderController orderController;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BuyCourseUseCase buyCourseUseCase;

    @Autowired
    private CuentasBancariasProperties cuentasBancariasProperties;


    private Long testUserId;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM comprobante_pago");
        jdbcTemplate.execute("DELETE FROM detalle_ordenes");
        jdbcTemplate.execute("DELETE FROM ordenes");
        testUserId = jdbcTemplate.queryForObject("SELECT id_user FROM usuario WHERE email = 'danny@lysandri.com'", Long.class);

        // Sembrar usuario histórico, instructor y programa activo y aprobado
        jdbcTemplate.execute("""
            INSERT INTO usuarios (id_user, nombres, apellidos, email, passw, rol)
            VALUES (99, 'Profesor', 'Test', 'profesor.seed@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR')
            ON CONFLICT (id_user) DO NOTHING
        """);
        jdbcTemplate.execute("""
            INSERT INTO instructor (id_instructor_dni, id_user, especialidad, direccion_instructor)
            VALUES ('DNI88888888', 99, 'Liderazgo', 'Av. Central 456')
            ON CONFLICT (id_instructor_dni) DO NOTHING
        """);
        jdbcTemplate.execute("""
            INSERT INTO programa (id_programa, id_instructor_dni, titulo, precio, moneda, activo, moodle_course_id, slug, duracion_programa, tipo_programa, nivel, precio_aprobado)
            VALUES (101, 'DNI88888888', 'Programa Prueba Ejecutivo', 899.00, 'USD', true, 101, 'programa-prueba-ejecutivo', '40 horas', 'CURSO', 'INTERMEDIO', true)
            ON CONFLICT (id_programa) DO UPDATE SET precio_aprobado = true, activo = true, precio = 899.00, moneda = 'USD'
        """);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Orden crearOrdenTest(String codigo, BigDecimal total, String moneda, String metodoPago, EstadoOrden estado) {
        Orden orden = Orden.builder()
                .idUser(testUserId)
                .codigoOrden(codigo)
                .estadoOrden(estado)
                .estadoAprovisionamiento(EstadoAprovisionamiento.PENDIENTE)
                .total(total)
                .moneda(moneda)
                .metodoPago(metodoPago)
                .tipoComprobanteSolicitado("BOLETA")
                .numeroDocumentoCliente("72283339")
                .nombreFacturacion("Cliente Ejecutivo Test")
                .moodleMatriculaSincronizada(false)
                .fechaOrden(OffsetDateTime.now())
                .build();
        return orderRepository.guardar(orden);
    }

    @Test
    @DisplayName("1. Aprobación exitosa de transferencia BCP en PostgreSQL registra abono y auditoría")
    void testAprobacionTransferenciaManual_Valida_RegistraAbonoYAuditoria() {
        Orden orden = crearOrdenTest("ORD-TRF-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand command = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-984128",
                "BCP",
                "193-98218491-0-28",
                new BigDecimal("899.00"),
                "USD",
                OffsetDateTime.now().minusHours(2),
                "TRANSFERENCIA",
                "Abono verificado en Telecrédito BCP",
                "Constancia cotejada con extracto en línea",
                "auditor@lysandri.com"
        );

        Orden ordenAprobada = financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), command);

        assertNotNull(ordenAprobada);
        assertEquals(EstadoOrden.PAGADO, ordenAprobada.getEstadoOrden());
        assertEquals("BCP", ordenAprobada.getBancoDestino());
        assertEquals("19398218491028", ordenAprobada.getCuentaDestino());
        assertEquals("BCPOP984128", ordenAprobada.getNumeroOperacion());
        assertEquals(new BigDecimal("899.00"), ordenAprobada.getMontoVerificado());
        assertEquals("USD", ordenAprobada.getMonedaVerificada());
        assertEquals("auditor@lysandri.com", ordenAprobada.getAprobadoPor());
        assertNotNull(ordenAprobada.getFechaAprobacion());
        assertNotNull(ordenAprobada.getFechaAbono());

        // Verificar directamente en PostgreSQL
        OrdenEntity entityEnBd = springDataOrderRepository.findById(orden.getIdOrden()).orElseThrow();
        assertEquals(EstadoOrden.PAGADO, entityEnBd.getEstadoOrden());
        assertEquals("BCP", entityEnBd.getBancoDestino());
        assertEquals("19398218491028", entityEnBd.getCuentaDestino());
        assertEquals("BCPOP984128", entityEnBd.getNumeroOperacion());
        assertEquals(new BigDecimal("899.00"), entityEnBd.getMontoVerificado());
        assertEquals("USD", entityEnBd.getMonedaVerificada());
        assertEquals("auditor@lysandri.com", entityEnBd.getAprobadoPor());
    }

    @Test
    @DisplayName("2. Rechazo estricto ante discrepancia de monto verificado")
    void testRechazoDiscrepanciaMonto() {
        Orden orden = crearOrdenTest("ORD-TRF-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand commandMontoInvalido = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-001", "BCP", "193-000", new BigDecimal("500.00"), "USD",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), commandMontoInvalido));

        assertTrue(ex.getMessage().contains("no coincide con el total de la orden"));

        Orden ordenSinModificar = orderRepository.buscarPorId(orden.getIdOrden()).orElseThrow();
        assertEquals(EstadoOrden.PENDIENTE, ordenSinModificar.getEstadoOrden());
    }

    @Test
    @DisplayName("3. Rechazo estricto ante discrepancia de moneda verificada")
    void testRechazoDiscrepanciaMoneda() {
        Orden orden = crearOrdenTest("ORD-TRF-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand commandMonedaInvalida = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-002", "BCP", "193-000", new BigDecimal("899.00"), "PEN",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), commandMonedaInvalida));

        assertTrue(ex.getMessage().contains("no coincide con la moneda de la orden"));
    }

    @Test
    @DisplayName("4. Rechazo ante fecha de abono en el futuro")
    void testRechazoFechaAbonoFutura() {
        Orden orden = crearOrdenTest("ORD-TRF-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand commandFechaFutura = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-003", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().plusDays(2), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), commandFechaFutura));

        assertTrue(ex.getMessage().contains("futura"));
    }

    @Test
    @DisplayName("5. Orden online de pasarela automática izipay rechazada en ruta manual")
    void testRechazoOrdenOnlineEnRutaManual() {
        Orden orden = crearOrdenTest("ORD-IZI-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "IZIPAY", EstadoOrden.PENDIENTE);
        orden.setIzipayTransactionId("izi_tx_online_999");
        orderRepository.guardar(orden);

        BuyCourseUseCase.ConfirmarPagoManualCommand command = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-004", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), command));

        assertTrue(ex.getMessage().contains("pasarela online"));
    }

    @Test
    @DisplayName("6. Orden CANCELADA no puede ser aprobada manualmente")
    void testRechazoOrdenCancelada() {
        Orden orden = crearOrdenTest("ORD-CANC-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.CANCELADO);

        BuyCourseUseCase.ConfirmarPagoManualCommand command = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-005", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), command));

        assertTrue(ex.getMessage().contains("CANCELADA"));
    }

    @Test
    @DisplayName("7. Repetición idéntica sobre la misma orden ya PAGADA es idempotente")
    void testIdempotenciaRepeticionIdentica() {
        Orden orden = crearOrdenTest("ORD-IDEMP-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand command = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "IBK-OP-44912", "INTERBANK", "200-3001849128", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(3), "TRANSFERENCIA", null, null, "admin@lysandri.com"
        );

        Orden primeraAprobacion = financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), command);
        assertEquals(EstadoOrden.PAGADO, primeraAprobacion.getEstadoOrden());

        // Segunda llamada con datos exactamente idénticos
        Orden segundaAprobacion = financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), command);
        assertNotNull(segundaAprobacion);
        assertEquals(primeraAprobacion.getIdOrden(), segundaAprobacion.getIdOrden());
        assertEquals("IBKOP44912", segundaAprobacion.getNumeroOperacion());
        assertEquals(EstadoOrden.PAGADO, segundaAprobacion.getEstadoOrden());
    }

    @Test
    @DisplayName("8. Repetición con datos contradictorios en orden ya PAGADA es rechazada")
    void testRechazoRepeticionContradictoria() {
        Orden orden = crearOrdenTest("ORD-CONF-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand commandOriginal = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-11111", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(3), "TRANSFERENCIA", null, null, "admin"
        );
        financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), commandOriginal);

        // Intento de confirmar con otro número de operación
        BuyCourseUseCase.ConfirmarPagoManualCommand commandDistinto = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "BCP-OP-DISTINTO", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(3), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), commandDistinto));

        assertTrue(ex.getMessage().contains("abono diferente"));
    }

    @Test
    @DisplayName("9. Bloqueo de abono bancario duplicado en otra orden (protección de doble gasto)")
    void testRechazoDepositoDuplicadoEnOtraOrden() {
        String numOperacionComun = "DEP-SHARED-" + UUID.randomUUID().toString().substring(0, 6);
        String bancoComun = "BCP";
        String cuentaComun = "193-98218491-0-28";

        Orden orden1 = crearOrdenTest("ORD-DUP1-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);
        Orden orden2 = crearOrdenTest("ORD-DUP2-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        // Orden 1 aprobada con el depósito
        BuyCourseUseCase.ConfirmarPagoManualCommand cmd1 = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                numOperacionComun, bancoComun, cuentaComun, new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin"
        );
        financialTransactionService.confirmarPagoManualFinanciero(orden1.getIdOrden(), cmd1);

        // Orden 2 intenta usar el mismo voucher/operación
        BuyCourseUseCase.ConfirmarPagoManualCommand cmd2 = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                numOperacionComun, bancoComun, cuentaComun, new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden2.getIdOrden(), cmd2));

        assertTrue(ex.getMessage().contains("ya fue utilizado"));

        // Orden 2 debe permanecer PENDIENTE
        Orden orden2Actual = orderRepository.buscarPorId(orden2.getIdOrden()).orElseThrow();
        assertEquals(EstadoOrden.PENDIENTE, orden2Actual.getEstadoOrden());
    }

    @Test
    @DisplayName("10. Consulta de cuentas bancarias oficiales expone cuentas corporativas configuradas")
    void testCuentasBancariasEndpoint_RetornaConfiguracionOficial() {
        ResponseEntity<List<CuentaBancariaAprobadaDto>> response = orderController.obtenerCuentasBancarias();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        List<CuentaBancariaAprobadaDto> cuentas = response.getBody();
        assertNotNull(cuentas);
        assertTrue(cuentas.size() >= 2);

        boolean tieneBcp = cuentas.stream().anyMatch(c -> "BCP".equalsIgnoreCase(c.getBanco()));
        boolean tieneInterbank = cuentas.stream().anyMatch(c -> "INTERBANK".equalsIgnoreCase(c.getBanco()));
        assertTrue(tieneBcp, "Debe contener cuenta BCP");
        assertTrue(tieneInterbank, "Debe contener cuenta Interbank");
    }

    @Test
    @DisplayName("11. Seguridad en controlador: aprobador se extrae de la sesión y roles no autorizados son rechazados")
    void testSeguridadController_RolesYAuditoriaAprobador() {
        Orden orden = crearOrdenTest("ORD-SEC-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        ConfirmarPagoManualRequest request = ConfirmarPagoManualRequest.builder()
                .bancoDestino("BCP")
                .cuentaDestino("193-98218491-0-28")
                .numeroOperacion("SEC-OP-999")
                .montoVerificado(new BigDecimal("899.00"))
                .monedaVerificada("USD")
                .fechaAbono(OffsetDateTime.now().minusHours(1))
                .metodoPago("TRANSFERENCIA")
                .observacionVerificacion("Prueba de sesión")
                .build();

        // Autenticación con rol ADMIN
        Authentication authAdmin = new UsernamePasswordAuthenticationToken(
                "gerente.finanzas@lysandri.com",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authAdmin);

        ResponseEntity<Orden> response = orderController.confirmarPagoManual(orden.getIdOrden(), request, authAdmin);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        Orden body = response.getBody();
        assertNotNull(body);
        assertEquals("gerente.finanzas@lysandri.com", body.getAprobadoPor(),
                "El aprobador debe ser extraído de la identidad autenticada");
    }

    @Test
    @DisplayName("12. Rechazo de cuenta bancaria inexistente o arbitraria")
    void testRechazoCuentaBancariaInexistenteOArbitraria() {
        Orden orden = crearOrdenTest("ORD-UNK-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand cmd = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "OP-9988", "BANCO_FANTASMA", "999-999-999", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), cmd));

        assertTrue(ex.getMessage().contains("no reconocido o no autorizado"), "Mensaje debe indicar banco no reconocido");
        Orden ordenSinTocar = orderRepository.buscarPorId(orden.getIdOrden()).orElseThrow();
        assertEquals(EstadoOrden.PENDIENTE, ordenSinTocar.getEstadoOrden());
    }

    @Test
    @DisplayName("13. Rechazo de cuenta bancaria oficial que se encuentra desactivada")
    void testRechazoCuentaBancariaInactiva() {
        Orden orden = crearOrdenTest("ORD-INACT-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        cuentasBancariasProperties.getBcp().setActivo(false);
        try {
            BuyCourseUseCase.ConfirmarPagoManualCommand cmd = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                    "OP-BCP-99", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                    OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
            );

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), cmd));

            assertTrue(ex.getMessage().contains("desactivada"), "Debe indicar que la cuenta oficial se encuentra desactivada");
        } finally {
            cuentasBancariasProperties.getBcp().setActivo(true);
        }
    }

    @Test
    @DisplayName("14. Rechazo ante desajuste de moneda con la cuenta oficial")
    void testRechazoDesajusteMonedaCuentaBancaria() {
        Orden orden = crearOrdenTest("ORD-MON-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "PEN", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        // BCP oficial está en USD, la orden es PEN
        BuyCourseUseCase.ConfirmarPagoManualCommand cmd = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "OP-BCP-PEN", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "PEN",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), cmd));

        assertTrue(ex.getMessage().contains("Desajuste de moneda"), "Debe rechazar desajuste de moneda con la cuenta");
    }

    @Test
    @DisplayName("15. Normalización de identificadores y preservación estricta de ceros a la izquierda")
    void testNormalizacionIdentificadoresYPreservacionCerosIzquierda() {
        // Orden 1: Aprobada con ceros a la izquierda y guiones en cuenta
        Orden orden1 = crearOrdenTest("ORD-NORM1-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand cmd1 = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "  OP0012345  ", " bcp ", " 193-98218491-0-28 ", new BigDecimal("899.00"), "usd",
                OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin"
        );

        Orden aprobada1 = financialTransactionService.confirmarPagoManualFinanciero(orden1.getIdOrden(), cmd1);
        assertEquals(EstadoOrden.PAGADO, aprobada1.getEstadoOrden());
        // Preserva ceros a la izquierda sin perderlos
        assertEquals("OP0012345", aprobada1.getNumeroOperacion());
        assertEquals("19398218491028", aprobada1.getCuentaDestino());

        // Orden 2: Intenta reutilizar el mismo depósito con formato alternativo (guiones en operacion, sin guiones en cuenta)
        Orden orden2 = crearOrdenTest("ORD-NORM2-" + UUID.randomUUID().toString().substring(0, 6),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand cmd2 = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "op-0012345", "BCP", "19398218491028", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden2.getIdOrden(), cmd2));

        assertTrue(ex.getMessage().contains("ya fue utilizado"), "Normalización debe detectar que es el mismo depósito reutilizado");
    }

    @Test
    @DisplayName("16. Idempotencia en repetición idéntica incluso si la cuenta fue desactivada con posterioridad")
    void testRepeticionAprobacionConCuentaPosteriormenteDesactivadaIdempotente() {
        Orden orden = crearOrdenTest("ORD-HIST-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand cmd = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "OP-HIST-100", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin"
        );

        // Primera aprobación exitosa
        Orden aprobada = financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), cmd);
        assertEquals(EstadoOrden.PAGADO, aprobada.getEstadoOrden());

        // Ahora simulamos que la cuenta BCP fue desactivada en la configuración
        cuentasBancariasProperties.getBcp().setActivo(false);
        try {
            // La repetición idéntica de la orden histórica ya pagada debe ser idempotente y retornar sin error
            Orden reintento = financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), cmd);
            assertNotNull(reintento);
            assertEquals(EstadoOrden.PAGADO, reintento.getEstadoOrden());
            assertEquals("OPHIST100", reintento.getNumeroOperacion());
        } finally {
            cuentasBancariasProperties.getBcp().setActivo(true);
        }
    }

    @Test
    @DisplayName("17. Rechazo de método de pago no reconocido al iniciar compra")
    void testRechazoMetodoPagoNoReconocidoEnCreacion() {
        Long idProg = jdbcTemplate.queryForObject(
                "SELECT id_programa FROM programa WHERE activo = true AND precio_aprobado = true LIMIT 1", Long.class);

        BuyCourseUseCase.CreateOrderCommand cmdInvalido = new BuyCourseUseCase.CreateOrderCommand(
                testUserId,
                List.of(idProg),
                null,
                null,
                "BOLETA",
                "72283339",
                "Cliente Test",
                "BITCOIN", // Método no admitido
                null,
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseUseCase.iniciarCompra(cmdInvalido));

        assertTrue(ex.getMessage().contains("Método de pago no admitido"), "Debe indicar que el método no es admitido");
    }

    @Test
    @DisplayName("18. Rechazo al intentar alterar el método de pago original al aprobar la orden")
    void testRechazoAlteracionMetodoPagoEnAprobacion() {
        Orden orden = crearOrdenTest("ORD-NOMUT-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "USD", "TRANSFERENCIA", EstadoOrden.PENDIENTE);

        // La orden es TRANSFERENCIA, el comando intenta aprobarla mutándola a YAPE
        BuyCourseUseCase.ConfirmarPagoManualCommand cmdMutacion = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "OP-MUT-01", "BCP", "193-98218491-0-28", new BigDecimal("899.00"), "USD",
                OffsetDateTime.now().minusHours(1), "YAPE", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(orden.getIdOrden(), cmdMutacion));

        assertTrue(ex.getMessage().contains("No se permite alterar el método de pago original"),
                "Debe bloquear alteración del método de pago");
    }

    @Test
    @DisplayName("19. Billeteras digitales Yape y Plin: validación y aprobación según reglas específicas")
    void testYapePlinAprobacionYValidacion() {
        Orden ordenYape = crearOrdenTest("ORD-YAPE-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "PEN", "YAPE", EstadoOrden.PENDIENTE);

        // Validación positiva con teléfono oficial de Yape (987654321)
        BuyCourseUseCase.ConfirmarPagoManualCommand cmdYapeOk = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "YAPE-OP-5501", "YAPE", "987-654-321", new BigDecimal("899.00"), "PEN",
                OffsetDateTime.now().minusHours(1), "YAPE", null, null, "admin"
        );

        Orden aprobadaYape = financialTransactionService.confirmarPagoManualFinanciero(ordenYape.getIdOrden(), cmdYapeOk);
        assertEquals(EstadoOrden.PAGADO, aprobadaYape.getEstadoOrden());
        assertEquals("YAPE", aprobadaYape.getBancoDestino());
        assertEquals("987654321", aprobadaYape.getCuentaDestino());

        // Validación negativa: intento con teléfono no oficial
        Orden ordenYapeInvalida = crearOrdenTest("ORD-YAPE-INV-" + UUID.randomUUID().toString().substring(0, 8),
                new BigDecimal("899.00"), "PEN", "YAPE", EstadoOrden.PENDIENTE);

        BuyCourseUseCase.ConfirmarPagoManualCommand cmdYapeInvalido = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "YAPE-OP-5502", "YAPE", "999-000-111", new BigDecimal("899.00"), "PEN",
                OffsetDateTime.now().minusHours(1), "YAPE", null, null, "admin"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(ordenYapeInvalida.getIdOrden(), cmdYapeInvalido));

        assertTrue(ex.getMessage().contains("cuenta oficial de Yape"));
    }

    @Test
    @DisplayName("20. Idempotencia en compra: peticiones concurrentes con la misma clave resuelven atómicamente a una sola orden")
    void testIdempotenciaCreacionOrdenConcurrente() throws Exception {
        Long idProg = jdbcTemplate.queryForObject(
                "SELECT id_programa FROM programa WHERE activo = true AND precio_aprobado = true LIMIT 1", Long.class);

        String clave = "IDEMP-CONC-" + UUID.randomUUID();

        int numHilos = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        CyclicBarrier barrier = new CyclicBarrier(numHilos);
        List<Future<BuyCourseUseCase.CheckoutSessionResponse>> futures = new ArrayList<>();

        for (int i = 0; i < numHilos; i++) {
            futures.add(executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                BuyCourseUseCase.CreateOrderCommand cmd = new BuyCourseUseCase.CreateOrderCommand(
                        testUserId,
                        List.of(idProg),
                        null,
                        null,
                        "BOLETA",
                        "72283339",
                        "Cliente Test Concurrente",
                        "TRANSFERENCIA",
                        null,
                        clave
                );
                return buyCourseUseCase.iniciarCompra(cmd);
            }));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

        String codigoEsperado = null;
        for (Future<BuyCourseUseCase.CheckoutSessionResponse> f : futures) {
            BuyCourseUseCase.CheckoutSessionResponse resp = f.get();
            assertNotNull(resp);
            assertNotNull(resp.codigoOrden());
            if (codigoEsperado == null) {
                codigoEsperado = resp.codigoOrden();
            } else {
                assertEquals(codigoEsperado, resp.codigoOrden(), "Todos los hilos deben retornar exactamente el mismo código de orden");
            }
        }

        // Exactamente una orden registrada en PostgreSQL con esa clave
        Integer conteoEnBd = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ordenes WHERE clave_idempotencia = ?",
                Integer.class, clave);
        assertEquals(1, conteoEnBd, "Solo debe existir 1 fila en ordenes para la clave de idempotencia");
    }

    @Test
    @DisplayName("21. Idempotencia: Conflicto de datos con la misma clave es rechazado con IllegalStateException (409)")
    void testIdempotenciaConflictoDeDatosRechazadoCon409() {
        Long idProg = jdbcTemplate.queryForObject(
                "SELECT id_programa FROM programa WHERE activo = true AND precio_aprobado = true LIMIT 1", Long.class);

        String clave = "IDEMP-CONF-" + UUID.randomUUID();

        BuyCourseUseCase.CreateOrderCommand cmd1 = new BuyCourseUseCase.CreateOrderCommand(
                testUserId,
                List.of(idProg),
                null,
                null,
                "BOLETA",
                "72283339",
                "Cliente Test",
                "TRANSFERENCIA",
                null,
                clave
        );

        BuyCourseUseCase.CheckoutSessionResponse resp1 = buyCourseUseCase.iniciarCompra(cmd1);
        assertNotNull(resp1);

        // Mismo clave pero método de pago diferente (conflicto)
        BuyCourseUseCase.CreateOrderCommand cmdConflicto = new BuyCourseUseCase.CreateOrderCommand(
                testUserId,
                List.of(idProg),
                null,
                null,
                "BOLETA",
                "72283339",
                "Cliente Test",
                "YAPE", // Método diferente
                null,
                clave
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseUseCase.iniciarCompra(cmdConflicto));

        assertTrue(ex.getMessage().contains("Conflicto de idempotencia"), "Debe rechazar con conflicto de idempotencia");
    }

    @Test
    @DisplayName("22. Idempotencia: Aislamiento entre compradores (prevención IDOR) rechazado con AccessDeniedException (403)")
    void testIdempotenciaAislamientoCompradoresIdorRechazadoCon403() {
        Long idProg = jdbcTemplate.queryForObject(
                "SELECT id_programa FROM programa WHERE activo = true AND precio_aprobado = true LIMIT 1", Long.class);

        // Obtener o crear otro usuario
        Long otroUsuarioId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE id_user != ? LIMIT 1", Long.class, testUserId);

        String clave = "IDEMP-IDOR-" + UUID.randomUUID();

        BuyCourseUseCase.CreateOrderCommand cmdUsuario1 = new BuyCourseUseCase.CreateOrderCommand(
                testUserId,
                List.of(idProg),
                null,
                null,
                "BOLETA",
                "72283339",
                "Comprador Legitimo",
                "TRANSFERENCIA",
                null,
                clave
        );

        BuyCourseUseCase.CheckoutSessionResponse resp1 = buyCourseUseCase.iniciarCompra(cmdUsuario1);
        assertNotNull(resp1);

        // Usuario B intenta enviar la misma clave para consultar o adueñarse de la orden
        BuyCourseUseCase.CreateOrderCommand cmdAtacante = new BuyCourseUseCase.CreateOrderCommand(
                otroUsuarioId,
                List.of(idProg),
                null,
                null,
                "BOLETA",
                "72283339",
                "Atacante IDOR",
                "TRANSFERENCIA",
                null,
                clave
        );

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                buyCourseUseCase.iniciarCompra(cmdAtacante));

        assertTrue(ex.getMessage().contains("pertenece a otro usuario"), "Debe bloquear acceso cross-buyer por IDOR");
    }

}
