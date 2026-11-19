package com.lysandri.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@Profile("!prod & !production & (clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name="purchase.sandbox.enabled",havingValue="true")
public class ComprasPruebaService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final ObjectMapper json;
    public ComprasPruebaService(JdbcTemplate jdbc, PlatformTransactionManager manager, ObjectMapper json) {
        this.jdbc=jdbc; this.transaccion=new TransactionTemplate(manager); this.json=json;
    }
    public record Solicitud(UUID clave, List<Long> programas, String codigo, int cuotas, String medio, boolean acepta, String revision) {}
    public record Beneficio(String codigo, BigDecimal porcentaje, int cupos, OffsetDateTime vence, boolean activo,
                            String tipo, BigDecimal importeFijo, Long programa, Long cliente, Integer limiteCliente,
                            boolean acumulable, OffsetDateTime inicio) {
        public Beneficio(String codigo,BigDecimal porcentaje,int cupos,OffsetDateTime vence,boolean activo) {
            this(codigo,porcentaje,cupos,vence,activo,null,null,null,null,null,false,null);
        }
    }
    private record Regla(BigDecimal porcentaje,BigDecimal importeFijo,Long programa,Long cliente,
                         Integer limiteCliente,boolean acumulable) {}
    public record Cuota(int numero, LocalDate vence, BigDecimal importe) {}
    public record EstadoCuota(int numero, LocalDate vence, BigDecimal importe, String estado) {}
    public record Cotizacion(List<SandboxOrderService.SandboxProgramResponse> programas, String moneda,
        BigDecimal base, BigDecimal descuento, BigDecimal total, String codigo, String medio, List<Cuota> cuotas, String revision) {}
    public record Compra(UUID id, UUID clave, long usuario, String estado, OffsetDateTime fecha, Cotizacion detalle) {}
    public record Seguimiento(Compra compra, List<EstadoCuota> cuotas) {}
    private record Entrada(List<Long> programas, String codigo, int cuotas, String medio) {}

    public List<SandboxOrderService.SandboxProgramResponse> programas() {return SandboxOrderService.programasDePrueba();}

    public void guardarBeneficio(Beneficio beneficio) {
        if (beneficio==null) throw new IllegalArgumentException("Indica las condiciones del beneficio.");
        String tipo = Objects.requireNonNullElse(beneficio.tipo(),"DESCUENTO");
        BigDecimal porcentaje = beneficio.importeFijo()==null ? beneficio.porcentaje() : new BigDecimal("100");
        if(beneficio.codigo()==null || !beneficio.codigo().matches("[A-Z0-9-]{3,30}")
            || porcentaje==null || porcentaje.scale()>2 || porcentaje.signum()<=0
            || porcentaje.compareTo(new BigDecimal("100"))>0 || beneficio.cupos()<1 || beneficio.cupos()>10000
            || !List.of("DESCUENTO","BECA","CONVENIO").contains(tipo)
            || (beneficio.importeFijo()!=null && (beneficio.importeFijo().scale()>2 || beneficio.importeFijo().signum()<=0
                    || beneficio.importeFijo().compareTo(new BigDecimal("99999999.99"))>0))
            || (beneficio.programa()!=null && programas().stream().noneMatch(p->p.id().equals(beneficio.programa())))
            || (beneficio.cliente()!=null && beneficio.cliente()<=0)
            || (beneficio.limiteCliente()!=null && (beneficio.limiteCliente()<1 || beneficio.limiteCliente()>beneficio.cupos()))
            || (beneficio.inicio()!=null && (beneficio.vence()==null || !beneficio.inicio().isBefore(beneficio.vence())))
            || beneficio.vence()==null || !beneficio.vence().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
            throw new IllegalArgumentException("Revisa el código, porcentaje, cupos y fecha futura del beneficio de prueba.");
        int cambios=jdbc.update("""
            INSERT INTO beneficio_prueba(codigo,porcentaje,cupos,vence,activo,tipo,importe_fijo,programa,cliente,limite_cliente,acumulable,inicio)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,COALESCE(?::timestamptz,'-infinity'::timestamptz))
            ON CONFLICT(codigo) DO UPDATE SET porcentaje=EXCLUDED.porcentaje,cupos=EXCLUDED.cupos,
                vence=EXCLUDED.vence,activo=EXCLUDED.activo,tipo=EXCLUDED.tipo,importe_fijo=EXCLUDED.importe_fijo,
                programa=EXCLUDED.programa,cliente=EXCLUDED.cliente,limite_cliente=EXCLUDED.limite_cliente,
                acumulable=EXCLUDED.acumulable,inicio=EXCLUDED.inicio WHERE beneficio_prueba.usados <= EXCLUDED.cupos
            """,beneficio.codigo(),porcentaje,beneficio.cupos(),beneficio.vence(),beneficio.activo(),tipo,
            beneficio.importeFijo(),beneficio.programa(),beneficio.cliente(),beneficio.limiteCliente(),beneficio.acumulable(),beneficio.inicio());
        if(cambios!=1) throw new IllegalStateException("Los cupos no pueden ser menores que los usos ya registrados.");
    }

    public Cotizacion cotizar(Solicitud solicitud) {return calcular(normalizar(solicitud),null,false);}

    public Cotizacion cotizar(Solicitud solicitud,long usuario) {return calcular(normalizar(solicitud),usuario,false);}

    public List<Map<String,Object>> beneficios() {
        return jdbc.queryForList("""
                SELECT codigo,tipo,porcentaje,importe_fijo,programa,cliente,limite_cliente,acumulable,cupos,usados,vence,activo
                FROM beneficio_prueba ORDER BY codigo LIMIT 200
                """);
    }

    public Compra registrar(long usuario, Solicitud solicitud) {
        Entrada entrada=normalizar(solicitud);
        if(solicitud.clave()==null || !solicitud.acepta() || solicitud.revision()==null) throw new IllegalArgumentException("Confirma las condiciones y la clave de la solicitud.");
        String hash=hash(escribir(List.of(entrada,solicitud.revision())));
        return transaccion.execute(tx->{
            // La misma clave conserva el resultado y no vuelve a consumir un cupo.
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",usuario+":"+solicitud.clave());
            var existentes=jdbc.queryForList("SELECT id,solicitud_hash FROM compra_prueba WHERE id_usuario=? AND clave=?",usuario,solicitud.clave());
            if(!existentes.isEmpty()) {
                if(!hash.equals(existentes.get(0).get("solicitud_hash"))) throw new IllegalStateException("Esta clave ya corresponde a otra compra. Inicia una nueva solicitud.");
                return buscar((UUID)existentes.get(0).get("id"));
            }
            Cotizacion detalle=calcular(entrada,usuario,true);
            if(!detalle.revision().equals(solicitud.revision())) throw new IllegalStateException("Las condiciones cambiaron. Solicita una nueva cotización antes de confirmar.");
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO compra_prueba(id,id_usuario,clave,solicitud_hash,detalle) VALUES (?,?,?,?,?::jsonb)",id,usuario,solicitud.clave(),hash,escribir(detalle));
            for(var cuota:detalle.cuotas()) jdbc.update("INSERT INTO cuota_compra_prueba(id_compra,numero,vence,importe) VALUES (?,?,?,?)",id,cuota.numero(),cuota.vence(),cuota.importe());
            return buscar(id);
        });
    }

    public Optional<Seguimiento> seguimiento(long usuario, UUID id) {
        return transaccion.execute(tx -> {
            var compras = jdbc.query("SELECT * FROM compra_prueba WHERE id = ? AND id_usuario = ? FOR SHARE",
                    (rs, n) -> leer(rs), id, usuario);
            return compras.stream().findFirst().map(compra -> new Seguimiento(compra, cuotas(id)));
        });
    }

    public List<Compra> listar() {
        return jdbc.query("SELECT * FROM compra_prueba ORDER BY fecha DESC,id LIMIT 100",(rs,n)->leer(rs));
    }

    public Compra resultado(long operador, UUID id, UUID evento, String resultado) {
        if(evento==null || !("APROBADA".equals(resultado) || "RECHAZADA".equals(resultado)))
            throw new IllegalArgumentException("Elige un resultado de prueba válido y un identificador de evento.");
        return transaccion.execute(tx->{
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","evento-prueba:"+evento);
            var filas=jdbc.query("SELECT * FROM compra_prueba WHERE id=? FOR UPDATE",(rs,n)->leer(rs),id);
            if(filas.isEmpty()) throw new IllegalArgumentException("No existe la compra de prueba.");
            Compra compra=filas.get(0);
            var anteriores=jdbc.queryForList("SELECT id_compra,resultado FROM evento_compra_prueba WHERE evento=?",evento);
            if(!anteriores.isEmpty()) {
                if(!id.equals(anteriores.get(0).get("id_compra")) || !resultado.equals(anteriores.get(0).get("resultado")))
                    throw new IllegalStateException("El evento ya se utilizó con otro resultado.");
                return compra;
            }
            if(!"PENDIENTE".equals(compra.estado())) throw new IllegalStateException("La compra ya tiene un resultado definitivo.");
            jdbc.update("INSERT INTO evento_compra_prueba(evento,id_compra,id_operador,resultado) VALUES (?,?,?,?)",evento,id,operador,resultado);
            jdbc.update("UPDATE compra_prueba SET estado=? WHERE id=?",resultado,id);
            jdbc.update("UPDATE cuota_compra_prueba SET estado=? WHERE id_compra=?","APROBADA".equals(resultado)?"PAGADA":"FALLIDA",id);
            return buscar(id);
        });
    }

    public List<EstadoCuota> cuotas(UUID id) {
        return jdbc.query("SELECT numero,vence,importe,CASE WHEN estado='PENDIENTE' AND vence<(clock_timestamp() AT TIME ZONE 'UTC')::date THEN 'VENCIDA' ELSE estado END AS estado FROM cuota_compra_prueba WHERE id_compra=? ORDER BY numero",
            (rs,n)->new EstadoCuota(rs.getInt("numero"),rs.getObject("vence",LocalDate.class),rs.getBigDecimal("importe"),rs.getString("estado")),id);
    }

    public Compra registrarCuota(long operador,UUID id,int numero,UUID evento,String resultado) {
        if(evento==null || !("PAGADA".equals(resultado) || "FALLIDA".equals(resultado))) throw new IllegalArgumentException("Selecciona un resultado de cuota válido.");
        return transaccion.execute(tx->{
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","cuota-prueba:"+evento);
            var compras=jdbc.query("SELECT * FROM compra_prueba WHERE id=? FOR UPDATE",(rs,n)->leer(rs),id);
            if(compras.isEmpty()) throw new IllegalArgumentException("No existe la compra de prueba.");
            var anteriores=jdbc.queryForList("SELECT id_compra,numero,resultado FROM evento_cuota_prueba WHERE evento=?",evento);
            if(!anteriores.isEmpty()) {
                var anterior=anteriores.get(0);
                if(!id.equals(anterior.get("id_compra")) || !Integer.valueOf(numero).equals(anterior.get("numero")) || !resultado.equals(anterior.get("resultado")))
                    throw new IllegalStateException("El evento ya pertenece a otro resultado de cuota.");
                return compras.get(0);
            }
            if(!List.of("PENDIENTE","EN_CUOTAS").contains(compras.get(0).estado())) throw new IllegalStateException("La compra ya tiene un resultado definitivo.");
            var cuotas=cuotas(id);var cuota=cuotas.stream().filter(c->c.numero()==numero).findFirst().orElseThrow(()->new IllegalArgumentException("No existe esa cuota."));
            if("PAGADA".equals(cuota.estado())) throw new IllegalStateException("La cuota ya se registró como pagada.");
            jdbc.update("INSERT INTO evento_cuota_prueba(evento,id_compra,numero,id_operador,resultado) VALUES (?,?,?,?,?)",evento,id,numero,operador,resultado);
            jdbc.update("UPDATE cuota_compra_prueba SET estado=? WHERE id_compra=? AND numero=?",resultado,id,numero);
            var actualizadas=cuotas(id);
            long pagadas=actualizadas.stream().filter(c->"PAGADA".equals(c.estado())).count();
            String estado=pagadas==actualizadas.size()?"APROBADA":pagadas>0?"EN_CUOTAS":"PENDIENTE";
            jdbc.update("UPDATE compra_prueba SET estado=? WHERE id=?",estado,id);
            return buscar(id);
        });
    }

    private Entrada normalizar(Solicitud solicitud) {
        if(solicitud==null || solicitud.programas()==null || solicitud.programas().isEmpty() || solicitud.programas().size()>6
            || solicitud.programas().stream().anyMatch(Objects::isNull) || new HashSet<>(solicitud.programas()).size()!=solicitud.programas().size()
            || solicitud.cuotas()<1 || solicitud.cuotas()>3 || !List.of("IZIPAY","BCP","INTERBANK").contains(solicitud.medio()==null?"":solicitud.medio()))
            throw new IllegalArgumentException("Selecciona programas distintos, de una a tres cuotas y un medio de prueba.");
        String codigo=solicitud.codigo()==null?"":solicitud.codigo().trim().toUpperCase(Locale.ROOT);
        if (!codigo.isEmpty()) {
            var codigos = Arrays.stream(codigo.split(",",-1)).map(String::strip).sorted().toList();
            if (codigos.size()>3 || new HashSet<>(codigos).size()!=codigos.size()
                    || codigos.stream().anyMatch(c->!c.matches("[A-Z0-9-]{3,30}"))) {
                throw new IllegalArgumentException("Usa hasta tres códigos distintos, separados por comas.");
            }
            codigo=String.join(",",codigos);
        }
        return new Entrada(solicitud.programas().stream().sorted().toList(),codigo,solicitud.cuotas(),solicitud.medio());
    }

    private Cotizacion calcular(Entrada entrada, Long usuario, boolean consumir) {
        var seleccion=programas().stream().filter(p->entrada.programas().contains(p.id())).toList();
        if(seleccion.size()!=entrada.programas().size()) throw new IllegalArgumentException("Uno de los programas no pertenece a este escenario de prueba.");
        BigDecimal base=seleccion.stream().map(SandboxOrderService.SandboxProgramResponse::precio).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);
        BigDecimal descuento=descuento(entrada,seleccion,usuario,consumir);
        BigDecimal total=base.subtract(descuento);
        long centavos=total.movePointRight(2).longValueExact();
        List<Cuota> cuotas=new ArrayList<>();
        LocalDate hoy=LocalDate.now(ZoneOffset.UTC);
        for(int i=0;i<entrada.cuotas();i++) cuotas.add(new Cuota(i+1,hoy.plusMonths(i),BigDecimal.valueOf(centavos/entrada.cuotas()+(i<centavos%entrada.cuotas()?1:0),2)));
        String revision=hash(escribir(List.of(seleccion,base,descuento,total,entrada.codigo(),entrada.medio(),cuotas)));
        return new Cotizacion(seleccion,"USD",base,descuento,total,entrada.codigo(),entrada.medio(),cuotas,revision);
    }

    private BigDecimal descuento(Entrada entrada,List<SandboxOrderService.SandboxProgramResponse> seleccion,Long usuario,boolean consumir) {
        if (entrada.codigo().isEmpty()) return BigDecimal.ZERO.setScale(2);
        var codigos = entrada.codigo().split(",");
        Map<Long,BigDecimal> restantes = new LinkedHashMap<>();
        for (var programa : seleccion) restantes.put(programa.id(),programa.precio());
        BigDecimal descuento = BigDecimal.ZERO.setScale(2);
        for (String codigo : codigos) {
            var reglas = jdbc.query("""
                    SELECT porcentaje,importe_fijo,programa,cliente,limite_cliente,acumulable FROM beneficio_prueba
                    WHERE codigo=? AND activo AND inicio<=clock_timestamp() AND vence>clock_timestamp() AND usados<cupos
                    """+(consumir?" FOR UPDATE":""),(rs,n)->new Regla(rs.getBigDecimal("porcentaje"),rs.getBigDecimal("importe_fijo"),
                    rs.getObject("programa",Long.class),rs.getObject("cliente",Long.class),
                    rs.getObject("limite_cliente",Integer.class),rs.getBoolean("acumulable")),codigo);
            if (reglas.isEmpty()) throw new IllegalArgumentException("Código inexistente, desactivado, vencido o sin cupos.");
            Regla regla=reglas.get(0);
            if (regla.cliente()!=null && !regla.cliente().equals(usuario) || regla.programa()!=null && !restantes.containsKey(regla.programa()))
                throw new IllegalArgumentException("El beneficio no aplica a este cliente o programa.");
            if (codigos.length>1 && !regla.acumulable()) throw new IllegalArgumentException("El beneficio no permite acumular códigos.");
            if (regla.limiteCliente()!=null && (usuario==null || jdbc.queryForObject("""
                    SELECT count(*) FROM compra_prueba
                    WHERE id_usuario=? AND ?=ANY(string_to_array(detalle->>'codigo',','))
                    """,Long.class,usuario,codigo)>=regla.limiteCliente())) {
                throw new IllegalArgumentException("Se alcanzó el límite del beneficio para este cliente.");
            }
            var aplicables = restantes.entrySet().stream()
                    .filter(p -> regla.programa()==null || regla.programa().equals(p.getKey())).toList();
            BigDecimal importe = aplicables.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal rebaja = regla.importeFijo()==null
                    ? importe.multiply(regla.porcentaje()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
                    : regla.importeFijo().min(importe).setScale(2);
            if (rebaja.signum()==0) throw new IllegalArgumentException("El beneficio no genera un descuento en esta selección.");
            BigDecimal porDistribuir=rebaja;
            for (var programa : aplicables) {
                BigDecimal aplicado=programa.getValue().min(porDistribuir);
                programa.setValue(programa.getValue().subtract(aplicado));
                porDistribuir=porDistribuir.subtract(aplicado);
            }
            descuento=descuento.add(rebaja);
            if (consumir) jdbc.update("UPDATE beneficio_prueba SET usados=usados+1 WHERE codigo=?",codigo);
        }
        return descuento;
    }

    private Compra buscar(UUID id) {return jdbc.queryForObject("SELECT * FROM compra_prueba WHERE id=?",(rs,n)->leer(rs),id);}
    private Compra leer(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {return new Compra(rs.getObject("id",UUID.class),rs.getObject("clave",UUID.class),rs.getLong("id_usuario"),rs.getString("estado"),rs.getObject("fecha",OffsetDateTime.class),json.readValue(rs.getString("detalle"),Cotizacion.class));}
        catch(JsonProcessingException e) {throw new IllegalStateException("No se pudo leer el detalle registrado.",e);}
    }
    private String escribir(Object objeto) {
        try {return json.writeValueAsString(objeto);}
        catch(JsonProcessingException e) {throw new IllegalStateException("No se pudo preparar la solicitud.",e);}
    }
    private static String hash(String contenido) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
