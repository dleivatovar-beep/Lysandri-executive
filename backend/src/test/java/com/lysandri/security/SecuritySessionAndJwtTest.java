package com.lysandri.security;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.RolUsuario;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de Seguridad: JwtService y Validación Estricta de Sesiones")
class SecuritySessionAndJwtTest {

    private JwtService jwtService;
    private final String VALID_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432A";
    private final String DIFFERENT_SECRET = "994E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432B";

    private UsuarioEntity clienteUser;
    private UsuarioEntity adminUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", VALID_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hora

        clienteUser = UsuarioEntity.builder()
                .idUser(10L)
                .email("cliente@empresa.com")
                .nombres("Cliente")
                .apellidos("Ejecutivo")
                .passw("$2a$10$encodedHash")
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .build();

        adminUser = UsuarioEntity.builder()
                .idUser(1L)
                .email("admin@lysandri.com")
                .nombres("Admin")
                .apellidos("General")
                .passw("$2a$10$encodedHash")
                .rol(RolUsuario.ADMIN)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .build();
    }

    @Test
    @DisplayName("Generación y validación de token JWT para usuario activo con datos válidos")
    void generateToken_usuarioValido_generaTokenCorrecto() {
        String token = jwtService.generateToken(clienteUser);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("cliente@empresa.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, clienteUser));
    }

    @Test
    @DisplayName("Token JWT vencido: es rechazado por validación de expiración")
    void tokenVencido_esRechazado() {
        // Generar token con expiración negativa (en el pasado)
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(VALID_SECRET));
        String expiredToken = Jwts.builder()
                .subject(clienteUser.getUsername())
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // Expiró hace 1 hora
                .signWith(key)
                .compact();

        assertThrows(ExpiredJwtException.class, () ->
                jwtService.isTokenValid(expiredToken, clienteUser));
    }

    @Test
    @DisplayName("Token JWT alterado: la manipulación de la carga firma inválida y es rechazado")
    void tokenAlterado_esRechazado() {
        String validToken = jwtService.generateToken(clienteUser);
        String[] parts = validToken.split("\\.");
        assertEquals(3, parts.length);

        // Alterar el payload (segunda parte)
        String tamperedPayload = parts[1] + "tampered";
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThrows(JwtException.class, () ->
                jwtService.extractUsername(tamperedToken));
    }

    @Test
    @DisplayName("Token JWT firmado con otra clave secreta: es rechazado estrictamente")
    void tokenFirmadoConOtraClave_esRechazado() {
        SecretKey rogueKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(DIFFERENT_SECRET));
        String rogueToken = Jwts.builder()
                .subject(clienteUser.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(rogueKey)
                .compact();

        assertThrows(JwtException.class, () ->
                jwtService.isTokenValid(rogueToken, clienteUser));
    }

    @Test
    @DisplayName("Token con reclamo de rol falsificado por el cliente: JwtService extrae solo identidad; la autorización procede del servidor")
    void tokenConRolFalsificado_noEnganaAlServidor() {
        // Un atacante genera un token legítimo para su usuario pero con reclamo 'rol': 'ADMIN'
        Map<String, Object> forgedClaims = new HashMap<>();
        forgedClaims.put("rol", "ADMIN");
        forgedClaims.put("id", 10L);

        String forgedToken = jwtService.generateToken(forgedClaims, clienteUser);

        // El token valida que el sujeto es el cliente
        assertEquals("cliente@empresa.com", jwtService.extractUsername(forgedToken));

        // Pero los permisos reales proceden de clienteUser.getAuthorities() que devuelve ROLE_CLIENTE
        assertEquals(1, clienteUser.getAuthorities().size());
        assertEquals("ROLE_CLIENTE", clienteUser.getAuthorities().iterator().next().getAuthority());
        assertFalse(clienteUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("Token para un usuario no es válido para otro usuario diferente")
    void tokenDeUsuarioA_noEsValidoParaUsuarioB() {
        String tokenUserA = jwtService.generateToken(clienteUser);

        UsuarioEntity otroUser = UsuarioEntity.builder()
                .idUser(20L)
                .email("otro@empresa.com")
                .nombres("Otro")
                .apellidos("Usuario")
                .rol(RolUsuario.CLIENTE)
                .build();

        assertFalse(jwtService.isTokenValid(tokenUserA, otroUser));
    }
}

