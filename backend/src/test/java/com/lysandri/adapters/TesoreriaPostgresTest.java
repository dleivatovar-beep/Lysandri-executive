package com.lysandri.adapters;

import com.lysandri.application.service.TesoreriaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TESORERIA_VALIDAR_ORIGINAL", matches = "true")
class TesoreriaPostgresTest {
    @Test void conservaImportesEvitaDuplicadosYDetectaDiferencias() throws Exception {
        try (var conexion = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/lysandri_db",
                System.getenv("TESORERIA_DB_USER"), System.getenv("TESORERIA_DB_PASSWORD"))) {
            conexion.setAutoCommit(false);
            try {
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
                String esquema = "tesoreria_validacion_" + UUID.randomUUID().toString().replace("-", "");
                jdbc.execute("CREATE SCHEMA " + esquema);
                jdbc.execute("SET LOCAL search_path TO " + esquema);
                try (var archivo = new ClassPathResource("db/migration/V54__conciliacion-financiera.sql").getInputStream()) {
                    jdbc.execute(new String(archivo.readAllBytes(), StandardCharsets.UTF_8));
                }
                try (var archivo = new ClassPathResource("db/migration/V55__abonos-liquidacion.sql").getInputStream()) {
                    jdbc.execute(new String(archivo.readAllBytes(), StandardCharsets.UTF_8));
                }
                var servicio = new TesoreriaService(jdbc);
                LocalDate hoy = LocalDate.now();
                UUID liquidacion = UUID.randomUUID();
                var datos = new TesoreriaService.Liquidacion(liquidacion, "PASARELA-PRUEBA", "L-1", "PEN",
                        new BigDecimal("100.00"), new BigDecimal("3.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        hoy, "CUENTA-PRUEBA", "PRUEBA");
                var guardada = servicio.registrar(datos, "validacion-local");
                assertEquals(new BigDecimal("97.00"), guardada.get("neto"));
                assertEquals(guardada, servicio.registrar(datos, "validacion-local"));
                UUID movimiento = UUID.randomUUID();
                var deposito = new TesoreriaService.Movimiento(movimiento, "CUENTA-PRUEBA", "D-1", "PEN",
                        new BigDecimal("97.00"), hoy, "PASARELA-PRUEBA", "PRUEBA");
                servicio.registrar(deposito, "validacion-local");
                servicio.registrar(deposito, "validacion-local");
                var enlace = new TesoreriaService.Conciliacion(liquidacion, movimiento, "Coinciden referencias e importe");
                var resultado = servicio.conciliar(enlace, "validacion-local");
                assertEquals("CONCILIADA", resultado.get("estado"));
                assertEquals(resultado, servicio.conciliar(enlace, "validacion-local"));
                assertThrows(IllegalStateException.class, () -> servicio.registrar(
                        new TesoreriaService.Movimiento(movimiento, "CUENTA-PRUEBA", "D-1", "PEN",
                                new BigDecimal("96.00"), hoy, "PASARELA-PRUEBA", "PRUEBA"), "validacion-local"));

                UUID otraLiquidacion = UUID.randomUUID();
                servicio.registrar(new TesoreriaService.Liquidacion(otraLiquidacion, "PASARELA-PRUEBA", "L-2", "PEN",
                        new BigDecimal("100.00"), new BigDecimal("3.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        hoy, "CUENTA-PRUEBA", "PRUEBA"), "validacion-local");
                assertThrows(IllegalStateException.class, () -> servicio.conciliar(
                        new TesoreriaService.Conciliacion(otraLiquidacion, movimiento, "Repetido"), "validacion-local"));
                UUID parcial = UUID.randomUUID();
                servicio.registrar(new TesoreriaService.Movimiento(parcial, "CUENTA-PRUEBA", "D-2", "PEN",
                        new BigDecimal("50.00"), hoy, "PASARELA-PRUEBA", "PRUEBA"), "validacion-local");
                var diferencia = servicio.conciliar(new TesoreriaService.Conciliacion(
                        otraLiquidacion, parcial, "Abono parcial pendiente de regularización"), "validacion-local");
                assertEquals("OBSERVADA", diferencia.get("estado"));
                assertEquals(new BigDecimal("-47.00"), diferencia.get("diferencia"));
                UUID complemento = UUID.randomUUID();
                servicio.registrar(new TesoreriaService.Movimiento(complemento, "CUENTA-PRUEBA", "D-3", "PEN",
                        new BigDecimal("47.00"), hoy, "PASARELA-PRUEBA", "PRUEBA"), "validacion-local");
                var segundoAbono = new TesoreriaService.Conciliacion(otraLiquidacion, complemento, "Completa el abono parcial");
                var abono = servicio.conciliar(segundoAbono, "validacion-local");
                assertEquals(abono, servicio.conciliar(segundoAbono, "validacion-local"));
                var reporte = (java.util.List<java.util.Map<String, Object>>) servicio.consultar(hoy, hoy, "PRUEBA").get("liquidaciones");
                var regularizada = reporte.stream().filter(fila -> otraLiquidacion.equals(fila.get("id"))).findFirst().orElseThrow();
                assertEquals("PROCESADA", regularizada.get("estado"));
                assertEquals(new BigDecimal("0.00"), regularizada.get("diferencia"));
                var movimientosConciliados = (java.util.List<java.util.Map<String, Object>>) servicio.consultar(hoy, hoy, "PRUEBA").get("movimientos");
                assertEquals(3, movimientosConciliados.stream().filter(fila -> "CONCILIADA".equals(fila.get("conciliacion"))).count());
                assertEquals("OBSERVADA", jdbc.queryForObject(
                        "SELECT estado FROM conciliacion_bancaria WHERE liquidacion = ?", String.class, otraLiquidacion));
                UUID automatico = UUID.randomUUID();
                servicio.registrar(new TesoreriaService.Liquidacion(automatico, "PASARELA-PRUEBA", "AUTO-1", "PEN",
                        new BigDecimal("100.00"), new BigDecimal("3.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        hoy, "CUENTA-PRUEBA", "PRUEBA"), "validacion-local");
                servicio.importar(java.util.List.of(new TesoreriaService.Movimiento(UUID.randomUUID(),
                        "CUENTA-PRUEBA", "AUTO-1", "PEN", new BigDecimal("97.00"), hoy,
                        "PASARELA-PRUEBA", "PRUEBA")), "validacion-local");
                assertEquals(1, servicio.conciliarCoincidencias(hoy, hoy, "PRUEBA", "validacion-local"));
                assertEquals(0, servicio.conciliarCoincidencias(hoy, hoy, "PRUEBA", "validacion-local"));
                assertEquals(0, ((java.util.List<?>) servicio.consultar(hoy, hoy, "REAL").get("liquidaciones")).size());
                assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM liquidacion_pasarela", Integer.class));
                assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM movimiento_bancario", Integer.class));
            } finally {
                conexion.rollback();
            }
        }
    }
}
