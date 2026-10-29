package com.lysandri.adapters;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.model.UsuarioActivacionToken;
import com.lysandri.domain.ports.in.AuthUseCase;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import com.lysandri.domain.ports.out.UserActivationPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentTokenConsumptionIntegrationTest extends PostgresTestDatabase {

    @Autowired
    private AuthUseCase authUseCase;

    @Autowired
    private UserManagementUseCase userManagementUseCase;

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private UserActivationPort userActivationPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Consumo atomico concurrente: exactamente 1 solicitud consume el token y la otra es rechazada de forma controlada")
    void testConsumoAtomicoConcurrenteToken() throws Exception {
        // 1. Crear usuario pendiente de activacion
        String email = "concurrency_" + UUID.randomUUID() + "@lysandri.test";
        Usuario user = Usuario.builder()
                .nombres("Test")
                .apellidos("Concurrency")
                .email(email)
                .passw("$2a$10$dummyHashPendingActivationPlaceholder")
                .telefono("+51999888777")
                .rol(RolUsuario.CLIENTE)
                .activo(false)
                .bloqueadoAdmin(false)
                .pendienteActivacion(true)
                .build();
        user = userRepositoryPort.guardar(user);

        // 2. Generar token de activacion
        UsuarioActivacionToken tokenRecord = userActivationPort.crearTokenActivacion(user.getIdUser());
        assertNotNull(tokenRecord);
        String plainToken = tokenRecord.getToken();
        assertNotNull(plainToken);

        // 3. Dos solicitudes simultaneas usan el mismo token con contraseñas diferentes
        String winnerPass = "WinnerPass2026!";
        String loserPass = "LoserPass2026!";

        int numThreads = 2;
        CountDownLatch readyLatch = new CountDownLatch(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectionCount = new AtomicInteger(0);
        AtomicReference<String> winningPassword = new AtomicReference<>(null);

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                authUseCase.activarCuenta(new AuthUseCase.ActivarCuentaCommand(plainToken, winnerPass));
                successCount.incrementAndGet();
                winningPassword.set(winnerPass);
            } catch (Exception e) {
                rejectionCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            startLatch.await();
            try {
                authUseCase.activarCuenta(new AuthUseCase.ActivarCuentaCommand(plainToken, loserPass));
                successCount.incrementAndGet();
                winningPassword.set(loserPass);
            } catch (Exception e) {
                rejectionCount.incrementAndGet();
            }
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Disparar ambas peticiones concurrentemente

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Exactamente una tuvo exito, la otra fue rechazada
        assertEquals(1, successCount.get(), "Exactamente una solicitud concurrente debe tener exito");
        assertEquals(1, rejectionCount.get(), "La solicitud concurrente perdedora debe recibir un rechazo controlado");

        // 5. El token queda consumido una unica vez
        Integer usedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario_activacion_token WHERE id_user = ? AND usado = true",
                Integer.class, user.getIdUser());
        assertEquals(1, usedCount, "El token debe estar marcado como usado exactamente una vez");

        // 6. Solo la contraseña ganadora permite iniciar sesion; la perdedora es rechazada
        String winner = winningPassword.get();
        assertNotNull(winner);
        String loser = winner.equals(winnerPass) ? loserPass : winnerPass;

        AuthUseCase.AuthResponse loginResponse = authUseCase.login(new AuthUseCase.LoginCommand(email, winner));
        assertNotNull(loginResponse.token());
        assertEquals(email, loginResponse.email());

        assertThrows(BadCredentialsException.class, () ->
                authUseCase.login(new AuthUseCase.LoginCommand(email, loser)));
    }

    @Test
    @DisplayName("Separacion de activacion y bloqueo admin: token nunca reactiva una cuenta con bloqueo administrativo")
    void testSeparacionActivacionYBloqueoAdmin() {
        // 1. Crear usuario y token
        String email = "blocked_" + UUID.randomUUID() + "@lysandri.test";
        Usuario user = Usuario.builder()
                .nombres("Blocked")
                .apellidos("User")
                .email(email)
                .passw(passwordEncoder.encode("NewValidPass123!"))
                .telefono("+51999111222")
                .rol(RolUsuario.CLIENTE)
                .activo(false)
                .bloqueadoAdmin(false)
                .pendienteActivacion(true)
                .build();
        user = userRepositoryPort.guardar(user);

        UsuarioActivacionToken tokenRecord = userActivationPort.crearTokenActivacion(user.getIdUser());
        String plainToken = tokenRecord.getToken();

        // 2. Administrador bloquea la cuenta antes de la activacion
        userManagementUseCase.desactivarUsuario(user.getIdUser(), "Bloqueo administrativo de seguridad", "admin@lysandri.com");

        Usuario userBlocked = userRepositoryPort.buscarPorId(user.getIdUser()).orElseThrow();
        assertTrue(userBlocked.isBloqueadoAdmin(), "La cuenta debe registrar bloqueo administrativo explicito");
        assertFalse(userBlocked.isActivo(), "La cuenta debe permanecer inactiva");

        // 3. El token previo debe haber sido revocado y no puede reactivar la cuenta
        assertThrows(IllegalStateException.class, () ->
                authUseCase.activarCuenta(new AuthUseCase.ActivarCuentaCommand(plainToken, "NewValidPass123!")));

        Usuario userStillBlocked = userRepositoryPort.buscarPorId(user.getIdUser()).orElseThrow();
        assertTrue(userStillBlocked.isBloqueadoAdmin(), "El token nunca debe levantar un bloqueo administrativo");
        assertFalse(userStillBlocked.isActivo());

        // 4. Intentar login en cuenta bloqueada administrativamente debe lanzar LockedException
        assertThrows(LockedException.class, () ->
                authUseCase.login(new AuthUseCase.LoginCommand(email, "NewValidPass123!")));
    }

    @Test
    @DisplayName("Recuperacion segura para cuentas provisionales o enlaces expirados")
    void testRecuperacionSeguraCuentaProvisional() {
        // 1. Cuenta provisional V26 (pendienteActivacion = true, activo = false, bloqueadoAdmin = false)
        String email = "recovery_" + UUID.randomUUID() + "@lysandri.test";
        Usuario user = Usuario.builder()
                .nombres("Recoverable")
                .apellidos("Guest")
                .email(email)
                .passw("$2a$10$v26InvalidatedProvisionalPlaceholder")
                .telefono("+51999333444")
                .rol(RolUsuario.CLIENTE)
                .activo(false)
                .bloqueadoAdmin(false)
                .pendienteActivacion(true)
                .build();
        user = userRepositoryPort.guardar(user);

        // 2. Solicitar nuevo enlace de activacion
        AuthUseCase.MensajePublicoResponse response = authUseCase.solicitarNuevoEnlaceActivacion(
                new AuthUseCase.SolicitarActivacionCommand(email));
        assertNotNull(response.mensaje());
        assertEquals("Si el correo electrónico corresponde a una cuenta pendiente de activación, se ha enviado un enlace a su bandeja de entrada.", response.mensaje());

        // 3. Comprobar que existe exactamente un token activo no revocado
        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario_activacion_token WHERE id_user = ? AND usado = false AND revocado = false",
                Integer.class, user.getIdUser());
        assertEquals(1, activeTokens, "Debe existir un token activo nuevo para la recuperacion");
    }
}
