package com.lysandri.adapters.in.web;

import com.lysandri.domain.ports.in.WhatsAppUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/whatsapp/webhook")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Webhook", description = "Recepción de webhooks oficiales de Meta WhatsApp Business Cloud API")
public class WhatsAppWebhookController {

    private final WhatsAppUseCase whatsAppUseCase;

    @GetMapping
    @Operation(summary = "Verificación inicial del Webhook (hub.challenge)")
    public ResponseEntity<String> verificarWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        String resultChallenge = whatsAppUseCase.verificarWebhook(mode, verifyToken, challenge);
        return ResponseEntity.ok(resultChallenge);
    }

    @PostMapping
    @Operation(summary = "Recepción de eventos y mensajes entrantes con firma HMAC-SHA256")
    public ResponseEntity<Void> recibirNotificacion(
            @RequestBody String rawPayload,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signatureHeader
    ) {
        whatsAppUseCase.procesarWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}

