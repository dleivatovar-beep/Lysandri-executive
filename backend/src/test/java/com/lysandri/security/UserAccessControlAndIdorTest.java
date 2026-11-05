package com.lysandri.security;

import com.lysandri.adapters.in.web.OrderController;
import com.lysandri.adapters.in.web.UsuarioController;
import com.lysandri.adapters.in.web.dto.UsuarioResponseDto;
import com.lysandri.adapters.in.web.dto.UsuarioUpdateRequest;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas de Controles de Acceso, Permisos y Protección Antifraude/IDOR")
class UserAccessControlAndIdorTest {

    @Mock
    private UserManagementUseCase userManagementUseCase;

    @Mock
    private BuyCourseUseCase buyCourseUseCase;

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private UserRepositoryPort userRepository;

    private UsuarioController usuarioController;
    private OrderController orderController;

    // Tres identidades de prueba: 2 usuarios distintos (clientes) y 1 administrador
    private Usuario user1;
    private Usuario user2;
    private Usuario adminUser;

    private Authentication user1Auth;
    private Authentication user2Auth;
    private Authentication adminAuth;

    private Orden orderUser1;
    private Orden orderUser2;

    @BeforeEach
    void setUp() {
        usuarioController = new UsuarioController(userManagementUseCase);
        orderController = new OrderController(buyCourseUseCase, orderRepository, userRepository);

        user1 = Usuario.builder()
                .idUser(101L)
                .email("user1@lysandri.com")
                .nombres("Carlos")
                .apellidos("García")
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .build();

        user2 = Usuario.builder()
                .idUser(102L)
                .email("user2@lysandri.com")
                .nombres("María")
                .apellidos("Rodríguez")
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .build();

        adminUser = Usuario.builder()
                .idUser(1L)
                .email("admin@lysandri.com")
                .nombres("Super")
                .apellidos("Admin")
                .rol(RolUsuario.ADMIN)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .build();

        UsuarioEntity ue1 = UsuarioEntity.builder().idUser(101L).email("user1@lysandri.com").rol(RolUsuario.CLIENTE).activo(true).build();
        UsuarioEntity ue2 = UsuarioEntity.builder().idUser(102L).email("user2@lysandri.com").rol(RolUsuario.CLIENTE).activo(true).build();
        UsuarioEntity ueAdmin = UsuarioEntity.builder().idUser(1L).email("admin@lysandri.com").rol(RolUsuario.ADMIN).activo(true).build();

        user1Auth = new UsernamePasswordAuthenticationToken(ue1, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        user2Auth = new UsernamePasswordAuthenticationToken(ue2, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        adminAuth = new UsernamePasswordAuthenticationToken(ueAdmin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        orderUser1 = Orden.builder()
                .idOrden(501L)
                .idUser(101L)
                .codigoOrden("ORD-USER1-501")
                .usuario(user1)
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("999.00"))
                .build();

        orderUser2 = Orden.builder()
                .idOrden(502L)
                .idUser(102L)
                .codigoOrden("ORD-USER2-502")
                .usuario(user2)
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("1200.00"))
                .build();
    }

    // =========================================================================
    // 1. Control de acceso a perfil de usuario (IDOR)
    // =========================================================================

    @Test
    @DisplayName("Usuario 1 consulta su propio perfil: acceso permitido (200 OK)")
    void user1_consultaSuPropioPerfil_accesoPermitido() {
        when(userManagementUseCase.obtenerPorId(101L)).thenReturn(user1);

        ResponseEntity<UsuarioResponseDto> response = usuarioController.obtenerPorId(101L, user1Auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("user1@lysandri.com", response.getBody().getEmail());
    }

    @Test
    @DisplayName("Usuario 1 intenta consultar perfil de Usuario 2 (IDOR): denegado con AccessDeniedException (403)")
    void user1_intentaConsultarPerfilUser2_denegado() {
        when(userManagementUseCase.obtenerPorId(102L)).thenReturn(user2);

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.obtenerPorId(102L, user1Auth));
    }

    @Test
    @DisplayName("Usuario 1 actualiza sus propios datos válidos: permitido (200 OK)")
    void user1_actualizaDatosPropios_permitido() {
        when(userManagementUseCase.obtenerPorId(101L)).thenReturn(user1);
        when(userManagementUseCase.actualizarUsuario(eq(101L), any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(1);
            u.setIdUser(101L);
            u.setEmail("user1@lysandri.com");
            u.setRol(RolUsuario.CLIENTE);
            u.setActivo(true);
            return u;
        });

        UsuarioUpdateRequest req = new UsuarioUpdateRequest();
        req.setNombres("Carlos Modificado");
        req.setTelefono("+51987654321");

        ResponseEntity<UsuarioResponseDto> response = usuarioController.actualizarUsuario(101L, req, user1Auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Carlos Modificado", response.getBody().getNombres());
    }

    @Test
    @DisplayName("Usuario 1 intenta modificar perfil de Usuario 2 (IDOR en PUT): denegado con AccessDeniedException")
    void user1_intentaModificarPerfilUser2_denegado() {
        when(userManagementUseCase.obtenerPorId(102L)).thenReturn(user2);

        UsuarioUpdateRequest req = new UsuarioUpdateRequest();
        req.setNombres("Hacked Name");

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.actualizarUsuario(102L, req, user1Auth));

        verify(userManagementUseCase, never()).actualizarUsuario(anyLong(), any());
    }

    @Test
    @DisplayName("Usuario 1 intenta auto-asignarse rol ADMIN enviando rol en el cuerpo: denegado con AccessDeniedException")
    void user1_intentaEscalamientoPrivilegiosRol_denegado() {
        when(userManagementUseCase.obtenerPorId(101L)).thenReturn(user1);

        UsuarioUpdateRequest req = new UsuarioUpdateRequest();
        req.setRol("ADMIN");

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.actualizarUsuario(101L, req, user1Auth));

        verify(userManagementUseCase, never()).actualizarUsuario(anyLong(), any());
    }

    @Test
    @DisplayName("Usuario 1 intenta alterar su estado de activo por PUT: denegado con AccessDeniedException")
    void user1_intentaAlterarEstadoActivoPorPut_denegado() {
        when(userManagementUseCase.obtenerPorId(101L)).thenReturn(user1);

        UsuarioUpdateRequest req = new UsuarioUpdateRequest();
        req.setActivo(false);

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.actualizarUsuario(101L, req, user1Auth));

        verify(userManagementUseCase, never()).actualizarUsuario(anyLong(), any());
    }

    // =========================================================================
    // 2. Control de acceso a órdenes y compras (IDOR)
    // =========================================================================

    @Test
    @DisplayName("Usuario 1 consulta su propia orden: acceso permitido (200 OK)")
    void user1_consultaSuPropiaOrden_permitido() {
        when(buyCourseUseCase.obtenerOrdenPorCodigo("ORD-USER1-501")).thenReturn(orderUser1);

        ResponseEntity<Orden> response = orderController.obtenerPorCodigo("ORD-USER1-501", user1Auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ORD-USER1-501", response.getBody().getCodigoOrden());
    }

    @Test
    @DisplayName("Usuario 1 intenta consultar orden de Usuario 2 cambiando código en URL: denegado (403)")
    void user1_intentaConsultarOrdenUser2_denegado() {
        when(buyCourseUseCase.obtenerOrdenPorCodigo("ORD-USER2-502")).thenReturn(orderUser2);

        assertThrows(AccessDeniedException.class, () ->
                orderController.obtenerPorCodigo("ORD-USER2-502", user1Auth));
    }

    @Test
    @DisplayName("Usuario 1 intenta confirmar pago de orden de Usuario 2: denegado (403)")
    void user1_intentaConfirmarPagoOrdenUser2_denegado() {
        when(orderRepository.buscarPorCualquierIdentificador("sess_user2")).thenReturn(Optional.of(orderUser2));
        when(userRepository.buscarPorId(102L)).thenReturn(Optional.of(user2));

        assertThrows(AccessDeniedException.class, () ->
                orderController.confirmarPago("sess_user2", user1Auth));

        verify(buyCourseUseCase, never()).confirmarPagoYMatricular(anyString());
    }

    @Test
    @DisplayName("Listar mis órdenes: Usuario 1 solo recibe sus órdenes vinculadas a su ID")
    void user1_listarMisOrdenes_retornaSoloSusOrdenes() {
        when(buyCourseUseCase.listarOrdenesPorUsuario(101L)).thenReturn(List.of(orderUser1));

        UsuarioEntity ue = (UsuarioEntity) user1Auth.getPrincipal();
        ResponseEntity<List<Orden>> response = orderController.listarMisOrdenes(ue);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("ORD-USER1-501", response.getBody().get(0).getCodigoOrden());
    }

    @Test
    @DisplayName("Listar mis órdenes sin autenticación: retorna 401 Unauthorized")
    void listarMisOrdenes_sinAutenticacion_retorna401() {
        ResponseEntity<List<Orden>> response = orderController.listarMisOrdenes(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // =========================================================================
    // 3. Verificación con cuenta de Administrador
    // =========================================================================

    @Test
    @DisplayName("Administrador consulta perfil de Usuario 1 y Usuario 2: permitido (200 OK)")
    void admin_consultaPerfilesUsuarios_permitido() {
        when(userManagementUseCase.obtenerPorId(101L)).thenReturn(user1);
        when(userManagementUseCase.obtenerPorId(102L)).thenReturn(user2);

        ResponseEntity<UsuarioResponseDto> res1 = usuarioController.obtenerPorId(101L, adminAuth);
        ResponseEntity<UsuarioResponseDto> res2 = usuarioController.obtenerPorId(102L, adminAuth);

        assertEquals(HttpStatus.OK, res1.getStatusCode());
        assertEquals("user1@lysandri.com", res1.getBody().getEmail());
        assertEquals(HttpStatus.OK, res2.getStatusCode());
        assertEquals("user2@lysandri.com", res2.getBody().getEmail());
    }

    @Test
    @DisplayName("Administrador consulta orden de cualquier usuario: permitido (200 OK)")
    void admin_consultaCualquierOrden_permitido() {
        when(buyCourseUseCase.obtenerOrdenPorCodigo("ORD-USER1-501")).thenReturn(orderUser1);
        when(buyCourseUseCase.obtenerOrdenPorCodigo("ORD-USER2-502")).thenReturn(orderUser2);

        ResponseEntity<Orden> res1 = orderController.obtenerPorCodigo("ORD-USER1-501", adminAuth);
        ResponseEntity<Orden> res2 = orderController.obtenerPorCodigo("ORD-USER2-502", adminAuth);

        assertEquals(HttpStatus.OK, res1.getStatusCode());
        assertEquals(HttpStatus.OK, res2.getStatusCode());
    }

    @Test
    @DisplayName("Administrador actualiza rol de usuario mediante operación auditada: exitoso")
    void admin_actualizaRolAuditado_exitoso() {
        when(userManagementUseCase.actualizarRol(eq(101L), eq(RolUsuario.TRABAJADOR), anyString(), eq("admin@lysandri.com")))
                .thenAnswer(i -> {
                    user1.setRol(RolUsuario.TRABAJADOR);
                    return user1;
                });

        ResponseEntity<UsuarioResponseDto> res = usuarioController.actualizarRol(
                101L,
                "TRABAJADOR",
                "Promoción a coordinador de ventas",
                adminAuth
        );

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("TRABAJADOR", res.getBody().getRol());
        verify(userManagementUseCase).actualizarRol(101L, RolUsuario.TRABAJADOR, "Promoción a coordinador de ventas", "admin@lysandri.com");
    }
}

