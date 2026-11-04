package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ChatRequest;
import com.lysandri.adapters.out.database.entity.ChatSessionEntity;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.adapters.out.database.repository.SpringDataChatSessionRepository;
import com.lysandri.adapters.out.database.repository.SpringDataHistorialRepository;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.in.ChatRagUseCase;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatRagUseCase chatRagUseCase;

    @Mock
    private ProgramRepositoryPort programRepository;

    @Mock
    private SpringDataHistorialRepository historialRepository;

    @Mock
    private SpringDataChatSessionRepository chatSessionRepository;

    @InjectMocks
    private ChatController chatController;

    private UsuarioEntity usuarioMock;
    private Programa programaActivo;
    private Programa programaInactivo;

    private String testToken;
    private String testTokenHash;

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        usuarioMock = UsuarioEntity.builder()
                .idUser(5L)
                .email("ejecutivo@corporativo.com")
                .nombres("Carlos")
                .apellidos("Mendoza")
                .build();

        programaActivo = Programa.builder()
                .idPrograma(101L)
                .titulo("FinOps")
                .activo(true)
                .precioAprobado(true)
                .precio(new BigDecimal("799.00"))
                .build();

        programaInactivo = Programa.builder()
                .idPrograma(102L)
                .titulo("FinOps Inactivo")
                .activo(false)
                .precioAprobado(false)
                .precio(new BigDecimal("799.00"))
                .build();

        testToken = "secret-token-123456789";
        testTokenHash = hashToken(testToken);
    }

    @Test
    void consultar_conPreguntaValida_debeRetornar200ConRespuestaYFuentes() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("¿Cuáles son los requisitos de admisión?");
        req.setIdPrograma(101L);

        when(programRepository.buscarPorId(101L)).thenReturn(Optional.of(programaActivo));

        ChatRagUseCase.ChatRagResponse mockResp = new ChatRagUseCase.ChatRagResponse(
                "Los requisitos son título universitario y experiencia gerencial.",
                List.of(new ChatRagUseCase.SourceCitation(1L, 2, "Requisitos...", 0.92)),
                120,
                85L
        );
        when(chatRagUseCase.procesarConsulta(any())).thenReturn(mockResp);

        ResponseEntity<Map<String, Object>> response = chatController.consultar(usuarioMock, null, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Los requisitos son título universitario y experiencia gerencial.", response.getBody().get("respuesta"));
        assertNotNull(response.getBody().get("sesionId"));
        assertNotNull(response.getBody().get("sessionToken"));
        assertEquals(1, ((List<?>) response.getBody().get("fuentes")).size());
        verify(chatSessionRepository).save(any(ChatSessionEntity.class));
    }

    @Test
    void consultar_conPreguntaVacia_debeRetornar400() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("   ");

        ResponseEntity<Map<String, Object>> response = chatController.consultar(usuarioMock, null, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("no puede estar vacío"));
    }

    @Test
    void consultar_conPreguntaMayorA1000Caracteres_debeRetornar400() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("A".repeat(1001));

        ResponseEntity<Map<String, Object>> response = chatController.consultar(usuarioMock, null, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("exceder"));
    }

    @Test
    void consultar_conViolacionIdor_debeRetornar403() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("Consulta válida");
        req.setIdUsuario(99L); // Diferente a usuarioMock.idUser = 5L

        ResponseEntity<Map<String, Object>> response = chatController.consultar(usuarioMock, null, req);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("No está autorizado a enviar consultas"));
    }

    @Test
    void consultar_conProgramaInactivo_debeRetornar400() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("Consulta sobre programa");
        req.setIdPrograma(102L);

        when(programRepository.buscarPorId(102L)).thenReturn(Optional.of(programaInactivo));

        ResponseEntity<Map<String, Object>> response = chatController.consultar(usuarioMock, null, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("no está activo"));
    }

    @Test
    void consultar_conSesionDeOtroUsuario_debeRetornar403() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("Consulta");
        req.setSesionId("session-user-99");

        ChatSessionEntity sesionAjena = ChatSessionEntity.builder()
                .idSesion("session-user-99")
                .idUser(99L)
                .sessionTokenHash(testTokenHash)
                .fechaCreacion(OffsetDateTime.now())
                .ultimaActividad(OffsetDateTime.now())
                .build();

        when(chatSessionRepository.findByIdSesion("session-user-99")).thenReturn(Optional.of(sesionAjena));

        ResponseEntity<Map<String, Object>> response = chatController.consultar(usuarioMock, null, req);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("No autorizado para interactuar"));
    }

    @Test
    void consultar_sesionAnonima_sinTokenValido_debeRetornar403() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("Consulta anónima");
        req.setSesionId("anon-session-1");
        req.setSessionToken("token-invalido");

        ChatSessionEntity sesionAnonima = ChatSessionEntity.builder()
                .idSesion("anon-session-1")
                .idUser(null)
                .sessionTokenHash(testTokenHash)
                .fechaCreacion(OffsetDateTime.now())
                .ultimaActividad(OffsetDateTime.now())
                .build();

        when(chatSessionRepository.findByIdSesion("anon-session-1")).thenReturn(Optional.of(sesionAnonima));

        ResponseEntity<Map<String, Object>> response = chatController.consultar(null, null, req);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("inválido o ausente"));
    }

    @Test
    void consultar_sesionAnonima_conTokenCorrecto_debeRetornar200() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("Consulta anónima legítima");
        req.setSesionId("anon-session-1");
        req.setSessionToken(testToken);

        ChatSessionEntity sesionAnonima = ChatSessionEntity.builder()
                .idSesion("anon-session-1")
                .idUser(null)
                .sessionTokenHash(testTokenHash)
                .fechaCreacion(OffsetDateTime.now())
                .ultimaActividad(OffsetDateTime.now())
                .build();

        when(chatSessionRepository.findByIdSesion("anon-session-1")).thenReturn(Optional.of(sesionAnonima));

        ChatRagUseCase.ChatRagResponse mockResp = new ChatRagUseCase.ChatRagResponse(
                "Respuesta anónima autorizada",
                List.of(),
                50,
                30L
        );
        when(chatRagUseCase.procesarConsulta(any())).thenReturn(mockResp);

        ResponseEntity<Map<String, Object>> response = chatController.consultar(null, null, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Respuesta anónima autorizada", response.getBody().get("respuesta"));
        assertEquals("anon-session-1", response.getBody().get("sesionId"));
        assertEquals(testToken, response.getBody().get("sessionToken"));
    }

    @Test
    void consultar_usuarioAutenticado_intentandoUsurparSesionAnonima_debeRetornar403() {
        ChatRequest req = new ChatRequest();
        req.setPregunta("Intento de usurpar sesión anónima");
        req.setSesionId("anon-session-1");
        req.setSessionToken(testToken);

        ChatSessionEntity sesionAnonima = ChatSessionEntity.builder()
                .idSesion("anon-session-1")
                .idUser(null)
                .sessionTokenHash(testTokenHash)
                .fechaCreacion(OffsetDateTime.now())
                .ultimaActividad(OffsetDateTime.now())
                .build();

        when(chatSessionRepository.findByIdSesion("anon-session-1")).thenReturn(Optional.of(sesionAnonima));

        ResponseEntity<Map<String, Object>> response = chatController.consultar(usuarioMock, null, req);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("transferir sesiones anónimas"));
    }
}
