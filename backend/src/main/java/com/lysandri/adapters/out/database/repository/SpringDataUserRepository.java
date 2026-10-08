package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.RolUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataUserRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UsuarioEntity> findByRol(RolUsuario rol);

    @Query("SELECT u FROM UsuarioEntity u WHERE " +
           "LOWER(u.nombres) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.apellidos) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<UsuarioEntity> buscarPorTexto(@Param("query") String query);
}
