package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.EnviarWhatsAppRequest;
import com.lysandri.domain.model.whatsapp.MensajeWhatsApp;
import com.lysandri.domain.ports.in.WhatsAppUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Operador", description = "Gestión autorizada de atención al cliente por WhatsApp")
public class WhatsAppOperadorController {

    private final WhatsAppUseCase whatsAppUseCase;

    @PostMapping("/mensajes")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Envío de mensaje WhatsApp por un operador autorizado")
    public ResponseEntity<MensajeWhatsApp> enviarMensaje(
            @Valid @RequestBody EnviarWhatsAppRequest request,
            Authentication authentication
    ) {
        String operador = authentication != null ? authentication.getName() : "operador";
        MensajeWhatsApp resultado = whatsAppUseCase.enviarMensajeOperador(
                request.getDestinatario(),
                request.getMensaje(),
                operador,
                request.getIdSolicitud()
        );
        return new ResponseEntity<>(resultado, HttpStatus.CREATED);
    }

    @GetMapping("/conversaciones/{telefono}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Consulta autorizada del historial de conversación de un número")
    public ResponseEntity<List<MensajeWhatsApp>> consultarConversacion(
            @PathVariable String telefono
    ) {
        List<MensajeWhatsApp> mensajes = whatsAppUseCase.consultarConversacion(telefono);
        return ResponseEntity.ok(mensajes);
    }
}
