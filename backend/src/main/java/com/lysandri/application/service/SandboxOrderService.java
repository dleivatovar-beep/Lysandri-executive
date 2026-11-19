package com.lysandri.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SandboxOrderService {

    private static final String CURRENCY = "USD";
    private static final Map<Long, SandboxProgramResponse> PROGRAMS = Map.of(
            1001L, new SandboxProgramResponse(1001L, "Inteligencia Artificial para Directivos & C-Suite", new BigDecimal("89.00"), CURRENCY),
            1002L, new SandboxProgramResponse(1002L, "Ciberseguridad & Protección Antifraude para Empresas", new BigDecimal("109.00"), CURRENCY),
            1003L, new SandboxProgramResponse(1003L, "Gestión Financiera Estratégica & Optimización de Costos", new BigDecimal("99.00"), CURRENCY),
            1004L, new SandboxProgramResponse(1004L, "Automatización de Procesos con Herramientas No-Code", new BigDecimal("59.00"), CURRENCY),
            1005L, new SandboxProgramResponse(1005L, "Transformación Digital & Liderazgo de Negocios", new BigDecimal("119.00"), CURRENCY),
            1006L, new SandboxProgramResponse(1006L, "Dirección Estratégica & Negociación para Alta Gerencia", new BigDecimal("79.00"), CURRENCY)
    );

    private final JdbcTemplate jdbcTemplate;

    public static List<SandboxProgramResponse> programasDePrueba() {
        return PROGRAMS.values().stream().sorted(Comparator.comparing(SandboxProgramResponse::id)).toList();
    }

    public record SandboxProgramRequest(Long id, String titulo) {}
    public record SandboxOrderRequest(String claveIdempotencia, String nombre, String email, String telefono, List<SandboxProgramRequest> programas) {}
    public record SandboxProgramResponse(Long id, String titulo, BigDecimal precio, String moneda) {}
    public record SandboxOrderResponse(String codigoOrden, String nombre, String email, String telefono,
                                       List<Long> programaIds, List<SandboxProgramResponse> programas,
                                       BigDecimal total, String moneda, String estado,
                                       OffsetDateTime fecha, String claveIdempotencia) {}
    private record OrderHeader(Long id, String code, String name, String email, String phone,
                               BigDecimal total, String currency, String status,
                               OffsetDateTime createdAt, String idempotencyKey) {}

    @Transactional
    public SandboxOrderResponse createOrder(SandboxOrderRequest request) {
        if (request == null || request.claveIdempotencia() == null || request.claveIdempotencia().isBlank()
                || request.claveIdempotencia().length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave de inscripción es obligatoria.");
        }
        if (request.nombre() == null || request.nombre().trim().length() < 3 || request.nombre().length() > 160
                || request.email() == null || !request.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || request.email().length() > 200
                || (request.telefono() != null && request.telefono().length() > 40)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresa un nombre y correo válidos.");
        }
        if (request.programas() == null || request.programas().isEmpty() || request.programas().size() > 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona entre uno y seis programas disponibles para esta práctica.");
        }

        List<SandboxProgramResponse> programs = new ArrayList<>();
        for (SandboxProgramRequest requested : request.programas()) {
            SandboxProgramResponse official = requested == null ? null : PROGRAMS.get(requested.id());
            if (official == null || requested.titulo() == null || !normalize(official.titulo()).equals(normalize(requested.titulo()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno de los programas no pertenece al catálogo de práctica autorizado.");
            }
            programs.add(official);
        }
        programs.sort(Comparator.comparing(SandboxProgramResponse::id));
        if (programs.stream().map(SandboxProgramResponse::id).distinct().count() != programs.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se permiten programas duplicados.");
        }

        String idempotencyKey = request.claveIdempotencia().trim();
        BigDecimal total = programs.stream().map(SandboxProgramResponse::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> inserted = jdbcTemplate.query(
                """
                INSERT INTO sandbox_orders (codigo_orden, clave_idempotencia, total, moneda, estado, comprador_nombre, comprador_email, comprador_telefono)
                VALUES (?, ?, ?, ?, 'PRACTICA', ?, ?, ?)
                ON CONFLICT (clave_idempotencia) DO NOTHING
                RETURNING id
                """,
                (result, row) -> result.getLong("id"),
                "PRACTICA-" + UUID.randomUUID(), idempotencyKey, total, CURRENCY,
                request.nombre().trim(), request.email().trim().toLowerCase(Locale.ROOT), cleanPhone(request.telefono())
        );

        if (!inserted.isEmpty()) {
            Long orderId = inserted.get(0);
            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO sandbox_order_items (sandbox_order_id, id_programa, titulo, precio_unitario, cantidad, subtotal)
                    VALUES (?, ?, ?, ?, 1, ?)
                    """,
                    programs, programs.size(), (statement, program) -> {
                        statement.setLong(1, orderId);
                        statement.setLong(2, program.id());
                        statement.setString(3, program.titulo());
                        statement.setBigDecimal(4, program.precio());
                        statement.setBigDecimal(5, program.precio());
                    }
            );
        }

        SandboxOrderResponse saved = findByKey(idempotencyKey, programs.stream().map(SandboxProgramResponse::id).toList());
        if (!saved.email().equalsIgnoreCase(request.email().trim()) || !saved.nombre().equals(request.nombre().trim())
                || !Objects.equals(saved.telefono(), cleanPhone(request.telefono()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La clave de inscripción ya pertenece a otra persona.");
        }
        return saved;
    }

    public List<SandboxOrderResponse> listOrders() {
        List<OrderHeader> headers = jdbcTemplate.query(
                "SELECT id, codigo_orden, comprador_nombre, comprador_email, comprador_telefono, total, moneda, estado, creado_en, clave_idempotencia FROM sandbox_orders ORDER BY creado_en DESC LIMIT 200",
                (result, row) -> new OrderHeader(result.getLong("id"), result.getString("codigo_orden"),
                        result.getString("comprador_nombre"), result.getString("comprador_email"), result.getString("comprador_telefono"),
                        result.getBigDecimal("total"), result.getString("moneda"), result.getString("estado"),
                        result.getObject("creado_en", OffsetDateTime.class), result.getString("clave_idempotencia"))
        );
        return headers.stream().map(header -> response(header, loadPrograms(header.id()))).toList();
    }

    private SandboxOrderResponse findByKey(String key, List<Long> requestedIds) {
        OrderHeader order = jdbcTemplate.queryForObject(
                "SELECT id, codigo_orden, comprador_nombre, comprador_email, comprador_telefono, total, moneda, estado, creado_en, clave_idempotencia FROM sandbox_orders WHERE clave_idempotencia = ?",
                (result, row) -> new OrderHeader(result.getLong("id"), result.getString("codigo_orden"),
                        result.getString("comprador_nombre"), result.getString("comprador_email"), result.getString("comprador_telefono"),
                        result.getBigDecimal("total"), result.getString("moneda"), result.getString("estado"),
                        result.getObject("creado_en", OffsetDateTime.class), result.getString("clave_idempotencia")), key);
        List<SandboxProgramResponse> saved = loadPrograms(order.id());
        if (!saved.stream().map(SandboxProgramResponse::id).toList().equals(requestedIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La clave de inscripción ya se usó con otros programas.");
        }
        return response(order, saved);
    }

    private List<SandboxProgramResponse> loadPrograms(Long orderId) {
        return jdbcTemplate.query(
                "SELECT id_programa, titulo, precio_unitario FROM sandbox_order_items WHERE sandbox_order_id = ? ORDER BY id_programa",
                (result, row) -> new SandboxProgramResponse(result.getLong("id_programa"), result.getString("titulo"), result.getBigDecimal("precio_unitario"), CURRENCY), orderId);
    }

    private SandboxOrderResponse response(OrderHeader order, List<SandboxProgramResponse> programs) {
        return new SandboxOrderResponse(order.code(), order.name(), order.email(), order.phone(),
                programs.stream().map(SandboxProgramResponse::id).toList(), programs, order.total(), order.currency(),
                order.status(), order.createdAt(), order.idempotencyKey());
    }

    private static String cleanPhone(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
    }
}
