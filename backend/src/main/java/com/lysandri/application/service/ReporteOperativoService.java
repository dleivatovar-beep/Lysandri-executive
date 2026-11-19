package com.lysandri.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReporteOperativoService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> consultar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta) || desde.plusYears(2).isBefore(hasta)) {
            throw new IllegalArgumentException("Selecciona un periodo válido de hasta dos años.");
        }
        var monedas = jdbc.queryForList("""
                SELECT moneda, count(*) AS operaciones,
                    coalesce(sum(total) FILTER (WHERE estado_orden = 'PAGADO'), 0) AS cobrado,
                    coalesce(sum(total) FILTER (WHERE estado_orden = 'PENDIENTE'), 0) AS pendiente,
                    count(*) FILTER (WHERE estado_orden = 'FALLIDO') AS rechazadas,
                    count(*) FILTER (WHERE estado_orden = 'PAGADO' AND NOT moodle_matricula_sincronizada) AS sin_matricula
                FROM ordenes WHERE fecha_orden >= ?::date AND fecha_orden < (?::date + 1)
                GROUP BY moneda ORDER BY moneda
                """, desde, hasta);
        var programas = jdbc.queryForList("""
                SELECT p.id_programa, p.titulo, o.moneda, sum(d.cantidad) AS unidades,
                    sum(d.subtotal) AS vendido,
                    coalesce(sum(d.subtotal) FILTER (WHERE o.estado_orden = 'PAGADO'), 0) AS cobrado,
                    coalesce(sum(d.cantidad) FILTER (WHERE d.moodle_matriculado), 0) AS matriculados
                FROM detalle_ordenes d JOIN ordenes o ON o.id_orden = d.id_orden
                JOIN programa p ON p.id_programa = d.id_programa
                WHERE o.fecha_orden >= ?::date AND o.fecha_orden < (?::date + 1)
                    AND o.estado_orden IN ('PAGADO', 'PENDIENTE')
                GROUP BY p.id_programa, p.titulo, o.moneda ORDER BY cobrado DESC, p.id_programa
                """, desde, hasta);
        var canales = jdbc.queryForList("""
                SELECT metodo_pago AS canal, moneda, estado_orden AS estado, count(*) AS operaciones, sum(total) AS importe
                FROM ordenes WHERE fecha_orden >= ?::date AND fecha_orden < (?::date + 1)
                GROUP BY metodo_pago, moneda, estado_orden ORDER BY metodo_pago, moneda, estado_orden
                """, desde, hasta);
        var corporativos = jdbc.queryForList("""
                SELECT l.id_lote, l.empresa_nombre, p.titulo AS programa, l.fecha_registro,
                    l.total_colaboradores, l.total_exitosos, l.total_fallidos
                FROM lote_matricula_corporativa l JOIN programa p ON p.id_programa = l.id_programa
                WHERE l.fecha_registro >= ?::date AND l.fecha_registro < (?::date + 1)
                ORDER BY l.fecha_registro DESC, l.id_lote
                """, desde, hasta);
        var notificaciones = jdbc.queryForList("""
                SELECT tipo_notificacion AS tipo, estado, count(*) AS cantidad, sum(intentos) AS intentos
                FROM notificaciones_log WHERE fecha_creacion >= ?::date AND fecha_creacion < (?::date + 1)
                GROUP BY tipo_notificacion, estado
                UNION ALL
                SELECT 'ACTIVACION_CUENTA', estado, count(*), sum(intentos)
                FROM outbox_notificacion_activacion
                WHERE fecha_creacion >= ?::date AND fecha_creacion < (?::date + 1) GROUP BY estado
                UNION ALL
                SELECT tipo, estado, count(*), NULL::bigint
                FROM aviso_cuenta WHERE fecha >= ?::date AND fecha < (?::date + 1)
                GROUP BY tipo, estado ORDER BY tipo, estado
                """, desde, hasta, desde, hasta, desde, hasta);
        return Map.of("monedas", monedas, "programas", programas, "canales", canales,
                "corporativos", corporativos, "notificaciones", notificaciones);
    }
}
