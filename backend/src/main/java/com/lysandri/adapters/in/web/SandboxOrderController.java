package com.lysandri.adapters.in.web;

import com.lysandri.application.service.SandboxOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/sandbox")
@RequiredArgsConstructor
@Validated
@org.springframework.context.annotation.Profile("(clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name = "purchase.sandbox.enabled", havingValue = "true")
public class SandboxOrderController {

    private final SandboxOrderService sandboxOrderService;

    public record ProgramRequest(
            @Positive Long id,
            @NotBlank @Size(max = 200) String titulo
    ) {}

    public record CreateRequest(
            @NotBlank @Size(max = 150) String claveIdempotencia,
            @NotBlank @Size(min = 3, max = 160) String nombre,
            @NotBlank @jakarta.validation.constraints.Email @Size(max = 200) String email,
            @Size(max = 40) String telefono,
            @NotEmpty @Size(max = 20) List<@Valid ProgramRequest> programas
    ) {}

    @PostMapping
    public ResponseEntity<SandboxOrderService.SandboxOrderResponse> create(
            @Valid @RequestBody CreateRequest request) {
        var serviceRequest = new SandboxOrderService.SandboxOrderRequest(
                request.claveIdempotencia(),
                request.nombre(),
                request.email(),
                request.telefono(),
                request.programas().stream()
                        .map(program -> new SandboxOrderService.SandboxProgramRequest(program.id(), program.titulo()))
                        .toList()
        );
        return ResponseEntity.ok(sandboxOrderService.createOrder(serviceRequest));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SandboxOrderService.SandboxOrderResponse>> list() {
        return ResponseEntity.ok(sandboxOrderService.listOrders());
    }
}
