package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.UsuarioActivacionTokenEntity;
import com.lysandri.adapters.out.database.repository.SpringDataUsuarioActivacionTokenRepository;
import com.lysandri.domain.model.UsuarioActivacionToken;
import com.lysandri.domain.ports.out.UserActivationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivationRepositoryAdapter implements UserActivationPort {

    private final SpringDataUsuarioActivacionTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public static String hashToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainToken.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error al calcular hash SHA-256 del token", e);
        }
    }

    @Override
    @Transactional
    public UsuarioActivacionToken crearTokenActivacion(Long idUser) {
        // Política explícita de renovación: invalidar tokens previos no consumidos
        repository.revocarTokensActivosUsuario(idUser, OffsetDateTime.now());

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashToken(plainToken);

        UsuarioActivacionTokenEntity entity = UsuarioActivacionTokenEntity.builder()
                .idUser(idUser)
                .token(tokenHash) // Se almacena exclusivamente el hash criptográfico SHA-256 en base de datos
                .fechaExpiracion(OffsetDateTime.now().plusHours(48))
                .usado(false)
                .revocado(false)
                .fechaCreacion(OffsetDateTime.now())
                .build();

        UsuarioActivacionTokenEntity guardado = repository.save(entity);
        log.info("Token de activación generado de forma segura para usuario ID: {} (almacenado como hash SHA-256)", idUser);

        UsuarioActivacionToken domain = toDomain(guardado);
        // Entregar el token en claro de un solo uso únicamente en el objeto retornado transitoriamente para envío de correo
        domain.setToken(plainToken);
        return domain;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsuarioActivacionToken> buscarPorToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hashToken(plainToken);
        if (tokenHash.isBlank()) {
            return Optional.empty();
        }
        // Búsqueda exclusivamente por el hash criptográfico SHA-256 del token provisto
        return repository.findByToken(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<UsuarioActivacionToken> buscarPorTokenConBloqueo(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hashToken(plainToken);
        if (tokenHash.isBlank()) {
            return Optional.empty();
        }
        // Búsqueda pesimista con bloqueo FOR UPDATE exclusivamente por el hash criptográfico SHA-256
        return repository.findByTokenForUpdate(tokenHash).map(this::toDomain);
    }

    @Override
    @Transactional
    public void marcarTokenUsado(Long idToken) {
        repository.findById(idToken).ifPresent(entity -> {
            entity.setUsado(true);
            entity.setFechaUso(OffsetDateTime.now());
            repository.save(entity);
            log.info("Token de activación ID: {} marcado como utilizado.", idToken);
        });
    }

    @Override
    @Transactional
    public void revocarTokensUsuario(Long idUser) {
        if (idUser != null) {
            int revocados = repository.revocarTokensActivosUsuario(idUser, OffsetDateTime.now());
            if (revocados > 0) {
                log.info("Se revocaron {} token(s) de activación activos para el usuario ID: {}", revocados, idUser);
            }
        }
    }

    private UsuarioActivacionToken toDomain(UsuarioActivacionTokenEntity entity) {
        return UsuarioActivacionToken.builder()
                .idToken(entity.getIdToken())
                .idUser(entity.getIdUser())
                .token(entity.getToken())
                .fechaExpiracion(entity.getFechaExpiracion())
                .usado(entity.isUsado())
                .fechaUso(entity.getFechaUso())
                .revocado(entity.isRevocado())
                .fechaRevocacion(entity.getFechaRevocacion())
                .fechaCreacion(entity.getFechaCreacion())
                .build();
    }
}
