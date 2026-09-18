package com.lysandri.api.service;

import com.lysandri.api.dto.request.ChatRequest;
import com.lysandri.api.dto.response.ChatResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.model.entity.ChatMensaje;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.model.enums.Rol;
import com.lysandri.api.repository.ChatMensajeRepository;
import com.lysandri.api.repository.InscripcionRepository;
import com.lysandri.api.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMensajeRepository chatMensajeRepository;

    @Mock
    private InscripcionRepository inscripcionRepository;

    @InjectMocks
    private ChatServiceImpl chatService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .idUser(5)
                .nombres("Carlos")
                .apellidos("Mendoza")
                .email("carlos@lysandri.com")
                .rol(Rol.ESTUDIANTE)
                .build();
    }

    @Test
    void procesarMensaje_Exito() {
        ChatRequest request = ChatRequest.builder()
                .mensaje("¿Cuál es el ROI de migrar a arquitectura con Kafka?")
                .build();

        when(chatMensajeRepository.save(any(ChatMensaje.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatResponse response = chatService.procesarMensaje(request, usuario);

        assertNotNull(response);
        assertNotNull(response.getRespuesta());
        assertTrue(response.getRespuesta().contains("Kafka"));
        assertNotNull(response.getFuentes());
        assertFalse(response.getFuentes().isEmpty());
        assertNotNull(response.getFechaEnvio());

        verify(chatMensajeRepository).save(any(ChatMensaje.class));
    }

    @Test
    void procesarMensaje_ConProgramaIdValido_AgregaContexto() {
        ChatRequest request = ChatRequest.builder()
                .mensaje("Revisar modelo de costos FinOps para EKS")
                .programaId(1L)
                .build();

        when(inscripcionRepository.existsByUsuarioIdUserAndProgramaIdPrograma(5, 1)).thenReturn(true);
        when(chatMensajeRepository.save(any(ChatMensaje.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatResponse response = chatService.procesarMensaje(request, usuario);

        assertNotNull(response);
        assertTrue(response.getRespuesta().contains("FinOps"));
        assertTrue(response.getRespuesta().contains("programa #1"));
        verify(inscripcionRepository).existsByUsuarioIdUserAndProgramaIdPrograma(5, 1);
        verify(chatMensajeRepository).save(any(ChatMensaje.class));
    }

    @Test
    void procesarMensaje_UsuarioNulo_LanzaBadRequestException() {
        ChatRequest request = ChatRequest.builder()
                .mensaje("Hola")
                .build();

        assertThrows(BadRequestException.class, () -> chatService.procesarMensaje(request, null));
        verifyNoInteractions(chatMensajeRepository);
    }
}
