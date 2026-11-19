package com.lysandri.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Profile("!prod & !production & (clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name="purchase.sandbox.enabled",havingValue="true")
public class ConversionPruebaService {
    public static final String CONSENTIMIENTO = "prueba-1";
    private final JdbcTemplate jdbc;

    public record Fila(String dispositivo,String canal,Long programa,long visitas,long inicios,long registradas,
                       long aprobadas,long abandonos,long sinConfirmar) {}

    public void visita(UUID id,String dispositivo,String canal,String consentimiento) {
        if(id==null || !CONSENTIMIENTO.equals(consentimiento) || dispositivo==null || canal==null
                || !List.of("MOVIL","TABLETA","ESCRITORIO").contains(dispositivo) || !List.of("DIRECTO","CAMPANA").contains(canal))
            throw new IllegalArgumentException("La visita necesita consentimiento y dimensiones válidas.");
        jdbc.update("""
                INSERT INTO visita_conversion_prueba(id,dispositivo,canal,consentimiento) VALUES (?,?,?,?)
                ON CONFLICT(id) DO NOTHING
                """,id,dispositivo,canal,consentimiento);
    }

    public void iniciar(UUID id,long programa) {
        if(id==null || programa<1001 || programa>1006) throw new IllegalArgumentException("Selecciona un programa de muestra.");
        jdbc.update("""
                UPDATE visita_conversion_prueba SET inicio=COALESCE(inicio,CURRENT_TIMESTAMP), programa=COALESCE(visita_conversion_prueba.programa,?)
                WHERE id=? AND visita>=CURRENT_TIMESTAMP-interval '30 days'
                """,programa,id);
    }

    @Transactional
    public void asociar(UUID id,UUID compra,long usuario) {
        if(id==null || compra==null) throw new IllegalArgumentException("Faltan la visita o la compra.");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","conversion:"+compra);
        jdbc.update("""
                UPDATE visita_conversion_prueba v SET compra=c.id
                FROM compra_prueba c
                WHERE v.id=? AND v.inicio IS NOT NULL AND v.visita>=CURRENT_TIMESTAMP-interval '30 days'
                  AND c.id=? AND c.id_usuario=? AND c.fecha>=v.inicio
                  AND (v.compra IS NULL OR v.compra=c.id)
                  AND EXISTS (SELECT 1 FROM jsonb_array_elements(c.detalle->'programas') p WHERE p->>'id'=v.programa::text)
                  AND NOT EXISTS(SELECT 1 FROM visita_conversion_prueba otra WHERE otra.compra=c.id AND otra.id<>v.id)
                """,id,compra,usuario);
    }

    public void revocar(UUID id) {
        if(id==null) throw new IllegalArgumentException("Falta la visita.");
        jdbc.update("DELETE FROM visita_conversion_prueba WHERE id=?",id);
    }

    @Scheduled(cron="0 17 3 * * *",zone="UTC")
    public void depurar() {
        jdbc.update("DELETE FROM visita_conversion_prueba WHERE visita<CURRENT_TIMESTAMP-interval '30 days'");
    }

    public List<Fila> consultar(LocalDate desde,LocalDate hasta) {
        if(desde==null || hasta==null || desde.isAfter(hasta) || desde.plusDays(30).isBefore(hasta))
            throw new IllegalArgumentException("Selecciona un periodo de hasta 31 días.");
        return jdbc.query("""
                SELECT v.dispositivo,v.canal,v.programa,count(*) AS visitas,
                       count(v.inicio) AS inicios,count(c.id) AS registradas,
                       count(*) FILTER(WHERE c.estado='APROBADA') AS aprobadas,
                       count(*) FILTER(WHERE v.inicio IS NOT NULL AND c.id IS NULL
                           AND v.inicio<=CURRENT_TIMESTAMP-interval '24 hours') AS abandonos,
                       count(*) FILTER(WHERE v.inicio IS NOT NULL AND c.id IS NULL
                           AND v.inicio>CURRENT_TIMESTAMP-interval '24 hours') AS sin_confirmar
                FROM visita_conversion_prueba v LEFT JOIN compra_prueba c ON c.id=v.compra
                WHERE v.visita >= (?::date::timestamp AT TIME ZONE 'UTC')
                  AND v.visita < ((?::date+1)::timestamp AT TIME ZONE 'UTC')
                  AND v.visita>=CURRENT_TIMESTAMP-interval '30 days'
                GROUP BY v.dispositivo,v.canal,v.programa ORDER BY v.dispositivo,v.canal,v.programa
                """,(rs,n)->new Fila(rs.getString("dispositivo"),rs.getString("canal"),rs.getObject("programa",Long.class),
                rs.getLong("visitas"),rs.getLong("inicios"),rs.getLong("registradas"),rs.getLong("aprobadas"),
                rs.getLong("abandonos"),rs.getLong("sin_confirmar")),desde,hasta);
    }
}
