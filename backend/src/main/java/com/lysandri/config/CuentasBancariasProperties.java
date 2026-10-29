package com.lysandri.config;

import com.lysandri.adapters.in.web.dto.CuentaBancariaAprobadaDto;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.bancos")
public class CuentasBancariasProperties {

    // Alias autorizados exactos para cada entidad financiera oficial (incluyendo variantes históricas)
    public static final Set<String> ALIASES_BCP = Set.of(
            "BCP", "BANCODECREDITO", "BANCODECREDITODELPERU", "BANCOCREDITO", "CREDITO"
    );
    public static final Set<String> ALIASES_INTERBANK = Set.of(
            "INTERBANK", "BANCOINTERNACIONALDELPERU", "BANCOINTERNACIONAL", "INTERBANKPERU"
    );
    public static final Set<String> ALIASES_YAPE = Set.of(
            "YAPE", "BILLETERAYAPE", "BILLETERADIGITALYAPE"
    );
    public static final Set<String> ALIASES_PLIN = Set.of(
            "PLIN", "BILLETERAPLIN", "BILLETERADIGITALPLIN"
    );

    private CuentaConfig bcp = new CuentaConfig("BCP", "LYSANDRI S.A.C.", "USD", "CORRIENTE", "", "", false);
    private CuentaConfig interbank = new CuentaConfig("INTERBANK", "LYSANDRI S.A.C.", "USD", "CORRIENTE", "", "", false);
    private BilleteraConfig yape = new BilleteraConfig("YAPE", "LYSANDRI S.A.C.", "PEN", "", false);
    private BilleteraConfig plin = new BilleteraConfig("PLIN", "LYSANDRI S.A.C.", "PEN", "", false);

    public record DestinoCanonico(
            String idDestino,
            String entidadCanonica,
            String cuentaCanonica,
            String cciCanonico,
            String titularCanonico,
            String monedaCanonica,
            String metodoPermitido
    ) {
        public DestinoCanonico(
                String idDestino,
                String entidadCanonica,
                String cuentaCanonica,
                String titularCanonico,
                String monedaCanonica,
                String metodoPermitido
        ) {
            this(idDestino, entidadCanonica, cuentaCanonica, null, titularCanonico, monedaCanonica, metodoPermitido);
        }
    }

    /**
     * Comprueba si una cadena bancaria arbitraria (histórica o actual) corresponde a una entidad canónica oficial.
     */
    public static boolean esAliasDeEntidad(String banco, String entidadCanonica) {
        if (banco == null || entidadCanonica == null) return false;
        String bNorm = normalizarIdentificador(banco);
        String eNorm = normalizarIdentificador(entidadCanonica);
        if (bNorm.equals(eNorm)) return true;
        if ("BCP".equals(eNorm)) return ALIASES_BCP.contains(bNorm) || bNorm.contains("BCP") || bNorm.contains("CREDITO");
        if ("INTERBANK".equals(eNorm)) return ALIASES_INTERBANK.contains(bNorm) || bNorm.contains("INTERBANK") || bNorm.contains("INTERNACIONAL");
        if ("YAPE".equals(eNorm)) return ALIASES_YAPE.contains(bNorm) || bNorm.contains("YAPE");
        if ("PLIN".equals(eNorm)) return ALIASES_PLIN.contains(bNorm) || bNorm.contains("PLIN");
        return false;
    }

    /**
     * Comprueba si una cuenta o CCI arbitrario (histórico o actual) corresponde a la cuenta canónica o CCI oficial.
     */
    public static boolean coincideConCuentaOCI(String cuentaRaw, String cuentaCanonica, String cciCanonico) {
        if (cuentaRaw == null) return false;
        String cNorm = normalizarIdentificador(cuentaRaw);
        if (cNorm.isBlank()) return false;
        String ctaNorm = cuentaCanonica != null ? normalizarIdentificador(cuentaCanonica) : "";
        String cciNorm = cciCanonico != null ? normalizarIdentificador(cciCanonico) : "";

        if (!ctaNorm.isBlank()) {
            if (cNorm.equals(ctaNorm) || cNorm.contains(ctaNorm) || ctaNorm.contains(cNorm)) {
                return true;
            }
        }
        if (!cciNorm.isBlank()) {
            if (cNorm.equals(cciNorm) || cNorm.contains(cciNorm) || cciNorm.contains(cNorm)) {
                return true;
            }
        }
        return false;
    }

    @Data
    public static class CuentaConfig {
        private String banco;
        private String titular;
        private String moneda;
        private String tipoCuenta;
        private String numeroCuenta;
        private String cci;
        private boolean activo;

        public CuentaConfig() {}

        public CuentaConfig(String banco, String titular, String moneda, String tipoCuenta, String numeroCuenta, String cci, boolean activo) {
            this.banco = banco;
            this.titular = titular;
            this.moneda = moneda;
            this.tipoCuenta = tipoCuenta;
            this.numeroCuenta = numeroCuenta;
            this.cci = cci;
            this.activo = activo;
        }
    }

    @Data
    public static class BilleteraConfig {
        private String nombre;
        private String titular;
        private String moneda;
        private String telefono;
        private boolean activo;

        public BilleteraConfig() {}

        public BilleteraConfig(String nombre, String titular, String moneda, String telefono, boolean activo) {
            this.nombre = nombre;
            this.titular = titular;
            this.moneda = moneda;
            this.telefono = telefono;
            this.activo = activo;
        }
    }

    /**
     * Normaliza un identificador bancario o número de operación eliminando espacios,
     * guiones, puntos y barras, convirtiendo a mayúsculas y conservando estrictamente
     * los ceros a la izquierda para evitar duplicados por formato.
     */
    public static String normalizarIdentificador(String id) {
        if (id == null) return "";
        return id.replaceAll("[\\s\\-\\./_]", "").toUpperCase();
    }

    public List<CuentaBancariaAprobadaDto> obtenerCuentas() {
        List<CuentaBancariaAprobadaDto> list = new ArrayList<>();
        list.add(mapearCuenta("bcp-oficial", bcp));
        list.add(mapearCuenta("interbank-oficial", interbank));
        if (yape != null && yape.getTelefono() != null && !yape.getTelefono().isBlank()) {
            list.add(mapearBilletera("yape-oficial", yape));
        }
        if (plin != null && plin.getTelefono() != null && !plin.getTelefono().isBlank()) {
            list.add(mapearBilletera("plin-oficial", plin));
        }
        return list;
    }

    private CuentaBancariaAprobadaDto mapearCuenta(String id, CuentaConfig cfg) {
        boolean esActiva = cfg != null
                && cfg.isActivo()
                && cfg.getNumeroCuenta() != null && !cfg.getNumeroCuenta().isBlank()
                && cfg.getCci() != null && !cfg.getCci().isBlank();

        return CuentaBancariaAprobadaDto.builder()
                .id(id)
                .banco(cfg != null ? cfg.getBanco() : "BANCO")
                .titular(cfg != null ? cfg.getTitular() : "LYSANDRI S.A.C.")
                .moneda(cfg != null ? cfg.getMoneda() : "USD")
                .tipoCuenta(cfg != null ? cfg.getTipoCuenta() : "CORRIENTE")
                .numeroCuenta(esActiva ? cfg.getNumeroCuenta() : null)
                .cci(esActiva ? cfg.getCci() : null)
                .activo(esActiva)
                .estado(esActiva ? "OFICIAL_ACTIVA" : "PENDIENTE_CONFIRMACION_OFICIAL")
                .instrucciones(esActiva
                        ? "Realice su transferencia bancaria o telecrédito a la cuenta indicada e ingrese el número de operación en el comprobante."
                        : "Cuenta bancaria directa en proceso de formalización oficial. Registre su orden y coordine los detalles con nuestra asesora por WhatsApp o pague con izipay Online.")
                .build();
    }

    private CuentaBancariaAprobadaDto mapearBilletera(String id, BilleteraConfig cfg) {
        boolean esActiva = cfg != null && cfg.isActivo() && cfg.getTelefono() != null && !cfg.getTelefono().isBlank();

        return CuentaBancariaAprobadaDto.builder()
                .id(id)
                .banco(cfg != null ? cfg.getNombre() : "BILLETERA")
                .titular(cfg != null ? cfg.getTitular() : "LYSANDRI S.A.C.")
                .moneda(cfg != null ? cfg.getMoneda() : "PEN")
                .tipoCuenta("BILLETERA_DIGITAL")
                .numeroCuenta(esActiva ? cfg.getTelefono() : null)
                .cci(null)
                .activo(esActiva)
                .estado(esActiva ? "OFICIAL_ACTIVA" : "PENDIENTE_CONFIRMACION_OFICIAL")
                .instrucciones(esActiva
                        ? "Realice su abono móvil al número indicado e ingrese el número de operación del comprobante digital."
                        : "Billetera digital en proceso de formalización oficial.")
                .build();
    }

    /**
     * Valida y resuelve los datos de abono a su registro canónico oficial configurado,
     * rechazando variantes ambiguas o no autorizadas y exigiendo que la cuenta esté activa.
     */
    public DestinoCanonico validarYResolverDestinoCanonico(String metodoPago, String banco, String cuentaDestino, String moneda) {
        return resolverDestinoCanonico(metodoPago, banco, cuentaDestino, moneda, true);
    }

    /**
     * Resuelve los datos de abono a su registro canónico oficial, con opción de verificar estado activo.
     */
    public DestinoCanonico resolverDestinoCanonico(
            String metodoPago,
            String banco,
            String cuentaDestino,
            String moneda,
            boolean exigirCuentaActiva
    ) {
        String metodoNorm = normalizarIdentificador(metodoPago);
        String bancoNorm = normalizarIdentificador(banco);
        String cuentaNorm = normalizarIdentificador(cuentaDestino);
        String monedaNorm = normalizarIdentificador(moneda);

        if (bancoNorm.isBlank()) {
            throw new IllegalArgumentException("El banco o entidad de destino es obligatorio");
        }
        if (cuentaNorm.isBlank()) {
            throw new IllegalArgumentException("La cuenta bancaria o número de destino es obligatorio");
        }
        if (monedaNorm.isBlank()) {
            throw new IllegalArgumentException("La moneda del abono es obligatoria");
        }

        // 1. Caso Billetera Digital: YAPE
        if (ALIASES_YAPE.contains(bancoNorm)) {
            if (!"YAPE".equals(metodoNorm)) {
                throw new IllegalArgumentException("Método de pago incompatible: La orden requiere método '"
                        + metodoPago + "' pero se especificó la entidad Yape.");
            }
            if (yape == null || yape.getTelefono() == null || yape.getTelefono().isBlank()) {
                throw new IllegalArgumentException("No existe una cuenta oficial de Yape configurada en el sistema.");
            }
            String telNorm = normalizarIdentificador(yape.getTelefono());
            if (!telNorm.equals(cuentaNorm)) {
                throw new IllegalArgumentException("El número de destino no coincide con la cuenta oficial de Yape configurada.");
            }
            if (exigirCuentaActiva && !yape.isActivo()) {
                throw new IllegalArgumentException("La cuenta oficial de Yape se encuentra desactivada temporalmente.");
            }
            if (!normalizarIdentificador(yape.getMoneda()).equals(monedaNorm)) {
                throw new IllegalArgumentException("Desajuste de moneda: La cuenta oficial de Yape opera en "
                        + yape.getMoneda() + ", pero el abono especifica " + moneda + ".");
            }
            return new DestinoCanonico(
                    "yape-oficial",
                    "YAPE",
                    telNorm,
                    yape.getTitular(),
                    normalizarIdentificador(yape.getMoneda()),
                    "YAPE"
            );
        }

        // 2. Caso Billetera Digital: PLIN
        if (ALIASES_PLIN.contains(bancoNorm)) {
            if (!"PLIN".equals(metodoNorm)) {
                throw new IllegalArgumentException("Método de pago incompatible: La orden requiere método '"
                        + metodoPago + "' pero se especificó la entidad Plin.");
            }
            if (plin == null || plin.getTelefono() == null || plin.getTelefono().isBlank()) {
                throw new IllegalArgumentException("No existe una cuenta oficial de Plin configurada en el sistema.");
            }
            String telNorm = normalizarIdentificador(plin.getTelefono());
            if (!telNorm.equals(cuentaNorm)) {
                throw new IllegalArgumentException("El número de destino no coincide con la cuenta oficial de Plin configurada.");
            }
            if (exigirCuentaActiva && !plin.isActivo()) {
                throw new IllegalArgumentException("La cuenta oficial de Plin se encuentra desactivada temporalmente.");
            }
            if (!normalizarIdentificador(plin.getMoneda()).equals(monedaNorm)) {
                throw new IllegalArgumentException("Desajuste de moneda: La cuenta oficial de Plin opera en "
                        + plin.getMoneda() + ", pero el abono especifica " + moneda + ".");
            }
            return new DestinoCanonico(
                    "plin-oficial",
                    "PLIN",
                    telNorm,
                    plin.getTitular(),
                    normalizarIdentificador(plin.getMoneda()),
                    "PLIN"
            );
        }

        // Si el método de la orden es YAPE o PLIN pero la entidad no es la correspondiente:
        if ("YAPE".equals(metodoNorm)) {
            throw new IllegalArgumentException("Método de pago incompatible: La orden requiere YAPE pero se especificó '" + banco + "'.");
        }
        if ("PLIN".equals(metodoNorm)) {
            throw new IllegalArgumentException("Método de pago incompatible: La orden requiere PLIN pero se especificó '" + banco + "'.");
        }

        // 3. Caso Transferencia Bancaria: BCP o INTERBANK
        if (!"TRANSFERENCIA".equals(metodoNorm)) {
            throw new IllegalArgumentException("Método de pago no admitido para abonos bancarios: " + metodoPago);
        }

        CuentaConfig cuentaOficial;
        String idDestino;
        String entidadCanonica;

        if (ALIASES_BCP.contains(bancoNorm)) {
            cuentaOficial = bcp;
            idDestino = "bcp-oficial";
            entidadCanonica = "BCP";
        } else if (ALIASES_INTERBANK.contains(bancoNorm)) {
            cuentaOficial = interbank;
            idDestino = "interbank-oficial";
            entidadCanonica = "INTERBANK";
        } else {
            throw new IllegalArgumentException("Banco de destino no reconocido o no autorizado: " + banco
                    + ". Solo se admiten cuentas oficiales BCP e Interbank para transferencias.");
        }

        if (cuentaOficial == null || cuentaOficial.getNumeroCuenta() == null || cuentaOficial.getNumeroCuenta().isBlank()) {
            throw new IllegalArgumentException("No hay una cuenta oficial configurada para el banco: " + entidadCanonica);
        }

        String nroOficialNorm = normalizarIdentificador(cuentaOficial.getNumeroCuenta());
        String cciOficialNorm = normalizarIdentificador(cuentaOficial.getCci());

        boolean cuentaCoincide = (!nroOficialNorm.isBlank() && nroOficialNorm.equals(cuentaNorm))
                || (!cciOficialNorm.isBlank() && cciOficialNorm.equals(cuentaNorm));

        if (!cuentaCoincide) {
            throw new IllegalArgumentException("La cuenta o CCI ingresado (" + cuentaDestino
                    + ") no coincide con la cuenta oficial activa del banco " + entidadCanonica);
        }

        if (exigirCuentaActiva && !cuentaOficial.isActivo()) {
            throw new IllegalArgumentException("La cuenta oficial de " + entidadCanonica
                    + " se encuentra desactivada. No se pueden aprobar abonos en cuentas inactivas.");
        }

        String monedaOficialNorm = normalizarIdentificador(cuentaOficial.getMoneda());
        if (!monedaOficialNorm.isBlank() && !monedaOficialNorm.equals(monedaNorm)) {
            throw new IllegalArgumentException("Desajuste de moneda: La cuenta oficial de " + entidadCanonica
                    + " opera en " + cuentaOficial.getMoneda() + ", pero el abono especifica " + moneda + ".");
        }

        // Se retorna SIEMPRE el destino canónico oficial con su cuenta y CCI para auditoría completa
        return new DestinoCanonico(
                idDestino,
                entidadCanonica,
                nroOficialNorm,
                cciOficialNorm,
                cuentaOficial.getTitular(),
                monedaOficialNorm,
                "TRANSFERENCIA"
        );
    }

    /**
     * Valida de forma estricta que los datos del abono correspondan a una cuenta oficial configurada y activa.
     * Mantiene compatibilidad con firmas previas.
     */
    public void validarDestinoParaAprobacion(String metodoPago, String banco, String cuentaDestino, String moneda) {
        validarYResolverDestinoCanonico(metodoPago, banco, cuentaDestino, moneda);
    }
}
