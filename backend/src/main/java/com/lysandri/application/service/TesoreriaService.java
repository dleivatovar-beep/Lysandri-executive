package com.lysandri.application.service;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TesoreriaService {
    private final JdbcTemplate jdbc;

    public record Liquidacion(
            @NotNull UUID id,
            @NotBlank @Size(max = 30) String pasarela,
            @NotBlank @Size(max = 120) String referencia,
            @Pattern(regexp = "PEN|USD") @NotNull String moneda,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal bruto,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal comision,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal retencion,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal otrosCargos,
            @NotNull LocalDate fechaEstimada,
            @NotBlank @Size(max = 120) String cuenta,
            @Pattern(regexp = "PRUEBA|REAL") @NotNull String entorno) {}

    public record Movimiento(
            @NotNull UUID id,
            @NotBlank @Size(max = 120) String cuenta,
            @NotBlank @Size(max = 120) String referencia,
            @Pattern(regexp = "PEN|USD") @NotNull String moneda,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal importe,
            @NotNull LocalDate fecha,
            @NotBlank @Size(max = 30) String pasarela,
            @Pattern(regexp = "PRUEBA|REAL") @NotNull String entorno) {}

    public record Conciliacion(@NotNull UUID liquidacion, @NotNull UUID movimiento,
                               @NotBlank @Size(max = 1000) String justificacion) {}

    @Transactional
    public Map<String, Object> registrar(Liquidacion datos, String operador) {
        if (datos.bruto().compareTo(datos.comision().add(datos.retencion()).add(datos.otrosCargos())) < 0) {
            throw new IllegalArgumentException("Los cargos superan el importe bruto.");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", datos.id().toString());
        var anteriores = jdbc.queryForList("SELECT * FROM liquidacion_pasarela WHERE id = ?", datos.id());
        if (!anteriores.isEmpty()) {
            var anterior = anteriores.get(0);
            if (!datos.pasarela().equals(anterior.get("pasarela"))
                    || !datos.referencia().equals(anterior.get("referencia"))
                    || !datos.moneda().equals(anterior.get("moneda"))
                    || !igual(datos.bruto(), anterior.get("bruto"))
                    || !igual(datos.comision(), anterior.get("comision"))
                    || !igual(datos.retencion(), anterior.get("retencion"))
                    || !igual(datos.otrosCargos(), anterior.get("otros_cargos"))
                    || !datos.fechaEstimada().equals(((java.sql.Date) anterior.get("fecha_estimada")).toLocalDate())
                    || !datos.cuenta().equals(anterior.get("cuenta"))
                    || !datos.entorno().equals(anterior.get("entorno"))) {
                throw new IllegalStateException("La clave ya corresponde a otra liquidación.");
            }
            return anterior;
        }
        int insertadas = jdbc.update("""
                INSERT INTO liquidacion_pasarela
                (id, pasarela, referencia, moneda, bruto, comision, retencion, otros_cargos,
                 fecha_estimada, cuenta, entorno, creado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, datos.id(), datos.pasarela(), datos.referencia(), datos.moneda(), datos.bruto(),
                datos.comision(), datos.retencion(), datos.otrosCargos(), datos.fechaEstimada(),
                datos.cuenta(), datos.entorno(), operador);
        if (insertadas != 1) throw new IllegalStateException("La referencia de liquidación ya existe.");
        return jdbc.queryForMap("SELECT * FROM liquidacion_pasarela WHERE id = ?", datos.id());
    }

    @Transactional
    public Map<String, Object> registrar(Movimiento datos, String operador) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", datos.id().toString());
        var anteriores = jdbc.queryForList("SELECT * FROM movimiento_bancario WHERE id = ?", datos.id());
        if (!anteriores.isEmpty()) {
            var anterior = anteriores.get(0);
            if (!datos.cuenta().equals(anterior.get("cuenta"))
                    || !datos.referencia().equals(anterior.get("referencia"))
                    || !datos.moneda().equals(anterior.get("moneda"))
                    || !igual(datos.importe(), anterior.get("importe"))
                    || !datos.fecha().equals(((java.sql.Date) anterior.get("fecha")).toLocalDate())
                    || !datos.pasarela().equals(anterior.get("pasarela"))
                    || !datos.entorno().equals(anterior.get("entorno"))) {
                throw new IllegalStateException("La clave ya corresponde a otro movimiento.");
            }
            return anterior;
        }
        int insertadas = jdbc.update("""
                INSERT INTO movimiento_bancario
                (id, cuenta, referencia, moneda, importe, fecha, pasarela, entorno, creado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, datos.id(), datos.cuenta(), datos.referencia(), datos.moneda(), datos.importe(),
                datos.fecha(), datos.pasarela(), datos.entorno(), operador);
        if (insertadas != 1) throw new IllegalStateException("La referencia bancaria ya existe en esta cuenta.");
        return jdbc.queryForMap("SELECT * FROM movimiento_bancario WHERE id = ?", datos.id());
    }

    @Transactional
    public Map<String, Object> conciliar(Conciliacion datos, String operador) {
        var liquidaciones = jdbc.queryForList("SELECT * FROM liquidacion_pasarela WHERE id = ? FOR UPDATE", datos.liquidacion());
        if (liquidaciones.isEmpty()) throw new IllegalArgumentException("La liquidación no existe.");
        var movimientos = jdbc.queryForList("SELECT * FROM movimiento_bancario WHERE id = ? FOR UPDATE", datos.movimiento());
        if (movimientos.isEmpty()) throw new IllegalArgumentException("El movimiento no existe.");
        var anteriores = jdbc.queryForList("SELECT * FROM conciliacion_bancaria WHERE liquidacion = ?", datos.liquidacion());
        if (!anteriores.isEmpty() && datos.movimiento().equals(anteriores.get(0).get("movimiento"))) {
            if (!datos.justificacion().equals(anteriores.get(0).get("justificacion"))) {
                throw new IllegalStateException("El depósito ya tiene otra justificación.");
            }
            return anteriores.get(0);
        }
        var abonos = jdbc.queryForList("SELECT * FROM abono_liquidacion WHERE movimiento = ?", datos.movimiento());
        if (!abonos.isEmpty()) {
            var anterior = abonos.get(0);
            if (!datos.liquidacion().equals(anterior.get("liquidacion"))
                    || !datos.justificacion().equals(anterior.get("justificacion"))) {
                throw new IllegalStateException("El depósito ya está asociado a otra operación.");
            }
            return anterior;
        }
        var liquidacion = liquidaciones.get(0);
        var movimiento = movimientos.get(0);
        for (String campo : List.of("cuenta", "moneda", "pasarela", "entorno")) {
            if (!Objects.equals(liquidacion.get(campo), movimiento.get(campo))) {
                throw new IllegalArgumentException("El movimiento y la liquidación difieren en " + campo + ".");
            }
        }
        if (!jdbc.queryForList("SELECT id FROM conciliacion_bancaria WHERE movimiento = ?", datos.movimiento()).isEmpty()) {
            throw new IllegalStateException("El depósito ya pertenece a otra conciliación.");
        }
        if (!anteriores.isEmpty()) {
            UUID idAbono = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO abono_liquidacion(id, liquidacion, movimiento, justificacion, creado_por)
                    VALUES (?, ?, ?, ?, ?)
                    """, idAbono, datos.liquidacion(), datos.movimiento(), datos.justificacion(), operador);
            return jdbc.queryForMap("SELECT * FROM abono_liquidacion WHERE id = ?", idAbono);
        }
        BigDecimal diferencia = ((BigDecimal) movimiento.get("importe")).subtract((BigDecimal) liquidacion.get("neto"));
        String estado = diferencia.signum() == 0 ? "CONCILIADA" : "OBSERVADA";
        UUID id = UUID.randomUUID();
        int insertadas = jdbc.update("""
                INSERT INTO conciliacion_bancaria
                (id, liquidacion, movimiento, estado, diferencia, justificacion, creado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, id, datos.liquidacion(), datos.movimiento(), estado, diferencia, datos.justificacion(), operador);
        if (insertadas != 1) throw new IllegalStateException("El movimiento ya pertenece a otra conciliación.");
        return jdbc.queryForMap("SELECT * FROM conciliacion_bancaria WHERE id = ?", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultar(LocalDate desde, LocalDate hasta, String entorno) {
        if (desde == null || hasta == null || desde.isAfter(hasta) || desde.plusYears(2).isBefore(hasta)) {
            throw new IllegalArgumentException("Selecciona un periodo válido de hasta dos años.");
        }
        if (!Set.of("PRUEBA", "REAL").contains(entorno)) throw new IllegalArgumentException("Selecciona el entorno.");
        var liquidaciones = jdbc.queryForList("""
                WITH enlaces AS (
                    SELECT liquidacion, movimiento, justificacion FROM conciliacion_bancaria
                    UNION ALL
                    SELECT liquidacion, movimiento, justificacion FROM abono_liquidacion
                ), resumen AS (
                    SELECT e.liquidacion, sum(m.importe) AS depositado, max(m.fecha) AS fecha_deposito,
                           string_agg(e.justificacion, ' | ' ORDER BY m.fecha, m.id) AS justificacion
                    FROM enlaces e JOIN movimiento_bancario m ON m.id = e.movimiento GROUP BY e.liquidacion
                )
                SELECT l.*, r.depositado, r.depositado - l.neto AS diferencia, r.justificacion, r.fecha_deposito,
                       CASE WHEN r.depositado = l.neto THEN 'CONCILIADA'
                            WHEN r.depositado IS NOT NULL THEN 'OBSERVADA' ELSE NULL END AS conciliacion,
                       CASE WHEN r.depositado = l.neto THEN 'PROCESADA'
                            WHEN r.depositado IS NOT NULL THEN 'OBSERVADA'
                            WHEN l.fecha_estimada < CURRENT_DATE THEN 'RETRASADA'
                            ELSE 'PENDIENTE' END AS estado
                FROM liquidacion_pasarela l
                LEFT JOIN resumen r ON r.liquidacion = l.id
                WHERE l.fecha_estimada BETWEEN ? AND ? AND l.entorno = ?
                ORDER BY l.fecha_estimada DESC, l.id
                """, desde, hasta, entorno);
        var movimientos = jdbc.queryForList("""
                WITH enlaces AS (
                    SELECT movimiento, liquidacion FROM conciliacion_bancaria
                    UNION ALL SELECT movimiento, liquidacion FROM abono_liquidacion
                ), resumen AS (
                    SELECT e.liquidacion, sum(m.importe) AS depositado
                    FROM enlaces e JOIN movimiento_bancario m ON m.id = e.movimiento GROUP BY e.liquidacion
                )
                SELECT m.*, e.liquidacion,
                       CASE WHEN r.depositado = l.neto THEN 'CONCILIADA'
                            WHEN e.liquidacion IS NOT NULL THEN 'OBSERVADA' ELSE NULL END AS conciliacion
                FROM movimiento_bancario m LEFT JOIN enlaces e ON e.movimiento = m.id
                LEFT JOIN liquidacion_pasarela l ON l.id = e.liquidacion
                LEFT JOIN resumen r ON r.liquidacion = e.liquidacion
                WHERE m.fecha BETWEEN ? AND ? AND m.entorno = ? ORDER BY m.fecha DESC, m.id
                """, desde, hasta, entorno);
        return Map.of("liquidaciones", liquidaciones, "movimientos", movimientos);
    }

    @Transactional
    public List<Map<String, Object>> importar(List<Movimiento> movimientos, String operador) {
        if (movimientos.isEmpty() || movimientos.size() > 500) {
            throw new IllegalArgumentException("Importa entre uno y 500 depósitos.");
        }
        List<Map<String, Object>> registros = new ArrayList<>();
        for (Movimiento movimiento : movimientos) registros.add(registrar(movimiento, operador));
        return registros;
    }

    @Transactional
    public int conciliarCoincidencias(LocalDate desde, LocalDate hasta, String entorno, String operador) {
        consultar(desde, hasta, entorno);
        var coincidencias = jdbc.queryForList("""
                WITH candidatos AS (
                    SELECT l.id AS liquidacion, m.id AS movimiento,
                           count(*) OVER (PARTITION BY l.id) AS depositos,
                           count(*) OVER (PARTITION BY m.id) AS liquidaciones
                    FROM liquidacion_pasarela l JOIN movimiento_bancario m
                        ON l.cuenta = m.cuenta AND l.moneda = m.moneda
                        AND l.pasarela = m.pasarela AND l.entorno = m.entorno
                        AND l.referencia = m.referencia AND l.neto = m.importe
                        AND m.fecha BETWEEN l.fecha_estimada - 3 AND l.fecha_estimada + 7
                    WHERE l.fecha_estimada BETWEEN ? AND ? AND l.entorno = ?
                        AND NOT EXISTS (SELECT 1 FROM conciliacion_bancaria c WHERE c.liquidacion = l.id)
                        AND NOT EXISTS (SELECT 1 FROM conciliacion_bancaria c WHERE c.movimiento = m.id)
                        AND NOT EXISTS (SELECT 1 FROM abono_liquidacion a WHERE a.movimiento = m.id)
                )
                SELECT liquidacion, movimiento FROM candidatos
                WHERE depositos = 1 AND liquidaciones = 1 ORDER BY liquidacion, movimiento
                """, desde, hasta, entorno);
        for (var fila : coincidencias) {
            conciliar(new Conciliacion((UUID) fila.get("liquidacion"), (UUID) fila.get("movimiento"),
                    "Coincidencia automática única de referencia, cuenta, moneda, pasarela, importe y ventana de fechas."),
                    operador);
        }
        return coincidencias.size();
    }

    private boolean igual(BigDecimal importe, Object anterior) {
        return anterior instanceof BigDecimal valor && importe.compareTo(valor) == 0;
    }
}
