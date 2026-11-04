package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
    Optional<ChatSessionEntity> findByIdSesion(String idSesion);
}

