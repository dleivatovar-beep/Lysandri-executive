package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, String> {

    Optional<Instructor> findByUsuarioIdUser(Integer idUser);
}
