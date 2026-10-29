package com.lysandri.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.out.BillingPort;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostgresPersistenceIntegrationTest extends PostgresTestDatabase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserManagementUseCase userManagement;

    @Autowired
    private AuthenticationManager authenticationManager;

        @Autowired
        private BillingPort billingPort;

    @Test
    void flywayAplicaYValidaLasMigracionesV1HastaV26YHibernateValidaElEsquema() {
        flyway.validate();

        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                Integer.class);
        assertEquals(36, appliedMigrations);

        Integer v20 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20' AND success = TRUE",
                Integer.class);
        Integer v21 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '21' AND success = TRUE",
                Integer.class);
        Integer v22 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '22' AND success = TRUE",
                Integer.class);
        Integer v23 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '23' AND success = TRUE",
                Integer.class);
        Integer v24 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '24' AND success = TRUE",
                Integer.class);
        Integer v25 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '25' AND success = TRUE",
                Integer.class);
        Integer v26 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '26' AND success = TRUE",
                Integer.class);
        Integer v27 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '27' AND success = TRUE",
                Integer.class);
        Integer v28 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '28' AND success = TRUE",
                Integer.class);
        Integer v29 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '29' AND success = TRUE",
                Integer.class);
        assertEquals(1, v20);
        assertEquals(1, v21);
        assertEquals(1, v22);
        assertEquals(1, v23);
        assertEquals(1, v24);
        assertEquals(1, v25);
        assertEquals(1, v26);
        assertEquals(1, v27);
        assertEquals(1, v28);
        assertEquals(1, v29);
        Integer v30 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '30' AND success = TRUE",
                Integer.class);
        assertEquals(1, v30);
        Integer v31 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31' AND success = TRUE",
                Integer.class);
        assertEquals(1, v31);
        Integer v32 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32' AND success = TRUE",
                Integer.class);
        assertEquals(1, v32);
        Integer v33 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '33' AND success = TRUE",
                Integer.class);
        assertEquals(1, v33);
        Integer v34 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '34' AND success = TRUE",
                Integer.class);
        assertEquals(1, v34);
        Integer v35 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '35' AND success = TRUE",
                Integer.class);
        assertEquals(1, v35);
        assertEquals("idx_ordenes_op_norm", jdbcTemplate.queryForObject(
                "SELECT indexname FROM pg_indexes WHERE schemaname='public' AND tablename='ordenes' AND indexname='idx_ordenes_op_norm'", String.class));
        assertEquals("guest_session_hash", jdbcTemplate.queryForObject(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='ordenes' AND column_name='guest_session_hash'", String.class));
        assertEquals("clave_idempotencia", jdbcTemplate.queryForObject(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='ordenes' AND column_name='clave_idempotencia'", String.class));

        assertEquals("whatsapp_mensaje", jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.whatsapp_mensaje')::text", String.class));

        assertEquals("usuario_cambio_audit", jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.usuario_cambio_audit')::text", String.class));
        assertEquals("bloqueado_admin", jdbcTemplate.queryForObject(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario' AND column_name='bloqueado_admin'", String.class));
        assertEquals("pendiente_activacion", jdbcTemplate.queryForObject(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario' AND column_name='pendiente_activacion'", String.class));
        assertEquals("revocado", jdbcTemplate.queryForObject(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario_activacion_token' AND column_name='revocado'", String.class));
        assertEquals(30, jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns " +
                        "WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name = 'rol'",
                Integer.class));
        assertEquals("bigint", jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema='public' " +
                        "AND table_name='comprobante_pago' AND column_name='id_comprobante'", String.class));
        assertEquals("character varying", jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema='public' " +
                        "AND table_name='comprobante_pago' AND column_name='estado'", String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' " +
                        "AND table_name='comprobante_correlativo'", Integer.class));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint " +
                        "WHERE conrelid = 'public.usuario'::regclass AND conname = 'usuario_rol_check'",
                String.class).contains("COORDINADOR_ACADEMICO"));

        assertEquals("bloque", jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.bloque')", String.class));
        assertEquals("inscripciones", jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.inscripciones')", String.class));
    }

    @Test
        void auditoriaPermaneceDisponibleEnConexionIndependiente() throws Exception {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE email = 'danny@lysandri.com'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO usuario_cambio_audit
                    (id_usuario, email, accion, rol_anterior, rol_nuevo, motivo, ejecutado_por)
                VALUES (?, ?, 'ROL_ACTUALIZADO', 'CLIENTE', 'TRABAJADOR', 'restart-check', 'integration-admin')
                """, userId, "danny@lysandri.com");
        Long auditId = jdbcTemplate.queryForObject(
                "SELECT id_auditoria FROM usuario_cambio_audit WHERE id_usuario=? AND motivo='restart-check'",
                Long.class, userId);

        try (var independentConnection = flyway.getConfiguration().getDataSource().getConnection();
             var statement = independentConnection.prepareStatement(
                     "SELECT COUNT(*) FROM usuario_cambio_audit WHERE id_auditoria=?")) {
            statement.setLong(1, auditId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void comprobantePagoTieneClaveBigintYNoSerial() {
        assertEquals("bigint", jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_schema='public' " +
                        "AND table_name='comprobante_pago' AND column_name='id_comprobante'",
                String.class));
    }

    @Test
    void cicloDeVidaAdministrativoPersisteCambiosAuditoriaYDesactivacion() throws Exception {
        String email = "worker-" + UUID.randomUUID() + "@integration.invalid";
        String password = "Integration-Only-Password-483!";
        String requestJson = "{\"nombres\":\"Integracion\",\"apellidos\":\"Trabajador\",\"email\":\"" + email
                + "\",\"passw\":\"" + password + "\",\"rol\":\"TRABAJADOR\"}";

        mockMvc.perform(post("/api/v1/usuarios")
                        .with(user("admin.integration@lysandri.invalid").roles("ADMIN"))
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isCreated());
        Long userId = jdbcTemplate.queryForObject("SELECT id_user FROM usuario WHERE email = ?", Long.class, email);
        assertNotNull(userId);

        // Issue a real token before disabling the account: blocking login alone is insufficient.
        String loginJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String tokenBeforeDisable = objectMapper.readTree(loginJson).path("token").asText();
        assertFalse(tokenBeforeDisable.isBlank());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenBeforeDisable))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/usuarios/{id}/rol", userId)
                        .with(user("admin.integration@lysandri.invalid").roles("ADMIN"))
                        .param("rol", "CONTABILIDAD")
                        .param("motivo", "Prueba de cambio autorizado"))
                .andExpect(status().isOk());

        Usuario actualizado = userManagement.obtenerPorId(userId);
        assertEquals("CONTABILIDAD", actualizado.getRol().name());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario_cambio_audit WHERE id_usuario = ? AND accion = 'ROL_ACTUALIZADO' " +
                        "AND ejecutado_por = ?", Integer.class, userId, "admin.integration@lysandri.invalid"));

        mockMvc.perform(patch("/api/v1/usuarios/{id}/rol", userId)
                        .with(user("client.integration@lysandri.invalid").roles("CLIENTE"))
                        .param("rol", "ADMIN"))
                .andExpect(status().isForbidden());
        assertEquals("CONTABILIDAD", userManagement.obtenerPorId(userId).getRol().name());

        mockMvc.perform(patch("/api/v1/usuarios/{id}/desactivar", userId)
                        .with(user("admin.integration@lysandri.invalid").roles("ADMIN"))
                        .param("motivo", "Fin de prueba"))
                .andExpect(status().isOk());
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "SELECT activo FROM usuario WHERE id_user = ?", Boolean.class, userId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario_cambio_audit WHERE id_usuario = ?", Integer.class, userId));
        assertThrows(LockedException.class, () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenBeforeDisable))
                .andExpect(status().isUnauthorized());

        String historyJson = mockMvc.perform(get("/api/v1/usuarios/{id}/historial", userId)
                        .with(user("admin.integration@lysandri.invalid").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode history = objectMapper.readTree(historyJson);
        assertEquals(2, history.size());
    }

    @Test
    void contabilidadRealPermiteConsultaPeroRechazaClientesYTrabajadoresSinPermiso() throws Exception {
        mockMvc.perform(get("/api/v1/internal/accounting/ventas")
                        .with(user("accountant@integration.invalid").roles("CONTABILIDAD"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/internal/accounting/ventas")
                        .with(user("client@integration.invalid").roles("CLIENTE"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/internal/accounting/ventas")
                        .with(user("worker@integration.invalid").roles("TRABAJADOR"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/internal/accounting/ventas")
                        .with(user("accountant@integration.invalid").roles("CONTABILIDAD"))
                        .header("X-Audit-PIN", "wrong-pin"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/internal/accounting/comprobantes/999999/anular")
                        .with(user("finance@integration.invalid").roles("FINANZAS"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin"))
                .andExpect(status().isForbidden());

        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE email = 'danny@lysandri.com'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO ordenes (id_orden, id_user, codigo_orden, estado_orden, total, moneda, metodo_pago)
                VALUES (910001, ?, 'IT-ACCOUNTING-910001', 'PAGADO', 118.00, 'PEN', 'TEST')
                """, adminId);
        var receipt = billingPort.emitirComprobante(Orden.builder()
                .idOrden(910001L)
                .total(new java.math.BigDecimal("118.00"))
                .moneda("PEN")
                .nombreFacturacion("Contabilidad Integracion")
                .numeroDocumentoCliente("12345678")
                .build());

        mockMvc.perform(post("/api/v1/internal/accounting/comprobantes/{id}/anular", receipt.getIdComprobante())
                        .with(user("accountant@integration.invalid").roles("CONTABILIDAD"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin")
                        .contentType("application/json")
                        .content("{\"motivo\":\"Prueba de auditoria\"}"))
                .andExpect(status().isOk());
        assertEquals("ANULADO", jdbcTemplate.queryForObject(
                "SELECT estado FROM comprobante_pago WHERE id_comprobante=?", String.class,
                receipt.getIdComprobante()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auditoria_contable WHERE accion='COMPROBANTE_ANULADO' " +
                        "AND entidad_id=? AND actor='accountant@integration.invalid'",
                Integer.class, receipt.getIdComprobante()));
    }

        @Test
        void comprobantesSePersistenYLosCorrelativosSonUnicosBajoConcurrencia() throws Exception {
                Long usuarioId = jdbcTemplate.queryForObject(
                        "SELECT id_user FROM usuario WHERE email = 'danny@lysandri.com'", Long.class);
                var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
                try {
                        var tasks = java.util.stream.IntStream.range(0, 24)
                                        .mapToObj(index -> (java.util.concurrent.Callable<Integer>) () -> {
                                long orderId = 800000L + index;
                                jdbcTemplate.update("""
                                        INSERT INTO ordenes (id_orden, id_user, codigo_orden, estado_orden, total, moneda, metodo_pago)
                                        VALUES (?, ?, ?, 'PAGADO', 118.00, 'PEN', 'TEST')
                                        """, orderId, usuarioId, "IT-CORR-" + index);
                                                Orden orden = Orden.builder()
                                        .idOrden(orderId)
                                                                .total(new java.math.BigDecimal("118.00"))
                                                                .moneda("PEN")
                                                                .tipoComprobanteSolicitado("BOLETA")
                                                                .nombreFacturacion("Prueba concurrencia " + index)
                                                                .numeroDocumentoCliente("12345678")
                                                                .build();
                                                return billingPort.emitirComprobante(orden).getCorrelativo();
                                        })
                                        .toList();
                        var futures = executor.invokeAll(tasks);
                        var correlativos = new java.util.HashSet<Integer>();
                        for (var future : futures) {
                                assertTrue(correlativos.add(future.get()));
                        }

                        assertEquals(24, correlativos.size());
                                                assertEquals(24, jdbcTemplate.queryForObject(
                                                                "SELECT COUNT(*) FROM comprobante_pago WHERE id_orden BETWEEN 800000 AND 800023",
                                                                Integer.class));
                                                assertEquals(24, jdbcTemplate.queryForObject(
                                                                "SELECT COUNT(DISTINCT correlativo) FROM comprobante_pago " +
                                                                        "WHERE serie='B001' AND id_orden BETWEEN 800000 AND 800023",
                                                                Integer.class));
                        assertEquals(jdbcTemplate.queryForObject(
                                                "SELECT MAX(correlativo)::BIGINT FROM comprobante_pago WHERE serie='B001'",
                                                Long.class),
                                        jdbcTemplate.queryForObject(
                                                "SELECT ultimo_correlativo FROM comprobante_correlativo WHERE serie='B001'",
                                                Long.class));
                } finally {
                        executor.shutdownNow();
                }
        }
}
