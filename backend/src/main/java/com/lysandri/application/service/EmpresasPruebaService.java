package com.lysandri.application.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Profile("!prod & !production & (clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name = "purchase.sandbox.enabled", havingValue = "true")
public class EmpresasPruebaService {
    private final JdbcTemplate jdbc;

    public record Participante(@NotBlank @Size(max=40) String alias, @NotBlank @Size(max=80) String cargo) {}
    public record Orden(
            @NotNull @Positive Long gestor,
            @NotBlank @Size(max=120) String empresa,
            @NotBlank @Pattern(regexp="[A-Z0-9-]{3,40}") String oc,
            @Min(1001) @Max(1006) long programa,
            @NotBlank @Size(max=80) String cohorte,
            @Min(1) @Max(50) int cupos,
            @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal descuento,
            @NotBlank @Pattern(regexp="CONTADO|CREDITO") String condicion,
            LocalDate vence,
            @NotEmpty @Size(max=50) List<@NotNull @Valid Participante> participantes) {}

    @Transactional
    public UUID registrar(Orden orden, long operador) {
        if (orden.participantes().size()>orden.cupos()) throw new IllegalArgumentException("La nómina supera los cupos de la orden.");
        var alias = new HashSet<String>();
        for (Participante p : orden.participantes()) {
            if (!alias.add(p.alias().strip().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("La nómina contiene alias repetidos.");
        }
        boolean credito = "CREDITO".equals(orden.condicion());
        if (credito && (orden.vence()==null || orden.vence().isBefore(LocalDate.now(ZoneOffset.UTC)))
                || !credito && orden.vence()!=null) throw new IllegalArgumentException("Indica una fecha vigente solo para operaciones a crédito.");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM usuario WHERE id_user=? AND rol='CLIENTE'
                  AND activo=true AND bloqueado_admin=false AND pendiente_activacion=false)
                """, Boolean.class, orden.gestor()))) throw new IllegalArgumentException("Selecciona una cuenta de cliente de prueba activa.");
        var programa = SandboxOrderService.programasDePrueba().stream().filter(p -> p.id()==orden.programa()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Programa de muestra no encontrado."));
        BigDecimal bruto = programa.precio().multiply(BigDecimal.valueOf(orden.cupos()));
        BigDecimal total = bruto.subtract(orden.descuento());
        if (total.signum()<=0) throw new IllegalArgumentException("El descuento debe ser menor que el bruto.");
        BigDecimal base = total.divide(new BigDecimal("1.18"),2,RoundingMode.HALF_UP);
        BigDecimal impuesto = total.subtract(base);
        UUID id = UUID.randomUUID();
        int insertados = jdbc.update("""
                INSERT INTO orden_empresa_prueba(id,gestor,operador,empresa,oc,programa,titulo,cohorte,cupos,moneda,bruto,descuento,
                    base,impuesto,condicion,vence,estado)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDIENTE') ON CONFLICT(gestor,oc) DO NOTHING
                """,id,orden.gestor(),operador,orden.empresa().strip(),orden.oc(),programa.id(),programa.titulo(),orden.cohorte().strip(),
                orden.cupos(),programa.moneda(),bruto,orden.descuento(),base,impuesto,orden.condicion(),orden.vence());
        if (insertados==0) throw new IllegalStateException("La orden de compra ya existe para este gestor. Puedes consultarla en el historial.");
        for (Participante participante : orden.participantes()) {
            jdbc.update("INSERT INTO participante_empresa_prueba VALUES (?,?,?)",id,participante.alias().strip(),participante.cargo().strip());
        }
        evento(id, "PENDIENTE", operador);
        return id;
    }

    @Transactional
    public void cambiar(UUID id, String nuevo, long operador) {
        if (nuevo==null) throw new IllegalArgumentException("Selecciona un estado.");
        var filas = jdbc.queryForList("SELECT estado,condicion FROM orden_empresa_prueba WHERE id=? FOR UPDATE",id);
        if (filas.isEmpty()) throw new IllegalArgumentException("No existe esa orden de muestra.");
        var orden = filas.get(0);
        String actual = (String) orden.get("estado");
        if (actual.equals(nuevo)) return;
        if ("PAGADA".equals(nuevo) && List.of("EN_TRANSITO","ENTREGADA").contains(actual)
                && "CREDITO".equals(orden.get("condicion"))) {
            jdbc.update("""
                    INSERT INTO evento_empresa_prueba(orden,estado,operador) VALUES (?,'PAGADA',?)
                    ON CONFLICT(orden,estado) DO NOTHING
                    """,id,operador);
            return;
        }
        var posibles = switch (actual) {
            case "PENDIENTE" -> List.of("APROBADA","ANULADA");
            case "APROBADA" -> List.of("CREDITO".equals(orden.get("condicion")) ? "A_CREDITO" : "PAGADA","ANULADA");
            case "A_CREDITO" -> List.of("EN_TRANSITO","PAGADA","ANULADA");
            case "PAGADA" -> List.of("EN_TRANSITO");
            case "EN_TRANSITO" -> List.of("ENTREGADA");
            default -> List.of();
        };
        if (!posibles.contains(nuevo)) throw new IllegalStateException("La transición no está disponible para esta orden.");
        jdbc.update("UPDATE orden_empresa_prueba SET estado=? WHERE id=?",nuevo,id);
        evento(id,nuevo,operador);
    }

    private void evento(UUID id,String estado,long operador) {
        jdbc.update("INSERT INTO evento_empresa_prueba(orden,estado,operador) VALUES (?,?,?)",id,estado,operador);
    }

    public List<Map<String,Object>> listar(Long gestor) {
        String sql = """
                SELECT o.id,o.empresa,o.oc,o.titulo,o.cohorte,o.cupos,o.moneda,o.bruto,o.descuento,o.base,o.impuesto,
                       o.base+o.impuesto AS total,o.condicion,o.vence,o.estado,o.fecha,
                       CASE WHEN o.estado='ANULADA' THEN 'ANULADO'
                            WHEN pagado.orden IS NOT NULL THEN 'PAGADO'
                            WHEN credito.orden IS NOT NULL THEN 'FACTURADO A CREDITO'
                            ELSE 'PENDIENTE' END AS estado_pago,
                       CASE WHEN o.estado<>'ANULADA' AND (pagado.orden IS NOT NULL OR credito.orden IS NOT NULL)
                            THEN 'DEMO-'||upper(substr(o.id::text,1,8)) END AS comprobante
                FROM orden_empresa_prueba o
                LEFT JOIN evento_empresa_prueba pagado ON pagado.orden=o.id AND pagado.estado='PAGADA'
                LEFT JOIN evento_empresa_prueba credito ON credito.orden=o.id AND credito.estado='A_CREDITO'
                """;
        String orden = " ORDER BY o.fecha DESC,o.id LIMIT 200";
        return gestor == null ? jdbc.queryForList(sql+orden) : jdbc.queryForList(sql+" WHERE o.gestor=?"+orden,gestor);
    }

    public List<Map<String,Object>> participantes(UUID id, Long gestor) {
        String sql = """
                SELECT p.alias,p.cargo, CASE o.estado WHEN 'ENTREGADA' THEN 'MATRICULADA DE PRUEBA'
                                                     WHEN 'ANULADA' THEN 'ANULADA' ELSE 'PENDIENTE' END AS matricula
                FROM participante_empresa_prueba p JOIN orden_empresa_prueba o ON o.id=p.orden
                WHERE o.id=?
                """;
        return gestor==null ? jdbc.queryForList(sql+" ORDER BY p.alias",id)
                : jdbc.queryForList(sql+" AND o.gestor=? ORDER BY p.alias",id,gestor);
    }
}
