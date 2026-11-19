package com.lysandri.application.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Profile("!prod & !production & (clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name = "purchase.sandbox.enabled", havingValue = "true")
public class CobrosPruebaService {
    private static final int INTENTOS = 3;
    private final JdbcTemplate jdbc;
    private final ComprasPruebaService compras;
    private final TransactionTemplate transaccion;

    public CobrosPruebaService(JdbcTemplate jdbc, ComprasPruebaService compras, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.compras = compras;
        this.transaccion = new TransactionTemplate(manager);
    }

    public record Reporte(UUID referencia, BigDecimal importe, String moneda, String estado) {}
    private record Plan(UUID compra, long usuario, String escenario, String moneda) {}
    private record Cuota(int numero, BigDecimal importe, int intentos) {}

    @Transactional
    public void autorizar(UUID compra, long usuario, boolean acepta, String escenario) {
        if (!acepta || escenario == null || !List.of("APROBAR", "RECUPERAR", "RECHAZAR").contains(escenario)) {
            throw new IllegalArgumentException("Acepta el cronograma y selecciona un escenario de cobro simulado.");
        }
        int creados = jdbc.update("""
                INSERT INTO plan_cobro_prueba(compra,estado,escenario)
                SELECT id,'ACTIVO',? FROM compra_prueba
                WHERE id=? AND id_usuario=? AND estado IN ('PENDIENTE','EN_CUOTAS')
                  AND detalle->>'medio'='IZIPAY' AND jsonb_array_length(detalle->'cuotas')>1
                ON CONFLICT(compra) DO NOTHING
                """, escenario, compra, usuario);
        if (creados == 0 && estado(compra, usuario).stream()
                .noneMatch(p -> "ACTIVO".equals(p.get("estado")) && escenario.equals(p.get("escenario")))) {
            throw new IllegalStateException("Se requiere una compra propia en cuotas por Izipay simulado y sin plan previo.");
        }
    }

    public List<Map<String, Object>> estado(UUID compra, long usuario) {
        return jdbc.queryForList("""
                SELECT p.estado,p.escenario,p.autorizado
                FROM plan_cobro_prueba p JOIN compra_prueba c ON c.id=p.compra
                WHERE p.compra=? AND c.id_usuario=?
                """, compra, usuario);
    }

    public List<Map<String, Object>> historial(UUID compra, long usuario) {
        return jdbc.queryForList("""
                SELECT t.cuota,t.intento,t.estado,t.motivo,t.fecha,t.importe,t.moneda
                FROM transaccion_pasarela_prueba t JOIN compra_prueba c ON c.id=t.compra
                WHERE t.compra=? AND c.id_usuario=? ORDER BY t.cuota,t.intento
                """, compra, usuario);
    }

    public void cancelar(UUID compra, long usuario) {
        jdbc.update("""
                UPDATE plan_cobro_prueba p SET estado='CANCELADO' FROM compra_prueba c
                WHERE p.compra=c.id AND p.compra=? AND c.id_usuario=? AND p.estado='ACTIVO'
                """, compra, usuario);
    }

    @Scheduled(fixedDelayString = "${purchase.sandbox.cobros-ms:30000}")
    public void procesar() {
        var ids = jdbc.queryForList("""
                SELECT p.compra FROM plan_cobro_prueba p
                WHERE p.estado='ACTIVO'
                  AND (EXISTS(SELECT 1 FROM compra_prueba c WHERE c.id=p.compra AND c.estado IN ('APROBADA','RECHAZADA'))
                    OR EXISTS(SELECT 1 FROM cuota_compra_prueba q
                    LEFT JOIN LATERAL (
                        SELECT count(*) AS intentos,max(fecha) AS ultimo FROM transaccion_pasarela_prueba t
                        WHERE t.compra=q.id_compra AND t.cuota=q.numero
                    ) h ON true
                    WHERE q.id_compra=p.compra AND q.estado<>'PAGADA' AND q.vence<=(now() AT TIME ZONE 'UTC')::date
                      AND h.intentos<3 AND (h.ultimo IS NULL OR h.ultimo<=now()-interval '1 minute')))
                ORDER BY p.autorizado,p.compra LIMIT 50
                """, UUID.class);
        for (UUID id : ids) transaccion.executeWithoutResult(tx -> procesarCompra(id));
        conciliar();
    }

    private void procesarCompra(UUID id) {
        var planes = jdbc.query("""
                SELECT p.compra,c.id_usuario,p.escenario,c.detalle->>'moneda' AS moneda
                FROM plan_cobro_prueba p JOIN compra_prueba c ON c.id=p.compra
                WHERE p.compra=? AND p.estado='ACTIVO' FOR UPDATE OF p SKIP LOCKED
                """, (rs,n) -> new Plan(rs.getObject("compra", UUID.class),rs.getLong("id_usuario"),
                rs.getString("escenario"),rs.getString("moneda")), id);
        if (planes.isEmpty()) return;
        Plan plan = planes.get(0);
        String estado = jdbc.queryForObject("SELECT estado FROM compra_prueba WHERE id=? FOR UPDATE", String.class, id);
        if (List.of("APROBADA", "RECHAZADA").contains(estado)) {
            terminar(id);
            return;
        }
        var pendientes = jdbc.query("""
                SELECT q.numero,q.importe,count(t.referencia)::int AS intentos
                FROM cuota_compra_prueba q LEFT JOIN transaccion_pasarela_prueba t ON t.compra=q.id_compra AND t.cuota=q.numero
                WHERE q.id_compra=? AND q.estado<>'PAGADA' AND q.vence<=(now() AT TIME ZONE 'UTC')::date
                GROUP BY q.numero,q.importe
                HAVING count(t.referencia) < ? AND (max(t.fecha) IS NULL OR max(t.fecha)<=now()-interval '1 minute')
                ORDER BY q.numero
                """, (rs,n) -> new Cuota(rs.getInt("numero"),rs.getBigDecimal("importe"),rs.getInt("intentos")), id, INTENTOS);
        for (Cuota cuota : pendientes) {
            int intento = cuota.intentos()+1;
            boolean aprobado = cuota.importe().signum()==0 || "APROBAR".equals(plan.escenario())
                    || ("RECUPERAR".equals(plan.escenario()) && intento>1);
            String resultado = aprobado ? "PAGADA" : "FALLIDA";
            UUID referencia = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO transaccion_pasarela_prueba(referencia,compra,cuota,intento,importe,moneda,estado,motivo)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, referencia,plan.compra(),cuota.numero(),intento,cuota.importe(),plan.moneda(),
                    resultado,aprobado ? null : intento==INTENTOS ? "INTENTOS_AGOTADOS" : "RECHAZO_SIMULADO");
            guardarReporte(new Reporte(referencia,cuota.importe(),plan.moneda(),resultado));
            var compra = compras.registrarCuota(plan.usuario(),id,cuota.numero(),referencia,resultado);
            if ("APROBADA".equals(compra.estado())) terminar(id);
        }
    }

    private void terminar(UUID id) {
        jdbc.update("UPDATE plan_cobro_prueba SET estado='TERMINADO' WHERE compra=?", id);
    }

    public void guardarReporte(Reporte reporte) {
        if (reporte==null || reporte.referencia()==null || reporte.importe()==null
                || reporte.importe().signum()<0 || reporte.importe().scale()>2 || reporte.importe().precision()>10
                || reporte.moneda()==null || !List.of("PEN","USD").contains(reporte.moneda())
                || reporte.estado()==null || !List.of("PAGADA","FALLIDA").contains(reporte.estado()))
            throw new IllegalArgumentException("Revisa la referencia, el importe, la moneda y el estado del reporte simulado.");
        int escritos = jdbc.update("""
                INSERT INTO reporte_pasarela_prueba(referencia,importe,moneda,estado) VALUES (?,?,?,?)
                ON CONFLICT(referencia) DO NOTHING
                """, reporte.referencia(),reporte.importe(),reporte.moneda(),reporte.estado());
        if (escritos == 0 && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM reporte_pasarela_prueba WHERE referencia=? AND importe=? AND moneda=? AND estado=?)
                """, Boolean.class,reporte.referencia(),reporte.importe(),reporte.moneda(),reporte.estado()))) {
            throw new IllegalStateException("La referencia ya está registrada con otros datos.");
        }
    }

    public void conciliar() {
        jdbc.update("""
                INSERT INTO conciliacion_pasarela_prueba(referencia,estado)
                SELECT COALESCE(t.referencia,r.referencia),
                    CASE WHEN t.referencia IS NULL THEN 'SIN_COMPRA' WHEN r.referencia IS NULL THEN 'PENDIENTE'
                         WHEN e.evento IS NULL OR t.importe<>r.importe OR t.moneda<>r.moneda OR e.resultado<>r.estado THEN 'OBSERVADA'
                         ELSE 'CONCILIADA' END
                FROM transaccion_pasarela_prueba t FULL JOIN reporte_pasarela_prueba r USING(referencia)
                LEFT JOIN evento_cuota_prueba e ON e.evento=t.referencia AND e.id_compra=t.compra AND e.numero=t.cuota
                ON CONFLICT(referencia) DO UPDATE SET estado=EXCLUDED.estado,revision=CURRENT_TIMESTAMP
                WHERE conciliacion_pasarela_prueba.estado<>EXCLUDED.estado
                """);
    }

    public List<Map<String, Object>> conciliaciones() {
        return jdbc.queryForList("""
                SELECT c.referencia,t.compra,t.cuota,t.intento,t.importe AS interno,r.importe AS pasarela,
                       coalesce(t.moneda,r.moneda) AS moneda,t.estado AS estado_interno,r.estado AS estado_pasarela,
                       t.motivo,c.estado,c.revision
                FROM conciliacion_pasarela_prueba c LEFT JOIN transaccion_pasarela_prueba t USING(referencia)
                LEFT JOIN reporte_pasarela_prueba r USING(referencia)
                WHERE t.referencia IS NOT NULL OR r.referencia IS NOT NULL
                ORDER BY c.revision DESC,c.referencia LIMIT 200
                """);
    }
}
