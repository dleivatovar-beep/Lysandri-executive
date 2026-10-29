package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.UsuarioActivacionTokenEntity;
import com.lysandri.adapters.out.database.repository.SpringDataUsuarioActivacionTokenRepository;
import com.lysandri.domain.model.UsuarioActivacionToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserActivationRepositoryAdapterTest {

    @Mock
    private SpringDataUsuarioActivacionTokenRepository repository;

    @InjectMocks
    private UserActivationRepositoryAdapter adapter;

    private Long userId;
    private Long tokenId;
    private String plainToken;
    private String tokenHash;
    private UsuarioActivacionTokenEntity entity;

    @BeforeEach
    void setUp() {
        userId = 42L;
        tokenId = 101L;
        plainToken = "sec_act_token_test_abc123_xyz";
        tokenHash = UserActivationRepositoryAdapter.hashToken(plainToken);

        entity = UsuarioActivacionTokenEntity.builder()
                .idToken(tokenId)
                .idUser(userId)
                .token(tokenHash)
                .fechaExpiracion(OffsetDateTime.now().plusHours(48))
                .usado(false)
                .revocado(false)
                .fechaCreacion(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("crearTokenActivacion: Almacena exclusivamente el hash SHA-256 en base de datos")
    void testCrearTokenActivacionAlmacenaExclusivamenteHash() {
        when(repository.save(any(UsuarioActivacionTokenEntity.class))).thenAnswer(inv -> {
            UsuarioActivacionTokenEntity e = inv.getArgument(0);
            e.setIdToken(tokenId);
            return e;
        });

        UsuarioActivacionToken created = adapter.crearTokenActivacion(userId);

        assertNotNull(created);
        assertEquals(userId, created.getIdUser());
        // El objeto de dominio entrega transitoriamente el token en claro para envío por correo
        assertNotNull(created.getToken());
        assertFalse(created.getToken().isBlank());

        ArgumentCaptor<UsuarioActivacionTokenEntity> captor = ArgumentCaptor.forClass(UsuarioActivacionTokenEntity.class);
        verify(repository).save(captor.capture());
        UsuarioActivacionTokenEntity saved = captor.getValue();

        // En la base de datos se guarda estrictamente el hash (longitud 64 hex), nunca el valor en claro
        assertEquals(64, saved.getToken().length());
        assertEquals(UserActivationRepositoryAdapter.hashToken(created.getToken()), saved.getToken());
        assertNotEquals(created.getToken(), saved.getToken());
    }

    @Test
    @DisplayName("buscarPorToken: El token original recibido por el cliente permite encontrar el registro")
    void testBuscarPorTokenOriginalExitoso() {
        when(repository.findByToken(eq(tokenHash))).thenReturn(Optional.of(entity));

        Optional<UsuarioActivacionToken> result = adapter.buscarPorToken(plainToken);

        assertTrue(result.isPresent());
        assertEquals(tokenId, result.get().getIdToken());
        assertEquals(userId, result.get().getIdUser());
        verify(repository).findByToken(tokenHash);
        // Verificar que no se invoca búsqueda por valor en claro
        verify(repository, never()).findByToken(plainToken.trim());
    }

    @Test
    @DisplayName("Hallazgo 1: El hash leido de la base, enviado como token por un atacante, es estrictamente RECHAZADO")
    void testHashEnviadoComoTokenEsRechazado() {
        // Un atacante obtiene el hash almacenado en la columna 'token' y lo envía como credencial
        String attackerInput = tokenHash;
        String hashOfHash = UserActivationRepositoryAdapter.hashToken(attackerInput);

        // La base de datos contiene el registro indexado por tokenHash, NO por hashOfHash
        when(repository.findByToken(eq(hashOfHash))).thenReturn(Optional.empty());

        Optional<UsuarioActivacionToken> result = adapter.buscarPorToken(attackerInput);

        // Debe retornar empty: el hash almacenado NO funciona como credencial
        assertTrue(result.isEmpty(), "El hash almacenado nunca debe servir como token de activación");
        verify(repository).findByToken(hashOfHash);
        // No debe existir fallback vulnerable
        verify(repository, never()).findByToken(attackerInput);
    }

    @Test
    @DisplayName("buscarPorTokenConBloqueo: El hash enviado como token es rechazado y no adquiere bloqueo")
    void testBuscarPorTokenConBloqueoRechazaHashAlmacenado() {
        String attackerInput = tokenHash;
        String hashOfHash = UserActivationRepositoryAdapter.hashToken(attackerInput);

        when(repository.findByTokenForUpdate(eq(hashOfHash))).thenReturn(Optional.empty());

        Optional<UsuarioActivacionToken> result = adapter.buscarPorTokenConBloqueo(attackerInput);

        assertTrue(result.isEmpty());
        verify(repository).findByTokenForUpdate(hashOfHash);
        verify(repository, never()).findByTokenForUpdate(attackerInput);
    }

    @Test
    @DisplayName("Tokens nulos o en blanco retornan Optional.empty() sin consultar el repositorio")
    void testTokensNulosOEnBlanco() {
        assertTrue(adapter.buscarPorToken(null).isEmpty());
        assertTrue(adapter.buscarPorToken("").isEmpty());
        assertTrue(adapter.buscarPorToken("   ").isEmpty());
        assertTrue(adapter.buscarPorTokenConBloqueo(null).isEmpty());
        assertTrue(adapter.buscarPorTokenConBloqueo("").isEmpty());

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("marcarTokenUsado: Actualiza usado y fechaUso de forma duradera")
    void testMarcarTokenUsado() {
        when(repository.findById(tokenId)).thenReturn(Optional.of(entity));

        adapter.marcarTokenUsado(tokenId);

        assertTrue(entity.isUsado());
        assertNotNull(entity.getFechaUso());
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("revocarTokensUsuario: Revoca tokens activos del usuario")
    void testRevocarTokensUsuario() {
        when(repository.revocarTokensActivosUsuario(eq(userId), any(OffsetDateTime.class))).thenReturn(2);

        adapter.revocarTokensUsuario(userId);

        verify(repository).revocarTokensActivosUsuario(eq(userId), any(OffsetDateTime.class));
    }
}
