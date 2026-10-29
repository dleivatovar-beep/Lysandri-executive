package com.lysandri.adapters.out.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.model.whatsapp.ResultadoLlamadaWhatsApp;
import com.lysandri.domain.ports.out.WhatsAppCloudClientPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Component
public class WhatsAppCloudApiAdapter implements WhatsAppCloudClientPort {

    private final boolean enabled;
    private final String apiUrl;
    private final String phoneNumberId;
    private final String accessToken;
    private final String verifyToken;
    private final String appSecret;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WhatsAppCloudApiAdapter(
            @Value("${whatsapp.enabled:false}") boolean enabled,
            @Value("${whatsapp.api-url:https://graph.facebook.com/v21.0}") String apiUrl,
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.access-token:}") String accessToken,
            @Value("${whatsapp.verify-token:}") String verifyToken,
            @Value("${whatsapp.app-secret:}") String appSecret,
            @Value("${whatsapp.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${whatsapp.read-timeout-ms:10000}") int readTimeoutMs,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl.replaceAll("/+$", "") : "https://graph.facebook.com/v21.0";
        this.phoneNumberId = phoneNumberId != null ? phoneNumberId.trim() : "";
        this.accessToken = accessToken != null ? accessToken.trim() : "";
        this.verifyToken = verifyToken != null ? verifyToken.trim() : "";
        this.appSecret = appSecret != null ? appSecret.trim() : "";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.add(0, new ByteArrayHttpMessageConverter());
                    converters.add(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
                    converters.add(new MappingJackson2HttpMessageConverter(this.objectMapper));
                })
                .build();

        if (this.enabled) {
            validarConfiguracion();
            log.info("Integración de WhatsApp Business Cloud API habilitada para Phone Number ID: {}", enmascararId(this.phoneNumberId));
        } else {
            log.info("Integración de WhatsApp Business Cloud API deshabilitada por configuración (whatsapp.enabled=false)");
        }
    }

    private void validarConfiguracion() {
        List<String> faltantes = new ArrayList<>();
        if (phoneNumberId.isBlank()) faltantes.add("WHATSAPP_PHONE_NUMBER_ID");
        if (accessToken.isBlank()) faltantes.add("WHATSAPP_ACCESS_TOKEN");
        if (verifyToken.isBlank()) faltantes.add("WHATSAPP_VERIFY_TOKEN");
        if (appSecret.isBlank()) faltantes.add("WHATSAPP_APP_SECRET");

        if (!faltantes.isEmpty()) {
            throw new IllegalStateException(
                    "Integración de WhatsApp habilitada pero con configuración incompleta. Variables requeridas ausentes: "
                            + String.join(", ", faltantes)
            );
        }
    }

    @Override
    public boolean isHabilitado() {
        return enabled;
    }

    @Override
    public boolean validarTokenVerificacion(String token) {
        if (!enabled) {
            return false;
        }
        if (token == null || verifyToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                verifyToken.getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public boolean validarFirmaWebhook(String rawPayload, String signatureHeader) {
        if (!enabled) {
            return false;
        }
        if (signatureHeader == null || signatureHeader.isBlank() || appSecret.isBlank()) {
            log.warn("Firma de webhook ausente o app-secret no configurado");
            return false;
        }
        if (rawPayload == null) {
            return false;
        }

        String expectedPrefix = "sha256=";
        if (!signatureHeader.startsWith(expectedPrefix)) {
            log.warn("Formato de encabezado de firma inválido (se esperaba prefijo 'sha256=')");
            return false;
        }

        String presentedHash = signatureHeader.substring(expectedPrefix.length()).trim();

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] computedBytes = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : computedBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            String computedHash = hexString.toString();

            return MessageDigest.isEqual(
                    computedHash.getBytes(StandardCharsets.UTF_8),
                    presentedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error criptográfico al validar la firma HMAC-SHA256 del webhook de WhatsApp: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ResultadoLlamadaWhatsApp enviarMensajeTexto(String telefono, String texto) {
        return enviarMensajeTexto(telefono, texto, null);
    }

    @Override
    public ResultadoLlamadaWhatsApp enviarMensajeTexto(String telefono, String texto, String idSolicitud) {
        if (!enabled) {
            throw new IllegalStateException("La integración de WhatsApp se encuentra deshabilitada");
        }
        validarConfiguracion();

        String telefonoSanitizado = sanitizarTelefono(telefono);
        if (telefonoSanitizado.isBlank()) {
            throw new IllegalArgumentException("El número de teléfono del destinatario no es válido");
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El contenido del mensaje no puede estar vacío");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", telefonoSanitizado);
        body.put("type", "text");
        body.put("text", Map.of("preview_url", false, "body", texto));
        if (idSolicitud != null && !idSolicitud.isBlank()) {
            body.put("biz_opaque_callback_data", idSolicitud.trim());
        }

        return ejecutarLlamadaSinReintentoCiego(body);
    }

    @Override
    public ResultadoLlamadaWhatsApp enviarPlantilla(String telefono, String templateName, String languageCode, List<String> parametros) {
        return enviarPlantilla(telefono, templateName, languageCode, parametros, null);
    }

    @Override
    public ResultadoLlamadaWhatsApp enviarPlantilla(String telefono, String templateName, String languageCode, List<String> parametros, String idSolicitud) {
        if (!enabled) {
            throw new IllegalStateException("La integración de WhatsApp se encuentra deshabilitada");
        }
        validarConfiguracion();

        String telefonoSanitizado = sanitizarTelefono(telefono);
        if (telefonoSanitizado.isBlank()) {
            throw new IllegalArgumentException("El número de teléfono del destinatario no es válido");
        }
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la plantilla es obligatorio");
        }

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName.trim());
        template.put("language", Map.of("code", languageCode != null && !languageCode.isBlank() ? languageCode.trim() : "es"));

        if (parametros != null && !parametros.isEmpty()) {
            List<Map<String, Object>> parameters = new ArrayList<>();
            for (String param : parametros) {
                parameters.add(Map.of("type", "text", "text", param != null ? param : ""));
            }
            template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", telefonoSanitizado);
        body.put("type", "template");
        body.put("template", template);
        if (idSolicitud != null && !idSolicitud.isBlank()) {
            body.put("biz_opaque_callback_data", idSolicitud.trim());
        }

        return ejecutarLlamadaSinReintentoCiego(body);
    }

    /**
     * Ejecuta la llamada hacia Meta WhatsApp Cloud API sin reintentos ciegos.
     * Meta /messages no implementa idempotency headers nativos. Un reintento ciego ante timeout
     * o desconexión de red provocaría envíos duplicados hacia el teléfono del cliente.
     */
    private ResultadoLlamadaWhatsApp ejecutarLlamadaSinReintentoCiego(Map<String, Object> body) {
        String endpoint = String.format("%s/%s/messages", apiUrl, phoneNumberId);
        try {
            return restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        byte[] rawBytes = response.getBody().readAllBytes();
                        String responseBody = new String(rawBytes, StandardCharsets.UTF_8);

                        if (status >= 200 && status < 300) {
                            String wamid = extraerIdMensajeProveedor(responseBody);
                            return ResultadoLlamadaWhatsApp.aceptado(wamid);
                        } else if (status >= 500) {
                            log.warn("WhatsApp Cloud API retornó código HTTP 5xx {}: {}", status, response.getStatusText());
                            return ResultadoLlamadaWhatsApp.incierto("Servidor del proveedor respondió con error " + status + ". No se garantiza estado de despacho.");
                        } else {
                            String errorMsg = "Error devuelto por WhatsApp Business API (" + status + "): " + response.getStatusText();
                            log.warn("WhatsApp Cloud API retornó código HTTP 4xx {}: {}", status, response.getStatusText());
                            return ResultadoLlamadaWhatsApp.fallido(errorMsg);
                        }
                    });
        } catch (ResourceAccessException ex) {
            // Timeout de lectura o error de transporte de red: la petición pudo haber llegado a Meta
            log.warn("Llamada a WhatsApp Cloud API terminó con resultado incierto (timeout/red): {}", ex.getMessage());
            return ResultadoLlamadaWhatsApp.incierto("Fallo de red o timeout al invocar WhatsApp Cloud API. Resultado no confirmado: " + ex.getMessage());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String errorMsg = "Error devuelto por WhatsApp Business API (" + status + "): " + ex.getStatusText();
            log.warn("WhatsApp Cloud API retornó código HTTP {}: {}", status, ex.getStatusText());
            if (status >= 500) {
                return ResultadoLlamadaWhatsApp.incierto("Servidor del proveedor respondió con error " + status + ". No se garantiza estado de despacho.");
            } else {
                return ResultadoLlamadaWhatsApp.fallido(errorMsg);
            }
        } catch (RestClientException ex) {
            log.warn("Llamada a WhatsApp Cloud API terminó con error de cliente/transporte (posible timeout): {}", ex.getMessage());
            return ResultadoLlamadaWhatsApp.incierto("Fallo de red o timeout al invocar WhatsApp Cloud API. Resultado no confirmado: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Excepción inesperada al invocar WhatsApp Cloud API: {}", ex.getMessage(), ex);
            return ResultadoLlamadaWhatsApp.incierto("Error inesperado en comunicación con proveedor: " + ex.getMessage());
        }
    }

    private String extraerIdMensajeProveedor(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalStateException("WhatsApp Business API retornó una respuesta vacía");
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode messages = root.path("messages");
            if (messages.isArray() && !messages.isEmpty()) {
                String wamid = messages.get(0).path("id").asText();
                if (wamid != null && !wamid.isBlank()) {
                    return wamid;
                }
            }
            throw new IllegalStateException("La respuesta de WhatsApp no contiene el identificador 'messages[0].id'");
        } catch (Exception e) {
            throw new IllegalStateException("Error al parsear respuesta JSON de WhatsApp Business API", e);
        }
    }

    private String sanitizarTelefono(String telefono) {
        if (telefono == null) return "";
        return telefono.replaceAll("[^0-9]", "");
    }

    private String enmascararId(String id) {
        if (id == null || id.length() <= 4) return "****";
        return id.substring(0, 2) + "****" + id.substring(id.length() - 2);
    }
}
