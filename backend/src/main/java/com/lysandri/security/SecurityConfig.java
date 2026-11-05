package com.lysandri.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:4173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write(
                                    "{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Autenticación requerida o token inválido\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write(
                                    "{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":403,\"error\":\"Forbidden\",\"message\":\"Acceso denegado: permisos insuficientes\"}"
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Preflight OPTIONS para navegadores
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Documentación Swagger y OpenAPI
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error"
                        ).permitAll()

                        // Autenticación pública
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/activar-cuenta", "/api/v1/auth/solicitar-activacion").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()

                        // Catálogo público de programas (solo lectura)
                        .requestMatchers(HttpMethod.GET, "/api/v1/programas/**").permitAll()

                        // Formulario de contacto / lead corporativo público
                        .requestMatchers(HttpMethod.POST, "/api/v1/solicitudes-informacion").permitAll()

                        // Checkout y confirmación de pago (permite compra como invitado y confirmación frontend verificada)
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/cuentas-bancarias").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/guest-session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/checkout", "/api/v1/orders/create-checkout-session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/confirm/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/mis-ordenes").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").authenticated() // Consulta de orden por código requiere autenticación y ownership

                        // Webhooks asíncronos de pasarelas de pago (validan firma internamente)
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()

                        // Webhook oficial de WhatsApp Cloud API (público, valida token en GET y firma HMAC en POST)
                        .requestMatchers("/api/v1/whatsapp/webhook/**", "/api/v1/whatsapp/webhook").permitAll()
                        .requestMatchers("/api/v1/whatsapp/**").hasAnyRole("ADMIN", "TRABAJADOR", "COORDINADOR_ACADEMICO")

                        // Asistente IA (Chat - módulo intacto)
                        .requestMatchers("/api/v1/chat/**", "/api/chat/**").permitAll()

                        // Gestión de usuarios: acceso restringido al personal interno autorizado y administración
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios").hasAnyRole("ADMIN", "TRABAJADOR", "COORDINADOR_ACADEMICO", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/usuarios/*/rol", "/api/v1/usuarios/*/desactivar").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/*/historial").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/usuarios/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/*").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/*").authenticated()

                        // Operaciones administrativas de órdenes y gestión del flujo comercial
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "FINANZAS", "CONTABILIDAD", "AUDITOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/confirmar-pago-manual").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "FINANZAS", "CONTABILIDAD")
                        .requestMatchers("/api/v1/admin/orders/**").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "FINANZAS", "CONTABILIDAD")

                        // Operaciones corporativas masivas y gestión B2B
                        .requestMatchers("/api/v1/corporativo/**", "/api/v1/admin/corporativo/**").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "COORDINADOR_ACADEMICO")
                        .requestMatchers("/api/v1/admin/solicitudes-informacion/**").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "COORDINADOR_ACADEMICO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/solicitudes-informacion/**").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "COORDINADOR_ACADEMICO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/solicitudes-informacion/**").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "COORDINADOR_ACADEMICO")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/solicitudes-informacion/**").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "COORDINADOR_ACADEMICO")

                        // Endpoints contables internos y de auditoría tributaria SIRE
                        .requestMatchers("/api/v1/internal/**").hasAnyRole("ADMIN", "CONTABILIDAD", "FINANZAS", "AUDITOR", "TRABAJADOR")

                        // Prefijo general de administración
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "TRABAJADOR", "VENTAS", "FINANZAS", "CONTABILIDAD", "COORDINADOR_ACADEMICO", "AUDITOR")

                        // Cualquier otra petición debe estar autenticada
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Audit-PIN", "Stripe-Signature", "X-Requested-With", "X-Idempotency-Key", "X-Guest-Session-Token"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization", "Content-Disposition", "X-Idempotency-Key", "X-Guest-Session-Token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
