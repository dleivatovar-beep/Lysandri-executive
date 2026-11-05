package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.UsuarioActivacionTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataUsuarioActivacionTokenRepository extends JpaRepository<UsuarioActivacionTokenEntity, Long> {

    Optional<UsuarioActivacionTokenEntity> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM UsuarioActivacionTokenEntity t WHERE t.token = :token")
    Optional<UsuarioActivacionTokenEntity> findByTokenForUpdate(@Param("token") String token);

    @Query("SELECT t FROM UsuarioActivacionTokenEntity t WHERE t.idUser = :idUser AND t.usado = false AND t.revocado = false")
    List<UsuarioActivacionTokenEntity> findActiveTokensByUser(@Param("idUser") Long idUser);

    @Modifying
    @Query("UPDATE UsuarioActivacionTokenEntity t SET t.revocado = true, t.fechaRevocacion = :fechaRevocacion WHERE t.idUser = :idUser AND t.usado = false AND t.revocado = false")
    int revocarTokensActivosUsuario(@Param("idUser") Long idUser, @Param("fechaRevocacion") OffsetDateTime fechaRevocacion);
}
