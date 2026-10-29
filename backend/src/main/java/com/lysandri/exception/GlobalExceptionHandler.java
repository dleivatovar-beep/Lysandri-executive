package com.lysandri.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String msg = com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage());
        log.warn("Solicitud inválida (IllegalArgumentException): {}", msg);
        return buildResponse(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        String msg = com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage());
        if (msg != null && msg.toLowerCase().contains("límite de solicitudes")) {
            log.warn("Límite de solicitudes excedido (429): {}", msg);
            return buildResponse(HttpStatus.TOO_MANY_REQUESTS, msg);
        }
        log.warn("Conflicto de estado (IllegalStateException): {}", msg);
        return buildResponse(HttpStatus.CONFLICT, msg);
    }

    public static final String MENSAJE_ERROR_LOGIN = "No se pudo iniciar sesión con los datos proporcionados";

    @ExceptionHandler({
            BadCredentialsException.class,
            DisabledException.class,
            LockedException.class,
            org.springframework.security.authentication.AccountStatusException.class,
            org.springframework.security.core.userdetails.UsernameNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleAuthenticationFailure(AuthenticationException ex) {
        log.warn("Fallo de autenticación en acceso de usuarios: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
        return buildResponse(HttpStatus.UNAUTHORIZED, MENSAJE_ERROR_LOGIN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Fallo de autenticación general: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
        return buildResponse(HttpStatus.UNAUTHORIZED, "Autenticación requerida o token inválido");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
        return buildResponse(HttpStatus.FORBIDDEN, "Acceso denegado: permisos insuficientes");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), com.lysandri.security.SanitizerUtils.sanitizeMessage(error.getDefaultMessage()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validación fallida");
        body.put("details", fieldErrors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Cuerpo de solicitud mal formado o campo con formato inválido: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
        return buildResponse(HttpStatus.BAD_REQUEST, "Solicitud mal formada o campo con formato inválido");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Excepción no controlada en backend: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error interno en el servidor. Por favor, contacte con soporte técnico.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message != null ? com.lysandri.security.SanitizerUtils.sanitizeMessage(message) : "");
        return new ResponseEntity<>(body, status);
    }
}
