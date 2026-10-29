package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ConfirmarPagoManualRequest;
import com.lysandri.adapters.in.web.dto.CreateOrderRequest;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import com.lysandri.config.CuentasBancariasProperties;
import com.lysandri.adapters.in.web.dto.CuentaBancariaAprobadaDto;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/v1/orders", "/api/v1/admin/orders"})
@Tag(name = "Órdenes y Checkout", description = "Procesamiento de compras y matrículas automáticas en Moodle")
public class OrderController {

    private final BuyCourseUseCase buyCourseUseCase;
    private final OrderRepositoryPort orderRepository;
    private final UserRepositoryPort userRepository;
    private final CuentasBancariasProperties cuentasBancariasProperties;
    private final com.lysandri.security.JwtService jwtService;

    public record GuestSessionRequest(String email) {}
    public record GuestSessionResponse(String guestSessionToken, String guestSessionId, long expiresIn) {}

    @org.springframework.beans.factory.annotation.Autowired
    public OrderController(
            BuyCourseUseCase buyCourseUseCase,
            OrderRepositoryPort orderRepository,
            UserRepositoryPort userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) CuentasBancariasProperties cuentasBancariasProperties,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.lysandri.security.JwtService jwtService
    ) {
        this.buyCourseUseCase = buyCourseUseCase;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.cuentasBancariasProperties = cuentasBancariasProperties != null ? cuentasBancariasProperties : new CuentasBancariasProperties();
        this.jwtService = jwtService;
    }

    public OrderController(BuyCourseUseCase buyCourseUseCase, OrderRepositoryPort orderRepository, UserRepositoryPort userRepository) {
        this(buyCourseUseCase, orderRepository, userRepository, new CuentasBancariasProperties(), null);
    }

    @PostMapping("/guest-session")
    @Operation(summary = "Emite un token seguro de sesión de compra para invitados antes de iniciar el checkout")
    public ResponseEntity<GuestSessionResponse> crearGuestSession(
            @RequestBody(required = false) GuestSessionRequest request) {
        String email = request != null && request.email() != null ? request.email().trim().toLowerCase() : "";
        String guestSessionId = java.util.UUID.randomUUID().toString();
        String token = jwtService != null ? jwtService.generateGuestCheckoutToken(guestSessionId, email) : "";
        log.info("Sesión de invitado emitida de forma segura para email: {}", email.isBlank() ? "anónimo" : email);
        return ResponseEntity.ok(new GuestSessionResponse(token, guestSessionId, 86400L));
    }

    @PostMapping({"/checkout", "/create-checkout-session"})
    @Operation(summary = "Inicia el proceso de checkout y retorna sesión de izipay Online o confirmación de pago manual")
    public ResponseEntity<BuyCourseUseCase.CheckoutSessionResponse> iniciarCompra(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyHeader,
            @RequestHeader(value = "X-Guest-Session-Token", required = false) String guestTokenHeader,
            @Valid @RequestBody CreateOrderRequest request) {

        Long idUser = usuario != null ? usuario.getIdUser() : null;
        String email = usuario != null ? usuario.getEmail() : request.getEmail();
        String nombre = usuario != null ? usuario.getNombres() + " " + usuario.getApellidos() : request.getNombreCompleto();

        String clave = request.getClaveIdempotencia() != null && !request.getClaveIdempotencia().isBlank()
                ? request.getClaveIdempotencia().trim()
                : (idempotencyHeader != null && !idempotencyHeader.isBlank() ? idempotencyHeader.trim() : null);

        String guestToken = request.getGuestSessionToken() != null && !request.getGuestSessionToken().isBlank()
                ? request.getGuestSessionToken().trim()
                : (guestTokenHeader != null && !guestTokenHeader.isBlank() ? guestTokenHeader.trim() : null);

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                idUser,
                request.getProgramaIds(),
                email,
                nombre,
                request.getTipoComprobante(),
                request.getNumeroDocumento(),
                request.getNombreFacturacion(),
                request.getMetodoPago(),
                request.getNumeroOperacion(),
                clave,
                guestToken
        );

        BuyCourseUseCase.CheckoutSessionResponse response = buyCourseUseCase.iniciarCompra(command);
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<BuyCourseUseCase.CheckoutSessionResponse> iniciarCompra(
            UsuarioEntity usuario,
            CreateOrderRequest request) {
        return iniciarCompra(usuario, null, null, request);
    }

    public ResponseEntity<BuyCourseUseCase.CheckoutSessionResponse> iniciarCompra(
            UsuarioEntity usuario,
            String idempotencyHeader,
            CreateOrderRequest request) {
        return iniciarCompra(usuario, idempotencyHeader, null, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'VENTAS', 'FINANZAS', 'CONTABILIDAD', 'AUDITOR')")
    @Operation(summary = "Lista todas las órdenes de compra con filtros por estado y búsqueda (personal interno autorizado)")
    public ResponseEntity<List<Orden>> listarOrdenes(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(buyCourseUseCase.listarOrdenes(estado, search));
    }

    @GetMapping("/mis-ordenes")
    @Operation(summary = "Obtiene las órdenes de compra del usuario autenticado")
    public ResponseEntity<List<Orden>> listarMisOrdenes(@AuthenticationPrincipal UsuarioEntity usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(buyCourseUseCase.listarOrdenesPorUsuario(usuario.getIdUser()));
    }

    @PostMapping("/confirm/{sessionId}")
    @Operation(summary = "Confirma el pago de la orden y matricula al usuario en Moodle LMS tras validación con la pasarela")
    public ResponseEntity<Orden> confirmarPago(
            @PathVariable String sessionId,
            Authentication authentication) {
        log.info("Llamada de confirmación de pago para sesión: {}", sessionId);

        Orden ordenExistente = orderRepository.buscarPorCualquierIdentificador(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Orden no encontrada para la sesión de pago: " + sessionId));

        boolean admin = isAdmin(authentication);
        String authEmail = authentication != null ? authentication.getName() : null;

        // Si la orden pertenece a un usuario registrado activo (no invitado pendiente)
        boolean esUsuarioRegistradoActivo = false;
        if (ordenExistente.getIdUser() != null) {
            esUsuarioRegistradoActivo = userRepository.buscarPorId(ordenExistente.getIdUser())
                    .map(u -> u.isActivo() && !u.isPendienteActivacion())
                    .orElse(false);
        }

        if (esUsuarioRegistradoActivo) {
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("Intento de confirmación anónima para orden {} perteneciente a usuario registrado activo", ordenExistente.getCodigoOrden());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            boolean esPropietario = false;
            if (ordenExistente.getUsuario() != null && ordenExistente.getUsuario().getEmail() != null) {
                esPropietario = ordenExistente.getUsuario().getEmail().equalsIgnoreCase(authEmail);
            }
            if (authentication.getPrincipal() instanceof UsuarioEntity ue) {
                if (ordenExistente.getIdUser() != null && ordenExistente.getIdUser().equals(ue.getIdUser())) {
                    esPropietario = true;
                }
            }

            if (!admin && !esPropietario) {
                log.warn("Usuario {} intentó confirmar la orden {} de otro usuario", authEmail, ordenExistente.getCodigoOrden());
                throw new AccessDeniedException("No tiene permisos para confirmar órdenes de otro usuario");
            }
        }

        Orden orden = buyCourseUseCase.confirmarPagoYMatricular(sessionId);
        return ResponseEntity.ok(orden);
    }

    @GetMapping("/cuentas-bancarias")
    @Operation(summary = "Lista las cuentas bancarias oficiales de la empresa para transferencias y depósitos directos")
    public ResponseEntity<List<CuentaBancariaAprobadaDto>> obtenerCuentasBancarias() {
        return ResponseEntity.ok(cuentasBancariasProperties != null ? cuentasBancariasProperties.obtenerCuentas() : List.of());
    }

    @PostMapping("/{idOrden}/confirmar-pago-manual")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'VENTAS', 'FINANZAS', 'CONTABILIDAD')")
    @Operation(summary = "Aprobación administrativa de transferencias bancarias y pagos manuales (BCP, Interbank, Yape, Plin)")
    public ResponseEntity<Orden> confirmarPagoManual(
            @PathVariable Long idOrden,
            @Valid @RequestBody ConfirmarPagoManualRequest request,
            Authentication authentication) {
        String authUser = authentication != null ? authentication.getName() : "ANONYMOUS";
        log.info("Llamada administrativa de aprobación para orden ID: {} por usuario: {}", idOrden, authUser);

        String aprobadoPor = (authentication != null && authentication.getName() != null && !authentication.getName().isBlank())
                ? authentication.getName()
                : "SISTEMA";

        BuyCourseUseCase.ConfirmarPagoManualCommand command = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                request != null ? request.getNumeroOperacion() : null,
                request != null ? request.getBancoDestino() : null,
                request != null ? request.getCuentaDestino() : null,
                request != null ? request.getMontoVerificado() : null,
                request != null ? request.getMonedaVerificada() : null,
                request != null ? request.getFechaAbono() : null,
                request != null ? request.getMetodoPago() : null,
                request != null ? request.getNotasAdmin() : null,
                request != null ? request.getObservacionVerificacion() : null,
                aprobadoPor
        );
        Orden orden = buyCourseUseCase.confirmarPagoManual(idOrden, command);
        return ResponseEntity.ok(orden);
    }

    @PostMapping("/{idOrden}/reintentar-matricula")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Reintenta la matrícula en Moodle para una orden pagada que tuvo fallos en sincronización previa")
    public ResponseEntity<Orden> reintentarMatricula(@PathVariable Long idOrden) {
        log.info("Llamada administrativa de reintento de matrícula para orden ID: {}", idOrden);
        Orden orden = buyCourseUseCase.reintentarMatricula(idOrden);
        return ResponseEntity.ok(orden);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Obtiene el detalle de una orden por su código")
    public ResponseEntity<Orden> obtenerPorCodigo(
            @PathVariable String codigo,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Orden orden = buyCourseUseCase.obtenerOrdenPorCodigo(codigo);
        boolean admin = isAdmin(authentication);
        String authEmail = authentication.getName();

        boolean esPropietario = false;
        if (orden.getUsuario() != null && orden.getUsuario().getEmail() != null) {
            esPropietario = orden.getUsuario().getEmail().equalsIgnoreCase(authEmail);
        }
        if (authentication.getPrincipal() instanceof UsuarioEntity ue) {
            if (orden.getIdUser() != null && orden.getIdUser().equals(ue.getIdUser())) {
                esPropietario = true;
            }
        }

        if (!admin && !esPropietario) {
            log.warn("Usuario {} intentó consultar la orden {} sin autorización", authEmail, codigo);
            throw new AccessDeniedException("No tiene permisos para consultar información de esta orden");
        }

        return ResponseEntity.ok(orden);
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(authority -> {
                    String normalized = authority != null ? authority.replace("ROLE_", "") : "";
                    return "ADMIN".equals(normalized)
                            || "TRABAJADOR".equals(normalized)
                            || "CONTABILIDAD".equals(normalized)
                            || "FINANZAS".equals(normalized)
                            || "VENTAS".equals(normalized)
                            || "COORDINADOR_ACADEMICO".equals(normalized)
                            || "AUDITOR".equals(normalized);
                });
    }
}
