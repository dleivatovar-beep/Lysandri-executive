package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.Usuario;

public interface AuthUseCase {

    record RegisterCommand(
            String nombres,
            String apellidos,
            String email,
            String password,
            String telefono
    ) {}

    record LoginCommand(
            String email,
            String password
    ) {}

    record AuthResponse(
            String token,
            Long idUser,
            String email,
            String nombreCompleto,
            String rol,
            Long moodleUserId
    ) {}

    AuthResponse register(RegisterCommand command);

    AuthResponse login(LoginCommand command);

    Usuario obtenerUsuarioAutenticado(String email);
}
