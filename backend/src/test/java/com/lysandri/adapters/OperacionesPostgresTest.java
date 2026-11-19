package com.lysandri.adapters;

import com.lysandri.application.service.IncidenciasService;
import com.lysandri.application.service.ReporteOperativoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TESORERIA_VALIDAR_ORIGINAL", matches = "true")
class OperacionesPostgresTest {
    @Test void reportaSinDuplicarImportesYConservaElCicloDeIncidencias() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/lysandri_db",
                System.getenv("TESORERIA_DB_USER"), System.getenv("TESORERIA_DB_PASSWORD"))) {
            conexion.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
                String esquema = "operaciones_validacion_" + UUID.randomUUID().toString().replace("-", "");
                jdbc.execute("CREATE SCHEMA " + esquema);
                jdbc.execute("SET LOCAL search_path TO " + esquema);
                jdbc.execute("""
                        CREATE TABLE ordenes (id_orden bigint, codigo_orden text, total numeric(12,2), moneda text,
                            estado_orden text, metodo_pago text, fecha_orden timestamptz, fecha_pago timestamptz,
                            moodle_matricula_sincronizada boolean);
                        CREATE TABLE programa (id_programa bigint, titulo text);
                        CREATE TABLE detalle_ordenes (id_orden bigint, id_programa bigint, cantidad integer,
                            subtotal numeric(12,2), moodle_matriculado boolean);
                        CREATE TABLE lote_matricula_corporativa (id_lote bigint, id_programa bigint,
                            empresa_nombre text, fecha_registro timestamptz, total_colaboradores integer,
                            total_exitosos integer, total_fallidos integer);
                        CREATE TABLE notificaciones_log (id_notificacion bigint, tipo_notificacion text,
                            estado text, intentos integer, fecha_creacion timestamptz);
                        CREATE TABLE outbox_notificacion_activacion (id_tarea bigint, estado text,
                            intentos integer, fecha_creacion timestamptz);
                        CREATE TABLE aviso_cuenta (id_aviso bigint, tipo text, estado text, fecha timestamptz);
                        INSERT INTO outbox_notificacion_activacion VALUES (1,'ENVIADO',1,now());
                        INSERT INTO aviso_cuenta VALUES (1,'PASSWORD_CAMBIADA','ENVIADO',now());
                        INSERT INTO ordenes VALUES
                            (1,'ORD-1',100,'PEN','PAGADO','IZIPAY',now(),now()-interval '30 minutes',false),
                            (2,'ORD-2',200,'USD','PAGADO','TRANSFERENCIA',now(),now(),true),
                            (3,'ORD-3',50,'PEN','PENDIENTE','IZIPAY',now(),null,false);
                        INSERT INTO programa VALUES (1,'Programa uno'),(2,'Programa dos');
                        INSERT INTO detalle_ordenes VALUES
                            (1,1,1,40,false),(1,2,1,60,false),(2,1,1,200,true),(3,1,1,50,false);
                        INSERT INTO lote_matricula_corporativa VALUES (1,1,'Empresa de prueba',now(),5,4,1);
                        INSERT INTO notificaciones_log VALUES (1,'ACTIVACION','FALLIDO',2,now());
                        """);
                try (var archivo = new ClassPathResource("db/migration/V56__incidencias-integraciones.sql").getInputStream()) {
                    jdbc.execute(new String(archivo.readAllBytes(), StandardCharsets.UTF_8));
                }
                var reportes = new ReporteOperativoService(jdbc);
                var reporte = reportes.consultar(LocalDate.now(), LocalDate.now());
                var monedas = (List<Map<String, Object>>) reporte.get("monedas");
                var pen = monedas.stream().filter(fila -> "PEN".equals(fila.get("moneda"))).findFirst().orElseThrow();
                assertEquals(new BigDecimal("100.00"), pen.get("cobrado"));
                assertEquals(new BigDecimal("50.00"), pen.get("pendiente"));
                assertEquals(2, monedas.size());
                assertEquals(1, ((List<?>) reporte.get("corporativos")).size());
                assertThrows(IllegalArgumentException.class,
                        () -> reportes.consultar(LocalDate.now(), LocalDate.now().minusDays(1)));
                assertEquals(3, ((List<?>) reporte.get("notificaciones")).size());
                var incidencias = new IncidenciasService(jdbc);
                incidencias.revisar();
                incidencias.revisar();
                assertEquals(2, incidencias.listar().size());
                UUID id = jdbc.queryForObject("SELECT id FROM incidencia_integracion WHERE servicio='MATRICULA'", UUID.class);
                assertEquals(1, incidencias.historial(id).size());
                incidencias.cambiar(id, "SEGUIMIENTO", "Se revisa el aprovisionamiento", "administrador-prueba");
                assertEquals(2, incidencias.historial(id).size());
                incidencias.cambiar(id, "SEGUIMIENTO", "Se confirmó el error recuperable", "administrador-prueba");
                incidencias.cambiar(id, "SEGUIMIENTO", "Se confirmó el error recuperable", "administrador-prueba");
                assertEquals(3, incidencias.historial(id).size());
                jdbc.update("UPDATE ordenes SET moodle_matricula_sincronizada=true WHERE id_orden=1");
                jdbc.update("UPDATE notificaciones_log SET estado='ENVIADO'");
                incidencias.revisar();
                assertEquals(2, jdbc.queryForObject(
                        "SELECT count(*) FROM incidencia_integracion WHERE estado='RESUELTA' AND resuelta_en IS NOT NULL", Integer.class));
                assertEquals(4, incidencias.historial(id).size());
                incidencias.revisar();
                assertEquals(4, incidencias.historial(id).size());
                jdbc.update("UPDATE ordenes SET moodle_matricula_sincronizada=false WHERE id_orden=1");
                incidencias.revisar();
                assertEquals("ABIERTA", jdbc.queryForObject("SELECT estado FROM incidencia_integracion WHERE id=?", String.class, id));
                assertEquals(5, incidencias.historial(id).size());
            } finally {
                conexion.rollback();
            }
        }
    }
}
