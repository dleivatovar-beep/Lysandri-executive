package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.CreateOrderRequest;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Órdenes y Checkout", description = "Procesamiento de compras y matrículas automáticas en Moodle")
public class OrderController {

    private final BuyCourseUseCase buyCourseUseCase;

    @PostMapping("/checkout")
    @Operation(summary = "Inicia el proceso de checkout y retorna URL de Stripe Checkout")
    public ResponseEntity<BuyCourseUseCase.CheckoutSessionResponse> iniciarCompra(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @Valid @RequestBody CreateOrderRequest request) {

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                usuario.getIdUser(),
                request.getProgramaIds()
        );

        BuyCourseUseCase.CheckoutSessionResponse response = buyCourseUseCase.iniciarCompra(command);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm/{sessionId}")
    @Operation(summary = "Confirma el pago de la orden y matricula al usuario en Moodle LMS")
    public ResponseEntity<Orden> confirmarPago(@PathVariable String sessionId) {
        log.info("Llamada de confirmación de pago para sesión Stripe: {}", sessionId);
        Orden orden = buyCourseUseCase.confirmarPagoYMatricular(sessionId);
        return ResponseEntity.ok(orden);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Obtiene el detalle de una orden por su código")
    public ResponseEntity<Orden> obtenerPorCodigo(@PathVariable String codigo) {
        Orden orden = buyCourseUseCase.obtenerOrdenPorCodigo(codigo);
        return ResponseEntity.ok(orden);
    }
}
