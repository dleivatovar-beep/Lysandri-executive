package com.lysandri.domain.ports.out;

import java.util.Optional;

public interface LmsClientPort {

    /**
     * Busca un usuario en Moodle por su correo electrónico.
     */
    Optional<Long> buscarUsuarioPorEmail(String email);

    /**
     * Registra un nuevo usuario en Moodle mediante core_user_create_users.
     *
     * @return ID asignado en Moodle (moodle_user_id).
     */
    Long crearUsuario(String username, String password, String firstname, String lastname, String email);

    /**
     * Matricula a un usuario en un curso específico de Moodle usando enrol_manual_enrol_users.
     *
     * @param moodleUserId ID del usuario en Moodle.
     * @param moodleCourseId ID del curso en Moodle.
     * @param roleId ID de rol en Moodle (5 = Student por defecto).
     */
    void matricularUsuarioEnCurso(Long moodleUserId, Long moodleCourseId, int roleId);
}
