package com.lysandri.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IncidenciasService {
    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "${app.incidencias.intervalo-ms:60000}")
    @Transactional
    public void revisar() {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended('revision-incidencias', 0))");
        var fuentes = jdbc.queryForList("""
                SELECT 'MATRICULA' AS servicio, codigo_orden AS referencia, 'ALTA' AS criticidad,
                       'Pago aprobado con matrícula pendiente de sincronización.' AS descripcion
                FROM ordenes WHERE estado_orden = 'PAGADO' AND NOT moodle_matricula_sincronizada
                    AND coalesce(fecha_pago, fecha_orden) < CURRENT_TIMESTAMP - INTERVAL '15 minutes'
                UNION ALL
                SELECT 'CORREO', id_notificacion::text, 'MEDIA', 'Notificación con envío fallido.'
                FROM notificaciones_log WHERE estado = 'FALLIDO'
                UNION ALL
                SELECT 'CORREO_ACTIVACION', id_tarea::text, 'ALTA', 'Activación de cuenta con envío fallido.'
                FROM outbox_notificacion_activacion WHERE estado IN ('FALLIDO', 'FALLIDO_CRIPTOGRAFICO')
                UNION ALL
                SELECT 'SEGURIDAD_CUENTA', id_aviso::text, 'ALTA', 'Aviso de seguridad pendiente de revisión de envío.'
                FROM aviso_cuenta WHERE estado = 'REVISAR'
                """);
        Set<String> presentes = new HashSet<>();
        for (var fuente : fuentes) {
            String servicio = (String) fuente.get("servicio");
            String referencia = (String) fuente.get("referencia");
            presentes.add(servicio + ":" + referencia);
            var anteriores = jdbc.queryForList(
                    "SELECT id, estado FROM incidencia_integracion WHERE servicio = ? AND referencia = ? FOR UPDATE",
                    servicio, referencia);
            if (anteriores.isEmpty()) {
                UUID id = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO incidencia_integracion
                        (id, servicio, referencia, criticidad, descripcion, estado)
                        VALUES (?, ?, ?, ?, ?, 'ABIERTA')
                        """, id, servicio, referencia, fuente.get("criticidad"), fuente.get("descripcion"));
                evento(id, "ABIERTA", "Incidencia detectada por la revisión automática.", "SISTEMA");
            } else if ("RESUELTA".equals(anteriores.get(0).get("estado"))) {
                cambiar((UUID) anteriores.get(0).get("id"), "ABIERTA",
                        "La revisión detectó que el problema persiste o volvió a ocurrir.", "SISTEMA");
            }
        }
        var abiertas = jdbc.queryForList("""
                SELECT id, servicio, referencia FROM incidencia_integracion
                WHERE estado <> 'RESUELTA' AND servicio IN ('MATRICULA', 'CORREO', 'CORREO_ACTIVACION', 'SEGURIDAD_CUENTA') ORDER BY id FOR UPDATE
                """);
        for (var incidencia : abiertas) {
            String clave = incidencia.get("servicio") + ":" + incidencia.get("referencia");
            if (!presentes.contains(clave)) {
                cambiar((UUID) incidencia.get("id"), "RESUELTA",
                        "La condición de error dejó de aparecer en los registros operativos.", "SISTEMA");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList("""
                SELECT *, extract(epoch FROM (coalesce(resuelta_en, CURRENT_TIMESTAMP) - creada_en))::bigint
                    AS duracion_segundos FROM incidencia_integracion ORDER BY creada_en DESC, id LIMIT 1000
                """);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historial(UUID id) {
        return jdbc.queryForList("""
                SELECT estado, comentario, operador, fecha FROM evento_incidencia
                WHERE incidencia = ? ORDER BY fecha, id
                """, id);
    }

    @Transactional
    public void cambiar(UUID id, String estado, String comentario, String operador) {
        if (!Set.of("ABIERTA", "SEGUIMIENTO", "RESUELTA").contains(estado)
                || comentario == null || comentario.isBlank() || comentario.length() > 1000) {
            throw new IllegalArgumentException("Indica un estado y un comentario de hasta 1000 caracteres.");
        }
        var filas = jdbc.queryForList("SELECT estado FROM incidencia_integracion WHERE id = ? FOR UPDATE", id);
        if (filas.isEmpty()) throw new IllegalArgumentException("La incidencia no existe.");
        if (estado.equals(filas.get(0).get("estado"))) {
            boolean repetido = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM evento_incidencia
                        WHERE incidencia = ? AND estado = ? AND comentario = ? AND operador = ?)
                    """, Boolean.class, id, estado, comentario, operador));
            if (!repetido) evento(id, estado, comentario, operador);
            return;
        }
        jdbc.update("""
                UPDATE incidencia_integracion SET estado = ?, actualizada_en = CURRENT_TIMESTAMP,
                    resuelta_en = CASE WHEN ? = 'RESUELTA' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id = ?
                """, estado, estado, id);
        evento(id, estado, comentario, operador);
    }

    private void evento(UUID id, String estado, String comentario, String operador) {
        jdbc.update("""
                INSERT INTO evento_incidencia(id, incidencia, estado, comentario, operador) VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, estado, comentario, operador);
    }
}
