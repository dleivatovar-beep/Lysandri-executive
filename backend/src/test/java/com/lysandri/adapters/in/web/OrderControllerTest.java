package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ConfirmarPagoManualRequest;
import com.lysandri.adapters.in.web.dto.CreateOrderRequest;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private BuyCourseUseCase buyCourseUseCase;

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private com.lysandri.domain.ports.out.UserRepositoryPort userRepository;

    @Mock
    private com.lysandri.security.JwtService jwtService;

    @InjectMocks
    private OrderController orderController;

    private UsuarioEntity usuarioEntity;
    private Authentication userAuth;
    private Authentication otherUserAuth;
    private Authentication adminAuth;
    private Orden ordenUser;

    @BeforeEach
    void setUp() {
        usuarioEntity = UsuarioEntity.builder()
                .idUser(5L)
                .email("alumno@lysandri.com")
                .nombres("Pedro")
                .apellidos("Suárez")
                .build();

        userAuth = new UsernamePasswordAuthenticationToken(
                usuarioEntity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        );

        UsuarioEntity otherEntity = UsuarioEntity.builder()
                .idUser(99L)
                .email("hacker@empresa.com")
                .nombres("Otro")
                .apellidos("Usuario")
                .build();

        otherUserAuth = new UsernamePasswordAuthenticationToken(
                otherEntity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        );

        UsuarioEntity adminEntity = UsuarioEntity.builder()
                .idUser(1L)
                .email("admin@lysandri.com")
                .nombres("Admin")
                .apellidos("Sistema")
                .build();

        adminAuth = new UsernamePasswordAuthenticationToken(
                adminEntity,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        ordenUser = Orden.builder()
                .idOrden(1L)
                .idUser(5L)
                .codigoOrden("ORD-001")
                .usuario(Usuario.builder().idUser(5L).email("alumno@lysandri.com").nombres("Pedro").apellidos("Suárez").build())
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("799.00"))
                .moodleMatriculaSincronizada(true)
                .build();

        lenient().when(userRepository.buscarPorId(5L)).thenReturn(Optional.of(
                Usuario.builder().idUser(5L).email("alumno@lysandri.com").activo(true).pendienteActivacion(false).build()
        ));
    }

    @Test
    void iniciarCompra_conUsuarioAutenticado_iniciaCheckoutCorrectamente() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setProgramaIds(List.of(10L));
        request.setMetodoPago("IZIPAY_ONLINE");

        BuyCourseUseCase.CheckoutSessionResponse sessionResponse =
                new BuyCourseUseCase.CheckoutSessionResponse("ORD-001", "https://pay.lysandri.com/123", "sess_123");

        when(buyCourseUseCase.iniciarCompra(any(BuyCourseUseCase.CreateOrderCommand.class)))
                .thenReturn(sessionResponse);

        ResponseEntity<BuyCourseUseCase.CheckoutSessionResponse> response =
                orderController.iniciarCompra(usuarioEntity, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("sess_123", response.getBody().stripeSessionId());
        verify(buyCourseUseCase).iniciarCompra(argThat(cmd ->
                cmd.idUsuario().equals(5L) && cmd.guestEmail().equals("alumno@lysandri.com")
        ));
    }

    @Test
    void listarMisOrdenes_sinUsuarioAutenticado_retornaUnauthorized() {
        ResponseEntity<List<Orden>> response = orderController.listarMisOrdenes(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void listarMisOrdenes_conUsuarioAutenticado_retornaListaDeOrdenes() {
        when(buyCourseUseCase.listarOrdenesPorUsuario(5L)).thenReturn(List.of(ordenUser));

        ResponseEntity<List<Orden>> response = orderController.listarMisOrdenes(usuarioEntity);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("ORD-001", response.getBody().get(0).getCodigoOrden());
    }

    @Test
    void obtenerPorCodigo_usuarioAutenticadoConsultaSuPropiaOrden_retornaOk() {
        when(buyCourseUseCase.obtenerOrdenPorCodigo("ORD-001")).thenReturn(ordenUser);

        ResponseEntity<Orden> response = orderController.obtenerPorCodigo("ORD-001", userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ORD-001", response.getBody().getCodigoOrden());
    }

    @Test
    void obtenerPorCodigo_usuarioIntentaConsultarOrdenAjena_lanzaAccessDeniedException() {
        when(buyCourseUseCase.obtenerOrdenPorCodigo("ORD-001")).thenReturn(ordenUser);

        assertThrows(AccessDeniedException.class, () ->
                orderController.obtenerPorCodigo("ORD-001", otherUserAuth));
    }

    @Test
    void obtenerPorCodigo_usuarioAnonimo_retornaUnauthorized() {
        ResponseEntity<Orden> response = orderController.obtenerPorCodigo("ORD-001", null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void obtenerPorCodigo_administradorConsultaCualquierOrden_retornaOk() {
        when(buyCourseUseCase.obtenerOrdenPorCodigo("ORD-001")).thenReturn(ordenUser);

        ResponseEntity<Orden> response = orderController.obtenerPorCodigo("ORD-001", adminAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ORD-001", response.getBody().getCodigoOrden());
    }

    @Test
    void confirmarPago_conPropietarioAutenticado_confirmaExitosamente() {
        when(orderRepository.buscarPorCualquierIdentificador("sess_abc")).thenReturn(Optional.of(ordenUser));
        when(buyCourseUseCase.confirmarPagoYMatricular("sess_abc")).thenReturn(ordenUser);

        ResponseEntity<Orden> response = orderController.confirmarPago("sess_abc", userAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EstadoOrden.PAGADO, response.getBody().getEstadoOrden());
        verify(buyCourseUseCase).confirmarPagoYMatricular("sess_abc");
    }

    @Test
    void confirmarPago_conOtroUsuarioNoAdmin_lanzaAccessDeniedException() {
        when(orderRepository.buscarPorCualquierIdentificador("sess_abc")).thenReturn(Optional.of(ordenUser));

        assertThrows(AccessDeniedException.class, () ->
                orderController.confirmarPago("sess_abc", otherUserAuth));

        verify(buyCourseUseCase, never()).confirmarPagoYMatricular(anyString());
    }

    @Test
    void confirmarPago_ordenDeUsuarioConLlamadaAnonima_retornaUnauthorized() {
        when(orderRepository.buscarPorCualquierIdentificador("sess_abc")).thenReturn(Optional.of(ordenUser));

        ResponseEntity<Orden> response = orderController.confirmarPago("sess_abc", null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(buyCourseUseCase, never()).confirmarPagoYMatricular(anyString());
    }

    @Test
    void confirmarPago_administradorConfirmaOrdenDeUsuario_retornaOk() {
        when(orderRepository.buscarPorCualquierIdentificador("sess_abc")).thenReturn(Optional.of(ordenUser));
        when(buyCourseUseCase.confirmarPagoYMatricular("sess_abc")).thenReturn(ordenUser);

        ResponseEntity<Orden> response = orderController.confirmarPago("sess_abc", adminAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(buyCourseUseCase).confirmarPagoYMatricular("sess_abc");
    }

    @Test
    void confirmarPagoManual_invocaUseCase() {
        ConfirmarPagoManualRequest req = new ConfirmarPagoManualRequest();
        req.setNumeroOperacion("BCP-987654");
        req.setBancoDestino("BCP");
        req.setCuentaDestino("193-98218491-0-28");
        req.setMontoVerificado(new BigDecimal("799.00"));
        req.setMonedaVerificada("USD");
        req.setFechaAbono(java.time.OffsetDateTime.now());
        req.setMetodoPago("TRANSFERENCIA");
        req.setNotasAdmin("Pago verificado en cuenta corriente BCP");

        Orden orden = Orden.builder()
                .idOrden(10L)
                .estadoOrden(EstadoOrden.PAGADO)
                .numeroOperacion("BCP-987654")
                .bancoDestino("BCP")
                .cuentaDestino("193-98218491-0-28")
                .montoVerificado(new BigDecimal("799.00"))
                .monedaVerificada("USD")
                .aprobadoPor("admin@lysandri.com")
                .build();

        when(buyCourseUseCase.confirmarPagoManual(eq(10L), any(BuyCourseUseCase.ConfirmarPagoManualCommand.class)))
                .thenReturn(orden);

        ResponseEntity<Orden> response = orderController.confirmarPagoManual(10L, req, adminAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EstadoOrden.PAGADO, response.getBody().getEstadoOrden());
        assertEquals("BCP-987654", response.getBody().getNumeroOperacion());
        assertEquals("admin@lysandri.com", response.getBody().getAprobadoPor());
    }

    @Test
    void reintentarMatricula_invocaUseCase() {
        Orden orden = Orden.builder()
                .idOrden(12L)
                .estadoOrden(EstadoOrden.PAGADO)
                .moodleMatriculaSincronizada(true)
                .build();

        when(buyCourseUseCase.reintentarMatricula(12L)).thenReturn(orden);

        ResponseEntity<Orden> response = orderController.reintentarMatricula(12L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isMoodleMatriculaSincronizada());
        verify(buyCourseUseCase).reintentarMatricula(12L);
    }

    @Test
    void crearGuestSession_retornaTokenSeguro() {
        when(jwtService.generateGuestCheckoutToken(anyString(), eq("invitado@empresa.pe")))
                .thenReturn("jwt-guest-token-abc");

        OrderController.GuestSessionRequest req = new OrderController.GuestSessionRequest("invitado@empresa.pe");
        ResponseEntity<OrderController.GuestSessionResponse> res = orderController.crearGuestSession(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("jwt-guest-token-abc", res.getBody().guestSessionToken());
        assertNotNull(res.getBody().guestSessionId());
    }
}
