package com.lysandri.api.repository;

import com.lysandri.api.model.entity.ChatMensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMensajeRepository extends JpaRepository<ChatMensaje, Integer> {

    List<ChatMensaje> findByUsuarioIdUserOrderByIdMensajeAsc(Integer idUser);
}
